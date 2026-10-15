package com.example.muzea.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET

/**
 * Минимальный клиент GitHub Releases: нужен, чтобы узнать последний релиз
 * приложения и предложить обновление. Запросы идут на публичный API без
 * авторизации, поэтому клиент для него собирается отдельно — без токенов
 * основного API.
 */
interface GitHubApi {

    @GET("repos/dabuldakov/muzea/releases")
    suspend fun getReleases(): Response<List<GitHubRelease>>
}

data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    @SerializedName("html_url")
    val htmlUrl: String,
    @SerializedName("body")
    val body: String? = null,
    @SerializedName("draft")
    val draft: Boolean = false,
    @SerializedName("prerelease")
    val prerelease: Boolean = false,
    @SerializedName("assets")
    val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    @SerializedName("name")
    val name: String,
    @SerializedName("browser_download_url")
    val downloadUrl: String
)
