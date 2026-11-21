package com.example.muzea.ui.auth

/**
 * Отсчёт до повторной отправки кода.
 *
 * Сервер не отдаёт новый код чаще раза в минуту, поэтому кнопку «Отправить
 * ещё раз» держим заблокированной и показываем остаток. Чистая логика без
 * Android — чтобы покрыть тестами.
 */
class ResendCooldown(private val totalSeconds: Int = DEFAULT_SECONDS) {

    var seconds: Int = 0
        private set

    val active: Boolean get() = seconds > 0

    fun start() {
        seconds = totalSeconds
    }

    fun tick() {
        if (seconds > 0) seconds--
    }

    companion object {
        const val DEFAULT_SECONDS = 60
    }
}
