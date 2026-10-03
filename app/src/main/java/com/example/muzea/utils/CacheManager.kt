package com.example.muzea.utils

import android.content.Context
import com.bumptech.glide.Glide
import com.example.muzea.data.repository.ChatListCache
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.data.repository.PrivateChatCache
import com.example.muzea.data.repository.VideoListCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Учёт и очистка дискового кэша приложения.
 *
 * Считает занятое место по всем кэш-директориям: сюда попадают превью
 * изображений (дисковый кэш Glide), скачанные видеофрагменты ([VideoCache]) и
 * временные файлы. Очистка идёт через API Glide и видеокэша, а файлы в корне
 * кэша удаляются напрямую.
 */
object CacheManager {

    suspend fun totalSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        cacheDirs(context).sumOf { directorySize(it) }
    }

    suspend fun clear(context: Context) {
        withContext(Dispatchers.Main) {
            runCatching { Glide.get(context).clearMemory() }
        }
        withContext(Dispatchers.IO) {
            runCatching { Glide.get(context).clearDiskCache() }
            VideoCache.clear()
            ChatMessagesCache.clear()
            ChatListCache.clear()
            VideoListCache.clear()
            PrivateChatCache.clear()

            // Временные файлы (загрузки, аватары) лежат в корне кэша — удаляем
            // их, но не трогаем поддиректории, которыми управляют Glide/VideoCache.
            cacheDirs(context).forEach { dir ->
                dir.listFiles()?.forEach { child ->
                    if (child.isFile) {
                        runCatching { child.delete() }
                    }
                }
            }
        }
    }

    private fun cacheDirs(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        context.cacheDir?.let { dirs += it }
        context.externalCacheDirs?.filterNotNull()?.forEach { dirs += it }
        return dirs.distinct()
    }

    private fun directorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { total += directorySize(it) }
        return total
    }
}
