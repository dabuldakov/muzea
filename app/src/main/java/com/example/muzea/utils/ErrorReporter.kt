package com.example.muzea.utils

import android.content.Context
import android.os.Build
import com.example.muzea.BuildConfig
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.ErrorEventRequest
import com.example.muzea.data.model.ErrorReportRequest
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.retryOnceAfterUnauthorized
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Диагностика ошибок: кольцевой буфер + отправка новых записей при старте.
 *
 * Краши перехватываются глобальным handler'ом и пишутся локально; отправка —
 * только при следующем запуске, когда сеть доступна. Перехваченные ошибки
 * пишутся через [logCaught]. После успешного 2xx записи удаляются.
 */
@Singleton
class ErrorReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ChatApiService,
    private val auth: ChatAuthManager,
    private val tokenManager: TokenManager
) {
    private val store = ErrorStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    init {
        instance = this
    }

    fun install() {
        if (previousHandler != null) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(
                level = "CRASH",
                tag = thread.name,
                message = error.message,
                stack = error.stackTraceToString()
            )
            previousHandler?.uncaughtException(thread, error)
        }
    }

    /** Отправка накопленного в фоне; вызывается при старте приложения. */
    fun uploadPending() {
        scope.launch { drain() }
    }

    fun recordCaught(tag: String, error: Throwable) {
        record("ERROR", tag, error.message, error.stackTraceToString())
    }

    private fun record(level: String, tag: String?, message: String?, stack: String?) {
        runCatching {
            store.append(
                ErrorEvent(
                    level = level,
                    tag = tag,
                    message = message?.let(ErrorSanitizer::sanitize)?.take(MAX_MESSAGE),
                    stack = stack?.let(ErrorSanitizer::sanitize)?.take(MAX_STACK),
                    appVersion = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    buildType = if (BuildConfig.DEBUG) "debug" else "release",
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    os = "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
                    userId = tokenManager.getChatUserUuid()
                )
            )
        }
    }

    private suspend fun drain() {
        repeat(MAX_BATCHES) {
            val batch = store.pending(BATCH_SIZE)
            if (batch.isEmpty()) return
            val response = runCatching {
                auth.retryOnceAfterUnauthorized {
                    api.uploadErrorEvents(
                        ErrorReportRequest(
                            events = batch.map { it.toRequest() },
                            overflowed = store.overflowed
                        )
                    )
                }
            }.getOrNull()
            if (response?.isSuccessful != true) return
            store.remove(batch.map { it.eventId }.toSet())
            store.lastUploadedAt = batch.maxOfOrNull { it.time } ?: store.lastUploadedAt
            // Всё ушло — буфер больше не переполнен.
            if (store.pending(1).isEmpty()) store.overflowed = false
        }
    }

    private fun ErrorEvent.toRequest() = ErrorEventRequest(
        eventId = eventId,
        time = time,
        level = level,
        tag = tag,
        message = message,
        stack = stack,
        appVersion = appVersion,
        versionCode = versionCode,
        buildType = buildType,
        device = device,
        os = os,
        userId = userId
    )

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_BATCHES = 5
        private const val MAX_MESSAGE = 1_000
        private const val MAX_STACK = 20_000

        @Volatile
        private var instance: ErrorReporter? = null

        /** Для перехваченных ошибок из любого места без DI-связи. */
        fun logCaught(tag: String, error: Throwable) {
            instance?.recordCaught(tag, error)
        }
    }
}
