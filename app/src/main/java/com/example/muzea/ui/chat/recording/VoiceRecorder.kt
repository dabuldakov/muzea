package com.example.muzea.ui.chat.recording

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Записанное голосовое сообщение. */
data class RecordedAudio(
    val file: File,
    val durationSec: Int
)

/** Запись голосового сообщения (m4a/AAC) через MediaRecorder. */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt: Long = 0

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        val dir = File(context.cacheDir, "chat_recordings").apply { mkdirs() }
        val target = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        return try {
            val mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            file = target
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            target.delete()
            false
        }
    }

    /** Останавливает запись; null — если запись слишком короткая/сломалась. */
    fun stop(): RecordedAudio? {
        val mediaRecorder = recorder ?: return null
        val target = file
        val duration = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        recorder = null
        file = null
        return try {
            mediaRecorder.stop()
            mediaRecorder.release()
            if (target == null || !target.exists() || duration < 1) {
                target?.delete()
                null
            } else {
                RecordedAudio(target, duration)
            }
        } catch (e: Exception) {
            runCatching { mediaRecorder.release() }
            target?.delete()
            null
        }
    }

    fun cancel() {
        val mediaRecorder = recorder
        val target = file
        recorder = null
        file = null
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        target?.delete()
    }
}
