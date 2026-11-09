package com.example.muzea.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
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
    ): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishMediaStore(context, source, displayName, mimeType)
        } else {
            publishLegacy(context, source, displayName)
        }
    }.getOrNull()

    private fun publishMediaStore(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            val copied = resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            if (copied > 0) uri else null
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun publishLegacy(context: Context, source: File, displayName: String): Uri? {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = uniqueTarget(dir, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.length() <= 0) return null
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", target
        )
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
