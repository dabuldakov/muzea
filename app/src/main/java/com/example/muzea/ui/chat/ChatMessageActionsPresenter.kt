package com.example.muzea.ui.chat

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.domain.model.MessageReceiptTime
import com.example.muzea.utils.LocalTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Плавающая карточка действий по сообщению и запасная шторка, если строка
 * ещё не разложена. Строки «Деталей» собираются здесь же.
 */
internal class ChatMessageActionsPresenter(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val recyclerView: RecyclerView,
    private val adapter: () -> MessageAdapter?,
    private val myUserUuid: () -> String?,
    private val isGroupChat: () -> Boolean,
    private val deliveryStatus: (String) -> MessageDeliveryStatus?,
    private val reactionAuthors: (Message) -> List<Pair<String, ChatReceipt?>>,
    private val participantStatuses: (Message) -> List<ParticipantDeliveryStatus>,
    private val receiptTime: (Message, String) -> MessageReceiptTime?,
    private val strings: (Int) -> String,
    private val topLimit: () -> Int,
    private val lastTap: () -> Pair<Int?, Int?>,
    private val scope: CoroutineScope,
    private val onAction: (String, String) -> Unit,
    private val onReaction: (Message, String) -> Unit,
    private val loadDetails: suspend (Message) -> Unit,
    private val onDismissed: () -> Unit
) {

    private var popup: MessageActionsPopup? = null

    fun isShowing(): Boolean = popup?.isShowing() == true

    fun dismiss() {
        popup?.dismiss()
    }

    fun show(message: Message) {
        val built = MessageActionsBuilder.build(
            message = message,
            myUserUuid = myUserUuid(),
            isGroupChat = isGroupChat(),
            deliveryStatus = deliveryStatus(message.messageUuid)
        )
        val actions = built.actions
        val showStatus = built.showStatus
        if (actions.isEmpty() && !showStatus) return

        val current = adapter() ?: return
        val position = current.currentList.indexOfFirst { it.messageUuid == message.messageUuid }
        val holder = if (position >= 0) {
            recyclerView.findViewHolderForAdapterPosition(position)
        } else {
            null
        }
        val anchor = holder?.itemView?.findViewById<View>(R.id.tvMessage)
            ?.takeIf { it.visibility == View.VISIBLE }
            ?: holder?.itemView
        if (anchor != null) {
            popup?.dismiss()
            val created = MessageActionsPopup(
                context = context,
                messageUuid = message.messageUuid,
                actions = actions,
                statusRows = if (showStatus) statusRows(message) else emptyList(),
                reactions = message.reactions,
                reactionRows = reactionRows(message),
                topLimit = topLimit(),
                showStatus = showStatus,
                onAction = { actionId -> onAction(message.messageUuid, actionId) },
                onReaction = { emoji -> onReaction(message, emoji) },
                onDismissed = onDismissed,
                // Квитанции обновляются раз в 30 секунд; на раскрытие «Деталей»
                // тянем их сразу, иначе список читателей отстаёт.
                onDetailsExpanded = {
                    scope.launch {
                        loadDetails(message)
                        if (popup?.isShowing() == true) {
                            popup?.refreshDetails(
                                statusRows = if (showStatus) statusRows(message) else emptyList(),
                                reactionRows = reactionRows(message)
                            )
                        }
                    }
                }
            )
            popup = created
            val (tapX, tapY) = lastTap()
            created.show(anchor, tapX = tapX, tapY = tapY)
        } else {
            // Строка ещё не разложена — показываем старую шторку.
            MessageActionsBottomSheet.newInstance(
                messageUuid = message.messageUuid,
                actions = actions,
                rows = if (showStatus) statusRows(message) else emptyList(),
                showStatus = showStatus
            ).show(fragmentManager, "message_actions")
        }
    }

    /** Авторы реакций — для «Деталей» на чужих сообщениях. */
    private fun reactionRows(message: Message): List<MessageStatusRow> {
        val receipts = reactionAuthors(message).associate { it.first to it.second }
        return MessageDetailsRows.reactions(
            message = message,
            receiptsByUuid = receipts,
            myUserUuid = myUserUuid(),
            youLabel = strings(R.string.reply_you),
            unknownLabel = strings(R.string.reply_unknown_sender),
            formatTime = { LocalTimeFormatter.format(it) }
        )
    }

    private fun statusRows(message: Message): List<MessageStatusRow> =
        participantStatuses(message).map { row ->
            // Время — только из отметки по этому сообщению. Водяной знак
            // квитанции показывает последнее прочтение чата и для старых
            // сообщений дал бы неверную дату, поэтому fallback нет.
            val exact = row.receipt.userUuid?.let { receiptTime(message, it) }
            val at = if (row.status == MessageDeliveryStatus.READ) {
                exact?.readAt
            } else {
                exact?.deliveredAt
            }
            MessageStatusRow(
                userUuid = row.receipt.userUuid,
                name = row.receipt.displayName(),
                avatarUrl = row.receipt.avatarUrl,
                status = row.status.name,
                at = LocalTimeFormatter.format(at).takeIf { it.isNotBlank() }
            )
        }
}
