package com.example.muzea.ui.chat

import com.example.muzea.domain.model.ChatReceipt
import com.example.muzea.domain.model.Message

/**
 * Строки блока «Детали»: кто поставил реакции и когда. Вынесено из фрагмента,
 * чтобы логика раскладки по людям проверялась тестами без UI.
 */
object MessageDetailsRows {

    fun reactions(
        message: Message,
        receiptsByUuid: Map<String, ChatReceipt?>,
        myUserUuid: String?,
        youLabel: String,
        unknownLabel: String,
        formatTime: (String?) -> String
    ): List<MessageStatusRow> =
        message.reactions
            .flatMap { reaction -> reaction.users.map { it.userUuid to it.at } }
            .mapNotNull { (uuid, at) -> uuid?.let { it to at } }
            .distinctBy { it.first }
            .map { (uuid, at) ->
                val receipt = receiptsByUuid[uuid]
                MessageStatusRow(
                    userUuid = uuid,
                    name = receipt?.displayName()
                        ?: if (uuid == myUserUuid) youLabel else unknownLabel,
                    avatarUrl = receipt?.avatarUrl,
                    status = STATUS_REACTED,
                    at = formatTime(at).takeIf { it.isNotBlank() }
                )
            }

    const val STATUS_REACTED = "REACTED"
}
