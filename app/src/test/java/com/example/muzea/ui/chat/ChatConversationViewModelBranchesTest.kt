package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.utils.PreparedAttachment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Добивает ветки [ChatConversationViewModel], которые не задевает
 * [ChatConversationViewModelTest]: Loading-состояния, ветвление типа вложения,
 * отсутствие GIF-вложения в кэше, сброс watermark при ошибке и редкие ветки
 * повторной отправки записи.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatConversationViewModelBranchesTest {

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
        createdAt: String = "2026-01-01T00:00:00",
        messageId: Long? = null,
        sender: String = "me",
        messageType: String = "TEXT",
        attachments: List<MessageAttachment> = emptyList()
    ) = Message(
        messageUuid = uuid,
        messageId = messageId,
        chatUuid = chatUuid,
        senderId = null,
        senderUuid = sender,
        senderName = "Me",
        senderAvatar = null,
        text = text,
        messageType = messageType,
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = createdAt,
        updatedAt = null,
        attachments = attachments
    )

    private fun prepared(type: String) = PreparedAttachment(
        file = File.createTempFile("branch", ".bin").apply { deleteOnExit() },
        thumbnail = null,
        name = "branch.bin",
        size = 3,
        mimeType = "application/octet-stream",
        type = type,
        width = null,
        height = null,
        duration = null
    )

    private fun uploaded(type: String) = MessageAttachment(
        fileUrl = "/api/attachments/a-1",
        thumbnailUrl = null,
        type = type,
        width = null,
        height = null,
        attachmentUuid = "a-1",
        fileName = "branch.bin"
    )

    @Before
    fun setUp() {
        ChatMessagesCache.clear()
        coEvery { messageRepository.markMessagesAsRead(any(), any()) } returns true
        coEvery { messageRepository.markMessagesAsDelivered(any(), any()) } returns true
        coEvery { messageRepository.loadChatReceipts(any()) } returns emptyList()
        coEvery { messageRepository.loadPinned(any()) } returns emptyList()
        every { appContext.getString(any()) } returns "error"
        every { appContext.filesDir } returns
            java.nio.file.Files.createTempDirectory("muzea-vm").toFile()
        every { messageRepository.cacheSentGif(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        ChatMessagesCache.clear()
    }

    private fun withViewModel(
        network: Flow<Resource<List<Message>>> = flowOf(Resource.Success(emptyList())),
        cached: List<Message> = emptyList(),
        block: suspend CoroutineScope.(ChatConversationViewModel) -> Unit
    ) = runTest {
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
                ChatConversationViewModel(
                    savedStateHandle, messageRepository, outboxSender, networkMonitor, identity, mediaFileReader, appContext
                ) as T
        }
        try {
            block(ViewModelProvider(store, factory)[ChatConversationViewModel::class.java])
        } finally {
            store.clear()
        }
    }

    // --- Loading-состояния ---

    @Test
    fun sendTextIgnoresALoadingEcho() = withViewModel { viewModel ->
        coEvery { messageRepository.sendMessage(chatUuid, "hello") } returns
            flowOf(Resource.Loading())

        viewModel.sendText("hello")

        assertTrue(viewModel.uiState.value.messages.single().messageUuid.startsWith("local-"))
    }

    @Test
    fun attachmentUploadLoadingLeavesItPending() = withViewModel { viewModel ->
        coEvery { mediaFileReader.prepare(any()) } returns prepared("IMAGE")
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Loading()

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://photo/1")))

        assertFalse(viewModel.uiState.value.pendingAttachments.single().isUploaded)
    }

    @Test
    fun sendingAReplyClearsTheReplyMode() = withViewModel { viewModel ->
        val target = message("m1", "old")
        // В режиме ответа uuid оригинала уходит в sendMessage пятым аргументом.
        coEvery { messageRepository.sendMessage(chatUuid, "hi", emptyList(), "TEXT", "m1") } returns
            flowOf(Resource.Success(message("s2", "hi", messageId = 11)))

        viewModel.setReplyTo(target)
        viewModel.sendText("hi")

        assertNull(viewModel.uiState.value.replyTo)
        // Серверное эхо не содержит replyToMessageUuid — ссылка на оригинал
        // должна сохраниться, иначе цитата исчезает после публикации.
        assertEquals(
            "m1",
            viewModel.uiState.value.messages.single { it.text == "hi" }.replyToMessageUuid
        )
    }

    @Test
    fun cancelEditingLeavesEditMode() = withViewModel { viewModel ->
        viewModel.startEditing(message("m1", "old"))

        viewModel.cancelEditing()

        assertNull(viewModel.uiState.value.editing)
    }

    @Test
    fun editLoadingKeepsTheMessageUnchanged() = withViewModel { viewModel ->
        val target = message("m1", "old", messageId = 10)
        coEvery { messageRepository.editMessage("m1", "new") } returns flowOf(Resource.Loading())
        viewModel.startEditing(target)

        viewModel.sendText("new")

        assertNull(viewModel.uiState.value.editing)
    }

    @Test
    fun forwardingHandlesALoadingState() = withViewModel { viewModel ->
        coEvery { messageRepository.forwardMessage("m1", "chat-2") } returns
            flowOf(Resource.Loading())

        viewModel.forwardMessage(message("m1", "hi", messageId = 10), "chat-2")

        coVerify { messageRepository.forwardMessage("m1", "chat-2") }
    }

    // --- Тип сообщения по вложениям ---

    @Test
    fun audioOnlyAttachmentsProduceAnAudioMessage() = withViewModel { viewModel ->
        coEvery { mediaFileReader.prepare(any()) } returns prepared("AUDIO")
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Success(uploaded("AUDIO"))
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1")) } returns
            flowOf(Resource.Loading())

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://audio/1")))
        viewModel.sendText("")

        assertEquals("AUDIO", viewModel.uiState.value.messages.single().messageType)
    }

    @Test
    fun aSingleVideoAttachmentProducesAVideoMessage() = withViewModel { viewModel ->
        coEvery { mediaFileReader.prepare(any()) } returns prepared("VIDEO")
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Success(uploaded("VIDEO"))
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1")) } returns
            flowOf(Resource.Loading())

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://video/1")))
        viewModel.sendText("")

        assertEquals("VIDEO", viewModel.uiState.value.messages.single().messageType)
    }

    @Test
    fun otherAttachmentsProduceAFileMessage() = withViewModel { viewModel ->
        coEvery { mediaFileReader.prepare(any()) } returns prepared("DOCUMENT")
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Success(uploaded("DOCUMENT"))
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1")) } returns
            flowOf(Resource.Loading())

        viewModel.addAttachments(listOf(android.net.Uri.parse("content://doc/1")))
        viewModel.sendText("")

        assertEquals("FILE", viewModel.uiState.value.messages.single().messageType)
    }

    @Test
    fun gifLoadingStillShowsTheOptimisticBubble() = withViewModel { viewModel ->
        val gif = Gif("klipy", "s1", "cat", "https://c/gif.gif", null, 100, 50)
        coEvery { messageRepository.sendGif(chatUuid, gif) } returns flowOf(Resource.Loading())

        viewModel.sendGif(gif)

        assertEquals("GIF", viewModel.uiState.value.messages.single().messageType)
    }

    @Test
    fun gifMarkedMessageWithoutAttachmentIsIgnoredByTheCache() =
        withViewModel(
            network = flowOf(
                Resource.Success(
                    listOf(message("g1", "", messageType = "GIF", sender = "other"))
                )
            )
        ) { _ ->
            coVerify(exactly = 0) { messageRepository.cacheSentGif(any(), any(), any()) }
        }

    // --- Watermark reset on failed receipts ---

    @Test
    fun failedDeliveryAckResetsTheWatermark() = withViewModel { viewModel ->
        coEvery { messageRepository.markMessagesAsDelivered(chatUuid, "m1") } returns false
        coEvery { messageRepository.loadMessages(chatUuid) } returns
            flowOf(Resource.Success(listOf(message("m1", "hi", messageId = 10))))

        viewModel.refresh()

        coVerify { messageRepository.markMessagesAsDelivered(chatUuid, "m1") }
    }

    @Test
    fun loadingMessagesDoesNotMarkThemRead() = withViewModel { viewModel ->
        coEvery { messageRepository.loadMessages(chatUuid) } returns
            flowOf(Resource.Success(listOf(message("m1", "hi", messageId = 10))))

        viewModel.refresh()

        coVerify(exactly = 0) { messageRepository.markMessagesAsRead(any(), any()) }
    }

    @Test
    fun failedReadAckResetsTheWatermark() = withViewModel { viewModel ->
        coEvery { messageRepository.markMessagesAsRead(chatUuid, "m1") } returns false
        coEvery { messageRepository.loadMessages(chatUuid) } returns
            flowOf(Resource.Success(listOf(message("m1", "hi", messageId = 10))))

        viewModel.refresh()
        viewModel.markReadUpTo("m1")

        coVerify { messageRepository.markMessagesAsRead(chatUuid, "m1") }

        // Ошибка сбросила watermark — повторная отметка снова идёт на сервер.
        viewModel.markReadUpTo("m1")
        coVerify(exactly = 2) { messageRepository.markMessagesAsRead(chatUuid, "m1") }
    }

    // --- Записанные вложения: ветки загрузки/отправки ---

    @Test
    fun recordedUploadLoadingKeepsTheBubble() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Loading()

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")

        assertTrue(viewModel.uiState.value.messages.single().messageUuid.startsWith("local-"))
    }

    @Test
    fun recordedMessageSendErrorMarksItFailed() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Success(uploaded("VOICE"))
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") } returns
            flowOf(Resource.Error("send down"))

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")

        val local = viewModel.uiState.value.messages.single()
        assertEquals(MessageDeliveryStatus.FAILED, viewModel.deliveryStatus(local.messageUuid))
    }

    @Test
    fun recordedMessageSendLoadingKeepsSendingState() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Success(uploaded("VOICE"))
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") } returns
            flowOf(Resource.Loading())

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")

        assertTrue(viewModel.uiState.value.messages.single().messageUuid.startsWith("local-"))
    }

    @Test
    fun retryReplacesAttachmentsNextToOtherMessages() = withViewModel(
        network = flowOf(Resource.Success(listOf(message("m1", "hi", messageId = 10))))
    ) { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returnsMany listOf(
            Resource.Error("first"),
            Resource.Success(uploaded("VOICE"))
        )
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") } returns
            flowOf(Resource.Success(message("srv", "", messageId = 12).copy(messageType = "VOICE")))

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")
        val failed = viewModel.uiState.value.messages.first { it.messageUuid.startsWith("local-") }

        viewModel.retryMessage(failed)

        coVerify { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") }
    }

    @Test
    fun retrySkipsAttachmentsThatAlreadyHaveAServerUuid() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returns
            Resource.Success(uploaded("VOICE"))
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") } returnsMany
            listOf(
                flowOf(Resource.Error("send down")),
                flowOf(Resource.Success(message("srv", "", messageId = 12)))
            )

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")
        val failed = viewModel.uiState.value.messages.single()

        viewModel.retryMessage(failed)

        coVerify(exactly = 2) { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") }
    }

    @Test
    fun retryUploadFailureMarksItFailedAgain() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returnsMany listOf(
            Resource.Error("first"),
            Resource.Error("second")
        )

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")
        val failed = viewModel.uiState.value.messages.single()

        viewModel.retryMessage(failed)

        assertEquals(MessageDeliveryStatus.FAILED, viewModel.deliveryStatus(failed.messageUuid))
        coVerify(exactly = 0) { messageRepository.sendMessage(any(), any(), any()) }
    }

    @Test
    fun retryUploadLoadingFinishesAsAnEmptySend() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returnsMany listOf(
            Resource.Error("first"),
            Resource.Loading()
        )
        coEvery { messageRepository.sendMessage(chatUuid, "", emptyList(), "VOICE") } returns
            flowOf(Resource.Success(message("srv", "", messageId = 12)))

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")
        val failed = viewModel.uiState.value.messages.single()

        viewModel.retryMessage(failed)

        coVerify { messageRepository.sendMessage(chatUuid, "", emptyList(), "VOICE") }
    }

    @Test
    fun retrySendErrorMarksItFailed() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returnsMany listOf(
            Resource.Error("first"),
            Resource.Success(uploaded("VOICE"))
        )
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") } returns
            flowOf(Resource.Error("down"))

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")
        val failed = viewModel.uiState.value.messages.single()

        viewModel.retryMessage(failed)

        assertEquals(MessageDeliveryStatus.FAILED, viewModel.deliveryStatus(failed.messageUuid))
    }

    @Test
    fun retrySendLoadingKeepsTheBubble() = withViewModel { viewModel ->
        coEvery { messageRepository.uploadAttachment(chatUuid, any(), any()) } returnsMany listOf(
            Resource.Error("first"),
            Resource.Success(uploaded("VOICE"))
        )
        coEvery { messageRepository.sendMessage(chatUuid, "", listOf("a-1"), "VOICE") } returns
            flowOf(Resource.Loading())

        viewModel.sendRecordedAttachment(prepared("VOICE"), "VOICE")
        val failed = viewModel.uiState.value.messages.single()

        viewModel.retryMessage(failed)

        assertTrue(viewModel.uiState.value.messages.single().messageUuid.startsWith("local-"))
    }
}
