package com.example.muzea.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.muzea.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Подготовленный к загрузке файл: копия в filesDir + метаданные. */
data class PreparedAttachment(
    val file: File,
    val thumbnail: File?,
    val name: String,
    val size: Long,
    val mimeType: String,
    /** IMAGE / VIDEO / AUDIO / DOCUMENT */
    val type: String,
    val width: Int?,
    val height: Int?,
    val duration: Int?
)

/**
 * Готовит выбранный в системном пикере файл к загрузке в чат.
 *
 * Копирует содержимое в filesDir (URI живёт только пока открыт экран),
 * определяет MIME/размер/тип, достаёт размеры и длительность, а для видео —
 * кадр-превью. Ограничения те же, что на сервере, чтобы не гонять зря трафик.
 *
 * Черновик лежит в filesDir, а не в cacheDir: систему (в т.ч. Samsung Device
 * Care) и кнопку «Очистить кэш» не должно ломать долгую загрузку видео.
 * Старые черновики подчищаем при подготовке следующего файла.
 */
@Singleton
class MediaFileReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun prepare(uri: Uri): PreparedAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: mimeByExtension(uri.lastPathSegment)
            ?: throw IllegalArgumentException(context.getString(R.string.attachment_unknown_type))

        val meta = queryMeta(uri)
        // Провайдер может не отдавать дескриптор (облачные/битые URI):
        // размер тогда неизвестен, но файл всё равно пробуем прочитать.
        val size = meta.second ?: runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: 0L
        val name = sanitizeName(meta.first ?: uri.lastPathSegment ?: "file")
        val type = typeOf(mime, name)
        checkSize(type, size)

        val dir = uploadDir().apply { mkdirs() }
        sweepStale(dir)
        val target = File(dir, "${System.nanoTime()}_$name")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException(context.getString(R.string.attachment_cannot_read))

        var width: Int? = null
        var height: Int? = null
        var duration: Int? = null
        var thumbnail: File? = null

        when (type) {
            "IMAGE" -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.absolutePath, options)
                width = options.outWidth.takeIf { it > 0 }
                height = options.outHeight.takeIf { it > 0 }
            }
            "VIDEO" -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(target.absolutePath)
                    width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    duration = retriever.durationSeconds()
                    thumbnail = retriever.frameThumbnail(dir)
                } finally {
                    runCatching { retriever.release() }
                }
            }
            "AUDIO" -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(target.absolutePath)
                    duration = retriever.durationSeconds()
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }

        PreparedAttachment(
            file = target,
            thumbnail = thumbnail,
            name = name,
            size = target.length().takeIf { it > 0 } ?: size,
            mimeType = mime,
            type = type,
            width = width,
            height = height,
            duration = duration
        )
    }

    private fun uploadDir(): File = File(context.filesDir, UPLOAD_DIR)

    /** Убирает черновики, забытые после падения процесса или отмены. */
    private fun sweepStale(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_MS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }

    fun cleanup(prepared: PreparedAttachment?) {
        prepared?.file?.delete()
        prepared?.thumbnail?.delete()
    }

    private fun queryMeta(uri: Uri): Pair<String?, Long?> {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null to null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    null
                }
                name to size
            } ?: (null to null)
        } catch (e: Exception) {
            null to null
        }
    }

    private fun typeOf(mime: String, name: String): String = when {
        mime.startsWith("image/") -> "IMAGE"
        mime.startsWith("video/") -> "VIDEO"
        mime.startsWith("audio/") -> "AUDIO"
        else -> "DOCUMENT"
    }.also {
        if (it == "DOCUMENT" && !isAllowedDocument(name)) {
            throw IllegalArgumentException(context.getString(R.string.attachment_type_not_allowed))
        }
    }

    private fun checkSize(type: String, size: Long) {
        val limit = when (type) {
            "IMAGE" -> 50L * 1024 * 1024
            "VIDEO" -> 200L * 1024 * 1024
            "AUDIO" -> 50L * 1024 * 1024
            else -> 100L * 1024 * 1024
        }
        if (size > limit) {
            throw IllegalArgumentException(context.getString(R.string.attachment_size_limit, limit / (1024 * 1024)))
        }
    }

    private fun isAllowedDocument(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in DOCUMENT_EXTENSIONS
    }

    private fun sanitizeName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_").trim()
        return (cleaned.takeIf { it.isNotEmpty() } ?: "file").take(120)
    }

    private fun mimeByExtension(name: String?): String? {
        val extension = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: return null
        return MIME_BY_EXTENSION[extension]
    }

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int? =
        extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 }

    private fun MediaMetadataRetriever.durationSeconds(): Int? =
        extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.let { (it / 1000).toInt() }
            ?.takeIf { it > 0 }

    private fun MediaMetadataRetriever.frameThumbnail(dir: File): File? {
        val frame: Bitmap = getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return null
        val file = File(dir, "thumb_${System.nanoTime()}.jpg")
        return try {
            file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            file.takeIf { it.length() > 0 }
        } catch (e: Exception) {
            file.delete()
            null
        } finally {
            frame.recycle()
        }
    }

    private companion object {
        const val UPLOAD_DIR = "chat_uploads"
        const val STALE_MS = 24L * 60 * 60 * 1000

        val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "txt", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
        )

        val MIME_BY_EXTENSION = mapOf(
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
            "webp" to "image/webp", "mp4" to "video/mp4", "mov" to "video/quicktime",
            "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac",
            "ogg" to "audio/ogg", "opus" to "audio/ogg", "wav" to "audio/wav",
            "pdf" to "application/pdf", "txt" to "text/plain", "csv" to "text/csv",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }
}
