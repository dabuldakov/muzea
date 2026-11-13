package com.example.muzea.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Таймер записи голоса/видео: тикает раз в 200 мс, обновляет подпись и
 * останавливает запись по достижении лимита. Отделён от UI и рекордеров.
 */
class RecordingTimer(
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val format: (Long) -> String,
    private val onTick: (String) -> Unit,
    private val onLimit: () -> Unit
) {

    private var job: Job? = null

    var startedAt: Long = 0L
        private set

    fun start(maxMs: Long) {
        stop()
        startedAt = now()
        val start = startedAt
        job = scope.launch {
            while (isActive) {
                val elapsed = now() - start
                onTick(format(elapsed))
                if (elapsed >= maxMs) {
                    onLimit()
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val TICK_MS = 200L
    }
}
