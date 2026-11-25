package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Тело запроса на создание комментария к новости. */
data class CreateNewsCommentRequest(
    @SerializedName("content")
    val content: String
)
