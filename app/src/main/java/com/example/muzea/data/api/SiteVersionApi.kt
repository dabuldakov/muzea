package com.example.muzea.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET

/**
 * Манифест последней версии на сайте muzea.su. Это первый источник проверки
 * обновлений; GitHub Releases остаётся запасным каналом для changelog и
 * совместимости со старыми сборками.
 */
interface SiteVersionApi {

    @GET("version.json")
    suspend fun getVersion(): Response<SiteVersionResponse>
}

data class SiteVersionResponse(
    @SerializedName("version")
    val version: String,
    @SerializedName("downloadUrl")
    val downloadUrl: String? = null,
    @SerializedName("changelog")
    val changelog: String? = null
)
