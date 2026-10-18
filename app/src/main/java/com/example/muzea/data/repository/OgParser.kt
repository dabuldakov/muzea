package com.example.muzea.data.repository

import com.example.muzea.data.model.LinkPreview
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Разбор Open Graph разметки из HTML страницы. Без сторонних библиотек:
 * мета-теги достаточно регулярно устроены, а полноценный HTML-парсер тут
 * избыточен. Вынесено отдельно от сети, чтобы тестировать на строках.
 */
object OgParser {

    private val META = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private val TITLE = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(html: String, baseUrl: String): LinkPreview? {
        val metas = HashMap<String, String>()
        for (tag in META.findAll(html)) {
            val attrs = HashMap<String, String>()
            for (a in ATTR.findAll(tag.value)) {
                val key = a.groupValues[1].lowercase()
                val value = a.groupValues[2]
                    .ifEmpty { a.groupValues[3].ifEmpty { a.groupValues[4] } }
                attrs[key] = value
            }
            val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase() ?: continue
            val content = attrs["content"] ?: continue
            metas.putIfAbsent(key, decode(content))
        }

        val title = metas["og:title"]
            ?: metas["twitter:title"]
            ?: TITLE.find(html)?.groupValues?.get(1)?.trim()?.let { decode(it) }
        val image = metas["og:image"] ?: metas["og:image:url"] ?: metas["twitter:image"]
        val description = metas["og:description"] ?: metas["description"]
        val siteName = metas["og:site_name"]

        if (title.isNullOrBlank() && image.isNullOrBlank()) return null

        return LinkPreview(
            url = baseUrl,
            title = title?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            imageUrl = image?.let { resolve(baseUrl, it) },
            siteName = siteName?.takeIf { it.isNotBlank() } ?: host(baseUrl)
        )
    }

    /** Приводит относительный адрес картинки к абсолютному. */
    fun resolve(baseUrl: String, ref: String): String {
        val base = baseUrl.toHttpUrlOrNull() ?: return ref
        return base.resolve(ref)?.toString() ?: ref
    }

    fun host(url: String): String? = url.toHttpUrlOrNull()?.host

    private fun decode(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()
}
