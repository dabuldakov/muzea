package com.example.muzea.ui.chat

/**
 * Разбор `@`-упоминания под курсором для подсказки участников чата.
 *
 * Вынесено из фрагмента, чтобы поведение (границы слова, фильтр, лимит)
 * покрывалось обычными unit-тестами без Android UI.
 */
object MentionSuggestions {

    data class Query(
        /** Индекс символа `@`. */
        val start: Int,
        /** Позиция курсора, после которой начинается остальной текст. */
        val end: Int,
        /** Подходящие имена участников в порядке получения. */
        val candidates: List<String>
    )

    fun resolve(
        text: String,
        cursor: Int,
        usernames: List<String>,
        limit: Int = 6
    ): Query? {
        if (cursor <= 0 || cursor > text.length) return null
        val at = text.lastIndexOf('@', cursor - 1)
        if (at < 0) return null
        // Упоминание только целиком: e-mail и «слово@слово» не подсказываем.
        if (at > 0 && !text[at - 1].isWhitespace()) return null
        val query = text.substring(at + 1, cursor)
        if (query.any { it.isWhitespace() }) return null
        val candidates = usernames
            .asSequence()
            .filter { it.contains(query, ignoreCase = true) }
            .distinct()
            .take(limit)
            .toList()
        return if (candidates.isEmpty()) null else Query(at, cursor, candidates)
    }
}
