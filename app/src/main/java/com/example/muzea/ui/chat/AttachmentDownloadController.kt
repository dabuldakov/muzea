package com.example.muzea.ui.chat

import android.net.Uri
import com.example.muzea.R
import com.example.muzea.domain.model.MessageAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Скачивание вложения: прогресс, отмена, публикация в «Загрузки» и открытие.
 * UI отдаётся через фабрику [createProgress], поэтому контроллер тестируется.
 */
class AttachmentDownloadController(
    private val scope: CoroutineScope,
    private val createProgress: (onCancel: () -> Unit) -> DownloadProgress,
    private val download: suspend (MessageAttachment, (Int) -> Unit) -> File?,
    private val publish: suspend (File, String?, String?) -> Uri?,
    private val open: (Uri?, File, MessageAttachment) -> Unit,
    private val toast: (Int) -> Unit
) {

    private var job: Job? = null

    fun start(attachment: MessageAttachment) {
        if (job?.isActive == true) return
        val progress = createProgress { job?.cancel() }
        progress.show()
        job = scope.launch {
            try {
                val file = download(attachment) { value -> progress.update(value) }
                if (progress.isShowing) progress.dismiss()
                if (file == null) {
                    toast(R.string.attachment_download_failed)
                    return@launch
                }
                val publicUri = publish(file, attachment.fileName, attachment.mimeType)
                toast(
                    if (publicUri != null) R.string.attachment_saved_downloads
                    else R.string.attachment_saved_private
                )
                open(publicUri, file, attachment)
            } finally {
                if (progress.isShowing) progress.dismiss()
                job = null
            }
        }
    }
}
