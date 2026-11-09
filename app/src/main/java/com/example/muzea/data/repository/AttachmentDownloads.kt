package com.example.muzea.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Публикация скачанного файла в системные «Загрузки».
 *
 * На Android 10+ пишем через MediaStore.Downloads — без разрешений, файл
 * виден в файловом менеджере. На Android 7–9 нужен WRITE_EXTERNAL_STORAGE;
 * если его нет, файл остаётся в приватной папке приложения.
 */
object AttachmentDownloads {

    fun publish(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String
    ): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishMediaStore(context, source, displayName, mimeType)
        } else {
            publishLegacy(context, source, displayName)
        }
    }.getOrDefault(false)

    private fun publishMediaStore(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            val copied = resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            copied > 0
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    private fun publishLegacy(context: Context, source: File, displayName: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return false
        val target = uniqueTarget(dir, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.length() > 0
    }

    private fun uniqueTarget(dir: File, displayName: String): File {
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        var candidate = File(dir, displayName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }
}
