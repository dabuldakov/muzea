package com.example.muzea.ui.chat

import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzea.R
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.databinding.FragmentChatConversationBinding
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatConversationFragment : Fragment() {

    private var _binding: FragmentChatConversationBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ChatConversationViewModel
    private lateinit var adapter: MessageAdapter
    private var chatUuid: String = ""
    private var lastMessageCount = 0

    companion object {
        private const val ARG_CHAT_UUID = "chat_uuid"
        private const val ARG_CHAT_TITLE = "chat_title"

        fun newInstance(chatUuid: String, chatTitle: String): ChatConversationFragment {
            return ChatConversationFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_UUID, chatUuid)
                    putString(ARG_CHAT_TITLE, chatTitle)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatConversationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatUuid = arguments?.getString(ARG_CHAT_UUID) ?: ""
        val chatTitle = arguments?.getString(ARG_CHAT_TITLE) ?: "Chat"

        binding.tvTitle.text = chatTitle
        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        initViewModel()
        setupRecyclerView()
        setupInput()
        observeViewModel()
    }

    private fun initViewModel() {
        val tokenManager = TokenManager(requireContext())
        val apiService = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(apiService, tokenManager)
        viewModel = ChatConversationViewModel(chatUuid, chatRepository, extractMyUserUuid(tokenManager))
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(viewModel.myUserUuid)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatConversationFragment.adapter
        }
    }

    private fun setupInput() {
        val sendClicked = {
            val text = binding.etMessageInput.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                viewModel.sendText(text)
                binding.etMessageInput.setText("")
            }
        }

        binding.btnSend.setOnClickListener { sendClicked() }
        binding.etMessageInput.setOnEditorActionListener { _, _, _ ->
            sendClicked()
            false
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.updateList(messages)
                updateEmptyState(messages)
                if (messages.size > lastMessageCount) {
                    if (messages.isNotEmpty()) {
                        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                    }
                }
                lastMessageCount = messages.size
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                if (loading && adapter.currentList.isEmpty()) {
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                if (error != null && adapter.currentList.isEmpty()) {
                    binding.tvError.text = error
                    binding.tvError.visibility = View.VISIBLE
                    binding.recyclerViewMessages.visibility = View.GONE
                } else if (error != null) {
                    binding.tvError.visibility = View.GONE
                    binding.recyclerViewMessages.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.sendError.collect { message ->
                Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateEmptyState(messages: List<MessageResponse>) {
        if (messages.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerViewMessages.visibility = View.GONE
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewMessages.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
        }
    }

    private fun extractMyUserUuid(tokenManager: TokenManager): String? {
        val token = tokenManager.getChatToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)
            JSONObject(String(decoded, Charsets.UTF_8)).getString("sub")
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}