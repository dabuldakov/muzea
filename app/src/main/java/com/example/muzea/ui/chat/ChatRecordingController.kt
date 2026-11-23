package com.example.muzea.ui.chat

import android.content.Context
import android.view.MotionEvent
import android.view.TextureView
import com.example.muzea.R
import com.example.muzea.ui.chat.recording.RecordedAudio
import com.example.muzea.ui.chat.recording.RecordedVideo
import com.example.muzea.ui.chat.recording.VideoNoteRecorder
import com.example.muzea.ui.chat.recording.VoiceRecorder
import com.example.muzea.utils.PreparedAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Запись голосовых и видео-заметок по удержанию кнопки отправки.
 *
 * Короткий тап переключает режим, удержание запускает запись, движение вверх
 * отменяет её. Готовый файл уходит в [onRecorded] с типом VOICE/VIDEO_NOTE.
 */
internal class ChatRecordingController(
    private val context: Context,
    private val textureView: TextureView,
    private val overlay: RecordingOverlayView,
    private val timer: RecordingTimer,
    private val scope: CoroutineScope,
    private val cancelDistancePx: Int,
    private val ensurePermissions: (SendMode) -> Boolean,
    private val onRecorded: (PreparedAttachment, String) -> Unit,
    private val toast: (Int) -> Unit
) {

    private var activeMode: SendMode? = null
    private var startJob: Job? = null
    private var downY = 0f
    private var cancelArmed = false
    private var sendPressed = false
    private var videoReady = false
    private var finishing = false
    private var voiceRecorder: VoiceRecorder? = null
    private var videoRecorder: VideoNoteRecorder? = null

    val isActive: Boolean
        get() = activeMode != null

    /**
     * Обрабатывает касание кнопки в режиме VOICE/VIDEO.
     * [onShortTap] вызывается, если запись так и не началась.
     */
    fun onTouch(event: MotionEvent, mode: SendMode, onShortTap: () -> Unit): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!ensurePermissions(mode)) return true
                sendPressed = true
                downY = event.rawY
                cancelArmed = false
                startJob?.cancel()
                startJob = scope.launch {
                    delay(LONG_PRESS_MS)
                    startRecording(mode)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeMode != null) {
                    val armed = event.rawY < downY - cancelDistancePx
                    if (armed != cancelArmed) {
                        cancelArmed = armed
                        overlay.setHint(cancelArmed)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                sendPressed = false
                if (activeMode != null) {
                    finish(cancelArmed)
                } else {
                    startJob?.cancel()
                    onShortTap()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                sendPressed = false
                if (activeMode != null) finish(cancel = true) else startJob?.cancel()
                return true
            }
        }
        return true
    }

    /** Ограничение длительности записи: завершаем и отправляем. */
    fun finishAtLimit() = finish(cancel = false)

    /** Отмена записи, например при уходе с экрана. */
    fun cancelIfActive() {
        if (activeMode != null) finish(cancel = true)
    }

    private fun startRecording(mode: SendMode) {
        if (activeMode != null) return
        if (!ensurePermissions(mode)) return

        activeMode = mode
        cancelArmed = false
        overlay.show(video = mode == SendMode.VIDEO)
        overlay.setHint(cancelArmed)

        when (mode) {
            SendMode.VOICE -> startVoiceRecording()
            SendMode.VIDEO -> startVideoRecording()
            SendMode.TEXT -> Unit
        }
    }

    private fun startVoiceRecording() {
        val recorder = VoiceRecorder(context)
        if (!recorder.start()) {
            abort(R.string.recording_failed)
            return
        }
        voiceRecorder = recorder
        timer.start(MAX_VOICE_MS)
    }

    private fun startVideoRecording() {
        val recorder = VideoNoteRecorder(context)
        videoRecorder = recorder
        videoReady = false
        overlay.setStartingCamera()
        val start = {
            recorder.start(
                textureView,
                onReady = {
                    // Пока камера открывалась, палец могли отпустить.
                    if (sendPressed && activeMode == SendMode.VIDEO) {
                        videoReady = true
                        overlay.setHint(cancelArmed)
                        timer.start(MAX_VIDEO_MS)
                    } else {
                        recorder.cancel()
                        if (activeMode == SendMode.VIDEO) {
                            activeMode = null
                            overlay.hide()
                        }
                    }
                },
                onError = { abort(R.string.recording_failed) }
            )
        }
        overlay.whenSurfaceReady(start)
    }

    private fun finish(cancel: Boolean) {
        if (finishing) return
        finishing = true
        val mode = activeMode
        activeMode = null
        timer.stop()
        val cancelEffective = cancel || (mode == SendMode.VIDEO && !videoReady)
        videoReady = false

        if (cancelEffective) {
            voiceRecorder?.cancel()
            videoRecorder?.cancel()
            voiceRecorder = null
            videoRecorder = null
            overlay.hide()
            finishing = false
            return
        }

        val prepared = when (mode) {
            SendMode.VOICE -> voiceRecorder?.stop()?.let { it.toPrepared() }
            SendMode.VIDEO -> videoRecorder?.stop()?.let { it.toPrepared() }
            else -> null
        }
        voiceRecorder = null
        videoRecorder = null
        overlay.hide()

        if (prepared == null) {
            toast(R.string.recording_failed)
        } else {
            val messageType = if (mode == SendMode.VOICE) "VOICE" else "VIDEO_NOTE"
            onRecorded(prepared, messageType)
        }
        finishing = false
    }

    private fun abort(messageRes: Int) {
        activeMode = null
        timer.stop()
        voiceRecorder?.cancel()
        videoRecorder?.cancel()
        voiceRecorder = null
        videoRecorder = null
        overlay.hide()
        toast(messageRes)
    }

    private fun RecordedAudio.toPrepared() = PreparedAttachment(
        file = file,
        thumbnail = null,
        name = file.name,
        size = file.length(),
        mimeType = "audio/mp4",
        type = "AUDIO",
        width = null,
        height = null,
        duration = durationSec
    )

    private fun RecordedVideo.toPrepared() = PreparedAttachment(
        file = file,
        thumbnail = thumbnail,
        name = file.name,
        size = file.length(),
        mimeType = "video/mp4",
        type = "VIDEO",
        width = width,
        height = height,
        duration = durationSec
    )

    private companion object {
        const val LONG_PRESS_MS = 250L
        const val MAX_VOICE_MS = 5 * 60 * 1000L
        const val MAX_VIDEO_MS = 60 * 1000L
    }
}
