package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment

/**
 * Открытие вложения по типу: аудио играем в чате, картинки — галереей,
 * видео — отдельным экраном, остальное скачиваем. Фрагмент передаёт колбэки
 * для запуска экранов и плеера.
 */
internal class ChatAttachmentPresenter(
    private val messages: () -> List<Message>,
    private val launchViewer: (MessageAttachment) -> Unit,
    private val launchGallery: (List<ViewerImage>, Int) -> Unit,
    private val playAudio: (Message, MessageAttachment) -> Unit,
    private val download: (MessageAttachment) -> Unit
) {

    fun open(message: Message, attachment: MessageAttachment) {
        when (attachment.type?.uppercase()) {
            "AUDIO" -> playAudio(message, attachment)
            "IMAGE" -> openImageGallery(message, attachment)
            "VIDEO" -> launchViewer(attachment)
            else -> download(attachment)
        }
    }

    /** Галерея картинок чата: открываем на выбранной, листаем свайпами. */
    private fun openImageGallery(message: Message, attachment: MessageAttachment) {
        val images = GalleryItems.from(messages()) { item ->
            AttachmentUrlResolver.resolve(item)
        }
        val startIndex = GalleryItems.startIndex(
            images, message.messageUuid, attachment.attachmentUuid
        )
        if (images.isEmpty()) {
            launchViewer(attachment)
            return
        }
        launchGallery(images, startIndex)
    }
}
