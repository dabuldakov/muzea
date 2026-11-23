package com.example.muzea.ui.chat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.EditText
import androidx.core.widget.doOnTextChanged

/**
 * Поле ввода и кнопка отправки: клики, touch-жесты, редактор и текст.
 * Текст уходит в ViewModel, черновик очищается, при заряде проигрывается взрыв.
 */
internal class ChatInputController(
    private val context: Context,
    private val input: EditText,
    private val sendButton: ImageButton,
    private val gifButton: View,
    private val burst: ChargeBurstView,
    private val hasAttachments: () -> Boolean,
    private val onSend: (text: String, power: Int) -> Unit,
    private val onCycleMode: () -> Unit,
    private val onTouch: (MotionEvent) -> Boolean,
    private val onTextChanged: (text: String, cursor: Int) -> Unit,
    private val onInputActivity: (hasText: Boolean) -> Unit,
    private val clearDraft: () -> Unit,
    private val onGif: () -> Unit
) {

    fun setup() {
        sendButton.setOnClickListener {
            if (canSendNow()) send() else onCycleMode()
        }
        sendButton.setOnTouchListener { _, event -> onTouch(event) }
        gifButton.setOnClickListener { onGif() }
        input.setOnEditorActionListener { _, _, _ ->
            if (canSendNow()) send()
            false
        }
        input.doOnTextChanged { text, _, _, _ ->
            onInputActivity(!text.isNullOrBlank())
            onTextChanged(text?.toString().orEmpty(), input.selectionStart)
        }
    }

    fun canSendNow(): Boolean =
        input.text?.toString()?.isNotBlank() == true || hasAttachments()

    fun send(power: Int = 0) {
        val text = input.text?.toString().orEmpty()
        if (!canSendNow()) return
        onSend(text, power)
        input.setText("")
        clearDraft()
        if (power > 0) playBurst(power)
    }

    /** Короткий «взрыв» кнопки: чем сильнее заряд, тем заметнее анимация. */
    private fun playBurst(power: Int) {
        val scale = 1f + 1.3f * (power / 100f)
        burst.burst(power)
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(140, 255))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(140)
            }
        } else {
            sendButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        sendButton.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(120)
            .withEndAction {
                sendButton.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            .start()
    }
}
