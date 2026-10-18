package com.example.muzea.utils

/**
 * Распознавание ссылок в тексте сообщений чата.
 *
 * Вынесено отдельно от адаптера, чтобы можно было тестировать без Android.
 * Хвостовая пунктуация («.,!?:;») в ссылку не включается — иначе клик уводит
 * на несуществующий адрес.
 */
object MessageLinks {

    data class LinkMatch(val url: String, val start: Int, val end: Int)

    private val URL = Regex("""(?i)(?:https?://|www\.)[^\s<>()\[\]{}"']+""")
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '»', '"', '\'')

    /** Находит ссылки в тексте с корректными границами (без хвостовой пунктуации). */
    fun matches(text: String?): List<LinkMatch> {
        if (text.isNullOrBlank()) return emptyList()
        return URL.findAll(text).mapNotNull { match ->
            val trimmed = match.value.trimEnd(*TRAILING)
            if (trimmed.isEmpty()) {
                null
            } else {
                LinkMatch(trimmed, match.range.first, match.range.first + trimmed.length)
            }
        }.toList()
    }

    /** Приводит ссылку к открываемому виду (добавляет https:// для www…). */
    fun normalize(raw: String): String =
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            raw
        } else {
            "https://$raw"
        }

    /**
     * Если сообщение целиком состоит из одной ссылки (без текста вокруг) —
     * возвращает её. Такие сообщения показываем как превью-карточку.
     */
    fun singleUrl(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val found = matches(trimmed)
        return if (found.size == 1 && found[0].start == 0 && found[0].end == trimmed.length) {
            found[0].url
        } else {
            null
        }
    }
}
