package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/**
 * Точные отметки одного сообщения по участнику.
 *
 * Время может быть null: для истории до появления отметок его нет, и клиент
 * показывает статус без времени, а не время последнего прочтения чата.
 */
data class MessageReceiptTimeResponse(
    @SerializedName("userUuid") val userUuid: String?,
    @SerializedName("deliveredAt") val deliveredAt: String?,
    @SerializedName("readAt") val readAt: String?
)
