package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Тело запроса на редактирование сообщения. Сервер меняет только текст. */
data class EditMessageRequest(
    @SerializedName("text")
    val text: String
)
