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
class ChatConversationFragment : Fragment(), GifPickerBottomSheet.OnGifSelected {

    private var _binding: FragmentChatConversationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatConversationViewModel by viewModels()
    private lateinit var adapter: MessageAdapter
    private var chatUuid: String = ""
    private var lastNewestUuid: String? = null
    private var unreadCount = 0L
    private var initialScrollDone = false
    private var pendingScrollPosition: Int? = null

    // Якорь при догрузке истории: сохраняем верхнее видимое сообщение и его
    // смещение, чтобы после вставки старых сообщений сверху вид не «прыгнул».
    private var historyAnchorUuid: String? = null
    private var historyAnchorOffset: Int = 0

    private val scrollObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = performPendingScroll()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            restoreHistoryAnchor()
            performPendingScroll()
        }
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = performPendingScroll()
    }

    companion object {
        private const val ARG_CHAT_TITLE = "chat_title"
        private const val ARG_CHAT_AVATAR = "chat_avatar"
        private const val ARG_UNREAD_COUNT = "chat_unread_count"
        private const val ARG_DRAFT_TEXT = "chat_draft_text"

        fun newInstance(
            chatUuid: String,
            chatTitle: String,
            avatarUrl: String? = null,
            unreadCount: Long = 0L,
            draftText: String? = null
        ): ChatConversationFragment {
            return ChatConversationFragment().apply {
                arguments = Bundle().apply {
                    putString(ChatConversationViewModel.ARG_CHAT_UUID, chatUuid)
                    putString(ARG_CHAT_TITLE, chatTitle)
                    putString(ARG_CHAT_AVATAR, avatarUrl)
                    putLong(ARG_UNREAD_COUNT, unreadCount)
                    putString(ARG_DRAFT_TEXT, draftText)
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
        prefillDraft()
        observeViewModel()
    }

    /**
     * Подставляет в поле ввода текст, которым с приложением поделились.
     * Отправляет его пользователь сам — это черновик, а не автоотправка.
     */
    private fun prefillDraft() {
        val draft = arguments?.getString(ARG_DRAFT_TEXT)?.takeIf { it.isNotEmpty() } ?: return
        binding.etMessageInput.setText(draft)
        binding.etMessageInput.setSelection(draft.length)
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
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    // Догружаем историю, когда пользователь домотал до самого верха.
                    if (dy < 0 && !recyclerView.canScrollVertically(-1)) {
                        requestOlderMessages()
                    }
                }
            })
        }
    }

    /** Запоминает якорь и просит ViewModel догрузить историю. */
    private fun requestOlderMessages() {
        val layoutManager = binding.recyclerViewMessages.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        val uuid = viewModel.uiState.value.messages.getOrNull(position)?.messageUuid
        if (uuid != null) {
            historyAnchorUuid = uuid
            historyAnchorOffset = layoutManager.findViewByPosition(position)?.top ?: 0
        }
        viewModel.loadOlder()
    }

    /** Возвращает список к тому же сообщению, что было сверху до догрузки. */
    private fun restoreHistoryAnchor() {
        val uuid = historyAnchorUuid ?: return
        historyAnchorUuid = null
        binding.recyclerViewMessages.post {
            if (_binding == null) return@post
            val position = adapter.currentList.indexOfFirst { it.messageUuid == uuid }
            if (position >= 0) {
                (binding.recyclerViewMessages.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(position, historyAnchorOffset)
            }
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

        binding.btnGif.setOnClickListener {
            GifPickerBottomSheet.newInstance(chatUuid)
                .show(childFragmentManager, "gif_picker")
        }
    }

    override fun onGifSelected(gif: com.example.muzea.domain.model.Gif) {
        viewModel.sendGif(gif)
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
                binding.progressHistory.visibility =
                    if (state.isLoadingMore) View.VISIBLE else View.GONE
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
                } else if (messages.isNotEmpty() && messages.last().messageUuid != lastNewestUuid) {
                    // Реагируем только на действительно новое сообщение внизу.
                    // Догрузка истории вверх добавляет сообщения в начало и не
                    // должна уводить список вниз.
                    val lastIsMine = messages.last().senderUuid == viewModel.myUserUuid
                    if (lastIsMine || wasAtBottom) {
                        pendingScrollPosition = messages.size - 1
                    }
                }
                if (messages.isNotEmpty()) lastNewestUuid = messages.last().messageUuid
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
