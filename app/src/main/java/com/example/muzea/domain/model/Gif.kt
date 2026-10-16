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
    /** MP4-вариант (для плавного беззвучного воспроизведения); может отсутствовать. */
    val mp4Url: String? = null,
    /** Наш ключ гифки (для «Мои»/«Этот чат»); у результатов поиска может быть null. */
    val gifKey: String? = null
)
