package com.example.muzea.utils

import com.example.muzea.BuildConfig

object Constants {
    val BASE_URL: String = BuildConfig.API_BASE_URL
    const val CHAT_BASE_URL = "https://chat.muzea.su"
    const val AUTH_BASE_URL = "https://auth.muzea.su"

    /** Постоянный каталог скачанных документов (чистится кнопкой «Очистить кэш»). */
    const val CHAT_DOWNLOADS_DIR = "chat_downloads"
}