package com.example.muzea.contract

import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.mapper.toDomainAttachment
import com.example.muzea.data.mapper.toGif
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.data.model.ChatReceiptResponse
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.KlipyGifsResponse
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.data.model.PageResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контрактные тесты на общих фикстурах `docs/fixtures/chat`.
 *
 * Те же JSON использует iOS (`ContractFixtureTests`). Если бэкенд меняет поле,
 * падают обе платформы, а не только та, которую вспомнили обновить.
 */
class SharedFixtureContractTest {

    private val gson = Gson()

    private fun text(path: String): String =
        javaClass.getResourceAsStream("/fixtures/chat/$path")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Fixture not found: $path")

    @Test
    fun `chats fixture maps to domain`() {
        val type = object : TypeToken<List<ChatResponse>>() {}.type
        val chats: List<ChatResponse> = gson.fromJson(text("chats.json"), type)
        val domain = chats.map { it.toDomain() }

        assertEquals(2, domain.size)
        assertEquals("11111111-1111-1111-1111-111111111111", domain[0].chatUuid)
        assertEquals("PRIVATE", domain[0].chatType)
        assertEquals("hello", domain[0].lastMessage?.text)
        assertEquals(3L, domain[0].unreadCount ?: -1L)
        assertEquals("GROUP", domain[1].chatType)
    }

    @Test
    fun `messages fixture maps media reactions reply and power`() {
        val type = object : TypeToken<PageResponse<MessageResponse>>() {}.type
        val page: PageResponse<MessageResponse> = gson.fromJson(text("messages_page.json"), type)
        val messages = page.content.map { it.toDomain() }

        assertEquals(2, messages.size)
        val text = messages[0]
        assertEquals(101L, text.messageId ?: -1L)
        assertEquals("TEXT", text.messageType)
        assertTrue(text.isPinned)
        assertEquals("👍", text.reactions.first().emoji)
        val reactions = JsonParser.parseString(text("messages_page.json"))
            .asJsonObject["content"].asJsonArray[0].asJsonObject["reactions"].asJsonArray
        assertEquals(
            "u-bob",
            reactions[0].asJsonObject["users"].asJsonArray[0]
                .asJsonObject["userUuid"].asString
        )

        val media = messages[1]
        assertEquals("IMAGE", media.messageType)
        assertEquals(text.messageUuid, media.replyToMessageUuid)
        assertEquals(42, media.power)
        assertEquals(
            "cccccccc-cccc-cccc-cccc-cccccccccccc",
            media.attachments.first().attachmentUuid
        )
    }

    @Test
    fun `receipts and exact statuses fixtures decode`() {
        val receiptsType = object : TypeToken<List<ChatReceiptResponse>>() {}.type
        val receipts: List<ChatReceiptResponse> = gson.fromJson(text("receipts.json"), receiptsType)
        assertEquals(2, receipts.size)
        assertEquals(102L, receipts[1].toDomain().lastReadMessageId ?: -1L)

        val statuses = JsonParser.parseString(text("message_statuses.json")).asJsonArray
        assertEquals("u-alice", statuses[0].asJsonObject["userUuid"].asString)
        assertEquals("2026-01-01T10:08:30", statuses[0].asJsonObject["readAt"].asString)
        assertEquals("u-bob", statuses[1].asJsonObject["userUuid"].asString)
    }

    @Test
    fun `participants fixture maps to domain`() {
        val type = object : TypeToken<List<ChatParticipantResponse>>() {}.type
        val participants: List<ChatParticipantResponse> =
            gson.fromJson(text("participants.json"), type)
        val domain = participants.map { it.toDomain() }

        assertEquals(2, domain.size)
        assertEquals("OWNER", domain[0].role)
        assertEquals("bob", domain[1].username)
        assertEquals("Bob", domain[1].displayName())
    }

    @Test
    fun `gif fixture maps to domain`() {
        val response: KlipyGifsResponse = gson.fromJson(text("gif_search.json"), KlipyGifsResponse::class.java)
        val gifs = response.items().mapNotNull { it.toGif() }

        assertEquals(1, gifs.size)
        assertEquals("party-cat", gifs[0].providerGifId)
        assertEquals("https://cdn/hd.webp", gifs[0].url)
        assertEquals("https://cdn/hd.mp4", gifs[0].mp4Url)
    }

    @Test
    fun `attachment fixture keeps server fields`() {
        val attachment: com.example.muzea.data.model.AttachmentResponse =
            gson.fromJson(text("attachment.json"), com.example.muzea.data.model.AttachmentResponse::class.java)
        val domain = attachment.toDomainAttachment()

        assertNotNull(domain)
        assertEquals("cat.jpg", domain?.fileName)
        assertEquals(12345L, domain?.fileSize)
        assertEquals("image/jpeg", domain?.mimeType)
    }
}
