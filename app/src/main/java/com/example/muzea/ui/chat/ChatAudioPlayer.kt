package com.example.muzea.ui.chat

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.muzea.utils.VideoCache
import okhttp3.OkHttpClient

/**
 * Небольшой плеер для прослушивания голосовых/аудио прямо в переписке.
 * Один экземпляр на экран: переключение между сообщениями останавливает
 * предыдущее.
 */
@UnstableApi
class ChatAudioPlayer(
    context: Context,
    token: String,
    private val onFinished: () -> Unit,
    private val onError: () -> Unit
) {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        }
        .build()

    private val defaultDataSource = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client))
    private val plainFactory = ProgressiveMediaSource.Factory(defaultDataSource)
    private val cachedFactory = ProgressiveMediaSource.Factory(
        CacheDataSource.Factory()
            .setCache(VideoCache.get(context))
            .setUpstreamDataSourceFactory(defaultDataSource)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    )

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .build().apply {
        // Источник создаётся заново под каждый файл в play().
        this.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onFinished()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onError()
            }
        })
        playWhenReady = false
    }

    val isPlaying: Boolean get() = player.isPlaying

    fun play(url: String) {
        player.stop()
        // Локальную запись (ещё не загруженную) не кэшируем, серверную — да:
        // повторное прослушивание не должно снова тянуть файл из сети.
        val factory = if (url.startsWith("/")) plainFactory else cachedFactory
        player.setMediaSource(factory.createMediaSource(MediaItem.fromUri(url.toUri())))
        player.prepare()
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun release() {
        player.release()
    }
}
