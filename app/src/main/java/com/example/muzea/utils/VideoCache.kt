package com.example.muzea.utils

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Дисковый кэш скачанных видеофрагментов для ExoPlayer.
 *
 * Раньше плеер тянул поток заново при каждом открытии ролика. Теперь уже
 * просмотренные куски складываются в `cacheDir/video_cache` и переиспользуются,
 * пока кэш не превысит [MAX_BYTES] — тогда вытесняются самые старые.
 *
 * SimpleCache обязан быть один на процесс для одной директории, поэтому
 * держим единственный экземпляр.
 */
@UnstableApi
object VideoCache {

    private const val DIR_NAME = "video_cache"
    private const val MAX_BYTES = 300L * 1024 * 1024

    @Volatile
    private var instance: SimpleCache? = null

    @UnstableApi
    fun get(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: SimpleCache(
                File(context.applicationContext.cacheDir, DIR_NAME),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES)
            ).also { instance = it }
        }
    }

    /** Сколько байт сейчас занято в видеокэше. */
    fun sizeBytes(): Long = instance?.cacheSpace ?: 0L

    /** Удаляет все скачанные видеофрагменты, не трогая сам объект кэша. */
    fun clear() {
        val cache = instance ?: return
        // Копия ключей: removeResource меняет состояние кэша во время обхода.
        for (key in cache.keys.toList()) {
            cache.removeResource(key)
        }
    }

    /**
     * Полностью освобождает единственный экземпляр. Нужен тестам, чтобы каждый
     * из них работал со своей директорией: SimpleCache допускает только один
     * объект на директорию в процессе.
     */
    @UnstableApi
    internal fun resetForTests() {
        runCatching { instance?.release() }
        instance = null
    }
}
