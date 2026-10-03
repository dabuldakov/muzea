package com.example.muzea.data.repository

import com.example.muzea.data.model.VideoResponse

/**
 * Кэш списка видео в памяти процесса.
 *
 * Аналог [ChatListCache]: и репозиторий, и ViewModel создаются заново при
 * каждом показе вкладки, поэтому без общего объекта лента каждый раз
 * запрашивалась бы с сервера и мигала бы индикатором загрузки. Здесь список
 * отдаётся мгновенно, а актуальные данные догружаются сетью в фоне.
 *
 * Хранится не больше [MAX_VIDEOS] записей, чтобы память не росла бесконечно.
 */
object VideoListCache {

    private const val MAX_VIDEOS = 200

    private val videos = LinkedHashMap<Long, VideoResponse>()

    @Synchronized
    fun get(): List<VideoResponse> = videos.values.toList()

    @Synchronized
    fun has(): Boolean = videos.isNotEmpty()

    @Synchronized
    fun put(list: List<VideoResponse>) {
        videos.clear()
        for (video in list) {
            videos[video.id] = video
        }
        while (videos.size > MAX_VIDEOS) {
            val oldest = videos.keys.firstOrNull() ?: break
            videos.remove(oldest)
        }
    }

    @Synchronized
    fun clear() {
        videos.clear()
    }
}
