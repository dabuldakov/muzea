package com.example.muzea.utils

/**
 * Политика автозагрузки медиа на текущем канале.
 *
 * Обновляется [NetworkMonitor]. Хорошая сеть — грузим как раньше; слабая —
 * только миниатюры и кэш, гифки/превью ссылок по запросу; офлайн — только
 * из кэша. Текст от этого не зависит.
 */
object MediaPolicy {

    @Volatile
    var status: NetworkStatus = NetworkStatus(NetworkQuality.GOOD, metered = false)

    val autoLoad: Boolean get() = status.quality == NetworkQuality.GOOD

    val cacheOnly: Boolean get() = status.quality == NetworkQuality.OFFLINE
}
