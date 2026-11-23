package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.NetworkQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ритм опроса переписки: полная страница, дельта по времени и периодическая
 * сверка правок. Пауза на невидимом экране и немедленный перезапуск после
 * возврата сети — чтобы не тратить батарею и не ждать offline-задержку.
 */
internal class ChatPollingController(
    private val scope: CoroutineScope,
    private val networkMonitor: NetworkMonitor,
    private val loadOnce: suspend (incremental: Boolean) -> Boolean,
    private val loadUpdatedOnce: suspend () -> Boolean,
    private val onNetworkRestored: () -> Unit
) {

    private var job: Job? = null
    private var paused = false
    private var pendingFullSync = false

    /** Время последнего серверного сообщения — курсор дельты. */
    var lastServerCreatedAt: String? = null

    /** До какого времени (UTC, формат сервера) уже сверены правки. */
    var lastUpdatedCheckAt: String? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            var iteration = 0
            while (isActive) {
                val quality = networkMonitor.status.value.quality
                if (paused || quality == NetworkQuality.OFFLINE) {
                    delay(OFFLINE_POLL_INTERVAL_MS)
                    continue
                }

                // Полная страница — только когда показать ещё нечего или после
                // возврата на экран; в остальное время хватает дельты.
                val fullSync = lastServerCreatedAt == null || pendingFullSync
                if (fullSync) pendingFullSync = false
                val success = loadOnce(!fullSync)
                if (success && !fullSync && iteration % UPDATED_SYNC_EVERY == 0) {
                    // Раз в UPDATED_SYNC_EVERY опросов подтягиваем только
                    // изменённые сообщения: правки, закреп, снятые реакции.
                    loadUpdatedOnce()
                }
                iteration++

                val delayMs = when (quality) {
                    NetworkQuality.GOOD ->
                        if (success) POLL_INTERVAL_MS else ERROR_POLL_INTERVAL_MS
                    NetworkQuality.WEAK -> WEAK_POLL_INTERVAL_MS
                    NetworkQuality.OFFLINE -> OFFLINE_POLL_INTERVAL_MS
                }
                delay(delayMs)
            }
        }
    }

    /** Пауза опроса, когда экран не виден: не тратим сеть и батарею. */
    fun pause() {
        paused = true
        // Не оставляем job спать в delay(60s) за спиной: при возврате
        // на экран запускаем его заново с немедленным запросом.
        job?.cancel()
        job = null
    }

    fun resume() {
        paused = false
        job?.cancel()
        // Вернулись на экран — один раз сверяемся полной страницей: дельта
        // и правки за это время могли выпасть из окна сверки.
        pendingFullSync = true
        start()
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * После блокировки/авиарежима сеть возвращается уже после onStart.
     * Перезапускаем polling, чтобы не ждать 60-секундный offline-delay.
     */
    fun observeNetworkWakeup() {
        scope.launch {
            var wasOnline = networkMonitor.status.value.online
            networkMonitor.status.collect { status ->
                if (status.online && !wasOnline && !paused) {
                    job?.cancel()
                    pendingFullSync = true
                    start()
                    onNetworkRestored()
                }
                wasOnline = status.online
            }
        }
    }

    fun rememberServerTime(messages: List<Message>) {
        val latest = messages.asSequence()
            .filter { it.messageId != null }
            .mapNotNull { it.createdAt }
            .maxOrNull()
        // +1 секунда: сервер сравнивает `created_at > since` без дробной части,
        // поэтому иначе последнее сообщение приходило бы в каждой дельте.
        // Сообщение, пришедшее в ту же секунду уже после запроса, подхватит
        // сверка /updated — она идёт раз в 15 секунд.
        LocalTimeFormatter.nextServerSecond(latest)?.let { lastServerCreatedAt = it }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
        const val ERROR_POLL_INTERVAL_MS = 15_000L
        const val WEAK_POLL_INTERVAL_MS = 15_000L
        const val OFFLINE_POLL_INTERVAL_MS = 60_000L
        /** Каждая пятая итерация опроса (≈15 с) — сверка только изменённых. */
        const val UPDATED_SYNC_EVERY = 5
    }
}
