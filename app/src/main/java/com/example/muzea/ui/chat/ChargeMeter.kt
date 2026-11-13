package com.example.muzea.ui.chat

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.sin

/**
 * Визуальная часть «заряда» кнопки отправки: полоса, проценты, вспышка,
 * раздувание кнопки и вибрация через колбэк. Логику удержания ведёт экран.
 */
class ChargeMeter(
    private val chargeBar: View,
    private val progress: ProgressBar,
    private val percentText: TextView,
    private val burst: ChargeBurstView,
    private val sendButton: View,
    private val chargeFormat: (Int) -> String,
    private val chargeZero: String,
    private val onVibrate: (Float) -> Unit
) {

    fun render(heldMs: Long) {
        val power = ChargeCalculator.power(heldMs)
        chargeBar.visibility = View.VISIBLE
        progress.progress = power
        percentText.text = chargeFormat(power)
        burst.updatePower(power)
        // Кнопка «раздувается» и пульсирует; к концу — сильнее.
        val level = power / 100f
        val pulse = 1f + 0.10f * level * (0.5f + 0.5f * sin(heldMs / 90.0).toFloat())
        val scale = 1f + 0.55f * level
        sendButton.scaleX = scale * pulse
        sendButton.scaleY = scale * pulse
        onVibrate(level)
    }

    fun reset() {
        chargeBar.visibility = View.GONE
        progress.progress = 0
        percentText.text = chargeZero
        burst.clear()
        sendButton.scaleX = 1f
        sendButton.scaleY = 1f
    }
}
