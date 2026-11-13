package com.example.muzea.ui.chat

import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.utils.MediaUrl

/** Единое правило, как из вложения получить URL: сервер, абсолютный или относительный. */
object AttachmentUrlResolver {

    fun resolve(attachment: MessageAttachment): String? {
        val uuid = attachment.attachmentUuid
        return when {
            !uuid.isNullOrBlank() -> MediaUrl.chat("/api/attachments/$uuid")
            attachment.fileUrl.startsWith("/") || attachment.fileUrl.startsWith("http") ->
                attachment.fileUrl
            else -> MediaUrl.chat(attachment.fileUrl)
        }
    }
}
