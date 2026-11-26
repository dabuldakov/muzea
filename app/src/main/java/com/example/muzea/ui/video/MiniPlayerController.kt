package com.example.muzea.ui.video

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.muzea.R
import com.example.muzea.databinding.ViewMiniPlayerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Глобальный mini-плеер поверх MainActivity. Живёт вне фрагментов, поэтому
 * остаётся при переключении вкладок, возврате из детали и пересоздании
 * Activity. Позиция на экране сохраняется между запусками.
 */
class MiniPlayerController(
    private val activity: FragmentActivity,
    private val parent: FrameLayout,
    private val playback: VideoPlaybackController
) {

    private val binding = ViewMiniPlayerBinding.inflate(activity.layoutInflater, parent, false)
    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var ticker: Job? = null
    private var userSeeking = false
    private var positionRestored = false
    private var lastDurationMs = 0L
    private var pipMode = false

    private val dragListener = DragTouchListener()

    fun start() {
        parent.addView(
            binding.root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = dp(12)
                bottomMargin = dp(92)
            }
        )
        setupControls()
        attachDrag(binding.miniPlayerView)
        attachDrag(binding.tvMiniTitle)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    playback.activeVideo,
                    playback.detailVisible,
                    playback.isPlaying,
                    playback.isBuffering
                ) { video, detailVisible, playing, buffering ->
                    MiniState(video, detailVisible, playing, buffering)
                }.collect { render(it) }
            }
        }
    }

    /** На время системного PiP mini-карточка уступает место отдельному PlayerView. */
    fun setPipMode(enabled: Boolean) {
        pipMode = enabled
        if (enabled) {
            ticker?.cancel()
            ticker = null
            binding.miniPlayerView.player = null
            binding.miniPlayerCard.visibility = View.GONE
        } else {
            refresh()
        }
    }

    fun refresh() {
        render(
            MiniState(
                video = playback.activeVideo.value,
                detailVisible = playback.detailVisible.value,
                playing = playback.isPlaying.value,
                buffering = playback.isBuffering.value
            )
        )
    }

    private fun setupControls() {
        binding.btnMiniPlayPause.setOnClickListener { playback.togglePlayPause() }
        binding.btnMiniRewind.setOnClickListener { playback.seekBy(-SEEK_STEP_MS) }
        binding.btnMiniForward.setOnClickListener { playback.seekBy(SEEK_STEP_MS) }
        binding.btnMiniClose.setOnClickListener {
            ticker?.cancel()
            ticker = null
            binding.miniPlayerView.player = null
            playback.close()
        }
        binding.miniSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || lastDurationMs <= 0) return
                val target = lastDurationMs * progress / seekBar.max
                playback.seekTo(target)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userSeeking = false
            }
        })
    }

    private fun render(state: MiniState) {
        if (pipMode) return
        val video = state.video
        val visible = video != null && !state.detailVisible && playback.player != null
        binding.miniPlayerCard.visibility = if (visible) View.VISIBLE else View.GONE

        if (!visible) {
            ticker?.cancel()
            ticker = null
            // Не держим PlayerView привязанным к общему плееру: детальный
            // экран в этот момент показывает тот же ExoPlayer.
            binding.miniPlayerView.player = null
            return
        }

        binding.tvMiniTitle.text = video?.title.orEmpty()
        binding.btnMiniPlayPause.setImageResource(
            if (state.playing) R.drawable.ic_pause else R.drawable.ic_play_circle
        )
        binding.btnMiniPlayPause.contentDescription = activity.getString(
            if (state.playing) R.string.video_pause else R.string.video_play
        )
        binding.miniBuffering.visibility =
            if (state.buffering) View.VISIBLE else View.GONE

        if (binding.miniPlayerView.player !== playback.player) {
            binding.miniPlayerView.player = playback.player
        }
        restorePositionIfNeeded()
        startTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = activity.lifecycleScope.launch {
            while (isActive) {
                val player = playback.player
                if (player != null) {
                    val duration = player.duration
                    lastDurationMs = if (duration > 0) duration else 0L
                    if (!userSeeking) {
                        val position = player.currentPosition.coerceAtLeast(0)
                        binding.miniSeek.progress = if (lastDurationMs > 0) {
                            (position * binding.miniSeek.max / lastDurationMs).toInt()
                        } else {
                            0
                        }
                    }
                }
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    private fun restorePositionIfNeeded() {
        if (positionRestored) {
            binding.miniPlayerCard.post { clampPosition() }
            return
        }
        positionRestored = true
        binding.miniPlayerCard.post {
            val savedX = prefs.getFloat(KEY_X, Float.NaN)
            val savedY = prefs.getFloat(KEY_Y, Float.NaN)
            if (!savedX.isNaN() && !savedY.isNaN()) {
                binding.miniPlayerCard.x = savedX
                binding.miniPlayerCard.y = savedY
            } else {
                binding.miniPlayerCard.x =
                    (parent.width - binding.miniPlayerCard.width - dp(12)).toFloat()
                binding.miniPlayerCard.y =
                    (parent.height - binding.miniPlayerCard.height - dp(92)).toFloat()
            }
            clampPosition()
        }
    }

    private fun clampPosition() {
        val card = binding.miniPlayerCard
        val maxX = (parent.width - card.width).coerceAtLeast(0).toFloat()
        val maxY = (parent.height - card.height).coerceAtLeast(0).toFloat()
        card.x = card.x.coerceIn(0f, maxX)
        card.y = card.y.coerceIn(0f, maxY)
    }

    private fun attachDrag(view: View) {
        view.setOnTouchListener(dragListener)
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0f
        private var startY = 0f
        private var dragging = false
        private var moved = false
        private var longPressPosted = false
        private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop

        private val startDrag = Runnable {
            dragging = true
            longPressPosted = false
            binding.miniPlayerCard.bringToFront()
            binding.miniPlayerCard.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val card = binding.miniPlayerCard
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = card.x
                    startY = card.y
                    dragging = false
                    moved = false
                    longPressPosted = true
                    view.postDelayed(startDrag, LONG_PRESS_MS)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (kotlin.math.abs(dx) > touchSlop ||
                                kotlin.math.abs(dy) > touchSlop)) {
                        moved = true
                        cancelLongPress(view)
                    }
                    if (dragging) {
                        card.x = (startX + dx).coerceIn(
                            0f,
                            (parent.width - card.width).coerceAtLeast(0).toFloat()
                        )
                        card.y = (startY + dy).coerceIn(
                            0f,
                            (parent.height - card.height).coerceAtLeast(0).toFloat()
                        )
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress(view)
                    card.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    if (dragging) {
                        prefs.edit {
                            putFloat(KEY_X, card.x)
                            putFloat(KEY_Y, card.y)
                        }
                    } else if (!moved && event.actionMasked == MotionEvent.ACTION_UP) {
                        openDetail()
                    }
                    dragging = false
                    return true
                }
            }
            return false
        }

        private fun cancelLongPress(view: View) {
            if (longPressPosted) {
                view.removeCallbacks(startDrag)
                longPressPosted = false
            }
        }
    }

    private fun openDetail() {
        val video = playback.activeVideo.value ?: return
        activity.startActivity(
            Intent(activity, VideoDetailActivity::class.java)
                .putExtra("video_id", video.id)
        )
    }

    private fun dp(value: Int): Int =
        (activity.resources.displayMetrics.density * value).toInt()

    private data class MiniState(
        val video: com.example.muzea.domain.model.Video?,
        val detailVisible: Boolean,
        val playing: Boolean,
        val buffering: Boolean
    )

    private companion object {
        const val PREFS = "mini_player"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val LONG_PRESS_MS = 420L
        const val SEEK_STEP_MS = 10_000L
        const val PROGRESS_TICK_MS = 500L
    }
}
