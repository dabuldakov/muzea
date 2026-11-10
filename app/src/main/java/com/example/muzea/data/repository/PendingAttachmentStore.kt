package com.example.muzea.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Вложение, ожидающее загрузки, в том виде, в котором переживает перезапуск. */
data class StoredPendingAttachment(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val type: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val uploadedUuid: String? = null
)

/**
 * Постоянная очередь ещё не загруженных вложений по чатам.
 *
 * Файлы уже лежат в filesDir (MediaFileReader), поэтому здесь храним только
 * метаданные. После перезапуска очередь восстанавливается и догружается.
 */
@Singleton
class PendingAttachmentStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val gson = Gson()
    private val fileName = "pending_attachments.json"
    private val file = File(context.filesDir, fileName)
    private val type = object : TypeToken<MutableMap<String, List<StoredPendingAttachment>>>() {}.type
    private val lock = Any()

    @Synchronized
    fun load(chatUuid: String): List<StoredPendingAttachment> = synchronized(lock) {
        val all = read()
        val items = all[chatUuid].orEmpty().filter {
            File(it.filePath).exists() && File(it.filePath).length() > 0
        }
        if (items.size != (all[chatUuid]?.size ?: 0)) {
            if (items.isEmpty()) all.remove(chatUuid) else all[chatUuid] = items
            write(all)
        }
        items
    }

    @Synchronized
    fun save(chatUuid: String, items: List<StoredPendingAttachment>) = synchronized(lock) {
        val all = read()
        if (items.isEmpty()) all.remove(chatUuid) else all[chatUuid] = items
        write(all)
    }

    @Synchronized
    fun clear(chatUuid: String) = synchronized(lock) {
        val all = read()
        all.remove(chatUuid)
        write(all)
    }

    private fun read(): MutableMap<String, List<StoredPendingAttachment>> = runCatching {
        if (!file.exists()) mutableMapOf()
        else gson.fromJson<MutableMap<String, List<StoredPendingAttachment>>>(file.readText(), type)
            ?: mutableMapOf()
    }.getOrElse { mutableMapOf() }

    private fun write(all: Map<String, List<StoredPendingAttachment>>) {
        val temp = File(file.parentFile, "$fileName.tmp")
        temp.writeText(gson.toJson(all, type))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}
