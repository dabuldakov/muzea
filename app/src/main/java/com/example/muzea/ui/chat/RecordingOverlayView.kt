package com.example.muzea.ui.chat

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import android.widget.TextView
import com.example.muzea.R

/**
 * Оверлей записи: затемнение, таймер, подсказка и превью видео-кружка.
 * Отвечает только за отображение, записью управляет экран.
 */
class RecordingOverlayView(
    private val overlay: View,
    private val timer: TextView,
    private val hint: TextView,
    private val previewCard: View,
    private val preview: TextureView
) {

    fun show(video: Boolean) {
        overlay.visibility = View.VISIBLE
        timer.text = "0:00"
        previewCard.visibility = if (video) View.VISIBLE else View.GONE
    }

    fun hide() {
        overlay.visibility = View.GONE
        previewCard.visibility = View.GONE
        preview.surfaceTextureListener = null
    }

    fun setTimer(text: String) {
        timer.text = text
    }

    fun setHint(cancelArmed: Boolean) {
        hint.setText(
            if (cancelArmed) R.string.recording_release_to_cancel
            else R.string.recording_release_to_send
        )
    }

    fun setStartingCamera() {
        hint.setText(R.string.recording_starting_camera)
    }

    /** Запускает [action], когда у TextureView готов surface. */
    fun whenSurfaceReady(action: () -> Unit) {
        if (preview.isAvailable) {
            preview.post(action)
            return
        }
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) = action()

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }
}
