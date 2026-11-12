package com.example.muzea.ui.chat

import com.example.muzea.R
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.ui.chat.recording.RecordedAudio
import com.example.muzea.ui.chat.recording.RecordedVideo
import com.example.muzea.ui.chat.recording.VideoNoteRecorder
import com.example.muzea.ui.chat.recording.VoiceRecorder
import com.example.muzea.utils.PreparedAttachment
import com.example.muzea.utils.MediaUrl
import com.example.muzea.utils.TokenManager
import com.example.muzea.data.repository.ChatListCache
import com.example.muzea.data.repository.LinkPreviewStore
import com.example.muzea.databinding.FragmentChatConversationBinding
import com.example.muzea.databinding.ItemPendingAttachmentBinding
import com.example.muzea.ui.common.ReportDialog
import com.example.muzea.ui.navigation.navigator
import com.example.muzea.utils.ActiveChatTracker
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.LocalTimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@AndroidEntryPoint
class ChatConversationFragment : Fragment(),
    GifPickerBottomSheet.OnGifSelected,
    MessageActionsBottomSheet.Listener,
    ForwardChatPickerBottomSheet.OnChatForwardTargetSelected,
    AttachmentsBottomSheet.Listener,
    EmojiGifBottomSheet.Listener {

    private var _binding: FragmentChatConversationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatConversationViewModel by viewModels()
    private lateinit var adapter: MessageAdapter

    @Inject
    lateinit var linkPreviewStore: LinkPreviewStore

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var networkMonitor: com.example.muzea.utils.NetworkMonitor
    private var chatUuid: String = ""
    private var lastNewestUuid: String? = null
    private var unreadCount = 0L
    /** Позиция первого непрочитанного: кнопка ведёт туда, пока не нажали. */
    private var unreadAnchorPosition: Int? = null
    private var initialScrollDone = false
    private var pendingScrollPosition: Int? = null
    private var lastStatuses: Map<String, MessageDeliveryStatus> = emptyMap()
    private var lastMessages: List<Message>? = null
    private var pendingHighlightUuid: String? = null
    private var highlightAttempts = 0
    private var lastPending: List<PendingAttachment> = emptyList()
    private var lastReplyUuid: String? = null
    private var lastEditingUuid: String? = null
    /** Сообщения, ожидающие выбора чата для пересылки (мультивыбор). */
    private var pendingForwardMessages: List<Message> = emptyList()
    private var selectionMode = false
    private val selectedMessages = LinkedHashSet<String>()
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }
    /** Текущий список закреплённых (для клика по панели и открепления). */
    private var pinnedMessages: List<Message> = emptyList()
    private var mentionPopup: ListPopupWindow? = null
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var chargeJob: Job? = null
    private var chargeStartedAt = 0L
    private var isCharging = false
    private var chargeCancelled = false
    private var lastChargeVibrationAt = 0L
    private var mentionStart = -1
    private var mentionEnd = -1

    private enum class SendMode { TEXT, VOICE, VIDEO }

    private var sendMode = SendMode.TEXT
    private var activeRecorderMode: SendMode? = null
    private var recordingStartJob: Job? = null
    private var recordTimerJob: Job? = null
    private var recordStartMs = 0L
    private var pressStartY = 0f
    private var cancelArmed = false
    private var sendPressed = false
    private var videoReady = false
    private var finishingRecording = false
    private var voiceRecorder: VoiceRecorder? = null
    private var videoRecorder: VideoNoteRecorder? = null
    private var audioPlayer: ChatAudioPlayer? = null
    private var playingAudioUuid: String? = null

    private val micPermission = registerForActivityResult(RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), R.string.permission_mic_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissions = registerForActivityResult(RequestMultiplePermissions()) { result ->
        if (result.values.any { !it }) {
            Toast.makeText(requireContext(), R.string.permission_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val pickPhotos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> if (uris.isNotEmpty()) viewModel.addAttachments(uris) }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addAttachments(listOf(it)) }
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addAttachments(listOf(it)) }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addAttachments(listOf(it)) }
    }

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
        private const val ARG_HIGHLIGHT_MESSAGE = "chat_highlight_message"
        private const val LONG_PRESS_MS = 250L
        private const val CHARGE_TICK_MS = 50L
        private const val MEDIA_RETRY_MS = 5 * 60 * 1000L
        private const val RECORD_CANCEL_DP = 72
        private const val MAX_VOICE_MS = 5 * 60 * 1000L
        private const val MAX_VIDEO_MS = 60 * 1000L
        private const val MAX_HIGHLIGHT_PAGES = 5
        private const val HIGHLIGHT_MS = 1800L
        private const val HIGHLIGHT_ATTEMPTS = 5
        private const val MENU_DELETE = 1
        private const val MENU_FORWARD = 2
        private const val HIGHLIGHT_COLOR = 0x55FFEB3B

        private val DOCUMENT_MIME_TYPES = arrayOf(
            "application/pdf", "text/plain", "text/csv", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )

        fun newInstance(
            chatUuid: String,
            chatTitle: String,
            avatarUrl: String? = null,
            unreadCount: Long = 0L,
            draftText: String? = null,
            highlightMessageUuid: String? = null
        ): ChatConversationFragment {
            return ChatConversationFragment().apply {
                arguments = Bundle().apply {
                    putString(ChatConversationViewModel.ARG_CHAT_UUID, chatUuid)
                    putString(ARG_CHAT_TITLE, chatTitle)
                    putString(ARG_CHAT_AVATAR, avatarUrl)
                    putLong(ARG_UNREAD_COUNT, unreadCount)
                    putString(ARG_DRAFT_TEXT, draftText)
                    putString(ARG_HIGHLIGHT_MESSAGE, highlightMessageUuid)
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
        pendingHighlightUuid = arguments?.getString(ARG_HIGHLIGHT_MESSAGE)
        AvatarLoader.load(binding.ivAvatar, arguments?.getString(ARG_CHAT_AVATAR))
        binding.tvTitle.setOnClickListener {
            openGroupSettings(chatTitle)
        }
        binding.btnBack.setOnClickListener {
            navigator.back()
        }

        setupRecyclerView()
        setupScrollButton()
        setupInput()
        setupAttachments()
        setupReplyBanner()
        setupPinnedBar()
        setupSelectionBar()
        prefillDraft()
        observeViewModel()
        observeNetwork()
        startMediaRetry()
    }

    /**
     * Подставляет в поле ввода текст, которым с приложением поделились.
     * Отправляет его пользователь сам — это черновик, а не автоотправка.
     */
    private fun prefillDraft() {
        val shared = arguments?.getString(ARG_DRAFT_TEXT)?.takeIf { it.isNotEmpty() }
        val draft = shared ?: ChatDrafts.get(requireContext(), chatUuid) ?: return
        binding.etMessageInput.setText(draft)
        binding.etMessageInput.setSelection(draft.length)
        if (shared != null) {
            // Текст из «Поделиться» — тоже черновик, пока его не отправили.
            ChatDrafts.save(requireContext(), chatUuid, shared)
        }
    }

    /**
     * Действия по нажатию на сообщение. Открываем bottom sheet поверх
     * чата: сверху ряд иконок, снизу — статус прочтения (для своих сообщений).
     * Чужие сообщения можно копировать/жаловаться/блокировать, свои — копировать.
     */
    private fun showMessageActions(message: Message) {
        // У удалённого сообщения действий нет.
        if (message.isDeleted) return
        val isMine = message.senderUuid != null && message.senderUuid == viewModel.myUserUuid
        // После серверного эха uuid остаётся локальным (чтобы DiffUtil не
        // пересоздавал строку), поэтому признак серверного сообщения — messageId.
        val isServerMessage = message.messageId != null
        val actions = mutableListOf<MessageActionSpec>()
        actions += MessageActionSpec(
            MessageActionsBottomSheet.ACTION_REACT,
            R.drawable.ic_reaction,
            R.string.action_react
        )

        val failedLocal = message.messageUuid.startsWith("local-") &&
            viewModel.deliveryStatus(message.messageUuid) == MessageDeliveryStatus.FAILED
        if (failedLocal) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_RETRY,
                R.drawable.ic_action_retry,
                R.string.action_retry
            )
        }

        if (!message.text.isNullOrBlank()) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_COPY,
                R.drawable.ic_action_copy,
                R.string.action_copy
            )
        }

        // Ответить можно на любое серверное сообщение, в том числе на своё.
        if (isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_REPLY,
                R.drawable.ic_action_reply,
                R.string.action_reply
            )
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_SELECT,
                R.drawable.ic_action_select,
                R.string.select_messages
            )
        }

        // Редактировать можно свои серверные сообщения с текстом: для медиа
        // это подпись к картинке/видео, а не только TEXT.
        // Подпись к медиа можно и добавить, и изменить — поэтому править
        // разрешаем и сообщения с вложением без текста.
        val canEdit = isMine && isServerMessage && !message.isDeleted &&
            (!message.text.isNullOrBlank() || message.attachments.isNotEmpty())
        if (canEdit) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_EDIT,
                R.drawable.ic_action_edit,
                R.string.action_edit
            )
        }

        // Удалить можно только своё серверное сообщение.
        if (isMine && isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_DELETE,
                R.drawable.ic_action_delete,
                R.string.action_delete
            )
        }

        // Пересылать и закреплять можно любое серверное не удалённое сообщение.
        if (isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_FORWARD,
                R.drawable.ic_action_forward,
                R.string.action_forward
            )
            if (message.isPinned) {
                actions += MessageActionSpec(
                    MessageActionsBottomSheet.ACTION_UNPIN,
                    R.drawable.ic_action_pin,
                    R.string.action_unpin
                )
            } else {
                actions += MessageActionSpec(
                    MessageActionsBottomSheet.ACTION_PIN,
                    R.drawable.ic_action_pin,
                    R.string.action_pin
                )
            }
        }

        if (!isMine && isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_REPORT,
                R.drawable.ic_action_report,
                R.string.action_report
            )
        }

        val senderUuid = message.senderUuid
        if (!isMine && senderUuid != null) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_BLOCK,
                R.drawable.ic_action_block,
                R.string.action_block
            )
        }

        // В личных чатах статус читается по галочкам; разворачивать список
        // участников там излишне. В группах — только компактные аватары.
        val showStatus = isMine && message.messageId != null && isGroupChat()
        if (actions.isEmpty() && !showStatus) return

        MessageActionsBottomSheet.newInstance(
            messageUuid = message.messageUuid,
            actions = actions,
            rows = if (showStatus) buildStatusRows(message) else emptyList(),
            showStatus = showStatus
        ).show(childFragmentManager, "message_actions")
    }

    private fun opaqueEmojiTextColor(): Int =
        if ((resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) android.graphics.Color.WHITE else android.graphics.Color.BLACK

    private fun showReactionPicker(message: Message) {
        val emojis = listOf(
            "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏",
            "🙏", "💯", "😍", "🤔", "😡", "🥰", "😎", "🤝",
            "✅", "❌", "👀", "🤯", "😴", "💜", "⚡", "🚀"
        )
        val density = resources.displayMetrics.density
        val grid = android.widget.GridLayout(requireContext()).apply { columnCount = 6 }
        val size = (48 * density).toInt()
        var dialog: androidx.appcompat.app.AlertDialog? = null
        emojis.forEach { emoji ->
            val cell = android.widget.TextView(requireContext()).apply {
                text = emoji
                textSize = 26f
                includeFontPadding = false
                gravity = android.view.Gravity.CENTER
                setTextColor(opaqueEmojiTextColor())
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                }
                setOnClickListener {
                    viewModel.toggleReaction(message, emoji)
                    dialog?.dismiss()
                }
            }
            grid.addView(cell)
        }
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_react)
            .setView(grid)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isGroupChat(): Boolean =
        ChatListCache.get().firstOrNull { it.chatUuid == chatUuid }
            ?.chatType.equals("GROUP", ignoreCase = true)

    /** Строки «кто прочитал / кому доставлено» для шторки статуса. */
    private fun buildStatusRows(message: Message): List<MessageStatusRow> =
        viewModel.participantStatuses(message).map { row ->
            val at = if (row.status == MessageDeliveryStatus.READ) {
                row.receipt.lastReadAt
            } else {
                row.receipt.lastDeliveredAt
            }
            MessageStatusRow(
                name = row.receipt.displayName(),
                avatarUrl = row.receipt.avatarUrl,
                status = row.status.name,
                at = LocalTimeFormatter.format(at).takeIf { it.isNotBlank() }
            )
        }

    override fun onMessageAction(messageUuid: String, actionId: String) {
        val message = viewModel.uiState.value.messages
            .firstOrNull { it.messageUuid == messageUuid } ?: return
        when (actionId) {
            MessageActionsBottomSheet.ACTION_REACT -> showReactionPicker(message)
            MessageActionsBottomSheet.ACTION_COPY ->
                message.text?.takeIf { it.isNotBlank() }?.let { copyMessage(it) }
            MessageActionsBottomSheet.ACTION_REPORT ->
                ReportDialog.show(requireContext()) { reason ->
                    reportMessage(message.messageUuid, reason)
                }
            MessageActionsBottomSheet.ACTION_BLOCK ->
                message.senderUuid?.let { confirmBlock(it) }
            MessageActionsBottomSheet.ACTION_REPLY ->
                viewModel.setReplyTo(message)
            MessageActionsBottomSheet.ACTION_SELECT ->
                enterSelectionMode(message)
            MessageActionsBottomSheet.ACTION_EDIT ->
                viewModel.startEditing(message)
            MessageActionsBottomSheet.ACTION_DELETE ->
                confirmDeleteMessage(message)
            MessageActionsBottomSheet.ACTION_FORWARD -> {
                pendingForwardMessages = listOf(message)
                ForwardChatPickerBottomSheet.newInstance(chatUuid)
                    .show(childFragmentManager, "forward_picker")
            }
            MessageActionsBottomSheet.ACTION_PIN ->
                viewModel.pinMessage(message)
            MessageActionsBottomSheet.ACTION_UNPIN ->
                viewModel.unpinMessage(message)
            MessageActionsBottomSheet.ACTION_RETRY ->
                viewModel.retryMessage(message)
        }
    }

    /** Выбран чат для пересылки: даём дописать комментарий и нажать самолётик. */
    override fun onForwardTargetSelected(chat: Chat) {
        val messages = pendingForwardMessages
        pendingForwardMessages = emptyList()
        if (messages.isEmpty()) return
        showForwardCommentDialog { comment ->
            viewModel.forwardMessages(messages, chat.chatUuid, comment)
        }
    }

    private fun showForwardCommentDialog(onSend: (String?) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_forward_comment, null)
        val input = view.findViewById<EditText>(R.id.etForwardComment)
        val dialog = Dialog(requireContext())
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        // Случайный тап мимо окна не должен «терять» пересылку.
        dialog.setCanceledOnTouchOutside(false)
        view.findViewById<View>(R.id.btnForwardCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnForwardSend).setOnClickListener {
            val comment = input.text?.toString()?.trim().orEmpty()
            dialog.dismiss()
            onSend(comment.ifBlank { null })
        }
        dialog.show()
        input.requestFocus()
    }

    private fun setupSelectionBar() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, selectionBackCallback
        )
        binding.btnSelectionDelete.setOnClickListener { deleteSelected() }
        binding.btnSelectionForward.setOnClickListener { forwardSelected() }
        binding.btnSelectionClose.setOnClickListener { exitSelectionMode() }
    }

    private fun enterSelectionMode(message: Message) {
        selectionMode = true
        selectedMessages.clear()
        selectedMessages.add(message.messageUuid)
        adapter.setSelection(selectedMessages)
        selectionBackCallback.isEnabled = true
        binding.selectionBar.visibility = View.VISIBLE
        binding.composerBar.visibility = View.GONE
        updateSelectionCount()
    }

    private fun toggleSelection(message: Message) {
        if (!selectedMessages.remove(message.messageUuid)) {
            selectedMessages.add(message.messageUuid)
        }
        adapter.setSelection(selectedMessages)
        if (selectedMessages.isEmpty()) {
            exitSelectionMode()
        } else {
            updateSelectionCount()
        }
    }

    private fun updateSelectionCount() {
        binding.tvSelectionCount.text =
            getString(R.string.selected_count, selectedMessages.size)
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedMessages.clear()
        adapter.setSelection(emptySet())
        selectionBackCallback.isEnabled = false
        binding.selectionBar.visibility = View.GONE
        binding.composerBar.visibility = View.VISIBLE
    }

    private fun selectedItems(): List<Message> =
        adapter.currentList.filter { it.messageUuid in selectedMessages }

    private fun deleteSelected() {
        val deletable = selectedItems().filter {
            it.messageId != null && it.senderUuid == viewModel.myUserUuid
        }
        if (deletable.isEmpty()) {
            Toast.makeText(requireContext(), R.string.delete_message_failed, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_selected_title)
            .setMessage(R.string.delete_selected_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deletable.forEach { viewModel.deleteMessage(it) }
                exitSelectionMode()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun forwardSelected() {
        val messages = selectedItems()
        if (messages.isEmpty()) return
        pendingForwardMessages = messages
        exitSelectionMode()
        ForwardChatPickerBottomSheet.newInstance(chatUuid)
            .show(childFragmentManager, "forward_picker")
    }

    /** Подтверждение удаления сообщения «для всех». */
    private fun confirmDeleteMessage(message: Message) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_message_confirm_title)
            .setMessage(R.string.delete_message_confirm_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteMessage(message)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun copyMessage(text: String) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        Toast.makeText(requireContext(), R.string.message_copied, Toast.LENGTH_SHORT).show()
    }

    private fun reportMessage(messageUuid: String, reason: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = viewModel.reportMessage(messageUuid, reason)
            Toast.makeText(
                requireContext(),
                if (ok) R.string.report_sent else R.string.report_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmBlock(userUuid: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.block_confirm_title)
            .setMessage(R.string.block_confirm_message)
            .setPositiveButton(R.string.action_block) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = viewModel.blockUser(userUuid)
                    Toast.makeText(
                        requireContext(),
                        if (ok) R.string.block_success else R.string.block_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openGroupSettings(chatTitle: String) {
        navigator.openGroupSettings(chatUuid, chatTitle, arguments?.getString(ARG_CHAT_AVATAR))
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            myUserUuid = viewModel.myUserUuid,
            linkPreviews = linkPreviewStore,
            onMessageClick = { message ->
                if (selectionMode) toggleSelection(message) else showMessageActions(message)
            },
            statusFor = { message -> viewModel.deliveryStatus(message.messageUuid) },
            onStatusClick = { message -> showMessageActions(message) },
            onAttachmentClick = { message, attachment ->
                openAttachment(message, attachment)
            },
            isGroupChat = isGroupChat(),
            onReactionClick = { message, emoji -> viewModel.toggleReaction(message, emoji) }
        )
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
            // мигание пузыря. Удаление — «схлопывание» в точку.
            itemAnimator = MessageItemAnimator().apply { supportsChangeAnimations = false }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    // Догружаем историю, когда пользователь домотал до самого верха.
                    if (dy < 0 && !recyclerView.canScrollVertically(-1)) {
                        requestOlderMessages()
                    }
                    updateScrollButton()
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

    private fun isAtBottom(): Boolean =
        !binding.recyclerViewMessages.canScrollVertically(1)

    /** Кнопка прокрутки: видна, только когда список не в самом низу. */
    private fun updateScrollButton() {
        val hasMessages = adapter.itemCount > 0
        val visible = hasMessages && !isAtBottom()
        binding.btnScrollBottom.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnScrollBottom.contentDescription = getString(
            if (unreadAnchorPosition != null) R.string.scroll_to_unread
            else R.string.scroll_to_latest
        )
    }

    private fun setupScrollButton() {
        binding.btnScrollBottom.setOnClickListener {
            val last = adapter.itemCount - 1
            if (last < 0) return@setOnClickListener
            val target = (unreadAnchorPosition ?: last).coerceIn(0, last)
            val layoutManager = binding.recyclerViewMessages.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(target, 0)
                ?: binding.recyclerViewMessages.scrollToPosition(target)
            // Первый тап ведёт к непрочитанному, второй — уже в самый низ.
            unreadAnchorPosition = null
            updateScrollButton()
        }
    }

    private fun setupAttachments() {
        binding.btnAdd.setOnClickListener { showAttachmentPicker() }
    }

    private fun setupReplyBanner() {
        binding.btnCancelReply.setOnClickListener {
            if (viewModel.uiState.value.editing != null) {
                viewModel.cancelEditing()
                binding.etMessageInput.setText("")
            } else {
                viewModel.setReplyTo(null)
            }
        }
    }

    /**
     * Баннер над полем ввода: либо «ответ», либо «редактирование». Оригинал
     * подставляем как цитату; при редактировании ещё и заполняем поле ввода.
     */
    private fun renderComposerBanner(state: ChatConversationUiState) {
        val editing = state.editing
        if (editing != null) {
            binding.replyBanner.visibility = View.VISIBLE
            binding.btnCancelReply.contentDescription = getString(R.string.action_cancel_edit)
            binding.tvReplyBannerTitle.text = getString(R.string.edit_message_title)
            binding.tvReplyBannerText.text = replyPreviewText(requireContext(), editing)
            if (editing.messageUuid != lastEditingUuid) {
                lastEditingUuid = editing.messageUuid
                val text = editing.text.orEmpty()
                binding.etMessageInput.setText(text)
                binding.etMessageInput.setSelection(text.length)
                binding.etMessageInput.requestFocus()
                showKeyboard()
            }
            return
        }
        lastEditingUuid = null

        binding.btnCancelReply.contentDescription = getString(R.string.action_cancel_reply)
        val replyTo = state.replyTo
        if (replyTo == null) {
            binding.replyBanner.visibility = View.GONE
            lastReplyUuid = null
            return
        }
        binding.replyBanner.visibility = View.VISIBLE
        binding.tvReplyBannerTitle.text = getString(
            R.string.reply_to_format,
            replySenderName(requireContext(), replyTo, viewModel.myUserUuid)
        )
        binding.tvReplyBannerText.text = replyPreviewText(requireContext(), replyTo)
        if (replyTo.messageUuid != lastReplyUuid) {
            lastReplyUuid = replyTo.messageUuid
            binding.etMessageInput.requestFocus()
            showKeyboard()
        }
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.showSoftInput(binding.etMessageInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupPinnedBar() {
        // Крестик открепляет верхнее закреплённое сообщение.
        binding.btnUnpin.setOnClickListener {
            pinnedMessages.firstOrNull()?.let { viewModel.unpinMessage(it) }
        }
        // Тап по панели ведёт к закреплённому сообщению (подсвечиваем).
        binding.pinnedBarContent.setOnClickListener {
            val target = pinnedMessages.firstOrNull() ?: return@setOnClickListener
            val position = viewModel.uiState.value.messages
                .indexOfFirst { it.messageUuid == target.messageUuid }
            if (position >= 0) {
                scrollToAndHighlight(position, target.messageUuid)
            } else {
                pendingHighlightUuid = target.messageUuid
                highlightAttempts = 0
                viewModel.loadOlder()
            }
        }
    }

    /** Панель закреплённого сообщения сверху чата. */
    private fun renderPinnedBar(state: ChatConversationUiState) {
        val pinned = state.pinnedMessages
        pinnedMessages = pinned
        if (pinned.isEmpty()) {
            binding.pinnedBar.visibility = View.GONE
            return
        }
        binding.pinnedBar.visibility = View.VISIBLE
        binding.tvPinnedTitle.text = if (pinned.size > 1) {
            getString(R.string.pinned_messages_count, pinned.size)
        } else {
            getString(R.string.pinned_message_title)
        }
        binding.tvPinnedText.text = replyPreviewText(requireContext(), pinned.first())
    }

    private fun showAttachmentPicker() {
        AttachmentsBottomSheet.newInstance()
            .show(childFragmentManager, "attachments")
    }

    override fun onAttachmentKind(kind: AttachmentsBottomSheet.Kind) {
        when (kind) {
            AttachmentsBottomSheet.Kind.PHOTO -> pickPhotos.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            AttachmentsBottomSheet.Kind.VIDEO -> pickVideo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
            AttachmentsBottomSheet.Kind.AUDIO -> pickAudio.launch(arrayOf("audio/*"))
            AttachmentsBottomSheet.Kind.FILE -> pickFile.launch(DOCUMENT_MIME_TYPES)
        }
    }

    private fun renderPendingAttachments(pending: List<PendingAttachment>) {
        binding.scrollPending.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        binding.pendingAttachments.removeAllViews()
        pending.forEach { item ->
            val row = ItemPendingAttachmentBinding.inflate(
                layoutInflater, binding.pendingAttachments, false
            )
            row.tvName.text = item.name
            row.pbUploading.visibility =
                if (item.isUploaded || item.error != null) View.GONE else View.VISIBLE
            row.pbUploading.isIndeterminate = item.progress <= 0
            row.pbUploading.progress = item.progress
            row.tvError.visibility = if (item.error != null) View.VISIBLE else View.GONE
            row.tvError.text = item.error
            row.ivRemove.setOnClickListener { viewModel.removePendingAttachment(item.id) }
            if (item.error != null) {
                row.root.setOnClickListener { viewModel.retryPendingAttachment(item.id) }
            }
            binding.pendingAttachments.addView(row.root)
        }
    }

    private fun openAttachment(message: Message, attachment: MessageAttachment) {
        when (attachment.type?.uppercase()) {
            "AUDIO" -> toggleAudio(message, attachment)
            "IMAGE", "VIDEO" -> AttachmentViewerActivity.start(
                requireContext(), attachment
            )
            else -> downloadAndOpen(attachment)
        }
    }

    /** Воспроизведение голосового/аудио прямо в чате, без отдельного экрана. */
    private fun toggleAudio(message: Message, attachment: MessageAttachment) {
        val url = resolveAttachmentUrl(attachment) ?: return
        val uuid = message.messageUuid
        val player = audioPlayer ?: ChatAudioPlayer(
            context = requireContext(),
            token = tokenManager.getToken().orEmpty(),
            onFinished = { resetAudioState() },
            onError = {
                resetAudioState()
                Toast.makeText(requireContext(), R.string.attachment_open_failed, Toast.LENGTH_SHORT)
                    .show()
            }
        ).also { audioPlayer = it }

        if (playingAudioUuid == uuid) {
            if (player.isPlaying) player.pause() else player.resume()
            adapter.setAudioState(uuid, player.isPlaying)
            return
        }

        playingAudioUuid = uuid
        adapter.setAudioState(uuid, playing = true)
        player.play(url)
    }

    private fun resetAudioState() {
        playingAudioUuid = null
        adapter.setAudioState(null, playing = false)
    }

    private fun resolveAttachmentUrl(attachment: MessageAttachment): String? {
        val uuid = attachment.attachmentUuid
        return when {
            !uuid.isNullOrBlank() -> MediaUrl.chat("/api/attachments/$uuid")
            attachment.fileUrl.startsWith("/") || attachment.fileUrl.startsWith("http") ->
                attachment.fileUrl
            else -> MediaUrl.chat(attachment.fileUrl)
        }
    }

    private fun downloadAndOpen(attachment: MessageAttachment) {
        // Повторные нажатия не должны запускать параллельные загрузки.
        if (downloadJob?.isActive == true) return
        val content = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        val progressBar = content.findViewById<android.widget.ProgressBar>(R.id.downloadProgress)
        val percentText = content.findViewById<android.widget.TextView>(R.id.downloadPercent)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.attachment_downloading)
            .setView(content)
            .setNegativeButton(android.R.string.cancel) { _, _ -> downloadJob?.cancel() }
            .setCancelable(false)
            .create()
        dialog.show()
        downloadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = viewModel.downloadAttachment(attachment) { value ->
                    content.post {
                        progressBar.progress = value
                        percentText.text = "$value%"
                    }
                }
                if (dialog.isShowing) dialog.dismiss()
                if (file == null) {
                    Toast.makeText(
                        requireContext(), R.string.attachment_download_failed, Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                val publicUri = viewModel.publishDownloadedAttachment(
                    file, attachment.fileName, attachment.mimeType
                )
                Toast.makeText(
                    requireContext(),
                    if (publicUri != null) R.string.attachment_saved_downloads
                    else R.string.attachment_saved_private,
                    Toast.LENGTH_SHORT
                ).show()
                openDownloadedFile(publicUri, file, attachment)
            } finally {
                if (dialog.isShowing) dialog.dismiss()
                downloadJob = null
            }
        }
    }

    private fun openDownloadedFile(
        publicUri: android.net.Uri?,
        file: java.io.File,
        attachment: MessageAttachment
    ) {
        val uri = publicUri ?: runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(
                requireContext(), R.string.attachment_open_failed, Toast.LENGTH_SHORT
            ).show()
            return
        }
        val mime = when {
            attachment.fileName?.endsWith(".apk", ignoreCase = true) == true ->
                "application/vnd.android.package-archive"
            !attachment.mimeType.isNullOrBlank() -> attachment.mimeType
            else -> "*/*"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Часть прошивок (Transsion/Infinix) не передаёт грант установщику
            // только по флагу — дублируем URI в ClipData.
            clipData = android.content.ClipData.newRawUri("", uri)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            showOpenFallback(uri, mime, publicUri != null)
        }
    }

    /** Нет обработчика для файла: предлагаем «Поделиться» и не теряем его. */
    private fun showOpenFallback(uri: android.net.Uri, mime: String, savedToDownloads: Boolean) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.attachment_open_failed)
            .setMessage(
                if (savedToDownloads) R.string.attachment_saved_downloads
                else R.string.attachment_saved_private
            )
            .setPositiveButton(R.string.action_share) { _, _ ->
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("", uri)
                }
                runCatching {
                    startActivity(android.content.Intent.createChooser(share, null))
                }
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            if (canSendNow()) {
                sendClicked()
            } else {
                cycleSendMode()
            }
        }
        binding.btnSend.setOnTouchListener { _, event -> handleSendTouch(event) }
        binding.etMessageInput.setOnEditorActionListener { _, _, _ ->
            if (canSendNow()) sendClicked()
            false
        }
        binding.etMessageInput.doOnTextChanged { text, _, _, _ ->
            if (!text.isNullOrBlank()) sendMode = SendMode.TEXT
            updateSendButton()
            showMentionSuggestions(
                text?.toString().orEmpty(),
                binding.etMessageInput.selectionStart
            )
        }

        binding.btnGif.setOnClickListener {
            EmojiGifBottomSheet.newInstance()
                .show(childFragmentManager, "emoji_gif")
        }
        updateSendButton()
    }

    /** Подсказка участников при вводе @: список над полем ввода. */
    private fun showMentionSuggestions(text: String, cursor: Int) {
        val usernames = viewModel.uiState.value.receipts
            .asSequence()
            .filter { it.userUuid != viewModel.myUserUuid }
            .mapNotNull { it.username }
            .toList()
        val suggestion = MentionSuggestions.resolve(text, cursor, usernames)
        if (suggestion == null) {
            dismissMentionSuggestions()
            return
        }
        mentionStart = suggestion.start
        mentionEnd = suggestion.end
        val names = suggestion.candidates
        val popup = mentionPopup ?: ListPopupWindow(requireContext()).apply {
            anchorView = binding.etMessageInput
            width = dp(240)
            isModal = false
            inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnItemClickListener { _, _, position, _ ->
                val adapter = mentionPopup?.listView?.adapter
                val name = adapter?.getItem(position) as? String
                    ?: return@setOnItemClickListener
                insertMention(name)
            }
            mentionPopup = this
        }
        popup.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
        )
        if (!popup.isShowing) popup.show()
    }

    private fun insertMention(username: String) {
        if (mentionStart < 0 || mentionEnd < mentionStart) return
        val input = binding.etMessageInput
        val text = input.text?.toString().orEmpty()
        val insertion = "@$username "
        val updated = text.substring(0, mentionStart) + insertion +
            text.substring(mentionEnd.coerceAtMost(text.length))
        input.setText(updated)
        val position = (mentionStart + insertion.length).coerceAtMost(updated.length)
        input.setSelection(position)
        dismissMentionSuggestions()
    }

    private fun dismissMentionSuggestions() {
        mentionPopup?.dismiss()
        mentionStart = -1
        mentionEnd = -1
    }

    private fun canSendNow(): Boolean =
        binding.etMessageInput.text?.toString()?.isNotBlank() == true ||
            viewModel.uiState.value.pendingAttachments.isNotEmpty()

    private fun sendClicked(power: Int = 0) {
        val text = binding.etMessageInput.text?.toString().orEmpty()
        if (canSendNow()) {
            viewModel.sendText(text, power)
            binding.etMessageInput.setText("")
            ChatDrafts.clear(requireContext(), chatUuid)
            if (power > 0) playSendBurst(power)
        }
    }

    /** Короткий «взрыв» кнопки: чем сильнее заряд, тем заметнее анимация. */
    private fun playSendBurst(power: Int) {
        val scale = 1f + 1.3f * (power / 100f)
        binding.chargeBurst.burst(power)
        val vibrator = requireContext().getSystemService(android.os.Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(140, 255))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(140)
            }
        } else {
            binding.btnSend.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        binding.btnSend.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(120)
            .withEndAction {
                binding.btnSend.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            .start()
    }

    private fun handleTextSendTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                chargeStartedAt = android.os.SystemClock.elapsedRealtime()
                isCharging = false
                chargeCancelled = false
                pressStartY = event.rawY
                chargeJob?.cancel()
                chargeJob = viewLifecycleOwner.lifecycleScope.launch {
                    while (isActive) {
                        delay(CHARGE_TICK_MS)
                        val held = android.os.SystemClock.elapsedRealtime() - chargeStartedAt
                        if (ChargeCalculator.isCharged(held)) {
                            isCharging = true
                            renderCharge(held)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isCharging && event.rawY < pressStartY - dp(RECORD_CANCEL_DP)) {
                    chargeCancelled = true
                    cancelCharge()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                chargeJob?.cancel()
                val held = android.os.SystemClock.elapsedRealtime() - chargeStartedAt
                val power = if (isCharging && !chargeCancelled) ChargeCalculator.power(held) else 0
                val shouldSend = !chargeCancelled
                resetChargeMeter()
                if (shouldSend) sendClicked(power)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelCharge()
                return true
            }
        }
        return true
    }

    private fun renderCharge(heldMs: Long) {
        val power = ChargeCalculator.power(heldMs)
        binding.chargeBar.visibility = View.VISIBLE
        binding.progressCharge.progress = power
        binding.tvChargePercent.text = getString(R.string.charge_format, power)
        binding.chargeBurst.updatePower(power)
        // Кнопка «раздувается» и пульсирует; к концу — сильнее.
        val level = power / 100f
        val pulse = 1f + 0.10f * level * (0.5f + 0.5f * kotlin.math.sin(heldMs / 90.0).toFloat())
        val scale = 1f + 0.55f * level
        binding.btnSend.scaleX = scale * pulse
        binding.btnSend.scaleY = scale * pulse
        vibrateCharge(level)
    }

    /** Вибрация усиливается и учащается по мере заряда. */
    private fun vibrateCharge(level: Float) {
        if (level <= 0.02f) return
        val now = android.os.SystemClock.elapsedRealtime()
        val interval = (220 - 170 * level).toLong().coerceAtLeast(50)
        if (now - lastChargeVibrationAt < interval) return
        lastChargeVibrationAt = now
        val vibrator = requireContext().getSystemService(android.os.Vibrator::class.java)
        val amplitude = (25 + 230 * level).toInt().coerceIn(1, 255)
        if (vibrator == null || !vibrator.hasVibrator()) {
            binding.btnSend.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(30, amplitude)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    private fun cancelCharge() {
        chargeJob?.cancel()
        isCharging = false
        resetChargeMeter()
    }

    private fun resetChargeMeter() {
        isCharging = false
        chargeStartedAt = 0L
        binding.chargeBar.visibility = View.GONE
        binding.progressCharge.progress = 0
        binding.tvChargePercent.text = getString(R.string.charge_zero)
        binding.chargeBurst.clear()
        binding.btnSend.scaleX = 1f
        binding.btnSend.scaleY = 1f
    }

    private fun cycleSendMode() {
        sendMode = when (sendMode) {
            SendMode.TEXT -> SendMode.VOICE
            SendMode.VOICE -> SendMode.VIDEO
            SendMode.VIDEO -> SendMode.TEXT
        }
        if (sendMode == SendMode.VOICE) requestMicPermission()
        if (sendMode == SendMode.VIDEO) requestCameraPermissions()
        updateSendButton()
    }

    private fun updateSendButton() {
        val effective = if (canSendNow()) SendMode.TEXT else sendMode
        val (icon, description) = when (effective) {
            SendMode.TEXT -> R.drawable.ic_send_burst to R.string.send_mode_text
            SendMode.VOICE -> R.drawable.ic_mic to R.string.send_mode_voice
            SendMode.VIDEO -> R.drawable.ic_videocam to R.string.send_mode_video
        }
        binding.btnSend.setImageResource(icon)
        // Взрыв — цветная иконка, её не тонируем; микрофон/камеру красим в акцент.
        binding.btnSend.imageTintList =
            if (effective == SendMode.TEXT) null
            else ContextCompat.getColorStateList(requireContext(), R.color.purple_500)
        binding.btnSend.contentDescription = getString(description)
    }

    private fun handleSendTouch(event: MotionEvent): Boolean {
        if (sendMode == SendMode.TEXT && canSendNow()) return handleTextSendTouch(event)
        if (sendMode == SendMode.TEXT || canSendNow()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!ensurePermisssionsFor(sendMode)) return true
                sendPressed = true
                pressStartY = event.rawY
                cancelArmed = false
                recordingStartJob?.cancel()
                recordingStartJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(LONG_PRESS_MS)
                    startRecording(sendMode)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeRecorderMode != null) {
                    val armed = event.rawY < pressStartY - dp(RECORD_CANCEL_DP)
                    if (armed != cancelArmed) {
                        cancelArmed = armed
                        updateRecordingHint()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                sendPressed = false
                val wasRecording = activeRecorderMode != null
                if (wasRecording) {
                    finishRecording(cancelArmed)
                } else {
                    recordingStartJob?.cancel()
                    cycleSendMode()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                sendPressed = false
                if (activeRecorderMode != null) finishRecording(cancel = true)
                else recordingStartJob?.cancel()
                return true
            }
        }
        return true
    }

    private fun ensurePermisssionsFor(mode: SendMode): Boolean {
        return when (mode) {
            SendMode.VOICE -> {
                if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    true
                } else {
                    requestMicPermission()
                    false
                }
            }
            SendMode.VIDEO -> {
                val mic = hasPermission(Manifest.permission.RECORD_AUDIO)
                val camera = hasPermission(Manifest.permission.CAMERA)
                if (mic && camera) {
                    true
                } else {
                    requestCameraPermissions()
                    // Разрешения спрашиваем до записи; пользователь нажмёт ещё раз.
                    false
                }
            }
            SendMode.TEXT -> true
        }
    }

    private fun requestMicPermission() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestCameraPermissions() {
        val missing = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) missing += Manifest.permission.RECORD_AUDIO
        if (!hasPermission(Manifest.permission.CAMERA)) missing += Manifest.permission.CAMERA
        if (missing.isNotEmpty()) cameraPermissions.launch(missing.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun startRecording(mode: SendMode) {
        if (activeRecorderMode != null) return
        if (!ensurePermisssionsFor(mode)) return

        activeRecorderMode = mode
        cancelArmed = false
        binding.recordingOverlay.visibility = View.VISIBLE
        binding.tvRecordingTimer.text = "0:00"
        binding.videoNotePreviewCard.visibility =
            if (mode == SendMode.VIDEO) View.VISIBLE else View.GONE
        updateRecordingHint()

        when (mode) {
            SendMode.VOICE -> startVoiceRecording()
            SendMode.VIDEO -> startVideoRecording()
            SendMode.TEXT -> Unit
        }
    }

    private fun startVoiceRecording() {
        val recorder = VoiceRecorder(requireContext())
        if (!recorder.start()) {
            abortRecording(R.string.recording_failed)
            return
        }
        voiceRecorder = recorder
        startRecordingTimer(MAX_VOICE_MS)
    }

    private fun startVideoRecording() {
        val texture = binding.videoNotePreview
        val recorder = VideoNoteRecorder(requireContext())
        videoRecorder = recorder
        videoReady = false
        binding.tvRecordingHint.setText(R.string.recording_starting_camera)
        val start = {
            recorder.start(
                texture,
                onReady = {
                    // Пока камера открывалась, палец могли отпустить.
                    if (sendPressed && activeRecorderMode == SendMode.VIDEO) {
                        videoReady = true
                        updateRecordingHint()
                        startRecordingTimer(MAX_VIDEO_MS)
                    } else {
                        recorder.cancel()
                        if (activeRecorderMode == SendMode.VIDEO) {
                            activeRecorderMode = null
                            hideRecordingOverlay()
                        }
                    }
                },
                onError = { abortRecording(R.string.recording_failed) }
            )
        }
        if (texture.isAvailable) {
            texture.post(start)
        } else {
            texture.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) = start()

                override fun onSurfaceTextureSizeChanged(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) = Unit

                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture) = true

                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
            }
        }
    }

    private fun startRecordingTimer(maxMs: Long) {
        recordStartMs = System.currentTimeMillis()
        recordTimerJob?.cancel()
        recordTimerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - recordStartMs
                binding.tvRecordingTimer.text = formatRecordingTime(elapsed)
                if (elapsed >= maxMs) {
                    finishRecording(cancel = false)
                    break
                }
                delay(200)
            }
        }
    }

    private fun updateRecordingHint() {
        binding.tvRecordingHint.setText(
            if (cancelArmed) R.string.recording_release_to_cancel
            else R.string.recording_release_to_send
        )
    }

    private fun finishRecording(cancel: Boolean) {
        if (finishingRecording) return
        finishingRecording = true
        val mode = activeRecorderMode
        activeRecorderMode = null
        recordTimerJob?.cancel()
        recordTimerJob = null
        val cancelEffective = cancel || (mode == SendMode.VIDEO && !videoReady)
        videoReady = false

        if (cancelEffective) {
            voiceRecorder?.cancel()
            videoRecorder?.cancel()
            voiceRecorder = null
            videoRecorder = null
            hideRecordingOverlay()
            finishingRecording = false
            return
        }

        val prepared = when (mode) {
            SendMode.VOICE -> voiceRecorder?.stop()?.let { it.toPrepared() }
            SendMode.VIDEO -> videoRecorder?.stop()?.let { it.toPrepared() }
            else -> null
        }
        voiceRecorder = null
        videoRecorder = null
        hideRecordingOverlay()

        if (prepared == null) {
            Toast.makeText(requireContext(), R.string.recording_failed, Toast.LENGTH_SHORT).show()
        } else {
            val messageType = if (mode == SendMode.VOICE) "VOICE" else "VIDEO_NOTE"
            viewModel.sendRecordedAttachment(prepared, messageType)
        }
        finishingRecording = false
    }

    private fun abortRecording(messageRes: Int) {
        activeRecorderMode = null
        recordTimerJob?.cancel()
        voiceRecorder?.cancel()
        videoRecorder?.cancel()
        voiceRecorder = null
        videoRecorder = null
        hideRecordingOverlay()
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun hideRecordingOverlay() {
        binding.recordingOverlay.visibility = View.GONE
        binding.videoNotePreviewCard.visibility = View.GONE
        binding.videoNotePreview.surfaceTextureListener = null
    }

    private fun RecordedAudio.toPrepared() = PreparedAttachment(
        file = file,
        thumbnail = null,
        name = file.name,
        size = file.length(),
        mimeType = "audio/mp4",
        type = "AUDIO",
        width = null,
        height = null,
        duration = durationSec
    )

    private fun RecordedVideo.toPrepared() = PreparedAttachment(
        file = file,
        thumbnail = thumbnail,
        name = file.name,
        size = file.length(),
        mimeType = "video/mp4",
        type = "VIDEO",
        width = width,
        height = height,
        duration = durationSec
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatRecordingTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    override fun onGifSelected(gif: com.example.muzea.domain.model.Gif) {
        viewModel.sendGif(gif)
    }

    override fun onEmojiSelected(emoji: String) {
        val input = binding.etMessageInput
        val start = input.selectionStart.coerceAtLeast(0)
        val end = input.selectionEnd.coerceAtLeast(start)
        val current = input.text?.toString().orEmpty()
        val updated = current.substring(0, start) + emoji + current.substring(end)
        input.setText(updated)
        input.setSelection((start + emoji.length).coerceAtMost(updated.length))
    }

    override fun onOpenGif() {
        GifPickerBottomSheet.newInstance(chatUuid)
            .show(childFragmentManager, "gif_picker")
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

    private fun observeNetwork() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkMonitor.status.collect { status ->
                    val show = status.quality != com.example.muzea.utils.NetworkQuality.GOOD
                    binding.tvNetworkBanner.visibility = if (show) View.VISIBLE else View.GONE
                    if (show) {
                        binding.tvNetworkBanner.setText(
                            if (status.quality == com.example.muzea.utils.NetworkQuality.OFFLINE)
                                R.string.network_offline_banner
                            else R.string.network_slow_banner
                        )
                    } else {
                        // Сеть вернулась — пробуем догрузить то, что отложили.
                        adapter.refreshMedia()
                    }
                }
            }
        }
    }

    /** Раз в 5 минут пробуем догрузить отложенные медиа, пока экран открыт. */
    private fun startMediaRetry() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(MEDIA_RETRY_MS)
                    if (networkMonitor.status.value.online) adapter.refreshMedia()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val messages = state.messages
                val wasAtBottom = isAtBottom()
                binding.progressHistory.visibility =
                    if (state.isLoadingMore) View.VISIBLE else View.GONE
                val previousStatuses = lastStatuses
                val statusesChanged = state.statuses != previousStatuses
                val changedStatusUuids = if (statusesChanged) {
                    (previousStatuses.keys + state.statuses.keys).filter {
                        previousStatuses[it] != state.statuses[it]
                    }
                } else {
                    emptyList()
                }
                val notifyChangedStatuses = {
                    changedStatusUuids.forEach { uuid ->
                        val position = messages.indexOfFirst { it.messageUuid == uuid }
                        if (position >= 0) adapter.notifyItemChanged(position)
                    }
                }
                val listChanged = messages !== lastMessages
                // Если пользователь читает историю, запоминаем верхний видимый
                // пузырь и возвращаем его после DiffUtil — иначе список
                // «прыгает» при фоновом обновлении.
                val layoutManager = binding.recyclerViewMessages.layoutManager as? LinearLayoutManager
                val anchorPosition = if (initialScrollDone && !wasAtBottom) {
                    layoutManager?.findFirstVisibleItemPosition() ?: -1
                } else {
                    -1
                }
                val anchorUuid = messages.getOrNull(anchorPosition)?.messageUuid
                val anchorOffset = if (anchorPosition >= 0) {
                    layoutManager?.findViewByPosition(anchorPosition)?.top ?: 0
                } else {
                    0
                }
                if (listChanged) {
                    lastMessages = messages
                    adapter.updateList(messages) {
                        if (statusesChanged) notifyChangedStatuses()
                        if (anchorUuid != null) {
                            val position = adapter.currentList.indexOfFirst {
                                it.messageUuid == anchorUuid
                            }
                            if (position >= 0) {
                                layoutManager?.scrollToPositionWithOffset(position, anchorOffset)
                            }
                        }
                    }
                } else if (statusesChanged) {
                    notifyChangedStatuses()
                }
                if (statusesChanged) lastStatuses = state.statuses
                if (state.pendingAttachments != lastPending) {
                    lastPending = state.pendingAttachments
                    renderPendingAttachments(state.pendingAttachments)
                }
                binding.btnSend.isEnabled = state.pendingAttachments.all { it.isUploaded }
                updateSendButton()
                updateEmptyState(messages)
                renderConversationState(state)
                renderComposerBanner(state)
                renderPinnedBar(state)

                if (messages.isNotEmpty() && !initialScrollDone) {
                    initialScrollDone = true
                    val target = if (unreadCount > 0) {
                        (messages.size - unreadCount).toInt().coerceIn(0, messages.size - 1)
                    } else {
                        messages.size - 1
                    }
                    pendingScrollPosition = target
                    if (unreadCount > 0) unreadAnchorPosition = target
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
                updateScrollButton()

                val targetUuid = pendingHighlightUuid
                if (targetUuid != null) {
                    val targetPosition = messages.indexOfFirst { it.messageUuid == targetUuid }
                    if (targetPosition >= 0) {
                        pendingHighlightUuid = null
                        scrollToAndHighlight(targetPosition, targetUuid)
                    } else if (!state.historyReached && !state.isLoadingMore && highlightAttempts < MAX_HIGHLIGHT_PAGES) {
                        // Сообщение из push может быть выше первой страницы.
                        highlightAttempts++
                        viewModel.loadOlder()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sendError.collect { message ->
                Toast.makeText(requireContext(), getString(R.string.error_prefix, message), Toast.LENGTH_LONG).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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

    /**
     * Прокручивает к сообщению из push и коротко подсвечивает его.
     *
     * Подсветка — полупрозрачный foreground строки: он рисуется поверх
     * содержимого и не ломает скругления пузыря.
     */
    private fun scrollToAndHighlight(position: Int, uuid: String) {
        val recyclerView = binding.recyclerViewMessages
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(position, dp(80))
            attemptHighlight(recyclerView, position, uuid, attempt = 0)
        }
    }

    private fun attemptHighlight(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        position: Int,
        uuid: String,
        attempt: Int
    ) {
        recyclerView.postDelayed({
            val holder = recyclerView.findViewHolderForAdapterPosition(position)
            if (holder == null) {
                // Строка могла ещё не разложиться после прокрутки.
                if (attempt < HIGHLIGHT_ATTEMPTS) {
                    attemptHighlight(recyclerView, position, uuid, attempt + 1)
                }
                return@postDelayed
            }
            val view = holder.itemView
            view.tag = uuid
            view.foreground = android.graphics.drawable.ColorDrawable(HIGHLIGHT_COLOR)
            view.postDelayed({
                if (view.tag == uuid) {
                    view.foreground = null
                    view.tag = null
                }
            }, HIGHLIGHT_MS)
        }, if (attempt == 0) 250L else 120L)
    }

    override fun onStart() {
        super.onStart()
        viewModel.resumePolling()
        // Внутри onStart lifecycle ещё CREATED, поэтому смотрим только на hidden.
        if (!isHidden) ActiveChatTracker.chatUuid = chatUuid
    }

    override fun onStop() {
        clearActiveChat()
        viewModel.pausePolling()
        super.onStop()
    }

    /**
     * Вкладки и экраны «поверх» не останавливают фрагмент, а прячут его.
     * Без этого чат считался бы открытым, пока пользователь на другой вкладке,
     * и push этого чата ошибочно подавлялся бы.
     */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            clearActiveChat()
        } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            ActiveChatTracker.chatUuid = chatUuid
        }
    }

    private fun clearActiveChat() {
        if (ActiveChatTracker.chatUuid == chatUuid) ActiveChatTracker.chatUuid = null
    }

    override fun onPause() {
        if (activeRecorderMode != null) finishRecording(cancel = true)
        // Черновик сохраняем только для обычного ввода: текст редактирования
        // не должен затирать неотправленное сообщение.
        if (_binding != null && viewModel.uiState.value.editing == null) {
            ChatDrafts.save(
                requireContext(), chatUuid, binding.etMessageInput.text?.toString()
            )
        }
        super.onPause()
    }

    override fun onDestroyView() {
        audioPlayer?.release()
        audioPlayer = null
        playingAudioUuid = null
        if (::adapter.isInitialized) {
            adapter.unregisterAdapterDataObserver(scrollObserver)
            adapter.release()
        }
        super.onDestroyView()
        _binding = null
    }
}
