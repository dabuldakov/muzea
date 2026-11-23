package com.example.muzea.ui.chat

import android.view.View
import com.example.muzea.domain.model.Message

/** Состояние долгого нажатия: обычный OnLongClick не срабатывает, если в
 *  тексте есть кликабельные ссылки (LinkMovementMethod забирает события). */
private val longPressStates =
    java.util.WeakHashMap<View, LongPressState>()
private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
private const val DEFAULT_TOUCH_SLOP = 24

private class LongPressState(
    val onLongPress: () -> Unit,
    val runnable: Runnable
) {
    var fired = false
    var downX = 0f
    var downY = 0f
}

/**
 * Долгое нажатие открывает меню действий, короткое — работает как раньше
 * (открывает ссылку или меню, в зависимости от вьюхи).
 */
internal fun bindLongPress(view: View, message: Message, onClick: (Message) -> Unit) {
    val slop = runCatching {
        android.view.ViewConfiguration.get(view.context).scaledTouchSlop
    }.getOrDefault(DEFAULT_TOUCH_SLOP)
    val timeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
    lateinit var state: LongPressState
    state = LongPressState(
        onLongPress = { onClick(message) },
        runnable = Runnable {
            state.fired = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            state.onLongPress()
        }
    )
    longPressStates[view]?.let { longPressHandler.removeCallbacks(it.runnable) }
    longPressStates[view] = state
    view.setOnTouchListener { v, event ->
        val current = longPressStates[v] ?: return@setOnTouchListener false
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                current.fired = false
                current.downX = event.x
                current.downY = event.y
                longPressHandler.postDelayed(current.runnable, timeout)
                false
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!current.fired &&
                    (Math.abs(event.x - current.downX) > slop ||
                        Math.abs(event.y - current.downY) > slop)
                ) {
                    longPressHandler.removeCallbacks(current.runnable)
                }
                false
            }
            android.view.MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(current.runnable)
                current.fired
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(current.runnable)
                false
            }
            else -> false
        }
    }
}
