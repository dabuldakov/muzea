package com.example.muzea.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** Успешный ответ Retrofit для unit-тестов. */
internal fun <T> httpOk(body: T): Response<T> = Response.success(body)

/** Ответ с HTTP-ошибкой: тело в формате JSON, как у бэкендов. */
internal fun <T> httpError(code: Int = 500, message: String = "error"): Response<T> =
    Response.error(code, message.toResponseBody("application/json".toMediaType()))
