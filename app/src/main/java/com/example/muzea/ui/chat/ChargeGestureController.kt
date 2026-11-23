package com.example.muzea.ui.chat

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Жест «заряда» на кнопке отправки: удержание копит силу, движение вверх
 * отменяет отправку. Вибрация усиливается по мере заряда.
 */
internal class ChargeGestureController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val meter: ChargeMeter,
    private val button: View,
    private val cancelDistancePx: Int,
    private val onSend: (Int) -> Unit
) {

    private var job: Job? = null
    private var startedAt = 0L
    private var isCharging = false
    private var cancelled = false
    private var downY = 0f
    private var lastVibrationAt = 0L

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startedAt = SystemClock.elapsedRealtime()
                isCharging = false
                cancelled = false
                downY = event.rawY
                job?.cancel()
                job = scope.launch {
                    while (isActive) {
                        delay(CHARGE_TICK_MS)
                        val held = SystemClock.elapsedRealtime() - startedAt
                        if (ChargeCalculator.isCharged(held)) {
                            isCharging = true
                            meter.render(held)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isCharging && event.rawY < downY - cancelDistancePx) {
                    cancelled = true
                    cancel()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                job?.cancel()
                val held = SystemClock.elapsedRealtime() - startedAt
                val power = if (isCharging && !cancelled) ChargeCalculator.power(held) else 0
                val shouldSend = !cancelled
                reset()
                if (shouldSend) onSend(power)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancel()
                return true
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
        isCharging = false
        reset()
    }

    private fun reset() {
        isCharging = false
        startedAt = 0L
        meter.reset()
    }

    /** Вибрация усиливается и учащается по мере заряда. */
    fun vibrate(level: Float) {
        if (level <= 0.02f) return
        val now = SystemClock.elapsedRealtime()
        val interval = (220 - 170 * level).toLong().coerceAtLeast(50)
        if (now - lastVibrationAt < interval) return
        lastVibrationAt = now
        val vibrator = context.getSystemService(Vibrator::class.java)
        val amplitude = (25 + 230 * level).toInt().coerceIn(1, 255)
        if (vibrator == null || !vibrator.hasVibrator()) {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    private companion object {
        const val CHARGE_TICK_MS = 50L
    }
}
