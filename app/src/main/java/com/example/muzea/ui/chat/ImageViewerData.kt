package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import java.io.Serializable

/** Картинка для полноэкранного просмотра: ссылка плюс автор и дата. */
data class ViewerImage(
    val url: String,
    val messageUuid: String,
    val attachmentUuid: String?,
    val fileName: String?,
    val senderName: String?,
    val senderAvatar: String?,
    val createdAt: String?
) : Serializable

/** Собирает галерею картинок чата в порядке ленты. */
object GalleryItems {

    fun from(
        messages: List<Message>,
        urlFor: (MessageAttachment) -> String?
    ): List<ViewerImage> = messages.flatMap { message ->
        message.attachments
            .filter { it.type?.equals("IMAGE", ignoreCase = true) == true }
            .mapNotNull { attachment ->
                val url = urlFor(attachment) ?: return@mapNotNull null
                ViewerImage(
                    url = url,
                    messageUuid = message.messageUuid,
                    attachmentUuid = attachment.attachmentUuid,
                    fileName = attachment.fileName,
                    senderName = message.senderName,
                    senderAvatar = message.senderAvatar,
                    createdAt = message.createdAt
                )
            }
    }

    /** Индекс открытой картинки в галерее; 0, если совпадения нет. */
    fun startIndex(
        items: List<ViewerImage>,
        messageUuid: String,
        attachmentUuid: String?
    ): Int {
        val index = items.indexOfFirst {
            it.messageUuid == messageUuid && it.attachmentUuid == attachmentUuid
        }
        return if (index >= 0) index else 0
    }
}

/** Направление свайпа в просмотрщике картинок. */
enum class SwipeDirection { LEFT, RIGHT, UP, DOWN, NONE }

object SwipeGesture {

    /**
     * Горизонтальный свайп закрывает просмотрщик, вертикальный листает
     * картинки: вверх — следующая, вниз — предыдущая.
     */
    fun classify(dx: Float, dy: Float, threshold: Float): SwipeDirection {
        val horizontal = kotlin.math.abs(dx)
        val vertical = kotlin.math.abs(dy)
        return when {
            horizontal >= threshold && horizontal > vertical ->
                if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
            vertical >= threshold && vertical > horizontal ->
                if (dy < 0) SwipeDirection.UP else SwipeDirection.DOWN
            else -> SwipeDirection.NONE
        }
    }
}
