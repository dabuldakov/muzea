package com.example.muzea.ui.chat

import com.example.muzea.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.utils.MediaUrl
import com.example.muzea.utils.TokenManager
import com.example.muzea.data.repository.ChatListCache
import com.example.muzea.data.repository.LinkPreviewStore
import com.example.muzea.databinding.FragmentChatConversationBinding
import com.example.muzea.databinding.ItemPendingAttachmentBinding
import com.example.muzea.ui.common.ReportDialog
import com.example.muzea.ui.navigation.navigator
import com.example.muzea.utils.ActiveChatTracker
import com.example.muzea.utils.ChatNotifications
import com.example.muzea.utils.AvatarLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private var pendingScrollToTop = false
    private var lastStatuses: Map<String, MessageDeliveryStatus> = emptyMap()
    private var lastMessages: List<Message>? = null
    private var pendingHighlightUuid: String? = null
    private var highlightAttempts = 0
    private lateinit var scrollController: ChatScrollController
    private lateinit var composerBanner: ComposerBanner
    private lateinit var pendingAttachmentsView: PendingAttachmentsView
    private lateinit var chargeMeter: ChargeMeter
    private lateinit var chargeGesture: ChargeGestureController
    private lateinit var recordingController: ChatRecordingController
    private lateinit var sendModeController: ChatSendModeController
    private lateinit var highlighter: ChatMessageHighlighter
    private lateinit var listScroller: ChatMessageListScroller
    private lateinit var actionsPresenter: ChatMessageActionsPresenter
    private lateinit var selectionActions: ChatSelectionActions
    private lateinit var composerIntents: ChatComposerIntents
    private lateinit var pinnedBarView: PinnedBarView
    private lateinit var downloadController: AttachmentDownloadController
    private lateinit var recordingTimer: RecordingTimer
    private lateinit var conversationStateView: ConversationStateView
    private lateinit var recordingOverlay: RecordingOverlayView
    private lateinit var networkBannerView: NetworkBannerView
    /** Пока модалка открыта, «назад» закрывает её, а не весь чат. */
    private val actionsBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (::actionsPresenter.isInitialized) actionsPresenter.dismiss()
        }
    }
    /** Точка последнего касания списка — к ней ставим модалку. */
    private var lastTapX = 0
    private var lastTapY = 0
    private var lastPending: List<PendingAttachment> = emptyList()
    /** Сообщения, ожидающие выбора чата для пересылки (мультивыбор). */
    private var pendingForwardMessages: List<Message> = emptyList()
    private lateinit var selectionController: SelectionController
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }
    /** Текущий список закреплённых (для клика по панели и открепления). */
    private var pinnedMessages: List<Message> = emptyList()


    private lateinit var audioController: ChatAudioController
    private lateinit var mentionController: MentionPopupController
    private lateinit var fileOpener: DownloadedFileOpener
    private lateinit var attachmentPresenter: ChatAttachmentPresenter

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

    private val imageGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val action = ImageGalleryActivity.actionOf(result.data) ?: return@registerForActivityResult
        handleImageViewerResult(action.first, action.second)
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addAttachments(listOf(it)) }
    }

    // Якорь при догрузке истории: сохраняем верхнее видимое сообщение и его
    // смещение, чтобы после вставки старых сообщений сверху вид не «прыгнул».

    private val scrollObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = performPendingScroll()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            listScroller.restore()
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
        private const val MEDIA_RETRY_MS = 5 * 60 * 1000L
        private const val RECORD_CANCEL_DP = 72
        private const val MAX_HIGHLIGHT_PAGES = 5
        private const val MENU_DELETE = 1
        private const val MENU_FORWARD = 2

        private val ATTACHMENT_FILE_MIME_TYPES = arrayOf(
            "audio/*", "video/*",
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
        readArguments()
        bindHeader()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, actionsBackCallback
        )
        setupRecyclerView()
        setupConversationViews()
        setupDownloadAndAudio()
        setupComposerViews()
        mentionController = MentionPopupController(requireContext(), binding.etMessageInput)
        setupInput()
        setupAttachments()
        setupReplyBanner()
        setupSelectionBar()
        prefillDraft()
        observeViewModel()
        observeNetwork()
        startMediaRetry()
    }

    private fun readArguments() {
        chatUuid = arguments?.getString(ChatConversationViewModel.ARG_CHAT_UUID) ?: ""
        unreadCount = arguments?.getLong(ARG_UNREAD_COUNT, 0L) ?: 0L
        pendingHighlightUuid = arguments?.getString(ARG_HIGHLIGHT_MESSAGE)
    }

    private fun bindHeader() {
        val chatTitle = arguments?.getString(ARG_CHAT_TITLE) ?: "Chat"
        binding.tvTitle.text = chatTitle
        AvatarLoader.load(binding.ivAvatar, arguments?.getString(ARG_CHAT_AVATAR))
        binding.tvTitle.setOnClickListener { openGroupSettings(chatTitle) }
        binding.btnBack.setOnClickListener { navigator.back() }
    }

    private fun setupConversationViews() {
        pinnedBarView = PinnedBarView(
            context = requireContext(),
            bar = binding.pinnedBar,
            content = binding.pinnedBarContent,
            unpin = binding.btnUnpin,
            title = binding.tvPinnedTitle,
            preview = binding.tvPinnedText,
            onUnpin = { pinnedMessages.firstOrNull()?.let { viewModel.unpinMessage(it) } },
            onOpen = { uuid -> jumpToMessage(uuid) }
        ).also { it.setup() }
        chargeMeter = ChargeMeter(
            chargeBar = binding.chargeBar,
            progress = binding.progressCharge,
            percentText = binding.tvChargePercent,
            burst = binding.chargeBurst,
            sendButton = binding.btnSend,
            chargeFormat = { power -> getString(R.string.charge_format, power) },
            chargeZero = getString(R.string.charge_zero),
            onVibrate = { level -> chargeGesture.vibrate(level) }
        )
        chargeGesture = ChargeGestureController(
            scope = viewLifecycleOwner.lifecycleScope,
            context = requireContext(),
            meter = chargeMeter,
            button = binding.btnSend,
            cancelDistancePx = (RECORD_CANCEL_DP * resources.displayMetrics.density).toInt(),
            onSend = { power -> sendClicked(power) }
        )
        recordingOverlay = RecordingOverlayView(
            overlay = binding.recordingOverlay,
            timer = binding.tvRecordingTimer,
            hint = binding.tvRecordingHint,
            previewCard = binding.videoNotePreviewCard,
            preview = binding.videoNotePreview
        )
        conversationStateView = ConversationStateView(
            progress = binding.progressBar,
            error = binding.tvError,
            list = binding.recyclerViewMessages
        )
        networkBannerView = NetworkBannerView(binding.tvNetworkBanner) {
            adapter.refreshMedia()
        }
        recordingTimer = RecordingTimer(
            scope = viewLifecycleOwner.lifecycleScope,
            format = { ms -> formatRecordingTime(ms) },
            onTick = { text -> binding.tvRecordingTimer.text = text },
            onLimit = { recordingController.finishAtLimit() }
        )
        sendModeController = ChatSendModeController(
            context = requireContext(),
            button = binding.btnSend,
            requestMic = ::requestMicPermission,
            requestCamera = ::requestCameraPermissions,
            canSendNow = ::canSendNow
        )
        recordingController = ChatRecordingController(
            context = requireContext(),
            textureView = binding.videoNotePreview,
            overlay = recordingOverlay,
            timer = recordingTimer,
            scope = viewLifecycleOwner.lifecycleScope,
            cancelDistancePx = (RECORD_CANCEL_DP * resources.displayMetrics.density).toInt(),
            ensurePermissions = sendModeController::ensurePermissions,
            onRecorded = { prepared, type -> viewModel.sendRecordedAttachment(prepared, type) },
            toast = { res -> Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show() }
        )
    }

    private fun setupDownloadAndAudio() {
        fileOpener = DownloadedFileOpener(
            context = requireContext(),
            isAdded = { isAdded },
            startActivity = { intent -> startActivity(intent) }
        )
        downloadController = AttachmentDownloadController(
            scope = viewLifecycleOwner.lifecycleScope,
            createProgress = { onCancel ->
                AttachmentDownloadDialog(requireContext(), layoutInflater, onCancel)
            },
            download = { attachment, onProgress ->
                viewModel.downloadAttachment(attachment, onProgress)
            },
            publish = { file, name, mime ->
                viewModel.publishDownloadedAttachment(file, name, mime)
            },
            open = { uri, file, attachment -> fileOpener.open(uri, file, attachment) },
            toast = { res -> Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show() }
        )
        audioController = ChatAudioController(
            adapter = adapter,
            createPlayer = {
                ChatAudioPlayer(
                    context = requireContext(),
                    token = tokenManager.getToken().orEmpty(),
                    onFinished = { audioController.finish() },
                    onError = { audioController.fail() }
                )
            },
            onFinished = {},
            onError = {
                Toast.makeText(
                    requireContext(), R.string.attachment_open_failed, Toast.LENGTH_SHORT
                ).show()
            }
        )
        attachmentPresenter = ChatAttachmentPresenter(
            messages = { viewModel.uiState.value.messages },
            launchViewer = { attachment ->
                AttachmentViewerActivity.start(requireContext(), attachment)
            },
            launchGallery = { images, startIndex ->
                imageGalleryLauncher.launch(
                    ImageGalleryActivity.intent(requireContext(), images, startIndex)
                )
            },
            playAudio = { message, attachment ->
                AttachmentUrlResolver.resolve(attachment)?.let { url ->
                    audioController.toggle(message.messageUuid, url)
                }
            },
            download = { attachment -> downloadController.start(attachment) }
        )
    }

    private fun setupComposerViews() {
        composerIntents = ChatComposerIntents(
            input = binding.etMessageInput,
            launchPhotos = {
                pickPhotos.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            launchVideo = {
                pickVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            launchFile = { pickFile.launch(ATTACHMENT_FILE_MIME_TYPES) },
            sendGif = { gif -> viewModel.sendGif(gif) }
        )
        pendingAttachmentsView = PendingAttachmentsView(
            inflater = layoutInflater,
            scroll = binding.scrollPending,
            container = binding.pendingAttachments,
            onRemove = { id -> viewModel.removePendingAttachment(id) },
            onRetry = { id -> viewModel.retryPendingAttachment(id) }
        )
        composerBanner = ComposerBanner(
            context = requireContext(),
            banner = binding.replyBanner,
            cancelButton = binding.btnCancelReply,
            titleView = binding.tvReplyBannerTitle,
            previewView = binding.tvReplyBannerText,
            input = binding.etMessageInput,
            myUserUuid = { viewModel.myUserUuid },
            requestInput = { showKeyboard() }
        )
        scrollController = ChatScrollController(
            recyclerView = binding.recyclerViewMessages,
            button = binding.btnScrollBottom,
            messageCount = { adapter.itemCount },
            unreadAnchor = { unreadAnchorPosition },
            clearUnreadAnchor = { unreadAnchorPosition = null },
            labelRes = { hasUnread ->
                if (hasUnread) R.string.scroll_to_unread else R.string.scroll_to_latest
            },
            label = { res -> getString(res) }
        ).also { it.setup() }
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
    private fun showMessageActions(message: Message) = actionsPresenter.show(message)

    /** Для тестов: видна ли сейчас плавающая модалка действий. */
    internal fun isActionsPopupShowing(): Boolean =
        ::actionsPresenter.isInitialized && actionsPresenter.isShowing()


    private fun showReactionPicker(message: Message) {
        ReactionPickerDialog.show(requireContext(), message) { target, emoji ->
            viewModel.toggleReaction(target, emoji)
        }
    }

    /**
     * Личный чат с удалённым пользователем: история видна, но отправка
     * запрещена. Сервер тоже отклонит сообщение.
     */
    private fun isPeerDeleted(): Boolean = !isGroupChat() &&
        ChatListCache.get().firstOrNull { it.chatUuid == chatUuid }?.peerDeleted == true

    private fun applyDeletedPeerState() {
        if (!isPeerDeleted()) return
        binding.etMessageInput.isEnabled = false
        binding.etMessageInput.hint = getString(R.string.deleted_user_chat_disabled)
        binding.etMessageInput.setText("")
        binding.btnSend.visibility = View.GONE
        binding.btnAdd.visibility = View.GONE
        binding.btnGif.visibility = View.GONE
    }

    private fun isGroupChat(): Boolean =
        ChatListCache.get().firstOrNull { it.chatUuid == chatUuid }
            ?.chatType.equals("GROUP", ignoreCase = true)

    /** Строки «кто прочитал / кому доставлено» для шторки статуса. */
    /** Ниже шапки чата с небольшим отступом — верхняя граница модалки. */
    private fun actionsTopLimit(): Int {
        val header = binding.chatHeader ?: return 0
        val location = IntArray(2)
        header.getLocationOnScreen(location)
        return location[1] + header.height + (8 * resources.displayMetrics.density).toInt()
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
                message.senderUuid?.let { uuid ->
                    ChatDialogs.confirmBlock(requireContext()) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val ok = viewModel.blockUser(uuid)
                            Toast.makeText(
                                requireContext(),
                                if (ok) R.string.block_success else R.string.block_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            MessageActionsBottomSheet.ACTION_REPLY ->
                viewModel.setReplyTo(message)
            MessageActionsBottomSheet.ACTION_SELECT ->
                enterSelectionMode(message)
            MessageActionsBottomSheet.ACTION_EDIT ->
                viewModel.startEditing(message)
            MessageActionsBottomSheet.ACTION_DELETE ->
                ChatDialogs.confirmDeleteMessage(requireContext()) {
                    viewModel.deleteMessage(message)
                }
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
        ChatDialogs.forwardComment(requireContext(), layoutInflater) { comment ->
            viewModel.forwardMessages(messages, chat.chatUuid, comment)
        }
    }

    private fun setupSelectionBar() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, selectionBackCallback
        )
        selectionActions = ChatSelectionActions(
            context = requireContext(),
            myUserUuid = { viewModel.myUserUuid },
            selectedItems = { selectionController.selectedItems() },
            deleteMessage = { message -> viewModel.deleteMessage(message) },
            exitSelection = { exitSelectionMode() },
            startForward = { messages ->
                pendingForwardMessages = messages
                ForwardChatPickerBottomSheet.newInstance(chatUuid)
                    .show(childFragmentManager, "forward_picker")
            }
        )
        selectionController = SelectionController(
            adapter = adapter,
            selectionBar = binding.selectionBar,
            composerBar = binding.composerBar,
            countView = binding.tvSelectionCount,
            backCallback = selectionBackCallback,
            messages = { adapter.currentList },
            countLabel = { count -> getString(R.string.selected_count, count) },
            onDelete = { selectionActions.requestDelete() },
            onForward = { selectionActions.requestForward() }
        ).also {
            it.setup(
                deleteButton = binding.btnSelectionDelete,
                forwardButton = binding.btnSelectionForward,
                closeButton = binding.btnSelectionClose
            )
        }
    }

    private fun enterSelectionMode(message: Message) {
        selectionController.enter(message)
    }

    private fun toggleSelection(message: Message) {
        selectionController.toggle(message)
    }

    private fun exitSelectionMode() {
        if (::selectionController.isInitialized) selectionController.exit()
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

    private fun openGroupSettings(chatTitle: String) {
        navigator.openGroupSettings(chatUuid, chatTitle, arguments?.getString(ARG_CHAT_AVATAR))
    }

    private fun setupRecyclerView() {
        highlighter = ChatMessageHighlighter(binding.recyclerViewMessages, dp(80))
        listScroller = ChatMessageListScroller(
            recyclerView = binding.recyclerViewMessages,
            adapter = { if (::adapter.isInitialized) adapter else null },
            messages = { viewModel.uiState.value.messages },
            isViewAlive = { _binding != null },
            onLoadOlder = { viewModel.loadOlder() },
            onMarkRead = { uuid -> viewModel.markReadUpTo(uuid) }
        )
        actionsPresenter = ChatMessageActionsPresenter(
            context = requireContext(),
            fragmentManager = childFragmentManager,
            recyclerView = binding.recyclerViewMessages,
            adapter = { if (::adapter.isInitialized) adapter else null },
            myUserUuid = { viewModel.myUserUuid },
            isGroupChat = ::isGroupChat,
            deliveryStatus = { uuid -> viewModel.deliveryStatus(uuid) },
            reactionAuthors = { message -> viewModel.reactionAuthors(message) },
            participantStatuses = { message -> viewModel.participantStatuses(message) },
            receiptTime = { message, uuid -> viewModel.receiptTime(message, uuid) },
            strings = { res -> getString(res) },
            topLimit = ::actionsTopLimit,
            lastTap = {
                val x = lastTapX.takeIf { it > 0 } ?: MessageAdapter.lastTouchX.takeIf { it > 0 }
                val y = lastTapY.takeIf { it > 0 } ?: MessageAdapter.lastTouchY.takeIf { it > 0 }
                x to y
            },
            scope = viewLifecycleOwner.lifecycleScope,
            onAction = ::onMessageAction,
            onReaction = { message, emoji -> viewModel.toggleReaction(message, emoji) },
            loadDetails = { message ->
                viewModel.loadMessageReceiptTimes(message)
                viewModel.refreshReceiptsNow()
            },
            onDismissed = { actionsBackCallback.isEnabled = false }
        )
        adapter = MessageAdapter(
            myUserUuid = viewModel.myUserUuid,
            linkPreviews = linkPreviewStore,
            onMessageClick = { message ->
                if (selectionController.isActive) {
                    toggleSelection(message)
                } else {
                    showMessageActions(message)
                }
            },
            statusFor = { message -> viewModel.deliveryStatus(message.messageUuid) },
            onStatusClick = { message -> showMessageActions(message) },
            onAttachmentClick = { message, attachment ->
                attachmentPresenter.open(message, attachment)
            },
            isGroupChat = isGroupChat(),
            onReactionClick = { message, emoji -> viewModel.toggleReaction(message, emoji) },
            onReplyClick = { uuid -> jumpToMessage(uuid) }
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
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(
                    rv: RecyclerView,
                    event: android.view.MotionEvent
                ): Boolean {
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        lastTapX = event.rawX.toInt()
                        lastTapY = event.rawY.toInt()
                        // Любое новое касание списка закрывает старую модалку:
                        // иначе она остаётся на месте прошлого тапа.
                        if (::actionsPresenter.isInitialized) actionsPresenter.dismiss()
                    }
                    return false
                }
            })
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        listScroller.markVisibleAsRead()
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    // Догружаем историю, когда пользователь домотал до самого верха.
                    if (dy < 0 && !recyclerView.canScrollVertically(-1)) {
                        listScroller.requestOlder()
                    }
                    if (::actionsPresenter.isInitialized) actionsPresenter.dismiss()
                    updateScrollButton()
                }
            })
        }
    }

    private fun performPendingScroll() {
        val position = pendingScrollPosition ?: return
        pendingScrollPosition = null
        if (pendingScrollToTop) {
            pendingScrollToTop = false
            // Отметку о прочтении ставим уже после выравнивания, иначе
            // до прокрутки успеет «прочитаться» низ ленты.
            listScroller.scrollToTop(position)
        } else {
            listScroller.scrollTo(position)
        }
    }

    private fun isAtBottom(): Boolean = scrollController.isAtBottom()

    /** Кнопка прокрутки: видна, только когда список не в самом низу. */
    private fun updateScrollButton() {
        if (::scrollController.isInitialized) scrollController.update()
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
        composerBanner.render(replyTo = state.replyTo, editing = state.editing)
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.showSoftInput(binding.etMessageInput, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Прокручивает к сообщению и подсвечивает его. Если оно не загружено,
     * поднимает историю и повторит переход, когда оно появится.
     */
    private fun jumpToMessage(messageUuid: String) {
        val position = viewModel.uiState.value.messages
            .indexOfFirst { it.messageUuid == messageUuid }
        if (position >= 0) {
            highlighter.scrollToAndHighlight(position, messageUuid)
            return
        }
        pendingHighlightUuid = messageUuid
        highlightAttempts = 0
        viewModel.loadOlder()
    }

    /** Панель закреплённого сообщения сверху чата. */
    private fun renderPinnedBar(state: ChatConversationUiState) {
        pinnedMessages = state.pinnedMessages
        pinnedBarView.render(state.pinnedMessages)
    }

    private fun showAttachmentPicker() {
        AttachmentsBottomSheet.newInstance()
            .show(childFragmentManager, "attachments")
    }

    override fun onAttachmentKind(kind: AttachmentsBottomSheet.Kind) =
        composerIntents.onAttachmentKind(kind)

    private fun renderPendingAttachments(pending: List<PendingAttachment>) {
        pendingAttachmentsView.render(pending)
    }

    /** Ответ/пересылка из просмотрщика картинок. */
    private fun handleImageViewerResult(action: String, messageUuid: String) {
        val message = viewModel.uiState.value.messages
            .firstOrNull { it.messageUuid == messageUuid } ?: return
        when (action) {
            ImageGalleryActivity.ACTION_REPLY -> viewModel.setReplyTo(message)
            ImageGalleryActivity.ACTION_FORWARD -> {
                pendingForwardMessages = listOf(message)
                ForwardChatPickerBottomSheet.newInstance(chatUuid)
                    .show(childFragmentManager, "forward_picker")
            }
        }
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            if (canSendNow()) {
                sendClicked()
            } else {
                sendModeController.cycle()
            }
        }
        binding.btnSend.setOnTouchListener { _, event -> handleSendTouch(event) }
        binding.etMessageInput.setOnEditorActionListener { _, _, _ ->
            if (canSendNow()) sendClicked()
            false
        }
        binding.etMessageInput.doOnTextChanged { text, _, _, _ ->
            sendModeController.onInputChanged(!text.isNullOrBlank())
            showMentionSuggestions(
                text?.toString().orEmpty(),
                binding.etMessageInput.selectionStart
            )
        }

        binding.btnGif.setOnClickListener {
            EmojiGifBottomSheet.newInstance()
                .show(childFragmentManager, "emoji_gif")
        }
        sendModeController.updateButton()
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
            mentionController.dismiss()
            return
        }
        mentionController.show(suggestion)
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

    private fun handleSendTouch(event: MotionEvent): Boolean {
        val mode = sendModeController.mode
        if (mode == SendMode.TEXT && canSendNow()) return chargeGesture.onTouch(event)
        if (mode == SendMode.TEXT || canSendNow()) return false
        return recordingController.onTouch(event, mode) { sendModeController.cycle() }
    }

    private fun requestMicPermission() {
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatRecordingTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    override fun onGifSelected(gif: com.example.muzea.domain.model.Gif) =
        composerIntents.onGifSelected(gif)

    override fun onEmojiSelected(emoji: String) = composerIntents.onEmojiSelected(emoji)

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
        conversationStateView.render(
            isLoading = state.isLoading,
            hasMessages = state.messages.isNotEmpty(),
            errorText = state.error
        )
    }

    private fun observeNetwork() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkMonitor.status.collect { status ->
                    networkBannerView.render(status.quality)
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
            applyDeletedPeerState()
            viewModel.uiState.collect { state ->
                val messages = state.messages
                val wasAtBottom = isAtBottom()
                renderMessages(state, messages, wasAtBottom)
                updateScrollForNewMessages(messages, wasAtBottom)
                applyPendingHighlight(state, messages)
            }
        }
        collectSendErrors()
        collectEvents()
    }

    private fun collectSendErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sendError.collect { message ->
                Toast.makeText(
                    requireContext(), getString(R.string.error_prefix, message), Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Обновляет адаптер и статические части экрана; прокруткой занимается отдельный шаг. */
    private fun renderMessages(
        state: ChatConversationUiState,
        messages: List<Message>,
        wasAtBottom: Boolean
    ) {
        binding.progressHistory.visibility =
            if (state.isLoadingMore) View.VISIBLE else View.GONE
        updateMessageList(messages, state.statuses, wasAtBottom)
        if (state.pendingAttachments != lastPending) {
            lastPending = state.pendingAttachments
            renderPendingAttachments(state.pendingAttachments)
        }
        binding.btnSend.isEnabled = state.pendingAttachments.all { it.isUploaded }
        sendModeController.updateButton()
        updateEmptyState(messages)
        renderConversationState(state)
        renderComposerBanner(state)
        renderPinnedBar(state)
    }

    /** DiffUtil-обновление списка с сохранением видимого якоря. */
    private fun updateMessageList(
        messages: List<Message>,
        statuses: Map<String, MessageDeliveryStatus>,
        wasAtBottom: Boolean
    ) {
        val previousStatuses = lastStatuses
        val statusesChanged = statuses != previousStatuses
        val changedStatusUuids = if (statusesChanged) {
            (previousStatuses.keys + statuses.keys).filter {
                previousStatuses[it] != statuses[it]
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
        // Если лента была внизу, держим её внизу после раскладки.
        val keepAtBottom = initialScrollDone && !pendingScrollToTop &&
            anchorUuid == null && wasAtBottom
        if (listChanged) {
            lastMessages = messages
            adapter.updateList(messages) {
                if (statusesChanged) notifyChangedStatuses()
                if (keepAtBottom) {
                    // Реакция/правка меняет высоту бабла. Если лента была
                    // внизу, держим её внизу после раскладки — иначе чат
                    // «скачет» на высоту нового чипа.
                    binding.recyclerViewMessages.post {
                        layoutManager?.scrollToPosition(
                            (adapter.itemCount - 1).coerceAtLeast(0)
                        )
                        listScroller.markVisibleAsRead()
                    }
                }
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
        if (statusesChanged) lastStatuses = statuses
    }

    /** Выбирает позицию прокрутки для первого открытия и новых сообщений. */
    private fun updateScrollForNewMessages(messages: List<Message>, wasAtBottom: Boolean) {
        if (messages.isNotEmpty() && !initialScrollDone) {
            initialScrollDone = true
            val unreadIndex = UnreadAnchor.firstUnreadIndex(
                messages, unreadCount, viewModel.myUserUuid
            )
            val target = if (unreadIndex >= 0) unreadIndex else messages.size - 1
            pendingScrollPosition = target
            if (unreadIndex >= 0) {
                unreadAnchorPosition = unreadIndex
                pendingScrollToTop = true
            }
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
        // Позицию запомнили уже после обновления адаптера, поэтому
        // наблюдатель её не увидит — прокручиваем сами.
        if (pendingScrollPosition != null) {
            binding.recyclerViewMessages.post {
                if (_binding != null) performPendingScroll()
            }
        }
        updateScrollButton()
    }

    /** Переход к сообщению из push; при необходимости догружает историю. */
    private fun applyPendingHighlight(state: ChatConversationUiState, messages: List<Message>) {
        val targetUuid = pendingHighlightUuid
        if (targetUuid != null) {
            val targetPosition = messages.indexOfFirst { it.messageUuid == targetUuid }
            if (targetPosition >= 0) {
                pendingHighlightUuid = null
                highlighter.scrollToAndHighlight(targetPosition, targetUuid)
            } else if (!state.historyReached && !state.isLoadingMore && highlightAttempts < MAX_HIGHLIGHT_PAGES) {
                // Сообщение из push может быть выше первой страницы.
                highlightAttempts++
                viewModel.loadOlder()
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

    override fun onStart() {
        super.onStart()
        viewModel.resumePolling()
        // Внутри onStart lifecycle ещё CREATED, поэтому смотрим только на hidden.
        if (!isHidden) markChatVisible()
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
            markChatVisible()
        }
    }

    /**
     * Чат на экране: подавляем пуши по нему и снимаем уже показанное
     * уведомление — оно больше не актуально, сообщения видно в ленте.
     */
    private fun markChatVisible() {
        ActiveChatTracker.chatUuid = chatUuid
        ChatNotifications.cancel(requireContext().applicationContext, chatUuid)
    }

    private fun clearActiveChat() {
        if (ActiveChatTracker.chatUuid == chatUuid) ActiveChatTracker.chatUuid = null
    }

    override fun onPause() {
        if (::actionsPresenter.isInitialized) actionsPresenter.dismiss()
        if (::recordingController.isInitialized) recordingController.cancelIfActive()
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
        if (::actionsPresenter.isInitialized) actionsPresenter.dismiss()
        actionsBackCallback.isEnabled = false
        if (::audioController.isInitialized) audioController.release()
        if (::adapter.isInitialized) {
            adapter.unregisterAdapterDataObserver(scrollObserver)
            adapter.release()
        }
        super.onDestroyView()
        _binding = null
    }
}
