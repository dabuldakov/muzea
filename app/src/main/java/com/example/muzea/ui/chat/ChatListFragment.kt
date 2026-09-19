package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzea.R
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.databinding.FragmentChatListBinding
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        loadChats()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(AUTO_REFRESH_MS)
                    loadChats()
                }
            }
        }
    }

    private fun initViewModel() {
        val tokenManager = TokenManager(requireContext())
        val apiService = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(apiService, ChatAuthManager(apiService, tokenManager))
        viewModel = ChatViewModel(chatRepository)
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            onItemClick = { chatUuid ->
                openChat(chatUuid)
            }
        )
        binding.recyclerViewChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatListFragment.adapter
        }
    }

    private fun openChat(chatUuid: String) {
        val chat = adapter.currentList.firstOrNull { it.chatUuid == chatUuid }
        val title = chat?.title ?: "Chat"

        val fragment = ChatConversationFragment.newInstance(
            chatUuid,
            title,
            chat?.avatarUrl,
            chat?.unreadCount ?: 0L
        )
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadChats()
        }
    }

    private fun loadChats() {
        viewModel.loadChats()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.chatsResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> handleLoadingState()
                    is NetworkResult.Success -> handleSuccessState(result.data ?: emptyList())
                    is NetworkResult.Error -> handleErrorState(result.message ?: "Unknown error")
                }
            }
        }
    }

    private fun handleLoadingState() {
        // Не мигаем прогрессом при фоновом автообновлении, если список уже показан.
        if (!binding.swipeRefresh.isRefreshing && adapter.itemCount == 0) {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    private fun handleSuccessState(chats: List<com.example.muzea.data.model.ChatResponse>) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        adapter.updateList(chats)
        updateEmptyState()
    }

    private fun handleErrorState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (adapter.currentList.isEmpty()) {
            binding.tvError.text = message
            binding.tvError.visibility = View.VISIBLE
            binding.recyclerViewChats.visibility = View.GONE
        } else {
            Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        if (adapter.currentList.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerViewChats.visibility = View.GONE
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewChats.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadChats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        private const val AUTO_REFRESH_MS = 8_000L
    }
}
