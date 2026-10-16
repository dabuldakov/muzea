package com.example.muzea.data.api

import com.example.muzea.data.model.KlipyGifDto
import com.example.muzea.data.model.KlipyGifsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Публичный GIF-API Klipy. Поиск выполняется **с устройства напрямую** (требование
 * провайдера: запросы и загрузка медиа должны идти с клиента, не через наш сервер).
 * Ключ передаётся в пути и встраивается в сборку.
 */
interface KlipyApi {

    @GET("api/v1/{key}/gifs/search")
    suspend fun search(
        @Path("key") key: String,
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("content_filter") contentFilter: String
    ): Response<KlipyGifsResponse>

    @GET("api/v1/{key}/gifs/trending")
    suspend fun trending(
        @Path("key") key: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("content_filter") contentFilter: String
    ): Response<KlipyGifsResponse>
}
