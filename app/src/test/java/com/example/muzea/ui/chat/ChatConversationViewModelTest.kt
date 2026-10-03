package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.muzea.domain.model.Message
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.domain.ChatUserIdentity
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatConversationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val messageRepository = mockk<MessageRepository>()
    private val chatUuid = "chat-1"

    private fun message(uuid: String, text: String, createdAt: String) = Message(
        messageUuid = uuid,
        chatUuid = chatUuid,
        senderId = null,
        senderUuid = "me",
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
        network: Flow<NetworkResult<List<Message>>> =
            flowOf(NetworkResult.Success(emptyList())),
        block: suspend CoroutineScope.(ChatConversationViewModel) -> Unit
    ) = runTest {
        every { messageRepository.cachedMessages(chatUuid) } returns cached
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
                ChatConversationViewModel(savedStateHandle, messageRepository, identity) as T
        }
        try {
            block(ViewModelProvider(store, factory)[ChatConversationViewModel::class.java])
        } finally {
            store.clear()
        }
    }

    @Test
    fun `opening a chat issues exactly one request`() = withViewModel(
        network = flowOf(NetworkResult.Success(listOf(message("m1", "hi", "2026-01-01T00:00:00"))))
    ) { viewModel ->
        // Раньше init звал refresh() и сразу startPolling(), давая два запроса
        // подряд при каждом входе в чат.
        assertEquals(1, viewModel.uiState.value.messages.size)

        coVerify(exactly = 1) { messageRepository.loadMessages(chatUuid) }
    }

    @Test
    fun `cached messages are shown immediately without spinner`() = withViewModel(
        cached = listOf(message("m1", "cached", "2026-01-01T00:00:00")),
        network = flowOf(NetworkResult.Success(listOf(message("m1", "cached", "2026-01-01T00:00:00"))))
    ) { viewModel ->
        assertEquals("cached", viewModel.uiState.value.messages.single().text)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `fresh messages are merged into cached history`() = withViewModel(
        cached = listOf(message("m1", "old", "2026-01-01T00:00:00")),
        network = flowOf(
            NetworkResult.Success(
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
        network = flowOf(NetworkResult.Error("network down"))
    ) { viewModel ->
        assertEquals("cached", viewModel.uiState.value.messages.single().text)
        assertEquals("network down", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `spinner turns off after loading finishes`() = withViewModel(
        network = flowOf(NetworkResult.Loading(), NetworkResult.Success(emptyList()))
    ) { viewModel ->
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `spinner never shows when cached messages are available`() = withViewModel(
        cached = listOf(message("m1", "cached", "2026-01-01T00:00:00")),
        network = flowOf(NetworkResult.Loading(), NetworkResult.Success(emptyList()))
    ) { viewModel ->
        // Даже если сервер сначала отвечает Loading, экран уже показывает
        // переписку из кэша, поэтому мигать индикатором нельзя.
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `optimistic bubble is updated in place by the server echo`() = withViewModel { viewModel ->
        coEvery { messageRepository.sendMessage(chatUuid, "hello") } returns flowOf(
            NetworkResult.Success(message("server-1", "hello", "2026-01-01T00:00:00"))
        )

        viewModel.sendText("hello")

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
        coEvery { messageRepository.sendMessage(chatUuid, "hello") } returns
            flowOf(NetworkResult.Success(serverCopy))

        viewModel.sendText("hello")
        assertEquals(1, viewModel.uiState.value.messages.size)

        // Следующий опрос возвращает серверную копию — она уже показана
        // локальным пузырём и не должна появиться второй строкой.
        coEvery { messageRepository.loadMessages(chatUuid) } returns
            flowOf(NetworkResult.Success(listOf(serverCopy)))
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
        network = flowOf(NetworkResult.Success(emptyList()))
    ) { viewModel ->
        assertEquals(listOf("older", "newer"), viewModel.uiState.value.messages.map { it.text })
    }

    @Test
    fun `send failure keeps optimistic message and reports error`() = withViewModel { viewModel ->
        coEvery { messageRepository.sendMessage(chatUuid, "hello") } returns flowOf(
            NetworkResult.Error("send failed")
        )

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
        // collect на одноразовом потоке не завершается сам, поэтому подписку
        // отменяем. К моменту отмены отправка уже разобрана: диспетчер теста
        // выполняет корутины незамедлительно.
        collector.cancel()

        assertEquals(1, viewModel.uiState.value.messages.size)
        assertTrue(viewModel.uiState.value.messages.single().messageUuid.startsWith("local-"))
        assertEquals(listOf("send failed"), errors)
    }
}