package com.example.muzea.data.repository

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File

/**
 * RequestBody с прогрессом загрузки: позволяет показать, сколько файла уже
 * ушло на сервер, вместо бесконечного индикатора на слабой сети.
 */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length().coerceAtLeast(1)
        var uploaded = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read
                onProgress(((uploaded * 100) / total).toInt().coerceIn(0, 100))
            }
        }
    }
}
