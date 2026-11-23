package com.example.muzea.ui.chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.repository.LinkPreviewStore
import com.example.muzea.databinding.ItemMessageIncomingBinding
import com.example.muzea.databinding.ItemMessageOutgoingBinding
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.ChatTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MessageAdapter(
    private val myUserUuid: String?,
    private val linkPreviews: LinkPreviewStore,
    private val onMessageClick: (Message) -> Unit,
    private val statusFor: (Message) -> MessageDeliveryStatus?,
    private val onStatusClick: (Message) -> Unit,
    private val onAttachmentClick: (Message, MessageAttachment) -> Unit,
    private val isGroupChat: Boolean = true,
    private val onReactionClick: (Message, String) -> Unit = { _, _ -> },
    /** Тап по цитате ответа: переход к оригиналу по uuid. */
    private val onReplyClick: (String) -> Unit = {}
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var selectionMode = false
    private var selectedUuids: Set<String> = emptySet()

    // Индекс по uuid для цитат ответов: без него каждый биндинг сканировал
    // весь список целиком. Пересчитываем один раз на новую версию списка.
    private var indexedList: List<Message>? = null
    private var indexedByUuid: Map<String, Message> = emptyMap()

    /** Перерисовать видимые элементы: повторная попытка загрузки медиа. */
    fun refreshMedia() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    /** Подсветка выбранных сообщений в режиме мультивыбора. */
    fun setSelection(uuids: Set<String>) {
        selectionMode = uuids.isNotEmpty()
        selectedUuids = uuids.toSet()
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    /** Вызывать при уничтожении вью: отменяет фоновые загрузки превью. */
    fun release() {
        scope.cancel()
    }

    /** UUID сообщения, которое сейчас проигрывается в чате (для иконки play/pause). */
    var playingAudioUuid: String? = null
        private set
    private var audioIsPlaying = false

    fun setAudioState(messageUuid: String?, playing: Boolean) {
        if (playingAudioUuid == messageUuid && audioIsPlaying == playing) return
        val previous = playingAudioUuid
        playingAudioUuid = messageUuid
        audioIsPlaying = playing
        // Обновляем только две затронутые строки: полный notifyItemRangeChanged
        // пересоздавал все SurfaceView/Glide и давал мерцание списка.
        notifyAudioRow(previous)
        if (messageUuid != previous) notifyAudioRow(messageUuid)
    }

    private fun notifyAudioRow(messageUuid: String?) {
        if (messageUuid == null) return
        val position = currentList.indexOfFirst { it.messageUuid == messageUuid }
        if (position >= 0) notifyItemChanged(position)
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (isMine(message)) TYPE_OUTGOING else TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_OUTGOING) {
            OutgoingViewHolder(
                ItemMessageOutgoingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            IncomingViewHolder(
                ItemMessageIncomingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        // Цитату ответа сервер отдаёт только uuid'ом — оригинал ищем в уже
        // загруженном списке. Если его нет (история не догружена), покажем заглушку.
        val replyTo = message.replyToMessageUuid?.let { uuid -> messageByUuid(uuid) }
        when (holder) {
            is OutgoingViewHolder -> holder.bind(
                message, replyTo, myUserUuid, linkPreviews, scope, onMessageClick,
                statusFor(message), onStatusClick, onAttachmentClick, playingAudioUuid, audioIsPlaying,
                onReactionClick = onReactionClick,
                onReplyClick = onReplyClick
            )
            is IncomingViewHolder -> {
                val previous = if (position > 0) getItem(position - 1) else null
                val showHeader = previous == null ||
                    previous.senderUuid != message.senderUuid ||
                    !sameDay(previous.createdAt, message.createdAt)
                holder.bind(
                    message, replyTo, myUserUuid, linkPreviews, scope, onMessageClick,
                    onAttachmentClick, playingAudioUuid, audioIsPlaying,
                    showHeader = showHeader && isGroupChat,
                    showAvatar = isGroupChat,
                    onReactionClick = onReactionClick,
                    onReplyClick = onReplyClick
                )
            }
        }
        val selected = selectionMode && message.messageUuid in selectedUuids
        holder.itemView.setBackgroundColor(
            if (selected) ContextCompat.getColor(holder.itemView.context, R.color.selected_message)
            else Color.TRANSPARENT
        )
    }

    private fun messageByUuid(uuid: String): Message? {
        val list = currentList
        if (list !== indexedList) {
            indexedList = list
            indexedByUuid = list.associateBy { it.messageUuid }
        }
        return indexedByUuid[uuid]
    }

    private fun sameDay(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        return first.take(10) == second.take(10)
    }

    private fun isMine(message: Message): Boolean {
        val sender = message.senderUuid ?: return false
        return myUserUuid != null && sender == myUserUuid
    }

    class IncomingViewHolder(
        private val binding: ItemMessageIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            message: Message,
            replyTo: Message?,
            myUserUuid: String?,
            linkPreviews: LinkPreviewStore,
            scope: CoroutineScope,
            onClick: (Message) -> Unit,
            onAttachmentClick: (Message, MessageAttachment) -> Unit,
            playingAudioUuid: String?,
            audioIsPlaying: Boolean,
            showHeader: Boolean,
            showAvatar: Boolean = true,
            onReactionClick: (Message, String) -> Unit = { _, _ -> },
            onReplyClick: (String) -> Unit = {}
        ) {
            // В личных чатах аватар не нужен вовсе — освобождаем место баблу.
            binding.ivAvatar.visibility = when {
                !showAvatar -> View.GONE
                showHeader -> View.VISIBLE
                else -> View.INVISIBLE
            }
            AvatarLoader.load(binding.ivAvatar, message.senderAvatar)
            bindReplyQuote(
                binding.replyQuote, binding.tvReplyName, binding.tvReplyText,
                message, replyTo, myUserUuid, onReplyClick
            )
            bindReactions(binding.reactionsContainer, message, onReactionClick)
            bindMedia(message)
            bindAttachments(
                binding.attachmentsContainer, binding.tvMessage, binding.linkPreview,
                message, playingAudioUuid, audioIsPlaying, onAttachmentClick, onClick
            )
            bindTextOrPreview(
                binding.tvMessage, binding.linkPreview,
                binding.ivLinkImage, binding.tvLinkTitle, binding.tvLinkDomain,
                message, linkPreviews, scope
            )
            binding.tvMessage.setOnClickListener { onClick(message) }
            binding.ivGif.setOnClickListener { onClick(message) }
            // linkPreview сам открывает ссылку — не перекрываем его обработчик.
            // Долгое нажатие открывает меню действий: иначе у сообщения-ссылки
            // меню недоступно — тап уходит в ссылку.
            bindLongPress(binding.tvMessage, message, onClick)
            bindLongPress(binding.linkPreview, message, onClick)
            bindLongPress(binding.ivGif, message, onClick)

            val senderLabel = when {
                !message.senderName.isNullOrEmpty() -> message.senderName
                message.senderDeleted -> itemView.context.getString(R.string.deleted_user)
                else -> null
            }
            if (showHeader && !senderLabel.isNullOrEmpty()) {
                binding.tvSenderName.text = senderLabel
                binding.tvSenderName.visibility = View.VISIBLE
            } else {
                binding.tvSenderName.visibility = View.GONE
            }

            binding.tvTime.text = formatTime(message.createdAt)
            binding.tvEdited.visibility =
                if (message.isEdited && !message.isDeleted) View.VISIBLE else View.GONE
            bindPower(binding.tvPower, message)
        }

        init {
            trackTouch(binding.root)
        }

        private val gifPlayer = MessageGifPlayer(
            binding.tvMessage, binding.ivGif, binding.playerGif
        )

        private fun bindMedia(message: Message) = gifPlayer.bind(message)

        fun releasePlayer() = gifPlayer.release()
    }

    class OutgoingViewHolder(
        private val binding: ItemMessageOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            message: Message,
            replyTo: Message?,
            myUserUuid: String?,
            linkPreviews: LinkPreviewStore,
            scope: CoroutineScope,
            onClick: (Message) -> Unit,
            status: MessageDeliveryStatus?,
            onStatusClick: (Message) -> Unit,
            onAttachmentClick: (Message, MessageAttachment) -> Unit,
            playingAudioUuid: String?,
            audioIsPlaying: Boolean,
            onReactionClick: (Message, String) -> Unit = { _, _ -> },
            onReplyClick: (String) -> Unit = {}
        ) {
            bindReplyQuote(
                binding.replyQuote, binding.tvReplyName, binding.tvReplyText,
                message, replyTo, myUserUuid, onReplyClick
            )
            bindReactions(binding.reactionsContainer, message, onReactionClick)
            bindMedia(message)
            bindAttachments(
                binding.attachmentsContainer, binding.tvMessage, binding.linkPreview,
                message, playingAudioUuid, audioIsPlaying, onAttachmentClick, onClick
            )
            bindTextOrPreview(
                binding.tvMessage, binding.linkPreview,
                binding.ivLinkImage, binding.tvLinkTitle, binding.tvLinkDomain,
                message, linkPreviews, scope
            )
            binding.tvMessage.setOnClickListener { onClick(message) }
            binding.ivGif.setOnClickListener { onClick(message) }
            // linkPreview сам открывает ссылку — не перекрываем его обработчик.
            // Долгое нажатие открывает меню действий: иначе у сообщения-ссылки
            // меню недоступно — тап уходит в ссылку.
            bindLongPress(binding.tvMessage, message, onClick)
            bindLongPress(binding.linkPreview, message, onClick)
            bindLongPress(binding.ivGif, message, onClick)
            binding.tvTime.text = formatTime(message.createdAt)
            binding.tvEdited.visibility =
                if (message.isEdited && !message.isDeleted) View.VISIBLE else View.GONE
            bindPower(binding.tvPower, message)
            bindStatus(message, status, onStatusClick)
        }

        init {
            trackTouch(binding.root)
        }

        private fun bindStatus(
            message: Message,
            status: MessageDeliveryStatus?,
            onStatusClick: (Message) -> Unit
        ) {
            // У удалённого сообщения статус не показываем: действий по нему нет.
            if (message.isDeleted) {
                binding.ivStatus.visibility = View.GONE
                binding.tvTime.setOnClickListener(null)
                return
            }
            if (status == null) {
                binding.ivStatus.visibility = View.GONE
                binding.tvTime.setOnClickListener(null)
                return
            }

            val context = binding.root.context
            val (icon, color, label) = when (status) {
                MessageDeliveryStatus.SENDING ->
                    Triple(R.drawable.ic_message_sending, R.color.gray, R.string.message_status_sending)
                MessageDeliveryStatus.SENT ->
                    Triple(R.drawable.ic_message_sent, R.color.gray, R.string.message_status_sent)
                MessageDeliveryStatus.DELIVERED ->
                    Triple(R.drawable.ic_message_delivered, R.color.gray, R.string.message_status_delivered)
                MessageDeliveryStatus.READ ->
                    Triple(R.drawable.ic_message_read, R.color.teal_700, R.string.message_status_read)
                MessageDeliveryStatus.FAILED ->
                    Triple(R.drawable.ic_message_failed, R.color.error, R.string.message_status_failed)
            }

            binding.ivStatus.visibility = View.VISIBLE
            binding.ivStatus.setImageResource(icon)
            binding.ivStatus.setColorFilter(ContextCompat.getColor(context, color))
            binding.ivStatus.contentDescription = context.getString(label)

            // Подробности доступны и по галочке, и по времени — так проще попасть.
            // У локального/неотправленного сообщения получателей ещё нет.
            if (status == MessageDeliveryStatus.SENDING || status == MessageDeliveryStatus.FAILED) {
                binding.ivStatus.setOnClickListener(null)
                binding.tvTime.setOnClickListener(null)
                return
            }
            val openDetails = View.OnClickListener { onStatusClick(message) }
            binding.ivStatus.setOnClickListener(openDetails)
            binding.tvTime.setOnClickListener(openDetails)
        }

        init {
            trackTouch(binding.root)
        }

        private val gifPlayer = MessageGifPlayer(
            binding.tvMessage, binding.ivGif, binding.playerGif
        )

        private fun bindMedia(message: Message) = gifPlayer.bind(message)

        fun releasePlayer() = gifPlayer.release()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is IncomingViewHolder -> holder.releasePlayer()
            is OutgoingViewHolder -> holder.releasePlayer()
        }
    }

    fun updateList(newList: List<Message>, onCommitted: (() -> Unit)? = null) {
        submitList(newList) { onCommitted?.invoke() }
    }

    companion object {
        private const val TYPE_INCOMING = 0
        private const val TYPE_OUTGOING = 1

        /** Последняя точка касания: по ней ставим плавающую модалку. */
        @Volatile
        var lastTouchX: Int = 0

        @Volatile
        var lastTouchY: Int = 0

        fun trackTouch(view: android.view.View) {
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchX = event.rawX.toInt()
                    lastTouchY = event.rawY.toInt()
                }
                false
            }
        }

        private fun formatTime(iso: String?): String {
            return ChatTimeFormatter.format(iso)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.messageUuid == newItem.messageUuid
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
