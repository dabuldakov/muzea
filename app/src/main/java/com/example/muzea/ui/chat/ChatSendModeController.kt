package com.example.muzea.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.muzea.R

/**
 * Режим кнопки отправки: текст/голос/видео, иконка и запрос разрешений.
 * Короткий тап по пустому полю переключает режим, ввод текста возвращает TEXT.
 */
internal class ChatSendModeController(
    private val context: Context,
    private val button: ImageView,
    private val requestMic: () -> Unit,
    private val requestCamera: () -> Unit,
    private val canSendNow: () -> Boolean
) {

    var mode: SendMode = SendMode.TEXT
        private set

    fun cycle() {
        mode = when (mode) {
            SendMode.TEXT -> SendMode.VOICE
            SendMode.VOICE -> SendMode.VIDEO
            SendMode.VIDEO -> SendMode.TEXT
        }
        if (mode == SendMode.VOICE) requestMic()
        if (mode == SendMode.VIDEO) requestCamera()
        updateButton()
    }

    /** Ввод текста всегда возвращает кнопку в режим отправки текста. */
    fun onInputChanged(hasText: Boolean) {
        if (hasText) mode = SendMode.TEXT
        updateButton()
    }

    fun updateButton() {
        val effective = if (canSendNow()) SendMode.TEXT else mode
        val (icon, description) = when (effective) {
            SendMode.TEXT -> R.drawable.ic_send_burst to R.string.send_mode_text
            SendMode.VOICE -> R.drawable.ic_mic to R.string.send_mode_voice
            SendMode.VIDEO -> R.drawable.ic_videocam to R.string.send_mode_video
        }
        button.setImageResource(icon)
        // Взрыв — цветная иконка, её не тонируем; микрофон/камеру красим в акцент.
        button.imageTintList =
            if (effective == SendMode.TEXT) null
            else ContextCompat.getColorStateList(context, R.color.purple_500)
        button.contentDescription = context.getString(description)
    }

    fun ensurePermissions(target: SendMode): Boolean = when (target) {
        SendMode.VOICE -> {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                true
            } else {
                requestMic()
                false
            }
        }
        SendMode.VIDEO -> {
            val mic = hasPermission(Manifest.permission.RECORD_AUDIO)
            val camera = hasPermission(Manifest.permission.CAMERA)
            if (mic && camera) {
                true
            } else {
                requestCamera()
                // Разрешения спрашиваем до записи; пользователь нажмёт ещё раз.
                false
            }
        }
        SendMode.TEXT -> true
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
