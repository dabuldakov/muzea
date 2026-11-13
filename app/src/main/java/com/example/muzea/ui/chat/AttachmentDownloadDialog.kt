package com.example.muzea.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.example.muzea.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Диалог «Скачивается» с прогрессом и отменой. */
class AttachmentDownloadDialog(
    context: Context,
    inflater: LayoutInflater,
    private val onCancel: () -> Unit
) : DownloadProgress {

    private val content: View = inflater.inflate(R.layout.dialog_download_progress, null)
    private val progress: ProgressBar = content.findViewById(R.id.downloadProgress)
    private val percent: TextView = content.findViewById(R.id.downloadPercent)
    private val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.attachment_downloading)
        .setView(content)
        .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
        .setCancelable(false)
        .create()

    override val isShowing: Boolean
        get() = dialog.isShowing

    override fun show() {
        dialog.show()
    }

    override fun update(percent: Int) {
        progress.progress = percent
        this.percent.text = "$percent%"
    }

    override fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }
}
