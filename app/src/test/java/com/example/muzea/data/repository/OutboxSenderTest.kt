package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.NetworkQuality
import com.example.muzea.utils.NetworkStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxSenderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun clearMessagesCache() {
        ChatMessagesCache.clear()
    }

    private fun store(): OutboxStore {
        val dir: File = temp.newFolder()
        val context = mockk<Context>()
        every { context.filesDir } returns dir
        every { context.applicationContext } returns context
        return OutboxStore(context)
    }

    private fun item(power: Int = 0) = OutboxItem(
        clientMessageId = "c1",
        localMessageUuid = "local-1",
        chatUuid = "chat-1",
        text = "hello",
        messageType = "TEXT",
        replyToMessageUuid = null,
        power = power
    )

    private fun serverMessage() = Message(
        messageUuid = "server-1",
        messageId = 10,
        chatUuid = "chat-1",
        senderId = 1,
        senderUuid = "me",
        senderName = null,
        senderAvatar = null,
        text = "hello",
        messageType = "TEXT",
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = null
    )

    private fun monitor(initial: NetworkQuality): Pair<NetworkMonitor, MutableStateFlow<NetworkStatus>> {
        val flow = MutableStateFlow(NetworkStatus(initial, metered = false))
        val monitor = mockk<NetworkMonitor>()
        every { monitor.status } returns flow
        return monitor to flow
    }

    @Test
    fun `successful send removes the item and emits Sent`() = runTest {
        val store = store()
        val repository = mockk<MessageRepository>()
        val (monitor, _) = monitor(NetworkQuality.GOOD)
        coEvery {
            repository.sendMessage("chat-1", "hello", emptyList(), "TEXT", null, "c1")
        } returns flowOf(Resource.Success(serverMessage()))
        val sender = OutboxSender(store, repository, monitor, backgroundScope)
        val events = mutableListOf<OutboxEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sender.events.collect { events += it }
        }

        sender.enqueue(item())
        sender.drainNow()

        assertTrue(store.all().isEmpty())
        assertTrue(events.single() is OutboxEvent.Sent)
    }

    @Test
    fun `charged message is sent with its power`() = runTest {
        val store = store()
        val repository = mockk<MessageRepository>()
        val (monitor, _) = monitor(NetworkQuality.GOOD)
        coEvery {
            repository.sendMessage("chat-1", "hello", emptyList(), "TEXT", null, "c1", 75)
        } returns flowOf(Resource.Success(serverMessage()))
        val sender = OutboxSender(store, repository, monitor, backgroundScope)

        sender.enqueue(item(power = 75))
        sender.drainNow()

        coVerify {
            repository.sendMessage("chat-1", "hello", emptyList(), "TEXT", null, "c1", 75)
        }
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `failed send keeps the item with attempts for a later retry`() = runTest {
        val store = store()
        val repository = mockk<MessageRepository>()
        val (monitor, _) = monitor(NetworkQuality.GOOD)
        coEvery {
            repository.sendMessage("chat-1", "hello", emptyList(), "TEXT", null, "c1")
        } returns flowOf(Resource.Error("offline"))
        val sender = OutboxSender(store, repository, monitor, backgroundScope)
        val events = mutableListOf<OutboxEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sender.events.collect { events += it }
        }

        sender.enqueue(item())
        sender.drainNow()

        assertEquals(1, store.all().single().attempts)
        assertTrue(events.single() is OutboxEvent.Failed)
    }

    @Test
    fun `offline queue waits for network and sends after reconnect`() = runTest {
        val store = store()
        val repository = mockk<MessageRepository>()
        val (monitor, status) = monitor(NetworkQuality.OFFLINE)
        coEvery {
            repository.sendMessage("chat-1", "hello", emptyList(), "TEXT", null, "c1")
        } returns flowOf(Resource.Success(serverMessage()))
        val sender = OutboxSender(store, repository, monitor, backgroundScope)

        sender.enqueue(item())
        sender.drainNow()
        assertEquals(1, store.all().size)

        status.value = NetworkStatus(NetworkQuality.GOOD, metered = false)
        sender.drainNow()

        assertTrue(store.all().isEmpty())
        coVerify(exactly = 1) {
            repository.sendMessage("chat-1", "hello", emptyList(), "TEXT", null, "c1")
        }
    }

    @Test
    fun `successful send replaces the local bubble in the messages cache`() = runTest {
        val store = store()
        val repository = mockk<MessageRepository>()
        val (monitor, _) = monitor(NetworkQuality.GOOD)
        val server = serverMessage()
        coEvery {
            repository.sendMessage("chat-1", "hello", emptyList(), "TEXT", null, "c1")
        } returns flowOf(Resource.Success(server))
        ChatMessagesCache.put(
            "chat-1",
            listOf(server.copy(messageUuid = "local-1", createdAt = null))
        )
        val sender = OutboxSender(store, repository, monitor, backgroundScope)

        sender.enqueue(item())
        sender.drainNow()

        val cached = ChatMessagesCache.get("chat-1").single()
        assertEquals("server-1", cached.messageUuid)
        assertEquals("2026-01-01T00:00:00", cached.createdAt)
    }
}
