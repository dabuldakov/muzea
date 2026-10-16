package com.example.muzea.data.repository

import com.example.muzea.domain.model.Gif
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Постоянный кэш первых страниц гифок («Мои», «Этот чат», «Библиотека», поиск).
 *
 * Гифки не меняются, поэтому держим их содержимое на устройстве (файл в
 * filesDir) без TTL: при повторном входе и после перезапуска список показывается
 * мгновенно, а в сеть идём только за новыми ключами (новый запрос/вкладка).
 * Инвалидируется при отправке гифки и при выходе из аккаунта.
 */
class GifCache(private val file: File) {

    private val gson = Gson()
    private val lock = Any()
    private var loaded = false
    private val entries = LinkedHashMap<String, Entry>()

    private data class Entry(val at: Long, val items: List<Gif>)

    /** Свежая запись (моложе [ttlMs]) — сеть не нужна. */
    fun getFresh(key: String, ttlMs: Long): List<Gif>? = synchronized(lock) {
        ensureLoaded()
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.at > ttlMs) null else entry.items
    }

    /** Запись независимо от возраста — показываем, если сеть не ответила. */
    fun getStale(key: String): List<Gif>? = synchronized(lock) {
        ensureLoaded()
        entries[key]?.items
    }

    fun put(key: String, items: List<Gif>) = synchronized(lock) {
        ensureLoaded()
        entries.remove(key)
        entries[key] = Entry(System.currentTimeMillis(), items)
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        persist()
    }

    /**
     * Догружает одну гифку в начало списка (живое обновление из переписки),
     * без обращения к сети. Дубликаты по нашему ключу/url не добавляются,
     * хвост подрезается по размеру.
     */
    fun prepend(key: String, gif: Gif) = synchronized(lock) {
        ensureLoaded()
        val current = entries[key]?.items.orEmpty()
        if (current.any { sameGif(it, gif) }) return
        val updated = (listOf(gif) + current).take(MAX_PAGE_ITEMS)
        entries.remove(key)
        entries[key] = Entry(System.currentTimeMillis(), updated)
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        persist()
    }

    private fun sameGif(a: Gif, b: Gif): Boolean =
        (a.gifKey != null && a.gifKey == b.gifKey) || a.url == b.url

    fun clear() = synchronized(lock) {
        ensureLoaded()
        entries.clear()
        persist()
    }

    /** Точечная инвалидация одного ключа (например, «Этот чат»). */
    fun remove(key: String) = synchronized(lock) {
        ensureLoaded()
        if (entries.remove(key) != null) {
            persist()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) {
                val type = object : TypeToken<LinkedHashMap<String, Entry>>() {}.type
                val stored: LinkedHashMap<String, Entry>? = gson.fromJson(file.readText(), type)
                stored?.let { entries.putAll(it) }
            }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val type = object : TypeToken<LinkedHashMap<String, Entry>>() {}.type
            file.writeText(gson.toJson(entries, type))
        }
    }

    companion object {
        private const val MAX_ENTRIES = 100
        private const val MAX_PAGE_ITEMS = 60

        fun recentKey(): String = "recent"
        fun chatKey(chatUuid: String): String = "chat:$chatUuid"
        fun searchKey(query: String): String = "search:${query.trim().lowercase()}"
    }
}
