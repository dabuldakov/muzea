package com.example.muzea.ui.chat

/** Прогресс скачивания вложения, чтобы контроллер не зависел от диалога. */
interface DownloadProgress {
    val isShowing: Boolean
    fun show()
    fun update(percent: Int)
    fun dismiss()
}
