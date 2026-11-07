package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.di.OutboxScope
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.utils.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OutboxEvent {
    data class Sent(val item: OutboxItem, val message: Message) : OutboxEvent
    data class Failed(val item: OutboxItem, val reason: String) : OutboxEvent
}

/**
 * Отправляет очередь исходящих сообщений вне ViewModel.
 *
 * Благодаря этому текст не теряется при уходе из чата или смерти процесса:
 * сообщение лежит в [OutboxStore], повторяется при появлении сети и по
 * таймеру. clientMessageId делает повтор идемпотентным на сервере.
 */
@Singleton
class OutboxSender @Inject constructor(
    private val store: OutboxStore,
    private val messageRepository: MessageRepository,
    private val networkMonitor: NetworkMonitor,
    @OutboxScope private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private var retryJob: kotlinx.coroutines.Job? = null

    private val _events = MutableSharedFlow<OutboxEvent>(
        extraBufferCapacity = 64
    )
    val events: SharedFlow<OutboxEvent> = _events.asSharedFlow()

    init {
        // Дренаж при появлении сети и при старте процесса.
        scope.launch {
            networkMonitor.status.collect { status ->
                if (status.online) drain()
            }
        }
        scope.launch { drain() }
    }

    fun enqueue(item: OutboxItem) {
        store.add(item)
        scope.launch { drain() }
    }

    /** Ручной повтор: сбрасываем попытки и пробуем сразу. */
    fun retry(chatUuid: String, localMessageUuid: String) {
        val item = store.forChat(chatUuid)
            .firstOrNull { it.localMessageUuid == localMessageUuid } ?: return
        store.update(item.copy(attempts = 0))
        scope.launch { drain() }
    }

    fun pendingForChat(chatUuid: String): List<OutboxItem> = store.forChat(chatUuid)

    /** Смена пользователя: чужие неотправленные сообщения не должны уйти. */
    fun clear() = store.clear()

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)
            drain()
        }
    }

    /** Дренаж по требованию (ручной повтор, тесты). */
    internal suspend fun drainNow() = drain()

    private suspend fun drain() {
        if (!networkMonitor.status.value.online) return
        mutex.withLock {
            if (!networkMonitor.status.value.online) return
            for (item in store.all()) {
                if (!send(item)) {
                    scheduleRetry()
                    break
                }
            }
        }
    }

    /** true — сообщение отправлено (или его уже нет); false — нужно повторить позже. */
    private suspend fun send(item: OutboxItem): Boolean {
        var error: String? = null
        var sent: Message? = null
        messageRepository.sendMessage(
            chatUuid = item.chatUuid,
            text = item.text,
            attachments = item.attachments.mapNotNull { it.attachmentUuid },
            messageType = item.messageType,
            replyToMessageUuid = item.replyToMessageUuid,
            clientMessageId = item.clientMessageId
        ).collect { result ->
            when (result) {
                is Resource.Success -> sent = result.data
                is Resource.Error -> error = result.message ?: "Failed to send message"
                is Resource.Loading -> Unit
            }
        }

        val message = sent
        return if (message != null) {
            store.remove(item.clientMessageId)
            // В кэше заменяем локальный пузырь серверным: иначе при повторном
            // входе в чат рядом окажутся «отправляется» и серверная копия.
            val cached = ChatMessagesCache.get(item.chatUuid)
            if (cached.any { it.messageUuid == item.localMessageUuid }) {
                ChatMessagesCache.put(
                    item.chatUuid,
                    (cached.filterNot { it.messageUuid == item.localMessageUuid } + message)
                        .sortedWith(compareBy(nullsLast()) { it.createdAt })
                )
            }
            _events.emit(OutboxEvent.Sent(item, message))
            true
        } else {
            store.update(item.copy(attempts = item.attempts + 1))
            _events.emit(OutboxEvent.Failed(item, error ?: "Failed to send message"))
            false
        }
    }

    private companion object {
        /** Повтор при сетевых сбоях; при появлении сети сработает раньше. */
        const val RETRY_DELAY_MS = 5 * 60 * 1000L
    }
}
