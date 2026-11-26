package com.example.muzea.ui.video

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.muzea.domain.model.Video
import com.example.muzea.utils.Constants
import com.example.muzea.utils.TokenManager
import com.example.muzea.utils.VideoPlayerHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Общий плеер приложения. Один ExoPlayer на процесс позволяет:
 * - продолжать воспроизведение при выходе с экрана видео;
 * - показывать mini-плеер на вкладках;
 * - переиспользовать дисковый кэш и позицию при повторном открытии.
 *
 * Экран видео только привязывает [player] к PlayerView и не освобождает его
 * при сворачивании. Освобождает плеер только явное закрытие mini-плеера,
 * удаление видео или завершение приложения.
 */
@Singleton
class VideoPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activeVideo = MutableStateFlow<Video?>(null)
    val activeVideo: StateFlow<Video?> = _activeVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _detailVisible = MutableStateFlow(false)
    val detailVisible: StateFlow<Boolean> = _detailVisible.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    var player: ExoPlayer? = null
        private set

    private var currentVideo: Video? = null
    private var retryJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    /**
     * Открывает видео или переиспользует текущий плеер, если это тот же ролик.
     * Возвращает null, если нет авторизации.
     */
    @UnstableApi
    fun play(video: Video): ExoPlayer? {
        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            _errors.tryEmit(context.getString(com.example.muzea.R.string.auth_required))
            return null
        }

        if (currentVideo?.id == video.id && player != null) {
            currentVideo = video
            _activeVideo.value = video
            player?.playWhenReady = true
            return player
        }

        releaseInternal()
        val fullUrl = video.getFullVideoUrl(Constants.BASE_URL)
        val newPlayer = VideoPlayerHelper.createPlayerWithAuth(
            context = context,
            videoUrl = fullUrl,
            token = token,
            useCache = true
        )
        newPlayer.addListener(playerListener)
        newPlayer.playWhenReady = true
        player = newPlayer
        currentVideo = video
        _activeVideo.value = video
        return newPlayer
    }

    fun setDetailVisible(visible: Boolean) {
        _detailVisible.value = visible
    }

    fun togglePlayPause() {
        val current = player ?: return
        when {
            current.isPlaying -> current.pause()
            current.playbackState == Player.STATE_ENDED -> {
                current.seekTo(0)
                current.play()
            }
            else -> current.play()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun seekBy(deltaMs: Long) {
        val current = player ?: return
        val duration = current.duration
        val target = current.currentPosition + deltaMs
        current.seekTo(if (duration > 0) target.coerceIn(0, duration) else target.coerceAtLeast(0))
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun close() {
        releaseInternal()
        _activeVideo.value = null
    }

    /**
     * Сетевой обрыв при частично скачанном видео: продолжаем с текущей позиции,
     * когда сеть вернётся. Кэш не удаляем — докачка идёт в тот же ресурс.
     */
    private fun handlePlaybackError(error: PlaybackException) {
        if (!VideoPlayerHelper.isRetryableIoError(error)) {
            _errors.tryEmit(
                context.getString(com.example.muzea.R.string.video_play_failed, error.errorCodeName)
            )
            return
        }
        retryJob?.cancel()
        _isBuffering.value = true
        retryJob = scope.launch {
            repeat(RETRY_ATTEMPTS) {
                delay(RETRY_DELAY_MS)
                if (!hasNetwork()) return@repeat
                val current = player ?: return@launch
                val position = current.currentPosition
                runCatching {
                    current.prepare()
                    current.seekTo(position)
                    current.playWhenReady = true
                }
                _isBuffering.value = false
                return@launch
            }
            _isBuffering.value = false
            _errors.tryEmit(
                context.getString(com.example.muzea.R.string.video_play_failed, error.errorCodeName)
            )
        }
    }

    private fun hasNetwork(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun releaseInternal() {
        retryJob?.cancel()
        retryJob = null
        player?.removeListener(playerListener)
        VideoPlayerHelper.releasePlayer(player)
        player = null
        currentVideo = null
        _isPlaying.value = false
        _isBuffering.value = false
    }

    private companion object {
        const val RETRY_ATTEMPTS = 30
        const val RETRY_DELAY_MS = 2_000L
    }
}
