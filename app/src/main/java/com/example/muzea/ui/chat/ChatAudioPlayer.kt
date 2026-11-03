package com.example.muzea.ui.chat

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient

/**
 * Небольшой плеер для прослушивания голосовых/аудио прямо в переписке.
 * Один экземпляр на экран: переключение между сообщениями останавливает
 * предыдущее.
 */
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

    private val mediaSourceFactory = ProgressiveMediaSource.Factory(
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client))
    )

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
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
        player.setMediaItem(MediaItem.fromUri(url.toUri()))
        player.prepare()
        player.play()
    }

    fun pause() = player.pause()

    fun resume() = player.play()

    fun release() {
        player.release()
    }
}
