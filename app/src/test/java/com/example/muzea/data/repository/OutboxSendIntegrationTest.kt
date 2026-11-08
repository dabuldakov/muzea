package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.data.IntegrationTestClient
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.NetworkQuality
import com.example.muzea.utils.NetworkStatus
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Интеграционный сценарий очереди исходящих: фото, поставленное в очередь
 * до перезапуска, восстанавливается из файла и уходит на сервер с тем же
 * attachmentUuid и clientMessageId. Это регрессия на R8-краш после отправки
 * фото (generic-тип attachments терялся при чтении Gson).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxSendIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    @get:Rule
    val temp = TemporaryFolder()

    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val appContext = mockk<Context>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val networkStatus = MutableStateFlow(
        NetworkStatus(NetworkQuality.GOOD, metered = false)
    )

    private fun store(dir: File): OutboxStore {
        val context = mockk<Context>()
        every { context.filesDir } returns dir
        every { context.applicationContext } returns context
        return OutboxStore(context)
    }

    private fun repository() = MessageRepositoryImpl(
        IntegrationTestClient.chatApi(server),
        auth,
        GifCache(File.createTempFile("gif-cache", ".json").apply { delete() }),
        appContext
    )

    private val photoItem = OutboxItem(
        clientMessageId = "client-photo-1",
        localMessageUuid = "local-photo-1",
        chatUuid = "chat-1",
        text = "",
        messageType = "IMAGE",
        replyToMessageUuid = null,
        attachments = listOf(
            MessageAttachment(
                fileUrl = "attachments/photo.png",
                thumbnailUrl = null,
                type = "IMAGE",
                width = 10,
                height = 10,
                attachmentUuid = "a-1"
            )
        )
    )

    private val serverMessage = """
        {"messageUuid":"server-photo-1","messageId":101,"chatUuid":"chat-1",
         "senderId":1,"senderUuid":"me","senderName":"Me","senderAvatar":null,
         "text":"","messageType":"IMAGE","replyToMessageUuid":null,
         "isEdited":false,"isDeleted":false,"isPinned":false,
         "createdAt":"2026-01-01T00:00:00","updatedAt":null,
         "attachments":[{"attachmentUuid":"a-1","fileUrl":"attachments/photo.png",
                         "thumbnailUrl":null,"type":"IMAGE","width":10,"height":10}]}
    """.trimIndent()

    @Test
    fun `photo queued before restart is restored and sent with its attachment`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        every { networkMonitor.status } returns networkStatus

        val dir = temp.newFolder()
        // Первый запуск: фото лежит в очереди на диске.
        store(dir).add(photoItem)

        // Второй запуск: новый экземпляр читает тот же файл.
        val restored = store(dir)
        val restoredAttachment = restored.all().single().attachments.single()
        assertEquals("a-1", restoredAttachment.attachmentUuid)
        assertEquals("IMAGE", restoredAttachment.type)

        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(serverMessage)
        )

        val sender = OutboxSender(restored, repository(), networkMonitor, backgroundScope)
        sender.drainNow()

        assertTrue("queue must be empty after a successful send", restored.all().isEmpty())

        val request = server.takeRequest()
        assertEquals("/api/messages/chat-1", request.path)
        assertEquals("POST", request.method)
        val body = JsonParser().parse(request.body.readUtf8()).asJsonObject
        assertEquals("client-photo-1", body.get("clientMessageId").asString)
        assertEquals("IMAGE", body.get("messageType").asString)
        assertEquals("a-1", body.getAsJsonArray("attachments")[0].asString)
    }

    @Test
    fun `failed restore send keeps the photo for a later retry`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        every { networkMonitor.status } returns networkStatus

        val dir = temp.newFolder()
        store(dir).add(photoItem)
        val restored = store(dir)

        server.enqueue(MockResponse().setResponseCode(500))

        val sender = OutboxSender(restored, repository(), networkMonitor, backgroundScope)
        sender.drainNow()

        val kept = restored.all().single()
        assertEquals("a-1", kept.attachments.single().attachmentUuid)
        assertEquals(1, kept.attempts)
    }
}
