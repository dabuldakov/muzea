package com.example.muzea.ui.chat

import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentUrlResolverTest {

    private fun attachment(
        uuid: String? = null,
        fileUrl: String = ""
    ) = MessageAttachment(
        fileUrl = fileUrl,
        thumbnailUrl = null,
        type = "IMAGE",
        width = null,
        height = null,
        attachmentUuid = uuid
    )

    @Test
    fun `server attachment uses chat api url`() {
        val url = AttachmentUrlResolver.resolve(attachment(uuid = "a1", fileUrl = "ignored"))
        assertEquals("${Constants.CHAT_BASE_URL.trimEnd('/')}/api/attachments/a1", url)
    }

    @Test
    fun `absolute url is kept as is`() {
        val url = AttachmentUrlResolver.resolve(
            attachment(fileUrl = "https://cdn.example.com/1.jpg")
        )
        assertEquals("https://cdn.example.com/1.jpg", url)
    }

    @Test
    fun `local path is kept as is`() {
        val url = AttachmentUrlResolver.resolve(attachment(fileUrl = "/data/local/1.jpg"))
        assertEquals("/data/local/1.jpg", url)
    }

    @Test
    fun `relative path is resolved against chat server`() {
        val url = AttachmentUrlResolver.resolve(attachment(fileUrl = "files/1.jpg"))
        assertTrue(url!!.endsWith("/files/1.jpg"))
    }
}
