package com.example.muzea.utils

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object VideoPlayerHelper {

    @UnstableApi
    @OptIn(UnstableApi::class)
    fun createPlayerWithAuth(
        context: Context,
        videoUrl: String,
        token: String,
        useCache: Boolean = true
    ): ExoPlayer {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            // Дефолтные 10 секунд рвут воспроизведение на слабой мобильной
            // сети; ответ идёт кусками, поэтому таймаут на чтение поднят.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)

        // Сначала отдаём уже скачанные фрагменты из дискового кэша, недостающее
        // догружаем по сети и попутно кэшируем. После ошибки чтения плеер
        // пересоздаётся без кэша (см. VideoDetailActivity), чтобы битый фрагмент
        // не блокировал просмотр.
        val dataSourceFactory: DataSource.Factory = if (useCache) {
            CacheDataSource.Factory()
                .setCache(VideoCache.get(context))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }

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

    /**
     * Ошибки, которые имеет смысл повторить один раз: сеть, таймаут, ответ
     * сервера и «неизвестный IO» (обрыв/EOF/сбой дискового кэша).
     */
    fun isRetryableIoError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true

        else -> false
    }

    fun releasePlayer(player: ExoPlayer?) {
        player?.release()
    }
}
