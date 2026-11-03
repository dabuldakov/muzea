package com.example.muzea.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.databinding.ActivityAttachmentViewerBinding
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.ui.common.SystemBars
import com.example.muzea.utils.MediaUrl
import com.example.muzea.utils.TokenManager
import com.example.muzea.utils.VideoPlayerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Полноэкранный просмотр вложения: фото, видео или аудио. */
@AndroidEntryPoint
class AttachmentViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttachmentViewerBinding
    private var player: ExoPlayer? = null
    private var isSeeking = false

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttachmentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)

        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty().uppercase()
        val name = intent.getStringExtra(EXTRA_NAME)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        binding.tvTitle.text = name ?: ""
        binding.btnBack.setOnClickListener { finish() }

        when (type) {
            "IMAGE" -> showImage(url)
            "VIDEO" -> showVideo(url, withControls = true)
            "AUDIO" -> showAudio(url)
            else -> finish()
        }
    }

    private fun showImage(url: String) {
        binding.ivImage.visibility = android.view.View.VISIBLE
        Glide.with(this)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.ivImage)
    }

    private fun showVideo(url: String, withControls: Boolean) {
        binding.playerView.visibility = android.view.View.VISIBLE
        player = VideoPlayerHelper.createPlayerWithAuth(this, url, tokenManager.getToken().orEmpty())
        binding.playerView.player = player
    }

    private fun showAudio(url: String) {
        binding.audioControls.visibility = android.view.View.VISIBLE
        player = VideoPlayerHelper.createPlayerWithAuth(this, url, tokenManager.getToken().orEmpty())
        // Аудио начинаем сразу: пользователь уже нажал Play в чате.
        player?.playWhenReady = true
        binding.btnPlay.setImageResource(R.drawable.ic_pause)

        binding.btnPlay.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            if (exo.isPlaying) {
                exo.pause()
                binding.btnPlay.setImageResource(R.drawable.ic_play_circle)
            } else {
                exo.play()
                binding.btnPlay.setImageResource(R.drawable.ic_pause)
            }
        }
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    binding.btnPlay.setImageResource(R.drawable.ic_play_circle)
                    binding.seekBar.progress = 0
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.btnPlay.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_circle
                )
            }
        })

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvAudioTime.text = formatTime(progress.toLong(), durationMs())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                player?.seekTo(seekBar.progress.toLong())
                isSeeking = false
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    updateAudioProgress()
                    delay(500)
                }
            }
        }
    }

    private fun updateAudioProgress() {
        val exo = player ?: return
        val duration = durationMs()
        if (duration > 0) {
            binding.seekBar.max = duration.toInt()
            if (!isSeeking) {
                binding.seekBar.progress = exo.currentPosition.toInt()
            }
            binding.tvAudioTime.text = formatTime(exo.currentPosition, duration)
        }
    }

    private fun durationMs(): Long {
        val d = player?.duration ?: 0L
        return if (d > 0) d else 0L
    }

    private fun formatTime(position: Long, duration: Long): String {
        fun mmss(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(total / 60, total % 60)
        }
        return "${mmss(position)} / ${mmss(duration)}"
    }

    override fun onDestroy() {
        binding.playerView.player = null
        VideoPlayerHelper.releasePlayer(player)
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, attachment: MessageAttachment) {
            val url = when {
                !attachment.attachmentUuid.isNullOrBlank() ->
                    MediaUrl.chat("/api/attachments/${attachment.attachmentUuid}")
                // Локальная запись до загрузки: абсолютный путь к файлу.
                attachment.fileUrl.startsWith("/") || attachment.fileUrl.startsWith("http") ->
                    attachment.fileUrl
                else -> MediaUrl.chat(attachment.fileUrl)
            }.orEmpty()

            context.startActivity(
                Intent(context, AttachmentViewerActivity::class.java).apply {
                    putExtra(EXTRA_TYPE, attachment.type)
                    putExtra(EXTRA_URL, url)
                    putExtra(EXTRA_NAME, attachment.fileName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
