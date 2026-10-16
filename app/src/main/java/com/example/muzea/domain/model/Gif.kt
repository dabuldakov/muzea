package com.example.muzea.domain.model

/** Гифка (результат поиска у провайдера или сохранённая в переписке). */
data class Gif(
    val provider: String,
    val providerGifId: String,
    val title: String?,
    val url: String,
    val previewUrl: String?,
    val width: Int?,
    val height: Int?,
    /** Наш ключ гифки (для «Мои»/«Этот чат»); у результатов поиска может быть null. */
    val gifKey: String? = null
)
