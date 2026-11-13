package com.example.muzea.ui.chat

import com.example.muzea.R
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageDeliveryStatus

/** Собирает список действий над сообщением. Чистая логика, без UI. */
object MessageActionsBuilder {

    data class Result(
        val actions: List<MessageActionSpec>,
        val showStatus: Boolean
    )

    fun build(
        message: Message,
        myUserUuid: String?,
        isGroupChat: Boolean,
        deliveryStatus: MessageDeliveryStatus? = null
    ): Result {
        if (message.isDeleted) return Result(emptyList(), showStatus = false)

        val isMine = message.senderUuid != null && message.senderUuid == myUserUuid
        // После серверного эха uuid остаётся локальным (чтобы DiffUtil не
        // пересоздавал строку), поэтому признак серверного сообщения — messageId.
        val isServerMessage = message.messageId != null
        val actions = mutableListOf<MessageActionSpec>()

        actions += MessageActionSpec(
            MessageActionsBottomSheet.ACTION_REACT,
            R.drawable.ic_reaction,
            R.string.action_react
        )

        val failedLocal = message.messageUuid.startsWith("local-") &&
            deliveryStatus == MessageDeliveryStatus.FAILED
        if (failedLocal) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_RETRY,
                R.drawable.ic_action_retry,
                R.string.action_retry
            )
        }

        if (!message.text.isNullOrBlank()) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_COPY,
                R.drawable.ic_action_copy,
                R.string.action_copy
            )
        }

        // Ответить можно на любое серверное сообщение, в том числе на своё.
        if (isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_REPLY,
                R.drawable.ic_action_reply,
                R.string.action_reply
            )
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_SELECT,
                R.drawable.ic_action_select,
                R.string.select_messages
            )
        }

        // Редактировать можно свои серверные сообщения с текстом или вложением:
        // подпись к медиа можно и добавить, и изменить.
        val canEdit = isMine && isServerMessage &&
            (!message.text.isNullOrBlank() || message.attachments.isNotEmpty())
        if (canEdit) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_EDIT,
                R.drawable.ic_action_edit,
                R.string.action_edit
            )
        }

        // Удалить можно только своё серверное сообщение.
        if (isMine && isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_DELETE,
                R.drawable.ic_action_delete,
                R.string.action_delete
            )
        }

        // Пересылать и закреплять можно любое серверное не удалённое сообщение.
        if (isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_FORWARD,
                R.drawable.ic_action_forward,
                R.string.action_forward
            )
            if (message.isPinned) {
                actions += MessageActionSpec(
                    MessageActionsBottomSheet.ACTION_UNPIN,
                    R.drawable.ic_action_pin,
                    R.string.action_unpin
                )
            } else {
                actions += MessageActionSpec(
                    MessageActionsBottomSheet.ACTION_PIN,
                    R.drawable.ic_action_pin,
                    R.string.action_pin
                )
            }
        }

        if (!isMine && isServerMessage) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_REPORT,
                R.drawable.ic_action_report,
                R.string.action_report
            )
        }

        if (!isMine && message.senderUuid != null) {
            actions += MessageActionSpec(
                MessageActionsBottomSheet.ACTION_BLOCK,
                R.drawable.ic_action_block,
                R.string.action_block
            )
        }

        // В личных чатах статус читается по галочкам; список участников
        // разворачиваем только в группах.
        val showStatus = isMine && isServerMessage && isGroupChat
        return Result(actions, showStatus)
    }
}
