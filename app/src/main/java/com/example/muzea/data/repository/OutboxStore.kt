package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.domain.model.MessageAttachment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Сообщение, ожидающее отправки. Переживает перезапуск приложения. */
data class OutboxItem(
    val clientMessageId: String,
    val localMessageUuid: String,
    val chatUuid: String,
    val text: String,
    val messageType: String,
    val replyToMessageUuid: String?,
    val attachments: List<MessageAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)

/**
 * Файловая очередь исходящих сообщений.
 *
 * Не Room: зависимость не нужна, объём — единицы сообщений. Запись атомарная
 * (temp + rename), чтобы падение процесса не оставило битый JSON.
 */
@Singleton
class OutboxStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val gson = Gson()
    private val file = File(context.filesDir, FILE_NAME)
    private val type = object : TypeToken<MutableList<OutboxItem>>() {}.type
    private val lock = Any()

    @Synchronized
    fun all(): List<OutboxItem> = synchronized(lock) { read() }

    @Synchronized
    fun forChat(chatUuid: String): List<OutboxItem> =
        all().filter { it.chatUuid == chatUuid }

    @Synchronized
    fun add(item: OutboxItem) = synchronized(lock) {
        val items = read().filterNot { it.clientMessageId == item.clientMessageId }
        write(items + item)
    }

    @Synchronized
    fun update(item: OutboxItem) = synchronized(lock) {
        write(read().map { if (it.clientMessageId == item.clientMessageId) item else it })
    }

    @Synchronized
    fun remove(clientMessageId: String) = synchronized(lock) {
        write(read().filterNot { it.clientMessageId == clientMessageId })
    }

    @Synchronized
    fun clear() = synchronized(lock) { write(emptyList()) }

    @Synchronized
    fun removeForChat(chatUuid: String, localMessageUuid: String) = synchronized(lock) {
        write(read().filterNot {
            it.chatUuid == chatUuid && it.localMessageUuid == localMessageUuid
        })
    }

    private fun read(): MutableList<OutboxItem> = runCatching {
        if (!file.exists()) mutableListOf()
        else gson.fromJson<MutableList<OutboxItem>>(file.readText(), type) ?: mutableListOf()
    }.getOrElse { mutableListOf() }

    private fun write(items: List<OutboxItem>) {
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(gson.toJson(items, type))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "outbox.json"
    }
}
