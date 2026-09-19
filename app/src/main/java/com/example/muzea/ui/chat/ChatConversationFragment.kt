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
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.databinding.FragmentChatConversationBinding
import com.example.muzea.utils.TokenManager
import com.example.muzea.utils.AvatarLoader
import kotlinx.coroutines.launch
import org.json.JSONObject

class ChatConversationFragment : Fragment() {

    private var _binding: FragmentChatConversationBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ChatConversationViewModel
    private lateinit var adapter: MessageAdapter
    private var chatUuid: String = ""
    private var lastMessageCount = 0
    private var unreadCount = 0L
    private var initialScrollDone = false
    private var pendingScrollPosition: Int? = null

    private val scrollObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = performPendingScroll()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = performPendingScroll()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = performPendingScroll()
    }

    companion object {
        private const val ARG_CHAT_UUID = "chat_uuid"
        private const val ARG_CHAT_TITLE = "chat_title"
        private const val ARG_CHAT_AVATAR = "chat_avatar"
        private const val ARG_UNREAD_COUNT = "chat_unread_count"

        fun newInstance(
            chatUuid: String,
            chatTitle: String,
            avatarUrl: String? = null,
            unreadCount: Long = 0L
        ): ChatConversationFragment {
            return ChatConversationFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_UUID, chatUuid)
                    putString(ARG_CHAT_TITLE, chatTitle)
                    putString(ARG_CHAT_AVATAR, avatarUrl)
                    putLong(ARG_UNREAD_COUNT, unreadCount)
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
        unreadCount = arguments?.getLong(ARG_UNREAD_COUNT, 0L) ?: 0L

        binding.tvTitle.text = chatTitle
        AvatarLoader.load(binding.ivAvatar, arguments?.getString(ARG_CHAT_AVATAR))
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
        val chatRepository = ChatRepository(apiService, ChatAuthManager(apiService, tokenManager))
        viewModel = ChatConversationViewModel(chatUuid, chatRepository, extractMyUserUuid(tokenManager))
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(viewModel.myUserUuid)
        adapter.registerAdapterDataObserver(scrollObserver)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatConversationFragment.adapter
        }
    }

    private fun performPendingScroll() {
        val position = pendingScrollPosition ?: return
        pendingScrollPosition = null
        binding.recyclerViewMessages.scrollToPosition(position)
    }

    private fun isAtBottom(): Boolean {
        val layoutManager = binding.recyclerViewMessages.layoutManager as? LinearLayoutManager ?: return true
        return layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                val wasAtBottom = isAtBottom()
                adapter.updateList(messages)
                updateEmptyState(messages)

                if (messages.isNotEmpty() && !initialScrollDone) {
                    initialScrollDone = true
                    val target = if (unreadCount > 0) {
                        (messages.size - unreadCount).toInt().coerceIn(0, messages.size - 1)
                    } else {
                        messages.size - 1
                    }
                    pendingScrollPosition = target
                } else if (messages.size > lastMessageCount) {
                    val lastIsMine = messages.last().senderUuid == viewModel.myUserUuid
                    if (lastIsMine || wasAtBottom) {
                        pendingScrollPosition = messages.size - 1
                    }
                }
                lastMessageCount = messages.size
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                if (loading && adapter.currentList.isEmpty()) {
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
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

        viewLifecycleOwner.lifecycleScope.launch {
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
        if (::adapter.isInitialized) {
            adapter.unregisterAdapterDataObserver(scrollObserver)
        }
        super.onDestroyView()
        _binding = null
    }
}
