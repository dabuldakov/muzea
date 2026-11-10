package com.example.muzea.utils

/**
 * Форматирует время сообщения чата, присланное сервером, в локальный пояс устройства.
 */
object ChatTimeFormatter {

    fun format(iso: String?): String = LocalTimeFormatter.format(iso)

    /** Время без даты — для метки внутри бабла. */
    fun formatTimeOnly(iso: String?): String = LocalTimeFormatter.formatTime(iso)
}