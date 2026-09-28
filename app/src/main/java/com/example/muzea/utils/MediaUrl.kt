package com.example.muzea.utils

/**
 * Приводит путь медиа к абсолютному URL.
 *
 * Сервер отдаёт imageUrl/thumbnailUrl как полные абсолютные адреса
 * (например "https://api-muzea.su/api/news/image/....jpeg"), а endpoint'ы
 * стриминга — как относительные ("/api/videos/stream/....mp4") или аватары —
 * ("/api/avatars/....png"). Префиксация базового URL поверх уже абсолютного
 * адреса ломает загрузку изображений, поэтому здесь единая логика разрешения.
 */
object MediaUrl {

    fun absolute(baseUrl: String, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
    }

    /** URL файла/изображения на основном сервере (8085). */
    fun main(path: String?): String? = absolute(Constants.BASE_URL, path)

    /** URL на чат-сервере (8086), например аватары. */
    fun chat(path: String?): String? = absolute(Constants.CHAT_BASE_URL, path)
}