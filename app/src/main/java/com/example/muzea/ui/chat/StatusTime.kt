package com.example.muzea.ui.chat

/** Время квитанции: только «ЧЧ:мм», без даты. */
object StatusTime {
    fun format(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val time = value.substringAfter('T', value.substringAfter(' ', value))
        return time.take(5)
    }
}
