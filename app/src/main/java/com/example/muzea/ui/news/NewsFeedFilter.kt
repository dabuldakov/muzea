package com.example.muzea.ui.news

import com.example.muzea.data.model.NewsResponse

/**
 * Чистая функция фильтрации ленты новостей: оставляет только новости авторов,
 * которые есть в контактах пользователя, плюс новости самого пользователя.
 *
 * Вынесена из NewsViewModel, чтобы поведение было тривиально тестируемым и
 * не зависело от Android-окружения.
 */
object NewsFeedFilter {

    fun filterByContacts(
        news: List<NewsResponse>,
        contactUsernames: Set<String>,
        ownUsername: String?
    ): List<NewsResponse> {
        val contacts = contactUsernames
            .mapNotNull { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val me = ownUsername?.trim()?.takeIf { it.isNotEmpty() }

        if (contacts.isEmpty() && me == null) return emptyList()

        return news.filter { item ->
            val author = item.author.trim()
            author in contacts || author == me
        }
    }
}