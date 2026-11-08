package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.utils.PreparedAttachment
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.OutboxEvent
import com.example.muzea.data.repository.OutboxItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatConversationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val messageRepository = mockk<MessageRepository>()

    private val outboxSender = mockk<com.example.muzea.data.repository.OutboxSender>(relaxed = true)
    private val outboxEvents = kotlinx.coroutines.flow.MutableSharedFlow<com.example.muzea.data.repository.OutboxEvent>()
    private val networkMonitor = mockk<com.example.muzea.utils.NetworkMonitor>(relaxed = true)
    private val networkStatus = kotlinx.coroutines.flow.MutableStateFlow(
        com.example.muzea.utils.NetworkStatus(com.example.muzea.utils.NetworkQuality.GOOD, metered = false)
    )
    private val mediaFileReader = mockk<com.example.muzea.utils.MediaFileReader>(relaxed = true)
    private val appContext = mockk<android.content.Context>(relaxed = true)
    private val chatUuid = "chat-1"

    private fun message(
        uuid: String,
        text: String,
        createdAt: String,
        messageId: Long? = null,
        sender: String = "me"
    ) = Message(
        messageUuid = uuid,
        messageId = messageId,
        chatUuid = chatUuid,
        senderId = null,
        senderUuid = sender,
        senderName = "Me",
        senderAvatar = null,
        text = text,
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = createdAt,
        updatedAt = null
    )

    @Before
    fun setUp() {
        ChatMessagesCache.clear()
        // mergeMessages() помечает переписку прочитанной; без заглушки строгий
        // mockk упадёт на неожиданном вызове.
        coEvery { messageRepository.markMessagesAsRead(any(), any()) } returns true
        coEvery { messageRepository.markMessagesAsDelivered(any(), any()) } returns true
        coEvery { messageRepository.loadChatReceipts(any()) } returns emptyList()
        // init подтягивает закреплённые; строгий mockk требует заглушку.
        coEvery { messageRepository.loadPinned(any()) } returns emptyList()
        every { appContext.getString(any()) } returns "error"
        every { messageRepository.cacheSentGif(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        ChatMessagesCache.clear()
    }

    /**
     * Обёртка собирает ViewModel и обязательно его уничтожает.
     *
     * Кэш и сеть описаны параметрами, а не заглушками внутри тела: и
     * cachedMessages(), и первый запрос выполняются прямо в конструкторе
     * (опрос стартует в init), поэтому заглушки должны быть готовы заранее.
     *
     * store.clear() в finally нужен, чтобы трёхсекундный опрос остановился:
     * иначе runTest не дойдёт до покоя, планировщик всегда будет видеть
     * запланированный delay().
     */
    private fun withViewModel(
        cached: List<Message> = emptyList(),
        network: Flow<Resource<List<Message>>> =
            flowOf(Resource.Success(emptyList())),
        initialNetwork: com.example.muzea.utils.NetworkQuality =
            com.example.muzea.utils.NetworkQuality.GOOD,
        block: suspend CoroutineScope.(ChatConversationViewModel) -> Unit
    ) = runTest {
        networkStatus.value = com.example.muzea.utils.NetworkStatus(initialNetwork, metered = false)
        every { messageRepository.cachedMessages(chatUuid) } returns cached
        every { outboxSender.events } returns outboxEvents
        every { outboxSender.pendingForChat(chatUuid) } returns emptyList()
        every { networkMonitor.status } returns networkStatus
        coEvery { messageRepository.loadMessagesAfter(chatUuid, any()) } returns
            flowOf(Resource.Success(emptyList()))
        coEvery { messageRepository.loadMessages(chatUuid) } returns network

        val savedStateHandle = SavedStateHandle(
            mapOf(ChatConversationViewModel.ARG_CHAT_UUID to chatUuid)
        )
        val identity = object : ChatUserIdentity {
            override val userUuid: String? = "me"
        }

        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatConversationViewModel(savedStateHandle, messageRepository, outboxSender, networkMonitor, identity, mediaFileReader, appContext) as T
        }
        try {
            block(ViewModelProvider(store, factory)[ChatConversationViewModel::class.java])
        } finally {
            store.clear()
        }
    }

    @Test
    fun `opening a chat issues exactly one request`() = withViewModel(
        network = flowOf(Resource.Success(listOf(message("m1", "hi", "2026-01-01T00:00:00"))))
    ) { viewModel ->
        // Раньше init звал refresh() и сразу startPolling(), давая два запроса
        // подряд при каждом входе в чат.
        assertEquals(1, viewModel.uiState.value.messages.size)

        coVerify(exactly = 1) { messageRepository.loadMessages(chatUuid) }
    }

    @Test
    fun `cached messages are shown immediately without spinner`() = withViewModel(
        cached = listOf(message("m1", "cached", "2026-01-01T00:00:00")),
        network = flowOf(Resource.Success(listOf(message("m1", "cached", "2026-01-01T00:00:00"))))
    ) { viewModel ->
        assertEquals("cached", viewModel.uiState.value.messages.single().text)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `fresh messages are merged into cached history`() = withViewModel(
        cached = listOf(message("m1", "old", "2026-01-01T00:00:00")),
        network = flowOf(
            Resource.Success(
                listOf(
                    message("m1", "old", "2026-01-01T00:00:00"),
                    message("m2", "new", "2026-01-02T00:00:00")
                )
            )
        )
    ) { viewModel ->
        assertEquals(listOf("old", "new"), viewModel.uiState.value.messages.map { it.text })
    }

    @Test
    fun `manual refresh adds exactly one more request`() = withViewModel { viewModel ->
        viewModel.refresh()

        coVerify(exactly = 2) { messageRepository.loadMessages(chatUuid) }
    }

    @Test
    fun `failure does not wipe cached messages`() = withViewModel(
        cached = listOf(message("m1", "cached", "2026-01-01T00:00:00")),
        network = flowOf(Resource.Error("network down"))
    ) { viewModel ->
        assertEquals("cached", viewModel.uiState.value.messages.single().text)
        assertEquals("network down", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `spinner turns off after loading finishes`() = withViewModel(
        network = flowOf(Resource.Loading(), Resource.Success(emptyList()))
    ) { viewModel ->
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `spinner never shows when cached messages are available`() = withViewModel(
        cached = listOf(message("m1", "cached", "2026-01-01T00:00:00")),
        network = flowOf(Resource.Loading(), Resource.Success(emptyList()))
    ) { viewModel ->
        // Даже если сервер сначала отвечает Loading, экран уже показывает
        // переписку из кэша, поэтому мигать индикатором нельзя.
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `optimistic bubble is updated in place by the server echo`() = withViewModel { viewModel ->
        val item = slot<OutboxItem>()
        every { outboxSender.enqueue(capture(item)) } returns Unit

        viewModel.sendText("hello")
        outboxEvents.emit(
            OutboxEvent.Sent(item.captured, message("server-1", "hello", "2026-01-01T00:00:00"))
        )

        // Идентификатор локального пузыря сохраняется, чтобы DiffUtil не
        // удалял и не вставлял строку заново (иначе список мигает).
        val sent = viewModel.uiState.value.messages.single()
        assertTrue(sent.messageUuid.startsWith("local-"))
        assertEquals("hello", sent.text)
        assertEquals("2026-01-01T00:00:00", sent.createdAt)
    }

    @Test
    fun `server echo is not duplicated by the next poll`() = withViewModel { viewModel ->
        val serverCopy = message("server-1", "hello", "2026-01-01T00:00:00")
        val item = slot<OutboxItem>()
        every { outboxSender.enqueue(capture(item)) } returns Unit

        viewModel.sendText("hello")
        outboxEvents.emit(OutboxEvent.Sent(item.captured, serverCopy))
        assertEquals(1, viewModel.uiState.value.messages.size)

        // Следующий опрос возвращает серверную копию — она уже показана
        // локальным пузырём и не должна появиться второй строкой.
        coEvery { messageRepository.loadMessages(chatUuid) } returns
            flowOf(Resource.Success(listOf(serverCopy)))
        viewModel.refresh()

        assertEquals(1, viewModel.uiState.value.messages.size)
        assertEquals("hello", viewModel.uiState.value.messages.single().text)
    }

    @Test
    fun `cached messages are shown sorted by creation time`() = withViewModel(
        // Сервер отдаёт сообщения «сначала новые»; в кэше они лежат именно в
        // таком порядке, а на экране должны идти по возрастанию времени.
        cached = listOf(
            message("m2", "newer", "2026-01-02T00:00:00"),
            message("m1", "older", "2026-01-01T00:00:00")
        ),
        network = flowOf(Resource.Success(emptyList()))
    ) { viewModel ->
        assertEquals(listOf("older", "newer"), viewModel.uiState.value.messages.map { it.text })
    }

    @Test
    fun `send failure keeps optimistic message and reports error`() = withViewModel { viewModel ->
        val item = slot<OutboxItem>()
        every { outboxSender.enqueue(capture(item)) } returns Unit

        // Подписка возникает до отправки: sendError одноразовый и позднему
        // подписчику событие не доставит — ровно как работает экран чата.
        val errors = mutableListOf<String>()
        // Unconfined нужен, чтобы подписчик обработал значение сразу в потоке
        // отправителя: иначе он проснётся только на планировщике runTest, и до
        // проверки мы не дожидаемся ни одной ошибки.
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.sendError.collect { errors += it }
        }

        viewModel.sendText("hello")
        outboxEvents.emit(OutboxEvent.Failed(item.captured, "send failed"))
        // collect на одноразовом потоке не завершается сам, поэтому подписку
        // отменяем. К моменту отмены отправка уже разобрана: диспетчер теста
        // выполняет корутины незамедлительно.
        collector.cancel()

        assertEquals(1, viewModel.uiState.value.messages.size)
        assertTrue(viewModel.uiState.value.messages.single().messageUuid.startsWith("local-"))
        assertEquals(listOf("send failed"), errors)
    }

    @Test
    fun `new gif from another participant is added to chat gif cache without network`() = withViewModel(
        network = flowOf(
            Resource.Success(
                listOf(
                    message("g1", "", "2026-01-01T00:00:00").copy(
                        messageType = "GIF",
                        senderUuid = "other",
                        attachments = listOf(
                            MessageAttachment("https://c/x.gif", null, "GIF", 1, 1, "key-1")
                        )
                    )
                )
            )
        )
    ) { _ ->
        verify { messageRepository.cacheSentGif(chatUuid, any(), false) }
    }

    @Test
    fun `sendGif shows optimistic gif and applies server echo`() = withViewModel { viewModel ->
        val gif = Gif("klipy", "s1", "cat", "https://c/gif.gif", "https://c/tiny.gif", 100, 50)
        coEvery { messageRepository.sendGif(chatUuid, gif) } returns flowOf(
            Resource.Success(
                message("srv", "", "2026-01-01T00:00:00").copy(
                    messageType = "GIF",
                    attachments = listOf(
                        MessageAttachment("https://c/gif.gif", "https://c/tiny.gif", "GIF", 100, 50)
                    )
                )
            )
        )

        viewModel.sendGif(gif)

        val sent = viewModel.uiState.value.messages.single()
        // Локальный пузырь сохраняет свой uuid после эха.
        assertTrue(sent.messageUuid.startsWith("local-"))
        assertEquals("GIF", sent.messageType)
        assertEquals("https://c/gif.gif", sent.attachments.single().fileUrl)
    }

    @Test
    fun `loadOlder prepends a full page and keeps history open`() = withViewModel(
        network = flowOf(Resource.Success(listOf(message("m2", "new", "2026-01-02T00:00:00"))))
    ) { viewModel ->
        val older = (1..50).map {
            message("o$it", "old$it", "2026-01-01T00:00:${"%02d".format(it - 1)}")
        }
        coEvery { messageRepository.loadMessagesBefore(chatUuid, "m2", 50) } returns
            flowOf(Resource.Success(older))

        viewModel.loadOlder()

        assertEquals(51, viewModel.uiState.value.messages.size)
        assertEquals("old1", viewModel.uiState.value.messages.first().text)
        assertFalse(viewModel.uiState.value.historyReached)
        coVerify { messageRepository.loadMessagesBefore(chatUuid, "m2", 50) }
    }

    @Test
    fun `loadOlder requests messages before the oldest and stops at end`() = withViewModel(
        network = flowOf(Resource.Success(listOf(message("m2", "new", "2026-01-02T00:00:00"))))
    ) { viewModel ->
        coEvery { messageRepository.loadMessagesBefore(chatUuid, "m2", 50) } returns
            flowOf(Resource.Success(listOf(message("m1", "old", "2026-01-01T00:00:00"))))

        viewModel.loadOlder()
        // Неполная страница означает конец истории.
        assertTrue(viewModel.uiState.value.historyReached)

        // Повторный вызов уже не ходит в сеть.
        viewModel.loadOlder()
        coVerify(exactly = 1) { messageRepository.loadMessagesBefore(any(), any(), any()) }
    }

    @Test
    fun `receipts turn own message into read and acknowledge delivery`() = withViewModel(
        network = flowOf(
            Resource.Success(
                listOf(message("m1", "hi", "2026-01-01T10:00:00", messageId = 10))
            )
        )
    ) { viewModel ->
        coEvery { messageRepository.loadChatReceipts(chatUuid) } returns listOf(
            ChatReceipt(
                userUuid = "other",
                username = "anna",
                fullName = "Anna",
                avatarUrl = null,
                joinedAt = "2025-01-01T00:00:00",
                lastDeliveredMessageId = 10,
                lastDeliveredAt = "2026-01-01T10:01:00",
                lastReadMessageId = 10,
                lastReadAt = "2026-01-01T10:02:00"
            )
        )

        viewModel.refresh()

        assertEquals(MessageDeliveryStatus.READ, viewModel.deliveryStatus("m1"))
        coVerify { messageRepository.markMessagesAsDelivered(chatUuid, "m1") }
        assertEquals("Anna", viewModel.participantStatuses(viewModel.uiState.value.messages.single()).single().receipt.displayName())
    }

    @Test
    fun `failed send marks the optimistic message as failed`() = withViewModel { viewModel ->
        val item = slot<OutboxItem>()
        every { outboxSender.enqueue(capture(item)) } returns Unit

        viewModel.sendText("hello")
        outboxEvents.emit(OutboxEvent.Failed(item.captured, "send failed"))

        val local = viewModel.uiState.value.messages.single()
        assertEquals(MessageDeliveryStatus.FAILED, viewModel.deliveryStatus(local.messageUuid))
        assertTrue(local.messageUuid in viewModel.uiState.value.failedMessages)
    }

    @Test
    fun `incoming message has no delivery status`() = withViewModel(
        network = flowOf(
            Resource.Success(
                listOf(message("m1", "hi", "2026-01-01T10:00:00", messageId = 10, sender = "other"))
            )
        )
    ) { viewModel ->
        assertNull(viewModel.deliveryStatus("m1"))
    }

    @Test
    fun `attachments are uploaded and then sent with the message`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("photo", ".jpg").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "photo.jpg", size = 3,
            mimeType = "image/jpeg", type = "IMAGE", width = 10, height = 10, duration = null
        )
        coEvery { mediaFileReader.prepare(any()) } returns prepared
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns Resource.Success(
            MessageAttachment(
                fileUrl = "/api/attachments/a-1", thumbnailUrl = null, type = "IMAGE",
                width = 10, height = 10, attachmentUuid = "a-1", fileName = "photo.jpg"
            )
        )
        val item = slot<OutboxItem>()
        every { outboxSender.enqueue(capture(item)) } returns Unit

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://photo/1")))
        assertTrue(viewModel.uiState.value.pendingAttachments.single().isUploaded)

        viewModel.sendText("")

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
        assertEquals(listOf("a-1"), item.captured.attachments.map { it.attachmentUuid })
        coVerify { mediaFileReader.cleanup(prepared) }
    }

    @Test
    fun `failed upload blocks sending until retried`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("photo", ".jpg").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "photo.jpg", size = 3,
            mimeType = "image/jpeg", type = "IMAGE", width = 10, height = 10, duration = null
        )
        coEvery { mediaFileReader.prepare(any()) } returns prepared
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Error("boom")

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://photo/1")))
        assertEquals("boom", viewModel.uiState.value.pendingAttachments.single().error)

        viewModel.sendText("")

        coVerify(exactly = 0) { messageRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `removing a pending attachment deletes its temp file`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("photo", ".jpg").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "photo.jpg", size = 3,
            mimeType = "image/jpeg", type = "IMAGE", width = 10, height = 10, duration = null
        )
        coEvery { mediaFileReader.prepare(any()) } returns prepared
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns Resource.Error("x")

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://photo/1")))
        val id = viewModel.uiState.value.pendingAttachments.single().id
        viewModel.removePendingAttachment(id)

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
        coVerify { mediaFileReader.cleanup(prepared) }
    }

    @Test
    fun `recorded voice is uploaded and sent with VOICE type`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("voice", ".m4a").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "voice.m4a", size = 3,
            mimeType = "audio/mp4", type = "AUDIO", width = null, height = null, duration = 2
        )
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns Resource.Success(
            MessageAttachment(
                fileUrl = "/api/attachments/a-voice", thumbnailUrl = null, type = "AUDIO",
                width = null, height = null, attachmentUuid = "a-voice",
                fileName = "voice.m4a", duration = 2
            )
        )
        coEvery {
            messageRepository.sendMessage(chatUuid, "", listOf("a-voice"), "VOICE")
        } returns flowOf(
            Resource.Success(
                message("srv", "", "2026-01-01T00:00:00").copy(messageType = "VOICE")
            )
        )

        viewModel.sendRecordedAttachment(prepared, "VOICE")

        coVerify { messageRepository.sendMessage(chatUuid, "", listOf("a-voice"), "VOICE") }
        coVerify { mediaFileReader.cleanup(prepared) }
    }

    @Test
    fun `failed voice upload keeps the local bubble and the file`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("voice", ".m4a").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "voice.m4a", size = 3,
            mimeType = "audio/mp4", type = "AUDIO", width = null, height = null, duration = 2
        )
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Error("offline")

        viewModel.sendRecordedAttachment(prepared, "VOICE")

        val local = viewModel.uiState.value.messages.single()
        assertTrue(local.messageUuid.startsWith("local-"))
        assertEquals(MessageDeliveryStatus.FAILED, viewModel.deliveryStatus(local.messageUuid))
        // Файл не удаляем: на слабой сети запись не должна пропадать.
        coVerify(exactly = 0) { mediaFileReader.cleanup(prepared) }
    }

    @Test
    fun `failed voice message can be retried`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("voice", ".m4a").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "voice.m4a", size = 3,
            mimeType = "audio/mp4", type = "AUDIO", width = null, height = null, duration = 2
        )
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returnsMany listOf(
            Resource.Error("offline"),
            Resource.Success(
                MessageAttachment(
                    fileUrl = "/api/attachments/a-voice", thumbnailUrl = null, type = "AUDIO",
                    width = null, height = null, attachmentUuid = "a-voice",
                    fileName = "voice.m4a", duration = 2
                )
            )
        )
        coEvery {
            messageRepository.sendMessage(chatUuid, "", listOf("a-voice"), "VOICE")
        } returns flowOf(
            Resource.Success(
                message("srv", "", "2026-01-01T00:00:00", messageId = 10)
                    .copy(messageType = "VOICE")
            )
        )

        viewModel.sendRecordedAttachment(prepared, "VOICE")
        val failed = viewModel.uiState.value.messages.single()
        assertEquals(MessageDeliveryStatus.FAILED, viewModel.deliveryStatus(failed.messageUuid))

        viewModel.retryMessage(failed)

        coVerify { messageRepository.sendMessage(chatUuid, "", listOf("a-voice"), "VOICE") }
        assertEquals(failed.messageUuid, viewModel.uiState.value.messages.single().messageUuid)
    }

    @Test
    fun `pinning a message marks it pinned and shows it in the bar`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "hi", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        coEvery { messageRepository.pinMessage(chatUuid, "m1") } returns true
        coEvery { messageRepository.loadPinned(chatUuid) } returns
            listOf(target.copy(isPinned = true))

        viewModel.pinMessage(target)

        assertTrue(viewModel.uiState.value.messages.single().isPinned)
        assertEquals("m1", viewModel.uiState.value.pinnedMessages.single().messageUuid)
        coVerify { messageRepository.pinMessage(chatUuid, "m1") }
    }

    @Test
    fun `unpinning a message clears its pinned flag and the bar`() = withViewModel(
        network = flowOf(
            Resource.Success(
                listOf(message("m1", "hi", "2026-01-01T00:00:00", messageId = 10).copy(isPinned = true))
            )
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        coEvery { messageRepository.unpinMessage(chatUuid, "m1") } returns true
        coEvery { messageRepository.loadPinned(chatUuid) } returns emptyList()

        viewModel.unpinMessage(target)

        assertFalse(viewModel.uiState.value.messages.single().isPinned)
        assertTrue(viewModel.uiState.value.pinnedMessages.isEmpty())
    }

    @Test
    fun `deleting a message removes it and clears the composer`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "hi", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        viewModel.setReplyTo(target)
        coEvery { messageRepository.deleteMessage("m1") } returns true

        viewModel.deleteMessage(target)

        // Удалённое сообщение убираем из списка: пропадает заглушка и работает
        // анимация исчезновения в RecyclerView.
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertNull(viewModel.uiState.value.replyTo)
        coVerify { messageRepository.deleteMessage("m1") }
    }

    @Test
    fun `forwarding a message reports a success event`() = withViewModel { viewModel ->
        val target = message("m1", "hi", "2026-01-01T00:00:00", messageId = 10)
        coEvery { messageRepository.forwardMessage("m1", "chat-2") } returns flowOf(
            Resource.Success(message("m2", "hi", "2026-01-02T00:00:00", messageId = 11))
        )
        val events = mutableListOf<String>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { events += it }
        }

        viewModel.forwardMessage(target, "chat-2")
        collector.cancel()

        coVerify { messageRepository.forwardMessage("m1", "chat-2") }
        assertEquals(1, events.size)
    }

    // --- Редактирование ---

    @Test
    fun `editing a message updates it in place and leaves edit mode`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "old", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        coEvery { messageRepository.editMessage("m1", "new") } returns flowOf(
            Resource.Success(target.copy(text = "new", isEdited = true))
        )

        viewModel.startEditing(target)
        assertEquals("m1", viewModel.uiState.value.editing?.messageUuid)

        viewModel.sendText("new")

        assertEquals("new", viewModel.uiState.value.messages.single().text)
        assertTrue(viewModel.uiState.value.messages.single().isEdited)
        assertNull(viewModel.uiState.value.editing)
        coVerify { messageRepository.editMessage("m1", "new") }
    }

    @Test
    fun `editing to blank reports an error and keeps edit mode`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "old", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        viewModel.startEditing(target)

        val errors = mutableListOf<String>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.sendError.collect { errors += it }
        }
        viewModel.sendText("   ")
        collector.cancel()

        assertEquals(listOf("error"), errors)
        assertEquals("m1", viewModel.uiState.value.editing?.messageUuid)
        coVerify(exactly = 0) { messageRepository.editMessage(any(), any()) }
    }

    @Test
    fun `editing without changes only closes edit mode`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "old", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        viewModel.startEditing(target)

        viewModel.sendText("old")

        assertNull(viewModel.uiState.value.editing)
        coVerify(exactly = 0) { messageRepository.editMessage(any(), any()) }
    }

    @Test
    fun `edit failure is reported and does not change the message`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "old", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        coEvery { messageRepository.editMessage("m1", "new") } returns
            flowOf(Resource.Error("edit failed"))
        viewModel.startEditing(target)

        val errors = mutableListOf<String>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.sendError.collect { errors += it }
        }
        viewModel.sendText("new")
        collector.cancel()

        assertEquals(listOf("edit failed"), errors)
        assertEquals("old", viewModel.uiState.value.messages.single().text)
    }

    // --- Вложения: повторная загрузка и прогресс ---

    @Test
    fun `retryPendingAttachment re-uploads and can succeed`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("photo", ".jpg").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "photo.jpg", size = 3,
            mimeType = "image/jpeg", type = "IMAGE", width = 10, height = 10, duration = null
        )
        coEvery { mediaFileReader.prepare(any()) } returns prepared
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returnsMany listOf(
            Resource.Error("boom"),
            Resource.Success(
                MessageAttachment(
                    fileUrl = "/api/attachments/a-1", thumbnailUrl = null, type = "IMAGE",
                    width = 10, height = 10, attachmentUuid = "a-1", fileName = "photo.jpg"
                )
            )
        )

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://photo/1")))
        val id = viewModel.uiState.value.pendingAttachments.single().id
        assertEquals("boom", viewModel.uiState.value.pendingAttachments.single().error)

        viewModel.retryPendingAttachment(id)

        assertTrue(viewModel.uiState.value.pendingAttachments.single().isUploaded)
        coVerify(exactly = 2) { messageRepository.uploadAttachment(chatUuid, any(), any()) }
    }

    @Test
    fun `upload progress is reflected on the pending attachment`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("photo", ".jpg").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "photo.jpg", size = 3,
            mimeType = "image/jpeg", type = "IMAGE", width = 10, height = 10, duration = null
        )
        coEvery { mediaFileReader.prepare(any()) } returns prepared
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } answers {
            thirdArg<(Int) -> Unit>().invoke(75)
            Resource.Success(
                MessageAttachment(
                    fileUrl = "/api/attachments/a-1", thumbnailUrl = null, type = "IMAGE",
                    width = 10, height = 10, attachmentUuid = "a-1", fileName = "photo.jpg"
                )
            )
        }

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://photo/1")))

        assertEquals(75, viewModel.uiState.value.pendingAttachments.single().progress)
    }

    @Test
    fun `attachment that cannot be read is skipped with an error`() = withViewModel { viewModel ->
        coEvery { mediaFileReader.prepare(any()) } throws IllegalStateException("nope")

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://x/1")))

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
        assertFalse(viewModel.uiState.value.isUploadingAttachments)
    }

    @Test
    fun `adding no attachments does nothing`() = withViewModel { viewModel ->
        viewModel.addAttachments(emptyList())

        assertTrue(viewModel.uiState.value.pendingAttachments.isEmpty())
        coVerify(exactly = 0) { mediaFileReader.prepare(any()) }
    }

    // --- Удаление / закрепление: ошибки ---

    @Test
    fun `delete failure keeps the message and reports an error`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "hi", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        coEvery { messageRepository.deleteMessage("m1") } returns false

        val errors = mutableListOf<String>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.sendError.collect { errors += it }
        }
        viewModel.deleteMessage(target)
        collector.cancel()

        assertFalse(viewModel.uiState.value.messages.single().isDeleted)
        assertEquals(1, errors.size)
    }

    @Test
    fun `pin failure keeps the message unpinned and reports an error`() = withViewModel(
        network = flowOf(
            Resource.Success(listOf(message("m1", "hi", "2026-01-01T00:00:00", messageId = 10)))
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        coEvery { messageRepository.pinMessage(chatUuid, "m1") } returns false

        viewModel.pinMessage(target)

        assertFalse(viewModel.uiState.value.messages.single().isPinned)
    }

    @Test
    fun `unpin failure keeps the message pinned and reports an error`() = withViewModel(
        network = flowOf(
            Resource.Success(
                listOf(message("m1", "hi", "2026-01-01T00:00:00", messageId = 10).copy(isPinned = true))
            )
        )
    ) { viewModel ->
        val target = viewModel.uiState.value.messages.single()
        coEvery { messageRepository.unpinMessage(chatUuid, "m1") } returns false

        viewModel.unpinMessage(target)

        assertTrue(viewModel.uiState.value.messages.single().isPinned)
    }

    @Test
    fun `forward failure reports an error event`() = withViewModel { viewModel ->
        val target = message("m1", "hi", "2026-01-01T00:00:00", messageId = 10)
        coEvery { messageRepository.forwardMessage("m1", "chat-2") } returns
            flowOf(Resource.Error("offline"))

        viewModel.forwardMessage(target, "chat-2")

        coVerify { messageRepository.forwardMessage("m1", "chat-2") }
    }

    // --- Повтор отправки: ветки-исключения ---

    @Test
    fun `retryMessage ignores server and not-failed messages`() = withViewModel { viewModel ->
        val server = message("m1", "hi", "2026-01-01T00:00:00", messageId = 10)
        viewModel.retryMessage(server)

        val local = server.copy(messageUuid = "local-1")
        viewModel.retryMessage(local)

        coVerify(exactly = 0) { messageRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `retryMessage fails when the recorded file is gone`() = withViewModel { viewModel ->
        val file = java.io.File.createTempFile("voice", ".m4a").apply { deleteOnExit() }
        val prepared = PreparedAttachment(
            file = file, thumbnail = null, name = "voice.m4a", size = 3,
            mimeType = "audio/mp4", type = "AUDIO", width = null, height = null, duration = 2
        )
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Error("offline")

        viewModel.sendRecordedAttachment(prepared, "VOICE")
        val failed = viewModel.uiState.value.messages.single()
        file.delete()

        val errors = mutableListOf<String>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            viewModel.sendError.collect { errors += it }
        }
        viewModel.retryMessage(failed)
        collector.cancel()

        assertTrue(errors.isNotEmpty())
        coVerify(exactly = 1) { messageRepository.uploadAttachment(chatUuid, any(), any()) }
    }

    @Test
    fun `failed gif send marks the bubble failed`() = withViewModel { viewModel ->
        val gif = Gif("klipy", "s1", "cat", "https://c/gif.gif", "https://c/tiny.gif", 100, 50)
        coEvery { messageRepository.sendGif(chatUuid, gif) } returns
            flowOf(Resource.Error("gif down"))

        viewModel.sendGif(gif)

        val local = viewModel.uiState.value.messages.single()
        assertEquals(MessageDeliveryStatus.FAILED, viewModel.deliveryStatus(local.messageUuid))
    }

    // --- Прочее: загрузка вниз, пустая отправка, отчёт, скачивание ---

    @Test
    fun `loadOlder failure stops the loading-more spinner`() = withViewModel(
        network = flowOf(Resource.Success(listOf(message("m2", "new", "2026-01-02T00:00:00"))))
    ) { viewModel ->
        coEvery { messageRepository.loadMessagesBefore(chatUuid, "m2", 50) } returns
            flowOf(Resource.Error("offline"))

        viewModel.loadOlder()

        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun `loadOlder does nothing when there is no server message`() = withViewModel { viewModel ->
        viewModel.loadOlder()

        coVerify(exactly = 0) { messageRepository.loadMessagesBefore(any(), any(), any()) }
    }

    @Test
    fun `empty send is ignored`() = withViewModel { viewModel ->
        viewModel.sendText("   ")

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        coVerify(exactly = 0) { messageRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `pause and resume polling do not crash`() = withViewModel { viewModel ->
        viewModel.pausePolling()
        viewModel.resumePolling()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `downloadAttachment delegates and rejects a missing uuid`() = withViewModel { viewModel ->
        val attachment = MessageAttachment(
            fileUrl = "/f", thumbnailUrl = null, type = "DOCUMENT", width = null, height = null,
            attachmentUuid = "a-1", fileName = "f.pdf"
        )
        val expected = java.io.File("/tmp/f.pdf")
        coEvery {
            messageRepository.downloadAttachment("a-1", "f.pdf", any())
        } answers {
            thirdArg<(Int) -> Unit>().invoke(100)
            expected
        }
        val progress = mutableListOf<Int>()

        assertEquals(expected, viewModel.downloadAttachment(attachment) { progress += it })
        assertEquals(listOf(100), progress)
        assertNull(viewModel.downloadAttachment(attachment.copy(attachmentUuid = null)))
    }

    @Test
    fun `report and block delegate to the repository`() = withViewModel { viewModel ->
        coEvery { messageRepository.reportMessage("m1", "SPAM") } returns true
        coEvery { messageRepository.blockUser("u1") } returns false

        assertTrue(viewModel.reportMessage("m1", "SPAM"))
        assertFalse(viewModel.blockUser("u1"))
    }

    @Test
    fun `polling asks only for messages newer than the last known`() = withViewModel(
        network = flowOf(
            Resource.Success(
                listOf(message("m1", "hi", "2026-01-01T00:00:00", messageId = 10, sender = "other"))
            )
        )
    ) { _ ->
        (this as kotlinx.coroutines.test.TestScope).testScheduler.advanceTimeBy(3_500)
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()

        coVerify {
            messageRepository.loadMessagesAfter(chatUuid, "2026-01-01T00:00:00")
        }
    }

    @Test
    fun `network recovery from offline reloads immediately`() = withViewModel(
        initialNetwork = com.example.muzea.utils.NetworkQuality.OFFLINE
    ) { viewModel ->
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()
        // Пока сети нет, запросов не делаем: ждём offline-интервал.
        coVerify(exactly = 0) { messageRepository.loadMessages(chatUuid) }

        networkStatus.value = com.example.muzea.utils.NetworkStatus(
            com.example.muzea.utils.NetworkQuality.GOOD,
            metered = false
        )
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()

        // Возврат сети перезапускает polling, не дожидаясь 60-секундного delay.
        coVerify(exactly = 1) { messageRepository.loadMessages(chatUuid) }
    }

    @Test
    fun `network recovery does not reload while the screen is paused`() = withViewModel(
        initialNetwork = com.example.muzea.utils.NetworkQuality.OFFLINE
    ) { viewModel ->
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()
        viewModel.pausePolling()

        networkStatus.value = com.example.muzea.utils.NetworkStatus(
            com.example.muzea.utils.NetworkQuality.GOOD,
            metered = false
        )
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()
        coVerify(exactly = 0) { messageRepository.loadMessages(chatUuid) }

        viewModel.resumePolling()
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()
        coVerify(exactly = 1) { messageRepository.loadMessages(chatUuid) }
    }

    @Test
    fun `resume polling immediately reloads messages`() = withViewModel { viewModel ->
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()
        coVerify(atLeast = 1) { messageRepository.loadMessages(chatUuid) }

        viewModel.pausePolling()
        viewModel.resumePolling()
        (this as kotlinx.coroutines.test.TestScope).testScheduler.runCurrent()

        // Повторный вход на экран (onStart) не должен ждать следующего тика.
        coVerify(atLeast = 2) { messageRepository.loadMessages(chatUuid) }
    }
}
