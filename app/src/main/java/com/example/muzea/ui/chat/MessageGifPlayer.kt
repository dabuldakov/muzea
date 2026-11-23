package com.example.muzea.ui.chat

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.muzea.domain.model.Message
import com.example.muzea.utils.MediaUrl

/**
 * Проигрывание MP4-варианта гифки внутри строки сообщения.
 *
 * Состояние игрока живёт вместе с ViewHolder: при переиспользовании строки
 * плеер переиспользуется, при уходе строки с экрана — освобождается.
 */
internal class MessageGifPlayer(
    private val tvMessage: TextView,
    private val ivGif: ImageView,
    private val playerView: PlayerView
) {

    private var player: ExoPlayer? = null

    fun bind(message: Message) {
        if (message.isDeleted) {
            release()
            playerView.visibility = View.GONE
            ivGif.visibility = View.GONE
            tvMessage.visibility = View.VISIBLE
            return
        }
        val mp4 = gifMp4Url(message)?.let { MediaUrl.chat(it) }
        if (mp4.isNullOrBlank()) {
            release()
            playerView.visibility = View.GONE
            bindGif(tvMessage, ivGif, message)
            return
        }
        tvMessage.visibility = View.GONE
        ivGif.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        val exo = player ?: ExoPlayer.Builder(playerView.context).build().also {
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.volume = 0f
            it.playWhenReady = true
            player = it
        }
        playerView.player = exo
        exo.setMediaItem(MediaItem.fromUri(mp4))
        exo.prepare()
    }

    fun release() {
        player?.release()
        player = null
        playerView.player = null
    }
}
