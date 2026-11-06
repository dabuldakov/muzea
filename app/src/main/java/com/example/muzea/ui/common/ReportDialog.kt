package com.example.muzea.ui.common

import android.content.Context
import com.example.muzea.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Единый диалог выбора причины жалобы для всех UGC-разделов
 * (чат, новости, видео). Значение передаётся в API как enum reason.
 */
object ReportDialog {

    private val reasons = listOf(
        R.string.report_reason_spam to "SPAM",
        R.string.report_reason_abuse to "ABUSE",
        R.string.report_reason_violence to "VIOLENCE",
        R.string.report_reason_adult to "ADULT",
        R.string.report_reason_illegal to "ILLEGAL",
        R.string.report_reason_other to "OTHER"
    )

    fun show(context: Context, onReason: (String) -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.report_reason_title)
            .setItems(reasons.map { context.getString(it.first) }.toTypedArray()) { _, which ->
                onReason(reasons[which].second)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
