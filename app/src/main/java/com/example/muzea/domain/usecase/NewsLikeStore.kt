package com.example.muzea.domain.usecase

import com.example.muzea.core.Resource
import com.example.muzea.core.firstTerminal
import com.example.muzea.domain.model.News
import com.example.muzea.domain.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Локальное состояние лайка, общее для ленты и детальной новости. */
data class NewsLikeState(
    val likedByMe: Boolean,
    val likes: Long
)

/**
 * Единый источник истины для лайков новостей.
 *
 * Лента и детальный экран живут в разных ViewModel, поэтому без общего
 * хранилища их состояния расходятся: лайк в ленте не виден в новости и
 * наоборот. Store хранит только изменённые пользователем лайки и
 * накладывает их поверх данных сервера. Благодаря этому:
 *
 * - лайк сразу виден на обоих экранах;
 * - не нужен полный refetch ленты после возврата из новости;
 * - ошибка сети откатывает только одну новость;
 * - повторный тап во время запроса игнорируется.
 */
@Singleton
class NewsLikeStore @Inject constructor(
    private val newsRepository: NewsRepository
) {

    private val _states = MutableStateFlow<Map<Long, NewsLikeState>>(emptyMap())
    val states: StateFlow<Map<Long, NewsLikeState>> = _states.asStateFlow()

    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    /** Накладывает локальный лайк поверх новости с сервера. */
    fun apply(news: News): News {
        val state = _states.value[news.id] ?: return news
        if (state.likedByMe == news.likedByMe && state.likes == news.likes) return news
        return news.copy(likedByMe = state.likedByMe, likes = state.likes)
    }

    /**
     * Оптимистично переключает лайк и синхронизирует счётчик с сервером.
     * Возвращает текст ошибки для показа пользователю или null при успехе.
     */
    suspend fun toggle(news: News): String? {
        if (!inFlight.add(news.id)) return null

        val previous = _states.value[news.id]
            ?: NewsLikeState(news.likedByMe, news.likes)
        val targetLiked = !previous.likedByMe
        val optimistic = NewsLikeState(
            likedByMe = targetLiked,
            likes = (previous.likes + if (targetLiked) 1 else -1).coerceAtLeast(0)
        )
        publish(news.id, optimistic)

        return try {
            val request = if (targetLiked) {
                newsRepository.likeNews(news.id)
            } else {
                newsRepository.unlikeNews(news.id)
            }
            when (val result = request.firstTerminal()) {
                is Resource.Success -> {
                    publish(
                        news.id,
                        optimistic.copy(likes = result.data ?: optimistic.likes)
                    )
                    null
                }
                is Resource.Error -> {
                    publish(news.id, previous)
                    result.message ?: "Failed to update like"
                }
                is Resource.Loading -> null
            }
        } finally {
            inFlight.remove(news.id)
        }
    }

    private fun publish(newsId: Long, state: NewsLikeState) {
        _states.value = _states.value + (newsId to state)
    }
}
