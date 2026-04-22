package com.example.muzea.utils

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

object VideoPlayerHelper {

    fun createPlayerWithAuth(context: Context, videoUrl: String, token: String): ExoPlayer {
        // Создаем фабрику с заголовком авторизации
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            .setAllowCrossProtocolRedirects(true)

        // Создаем MediaItem
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(videoUrl))
            .build()

        // СОЗДАЕМ MEDIASOURCE С ИСПОЛЬЗОВАНИЕМ ФАБРИКИ
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        // Создаем плеер
        val player = ExoPlayer.Builder(context).build()

        // Устанавливаем MediaSource (а не просто MediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()

        return player
    }

    fun releasePlayer(player: ExoPlayer?) {
        player?.release()
    }
}