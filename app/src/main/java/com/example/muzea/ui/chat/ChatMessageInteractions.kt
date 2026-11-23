package com.example.muzea.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.muzea.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Быстрые действия над сообщением вне меню: копирование текста и жалоба.
 */
internal class ChatMessageInteractions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sendReport: suspend (messageUuid: String, reason: String) -> Boolean
) {

    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        Toast.makeText(context, R.string.message_copied, Toast.LENGTH_SHORT).show()
    }

    fun report(messageUuid: String, reason: String) {
        scope.launch {
            val ok = sendReport(messageUuid, reason)
            Toast.makeText(
                context,
                if (ok) R.string.report_sent else R.string.report_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
