package com.example.muzea.domain.model

/** Когда конкретное сообщение доставлено и прочитано участником. */
data class MessageReceiptTime(
    val userUuid: String?,
    val deliveredAt: String?,
    val readAt: String?
)
