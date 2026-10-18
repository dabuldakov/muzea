package com.example.muzea.data.model

/** Превью ссылки (Open Graph): заголовок, картинка, описание, домен. */
data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?
)
