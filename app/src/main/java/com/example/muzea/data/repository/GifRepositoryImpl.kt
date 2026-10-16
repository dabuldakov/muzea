package com.example.muzea.data.repository

import com.example.muzea.BuildConfig
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.api.KlipyApi
import com.example.muzea.data.mapper.toGif
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.repository.GifRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Гифки: поиск/trending идут напрямую в Klipy с устройства (как требует
 * провайдер), «Мои»/«Этот чат»/«Библиотека» — с нашего API. Ключ Klipy встроен
 * в сборку.
 *
 * Первая страница кэшируется на устройстве (см. {@link GifCache}). Обновление —
 * по требованию с троттлингом: пока запись свежая (моложе {@code FRESH_TTL_MS}),
 * сеть не трогаем; иначе обновляем при очередном открытии. Если сети нет —
 * отдаём старый кэш. Никакого таймера/фонового опроса.
 */
@Singleton
class GifRepositoryImpl @Inject constructor(
    private val klipyApi: KlipyApi,
    private val chatApiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager,
    private val gifCache: GifCache
) : GifRepository {

    override suspend fun search(query: String, page: Int, size: Int): List<Gif> {
        val key = BuildConfig.KLIPY_APP_KEY
        if (key.isBlank()) return emptyList()
        return cached(page, GifCache.searchKey(query)) {
            val response = if (query.isBlank()) {
                klipyApi.trending(key, page, size, CONTENT_FILTER)
            } else {
                klipyApi.search(key, query, page, size, CONTENT_FILTER)
            }
            response.body()?.items()?.mapNotNull { it.toGif() } ?: emptyList()
        }
    }

    override suspend fun recent(page: Int, size: Int): List<Gif> =
        cached(page, GifCache.recentKey()) {
            chatAuthManager.authenticatedRequest { chatApiService.getRecentGifs(page, size) }
                .body()?.map { it.toGif() } ?: emptyList()
        }

    override suspend fun chatGifs(chatUuid: String, page: Int, size: Int): List<Gif> =
        cached(page, GifCache.chatKey(chatUuid)) {
            chatAuthManager.authenticatedRequest { chatApiService.getChatGifs(chatUuid, page, size) }
                .body()?.map { it.toGif() } ?: emptyList()
        }

    override suspend fun library(query: String, page: Int, size: Int): List<Gif> =
        cached(page, GifCache.libraryKey(query)) {
            chatAuthManager.authenticatedRequest { chatApiService.getLibraryGifs(query, page, size) }
                .body()?.map { it.toGif() } ?: emptyList()
        }

    /**
     * Первая страница: свежий кэш → отдаём без сети; иначе грузим. При ошибке
     * сети/пустом ответе показываем старый кэш. Страницы > 0 всегда из сети.
     */
    private suspend fun cached(page: Int, key: String, fetch: suspend () -> List<Gif>): List<Gif> {
        if (page == 0) {
            gifCache.getFresh(key, FRESH_TTL_MS)?.let { return it }
        }
        val items = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (page == 0) {
            if (items.isNotEmpty()) {
                gifCache.put(key, items)
                return items
            }
            // Сеть не дала данных — лучше старый кэш, чем пусто.
            return gifCache.getStale(key) ?: emptyList()
        }
        return items
    }

    private companion object {
        /** Безопасный контент: без 18+/NSFW. */
        const val CONTENT_FILTER = "high"

        /**
         * Через сколько кэш считается устаревшим и обновляется при следующем
         * открытии вкладки. Гифки не меняются, поэтому обновляем нечасто.
         */
        const val FRESH_TTL_MS = 30 * 60 * 1000L
    }
}
