package com.example.muzea.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.example.muzea.databinding.ItemPendingAttachmentBinding

/**
 * Список прикреплённых, но ещё не отправленных файлов: имя, прогресс,
 * ошибка, удаление и повтор. Отделён от фрагмента, чтобы экран чата не
 * занимался разметкой вложений.
 */
class PendingAttachmentsView(
    private val inflater: LayoutInflater,
    private val scroll: View,
    private val container: LinearLayout,
    private val onRemove: (String) -> Unit,
    private val onRetry: (String) -> Unit
) {

    fun render(pending: List<PendingAttachment>) {
        scroll.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        container.removeAllViews()
        pending.forEach { item ->
            val row = ItemPendingAttachmentBinding.inflate(inflater, container, false)
            row.tvName.text = item.name
            row.pbUploading.visibility =
                if (item.isUploaded || item.error != null) View.GONE else View.VISIBLE
            row.pbUploading.isIndeterminate = item.progress <= 0
            row.pbUploading.progress = item.progress
            row.tvError.visibility = if (item.error != null) View.VISIBLE else View.GONE
            row.tvError.text = item.error
            row.ivRemove.setOnClickListener { onRemove(item.id) }
            if (item.error != null) {
                row.root.setOnClickListener { onRetry(item.id) }
            }
            container.addView(row.root)
        }
    }
}
