package com.example.muzea.utils

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.time.Instant
import java.util.UUID

/** Одна запись об ошибке в локальном буфере. */
data class ErrorEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val time: String = Instant.now().toString(),
    val level: String,
    val tag: String?,
    val message: String?,
    val stack: String?,
    val appVersion: String,
    val versionCode: Int,
    val buildType: String,
    val device: String,
    val os: String,
    val userId: String? = null
)

/**
 * Локальный буфер ошибок: JSON-lines в filesDir.
 *
 * Не cacheDir — «Очистить кэш» и очистка кэша при выходе не должны стирать
 * диагностику. Лимит 5 МБ: при переполнении заменяем самые старые записи.
 * Удаляем запись только после успешной отправки на сервер.
 */
class ErrorStore(context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, FILE_NAME)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    @Synchronized
    fun append(event: ErrorEvent) = synchronized(lock) {
        if (file.length() >= MAX_BYTES) trim()
        file.appendText(gson.toJson(event) + "\n")
    }

    @Synchronized
    fun pending(limit: Int = 100): List<ErrorEvent> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        file.useLines { lines ->
            lines.mapNotNull { line ->
                runCatching { gson.fromJson(line, ErrorEvent::class.java) }.getOrNull()
            }.take(limit).toList()
        }
    }

    /** Удаляет только реально отправленные записи. */
    @Synchronized
    fun remove(eventIds: Set<String>) = synchronized(lock) {
        if (!file.exists() || eventIds.isEmpty()) return
        val keep = file.useLines { lines ->
            lines.filter { line ->
                val event = runCatching { gson.fromJson(line, ErrorEvent::class.java) }.getOrNull()
                event == null || event.eventId !in eventIds
            }.toList()
        }
        writeAtomically(keep)
    }

    var lastUploadedAt: String?
        get() = prefs.getString(KEY_LAST_UPLOAD, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_UPLOAD, value).apply()
        }

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0

    private fun trim() {
        // Оставляем примерно 4 МБ свежих записей; режем по строкам.
        val lines = file.useLines { it.toList() }
        val keep = ArrayList<String>(lines.size)
        var bytes = 0L
        for (line in lines.asReversed()) {
            val size = line.toByteArray().size + 1
            if (bytes + size > TRIM_TARGET_BYTES) break
            keep.add(line)
            bytes += size
        }
        writeAtomically(keep.asReversed())
    }

    private fun writeAtomically(lines: List<String>) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(lines.joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n"))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "error_events.jsonl"
        const val PREFS = "error_reporting"
        const val KEY_LAST_UPLOAD = "last_uploaded_at"
        const val MAX_BYTES = 5L * 1024 * 1024
        const val TRIM_TARGET_BYTES = 4L * 1024 * 1024
    }
}
