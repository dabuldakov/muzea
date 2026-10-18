package com.example.muzea.data.repository

import com.example.muzea.data.model.LinkPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Загрузка и кэширование превью ссылок (Open Graph) для сообщений-ссылок.
 *
 * Отдельный OkHttp-клиент без пиннинга и без наших интерцепторов: ходит на
 * произвольные внешние сайты. Тело читается частично (peekBody) — в head
 * мета-теги обычно в первых килобайтах. Результаты (в т.ч. неудачи) кэшируются
 * в памяти на сутки, чтобы не дёргать сайт на каждую перерисовку.
 */
@Singleton
class LinkPreviewStore @Inject constructor() {

    private data class Entry(val preview: LinkPreview?, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val ttlMs = TimeUnit.HOURS.toMillis(24)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Уже загруженное (и не устаревшее) превью; null — если ещё не грузили или не удалось. */
    fun cached(url: String): LinkPreview? =
        cache[url]?.takeIf { fresh(it) }?.preview

    /** true, если по этому URL уже есть свежий результат (в т.ч. неудачный). */
    fun isLoaded(url: String): Boolean = cache[url]?.let { fresh(it) } ?: false

    suspend fun load(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.at < ttlMs }?.let { return@withContext it.preview }
        val preview = fetch(url)
        cache[url] = Entry(preview, now)
        preview
    }

    private fun fresh(entry: Entry) = System.currentTimeMillis() - entry.at < ttlMs

    private fun fetch(url: String): LinkPreview? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") return null

        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Mozilla/5.0 (compatible; Muzea/1.0)")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val contentType = response.body?.contentType()?.toString().orEmpty()
                if (!contentType.contains("html", ignoreCase = true)) return null
                val html = response.peekBody(MAX_BYTES).string()
                OgParser.parse(html, response.request.url.toString())
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_BYTES = 512L * 1024
    }
}
