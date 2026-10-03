package com.example.muzea.utils

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient

object VideoPlayerHelper {

    @UnstableApi
    @OptIn(UnstableApi::class)
    fun createPlayerWithAuth(context: Context, videoUrl: String, token: String): ExoPlayer {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)

        // Сначала отдаём уже скачанные фрагменты из дискового кэша, недостающее
        // догружаем по сети и попутно кэшируем.
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(VideoCache.get(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl.toUri())
            .build()

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        return ExoPlayer.Builder(context)
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
            }
    }

    fun releasePlayer(player: ExoPlayer?) {
        player?.release()
    }
}
