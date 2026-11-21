package com.example.muzea.data.repository

import com.example.muzea.di.S3UploadClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Хранилище ответило не-2xx на presigned PUT. */
class UploadFailedException(val statusCode: Int) : IOException("Upload failed: HTTP $statusCode")

/**
 * Прямая загрузка файла по presigned PUT в MinIO.
 *
 * Используется отдельный HTTP-клиент без интерцепторов авторизации: URL уже
 * подписан (SigV4), и добавленный `Authorization: Bearer` ломает проверку.
 * Загрузку можно отменить — вместе с корутиной отменяется и HTTP-вызов.
 */
@Singleton
class PresignedUploader @Inject constructor(
    @S3UploadClient private val client: OkHttpClient
) {

    suspend fun put(
        uploadUrl: String,
        file: File,
        contentType: String,
        onProgress: (Int) -> Unit
    ) {
        val request = Request.Builder()
            .url(uploadUrl)
            .put(ProgressRequestBody(file, contentType.toMediaTypeOrNull(), onProgress))
            .build()
        val call = client.newCall(request)

        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    response.close()
                    if (!continuation.isActive) return
                    if (code in 200..299) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(UploadFailedException(code))
                    }
                }
            })
        }
    }
}
