package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.muzea.domain.model.Message
import com.example.muzea.databinding.FragmentChatConversationBinding
import com.example.muzea.ui.navigation.navigator
import com.example.muzea.utils.AvatarLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatConversationFragment : Fragment() {

    private var _binding: FragmentChatConversationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatConversationViewModel by viewModels()
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
                    putString(ChatConversationViewModel.ARG_CHAT_UUID, chatUuid)
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
        chatUuid = arguments?.getString(ChatConversationViewModel.ARG_CHAT_UUID) ?: ""
        val chatTitle = arguments?.getString(ARG_CHAT_TITLE) ?: "Chat"
        unreadCount = arguments?.getLong(ARG_UNREAD_COUNT, 0L) ?: 0L

        binding.tvTitle.text = chatTitle
        AvatarLoader.load(binding.ivAvatar, arguments?.getString(ARG_CHAT_AVATAR))
        binding.tvTitle.setOnClickListener {
            openGroupSettings(chatTitle)
        }
        binding.btnBack.setOnClickListener {
            navigator.back()
        }

        setupRecyclerView()
        setupInput()
        observeViewModel()
    }

    private fun openGroupSettings(chatTitle: String) {
        navigator.openGroupSettings(chatUuid, chatTitle, arguments?.getString(ARG_CHAT_AVATAR))
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(viewModel.myUserUuid)
        adapter.registerAdapterDataObserver(scrollObserver)
        binding.recyclerViewMessages.apply {
            // stackFromEnd держит ленту «прижатой» к низу: при открытии клавиатуры
            // окно сжимается (adjustResize), и последнее сообщение остаётся
            // видимым над клавиатурой, а не уезжает вниз.
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@ChatConversationFragment.adapter
            // Обновление содержимого (локальное сообщение -> серверное эхо) не
            // должно проходить через crossfade-анимацию: она и выглядит как
            // мигание пузыря.
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
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

    /**
     * Единая отрисовка экрана переписки.
     *
     * Список берём из ViewModel, потому что [androidx.recyclerview.widget.ListAdapter]
     * обновляет currentList асинхронно: опираясь на него, мы сразу после
     * submitList() считали бы экран пустым и спрятали бы переписку.
     */
    private fun renderConversationState(state: ChatConversationUiState) {
        val hasMessages = state.messages.isNotEmpty()

        binding.progressBar.visibility = if (state.isLoading && !hasMessages) View.VISIBLE else View.GONE

        if (state.error != null && !hasMessages) {
            binding.tvError.text = state.error
            binding.tvError.visibility = View.VISIBLE
            binding.recyclerViewMessages.visibility = View.GONE
        } else {
            binding.tvError.visibility = View.GONE
            binding.recyclerViewMessages.visibility =
                if (hasMessages) View.VISIBLE else View.GONE
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val messages = state.messages
                val wasAtBottom = isAtBottom()
                adapter.updateList(messages)
                updateEmptyState(messages)
                renderConversationState(state)

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
            viewModel.sendError.collect { message ->
                Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateEmptyState(messages: List<Message>) {
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

    override fun onDestroyView() {
        if (::adapter.isInitialized) {
            adapter.unregisterAdapterDataObserver(scrollObserver)
        }
        super.onDestroyView()
        _binding = null
    }
}
