package com.example.muzea.ui.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.muzea.R
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.utils.AttachmentOpenPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Открытие скачанного вложения системным приложением.
 *
 * Исполняемые файлы (APK/EXE и подобные) не запускаем: файл уже сохранён,
 * но приложение не должно провоцировать установку из чата.
 */
internal class DownloadedFileOpener(
    private val context: Context,
    private val isAdded: () -> Boolean,
    private val startActivity: (Intent) -> Unit
) {

    fun open(publicUri: Uri?, file: File, attachment: MessageAttachment) {
        if (AttachmentOpenPolicy.isExecutable(attachment.fileName, attachment.mimeType)) {
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.attachment_executable_blocked)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val uri = publicUri ?: runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, R.string.attachment_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val mime = attachment.mimeType?.takeIf { it.isNotBlank() } ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Часть прошивок (Transsion/Infinix) не передаёт грант только по
            // флагу — дублируем URI в ClipData.
            clipData = ClipData.newRawUri("", uri)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No viewer for $mime", e)
            showOpenFallback(uri, mime, publicUri != null)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to open $mime", e)
            showOpenFallback(uri, mime, publicUri != null)
        }
    }

    /** Нет обработчика для файла: предлагаем «Поделиться» и не теряем его. */
    private fun showOpenFallback(uri: Uri, mime: String, savedToDownloads: Boolean) {
        if (!isAdded()) return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.attachment_open_failed)
            .setMessage(
                if (savedToDownloads) R.string.attachment_saved_downloads
                else R.string.attachment_saved_private
            )
            .setPositiveButton(R.string.action_share) { _, _ ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("", uri)
                }
                runCatching {
                    startActivity(Intent.createChooser(share, null))
                }
            }
            .setNegativeButton(R.string.action_later, null)
            .show()
    }

    private companion object {
        const val TAG = "DownloadedFileOpener"
    }
}
