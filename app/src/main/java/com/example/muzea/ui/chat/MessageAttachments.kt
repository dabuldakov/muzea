package com.example.muzea.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.databinding.ItemAttachmentAudioBinding
import com.example.muzea.databinding.ItemAttachmentDocumentBinding
import com.example.muzea.databinding.ItemAttachmentImageBinding
import com.example.muzea.databinding.ItemAttachmentVideoBinding
import com.example.muzea.databinding.ItemAttachmentVideoNoteBinding
import com.example.muzea.databinding.ItemAttachmentVoiceBinding
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.utils.MediaPolicy
import com.example.muzea.utils.MediaUrl

private fun gifUrl(message: Message): String? =
    message.attachments
        .firstOrNull { it.type?.equals("GIF", ignoreCase = true) == true }
        ?.fileUrl

/** MP4-вариант гифки, если есть (для воспроизведения вместо WebP/GIF). */
internal fun gifMp4Url(message: Message): String? =
    message.attachments
        .firstOrNull { it.type?.equals("GIF", ignoreCase = true) == true }
        ?.mp4Url

private fun attachmentUrl(attachment: MessageAttachment): String? {
    val uuid = attachment.attachmentUuid
    return if (!uuid.isNullOrBlank()) {
        MediaUrl.chat("/api/attachments/$uuid")
    } else if (attachment.fileUrl.startsWith("/") || attachment.fileUrl.startsWith("http")) {
        attachment.fileUrl
    } else {
        MediaUrl.chat(attachment.fileUrl)
    }
}

private fun attachmentPreviewUrl(attachment: MessageAttachment): String? {
    val uuid = attachment.attachmentUuid
    return when {
        !uuid.isNullOrBlank() && !attachment.thumbnailUrl.isNullOrBlank() ->
            MediaUrl.chat("/api/attachments/$uuid/preview")
        attachment.thumbnailUrl?.startsWith("/") == true -> attachment.thumbnailUrl
        else -> null
    }
}

/**
 * Фото, видео, аудио и документы. Гифки обрабатываются отдельно в [bindGif].
 * Вложения рисуются карточками; пустой текстовый пузырь скрывается.
 */
internal fun bindAttachments(
    container: LinearLayout,
    textView: TextView,
    linkCard: View,
    message: Message,
    playingAudioUuid: String?,
    audioIsPlaying: Boolean,
    onClick: (Message, MessageAttachment) -> Unit,
    onLongClick: (Message) -> Unit
) {
    container.removeAllViews()
    // У удалённого сообщения вложений не показываем — только заглушку текста.
    if (message.isDeleted) {
        container.visibility = View.GONE
        return
    }
    val attachments = message.attachments.filterNot {
        it.type.equals("GIF", ignoreCase = true)
    }
    if (attachments.isEmpty()) {
        container.visibility = View.GONE
        return
    }

    container.visibility = View.VISIBLE
    if (message.text.isNullOrBlank()) {
        textView.visibility = View.GONE
        linkCard.visibility = View.GONE
    }

    // Голосовые рисуем пузырём с волной и длительностью. Свой клиент шлёт их
    // типом VOICE, другие (iOS) — AUDIO с единственной записью и длительностью.
    val voice = voiceAttachmentFor(message, attachments)
    if (voice != null) {
        addVoiceCard(container, message, voice, playingAudioUuid, audioIsPlaying, onClick, onLongClick)
        return
    }
    // Тип VOICE без аудио-вложения: пузырь не рисуем, но и карточку файла не
    // показываем — раньше такое сообщение оставалось пустым.
    if (message.messageType.equals("VOICE", ignoreCase = true)) return

    if (message.messageType == "VIDEO_NOTE") {
        val video = attachments.firstOrNull { it.type.equals("VIDEO", ignoreCase = true) }
        if (video != null) {
            addVideoNoteCard(container, message, video, onClick, onLongClick)
        }
        return
    }

    attachments.forEachIndexed { index, attachment ->
        val card: View = when (attachment.type?.uppercase()) {
            "IMAGE" -> addImageCard(container, message, attachment, onClick)
            "VIDEO" -> addVideoCard(container, message, attachment, onClick)
            "AUDIO" -> addAudioCard(container, message, attachment, playingAudioUuid, audioIsPlaying, onClick)
            else -> addDocumentCard(container, message, attachment, onClick)
        }
        card.setOnLongClickListener {
            onLongClick(message)
            true
        }
        if (index != attachments.lastIndex) {
            // небольшой отступ между карточками
            card.setPadding(0, 2, 0, 2)
        }
    }
}

private fun addVoiceCard(
    container: LinearLayout,
    message: Message,
    voice: MessageAttachment,
    playingAudioUuid: String?,
    audioIsPlaying: Boolean,
    onClick: (Message, MessageAttachment) -> Unit,
    onLongClick: (Message) -> Unit
) {
    val item = ItemAttachmentVoiceBinding.inflate(LayoutInflater.from(container.context), container, false)
    item.tvDuration.text = formatDuration(voice.duration)
    item.ivPlay.setImageResource(playPauseIcon(message, playingAudioUuid, audioIsPlaying))
    item.root.setOnClickListener { onClick(message, voice) }
    item.root.setOnLongClickListener {
        onLongClick(message)
        true
    }
    container.addView(item.root)
}

private fun addVideoNoteCard(
    container: LinearLayout,
    message: Message,
    video: MessageAttachment,
    onClick: (Message, MessageAttachment) -> Unit,
    onLongClick: (Message) -> Unit
) {
    val item = ItemAttachmentVideoNoteBinding.inflate(LayoutInflater.from(container.context), container, false)
    Glide.with(item.ivThumb)
        .load(attachmentPreviewUrl(video))
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(item.ivThumb)
    item.tvDuration.text = formatDuration(video.duration)
    item.root.setOnClickListener { onClick(message, video) }
    item.root.setOnLongClickListener {
        onLongClick(message)
        true
    }
    container.addView(item.root)
}

private fun addImageCard(
    container: LinearLayout,
    message: Message,
    attachment: MessageAttachment,
    onClick: (Message, MessageAttachment) -> Unit
): View {
    val item = ItemAttachmentImageBinding.inflate(LayoutInflater.from(container.context), container, false)
    // Резервируем высоту до загрузки картинки: иначе пузырь
    // «прыгает» и список дёргается при появлении изображения.
    val width = attachment.width?.takeIf { it > 0 }
    val height = attachment.height?.takeIf { it > 0 }
    if (width != null && height != null) {
        val cardWidth = dpToPx(container.context, 220)
        val cardHeight = (cardWidth.toLong() * height / width)
            .toInt()
            .coerceIn(dpToPx(container.context, 120), dpToPx(container.context, 280))
        item.root.layoutParams = item.root.layoutParams.apply { this.height = cardHeight }
        item.ivImage.layoutParams = item.ivImage.layoutParams.apply { this.height = cardHeight }
    }
    Glide.with(item.ivImage)
        .load(attachmentUrl(attachment))
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(item.ivImage)
    item.root.setOnClickListener { onClick(message, attachment) }
    container.addView(item.root)
    return item.root
}

private fun addVideoCard(
    container: LinearLayout,
    message: Message,
    attachment: MessageAttachment,
    onClick: (Message, MessageAttachment) -> Unit
): View {
    val item = ItemAttachmentVideoBinding.inflate(LayoutInflater.from(container.context), container, false)
    Glide.with(item.ivThumb)
        .load(attachmentPreviewUrl(attachment))
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(item.ivThumb)
    item.tvDuration.text = formatDuration(attachment.duration)
    item.tvDuration.visibility = if (attachment.duration != null) View.VISIBLE else View.GONE
    item.root.setOnClickListener { onClick(message, attachment) }
    container.addView(item.root)
    return item.root
}

private fun addAudioCard(
    container: LinearLayout,
    message: Message,
    attachment: MessageAttachment,
    playingAudioUuid: String?,
    audioIsPlaying: Boolean,
    onClick: (Message, MessageAttachment) -> Unit
): View {
    val item = ItemAttachmentAudioBinding.inflate(LayoutInflater.from(container.context), container, false)
    item.tvName.text = attachment.fileName ?: "Audio"
    item.tvSize.text = formatSize(attachment.fileSize)
    item.ivPlay.setImageResource(playPauseIcon(message, playingAudioUuid, audioIsPlaying))
    item.root.setOnClickListener { onClick(message, attachment) }
    container.addView(item.root)
    return item.root
}

private fun addDocumentCard(
    container: LinearLayout,
    message: Message,
    attachment: MessageAttachment,
    onClick: (Message, MessageAttachment) -> Unit
): View {
    val item = ItemAttachmentDocumentBinding.inflate(LayoutInflater.from(container.context), container, false)
    item.tvName.text = attachment.fileName ?: "File"
    item.tvSize.text = formatSize(attachment.fileSize)
    item.root.setOnClickListener { onClick(message, attachment) }
    container.addView(item.root)
    return item.root
}

private fun playPauseIcon(message: Message, playingAudioUuid: String?, audioIsPlaying: Boolean): Int =
    if (playingAudioUuid == message.messageUuid && audioIsPlaying) R.drawable.ic_pause
    else R.drawable.ic_play_circle

/**
 * Аудио-вложение, которое рисуем голосовым пузырём, либо null.
 *
 * Свой клиент помечает записи типом VOICE. Другие клиенты (iOS) отправляют
 * голосовые типом AUDIO с единственной записью `voice_*.m4a`: раньше такие
 * сообщения попадали в карточку аудиофайла, и на приёмной стороне было видно
 * только имя файла. Аудиофайлы из меню вложений (имя не `voice_*`) остаются
 * карточкой с именем и размером.
 */
internal fun voiceAttachmentFor(
    message: Message,
    attachments: List<MessageAttachment>
): MessageAttachment? {
    if (message.messageType.equals("VOICE", ignoreCase = true)) {
        return attachments.firstOrNull { it.type.equals("AUDIO", ignoreCase = true) }
    }
    if (message.messageType.equals("AUDIO", ignoreCase = true) && attachments.size == 1) {
        val only = attachments.single()
        val isAudio = only.type.equals("AUDIO", ignoreCase = true)
        val isRecording = only.fileName?.startsWith(VOICE_FILE_PREFIX, ignoreCase = true) == true
        if (isAudio && isRecording) return only
    }
    return null
}

/** Так клиенты называют файлы записанных голосовых. */
private const val VOICE_FILE_PREFIX = "voice_"

private fun dpToPx(context: Context, dp: Int): Int =
    (dp * context.resources.displayMetrics.density).toInt()

private fun formatDuration(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(minutes, rest)
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val kb = bytes / 1024.0
    return if (kb < 1024) {
        "%.0f KB".format(kb)
    } else {
        "%.1f MB".format(kb / 1024.0)
    }
}

/**
 * Если сообщение — гифка, показываем её (Glide, анимированный GIF, кэш на
 * устройстве) вместо текста. Иначе — обычный текстовый бабл.
 */
internal fun bindGif(textView: TextView, imageView: ImageView, message: Message) {
    // Гифка из «Библиотеки» хранится у нас и приходит относительным URL —
    // резолвим в абсолютный адрес чата (Klipy-URL уже абсолютные).
    val url = gifUrl(message)?.let { MediaUrl.chat(it) }
    if (url.isNullOrBlank()) {
        imageView.visibility = View.GONE
        textView.visibility = View.VISIBLE
        return
    }
    // На слабой сети анимированную гифку не тянем: покажем подпись, пока
    // канал не станет лучше или пользователь не откроет чат заново.
    if (!MediaPolicy.autoLoad && !MediaPolicy.cacheOnly) {
        imageView.visibility = View.GONE
        textView.visibility = View.VISIBLE
        textView.text = "GIF"
        return
    }
    textView.visibility = View.GONE
    imageView.visibility = View.VISIBLE
    Glide.with(imageView)
        .load(url)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(imageView)
}
