package com.example.muzea.ui.chat.recording

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Camera
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.view.Surface
import android.view.TextureView
import java.io.File

/** Записанное видео-сообщение («кружок»). */
data class RecordedVideo(
    val file: File,
    val thumbnail: File?,
    val durationSec: Int,
    val width: Int?,
    val height: Int?
)

/**
 * Запись видео-кружка фронтальной камерой.
 *
 * Камера открывается только на время записи, превью показывается в круглой
 * карточке. Максимальная длительность контролируется экраном.
 */
class VideoNoteRecorder(private val context: Context) {

    private var camera: Camera? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt: Long = 0

    val isRecording: Boolean get() = recorder != null

    fun start(textureView: TextureView): Boolean {
        if (!textureView.isAvailable) return false
        return try {
            val dir = File(context.cacheDir, "chat_recordings").apply { mkdirs() }
            val target = File(dir, "video_note_${System.currentTimeMillis()}.mp4")

            val cameraId = frontCameraId()
            val cam = Camera.open(cameraId)
            val info = Camera.CameraInfo().apply { Camera.getCameraInfo(cameraId, this) }
            cam.setDisplayOrientation(previewOrientation(info))
            val params = cam.parameters
            val size = params.supportedPreviewSizes
                .filter { it.width <= 1280 && it.height <= 1280 }
                .minByOrNull { kotlin.math.abs(it.width - 640) + kotlin.math.abs(it.height - 480) }
                ?: params.supportedPreviewSizes.first()
            params.setPreviewSize(size.width, size.height)
            params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            cam.parameters = params
            cam.setPreviewTexture(textureView.surfaceTexture)
            cam.startPreview()

            cam.unlock()
            val mediaRecorder = MediaRecorder().apply {
                setCamera(cam)
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(size.width, size.height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(2_000_000)
                setOrientationHint(recordingOrientation(info))
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }

            camera = cam
            recorder = mediaRecorder
            file = target
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            runCatching { camera?.lock() }
            runCatching { camera?.release() }
            camera = null
            file?.delete()
            file = null
            false
        }
    }

    /** Останавливает запись и готовит превью; null — если запись не удалась. */
    fun stop(): RecordedVideo? {
        val mediaRecorder = recorder ?: return null
        val cam = camera
        val target = file
        val duration = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        recorder = null
        camera = null
        file = null
        return try {
            mediaRecorder.stop()
            mediaRecorder.release()
            runCatching { cam?.lock() }
            runCatching { cam?.release() }
            if (target == null || !target.exists() || duration < 1) {
                target?.delete()
                null
            } else {
                val meta = readMetadata(target)
                RecordedVideo(target, meta.thumbnail, duration, meta.width, meta.height)
            }
        } catch (e: Exception) {
            runCatching { mediaRecorder.release() }
            runCatching { cam?.lock() }
            runCatching { cam?.release() }
            target?.delete()
            null
        }
    }

    fun cancel() {
        val mediaRecorder = recorder
        val cam = camera
        val target = file
        recorder = null
        camera = null
        file = null
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        runCatching { cam?.lock() }
        runCatching { cam?.release() }
        target?.delete()
    }

    private fun frontCameraId(): Int {
        val info = Camera.CameraInfo()
        for (id in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(id, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return id
        }
        return 0
    }

    private fun previewOrientation(info: Camera.CameraInfo): Int {
        val rotation = when (context.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - ((info.orientation + rotation) % 360)) % 360
        } else {
            (info.orientation - rotation + 360) % 360
        }
    }

    private fun recordingOrientation(info: Camera.CameraInfo): Int {
        val rotation = when (context.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (info.orientation - rotation + 360) % 360
        } else {
            (info.orientation + rotation) % 360
        }
    }

    private fun readMetadata(file: File): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame: Bitmap? = retriever.getFrameAtTime(0)
            val thumb = frame?.let {
                val dir = file.parentFile ?: context.cacheDir
                val target = File(dir, "video_note_thumb_${System.currentTimeMillis()}.jpg")
                target.outputStream().use { out -> it.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                it.recycle()
                target.takeIf { f -> f.length() > 0 }
            }
            Metadata(
                thumbnail = thumb,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            )
        } catch (e: Exception) {
            Metadata(null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class Metadata(val thumbnail: File?, val width: Int?, val height: Int?)
}
