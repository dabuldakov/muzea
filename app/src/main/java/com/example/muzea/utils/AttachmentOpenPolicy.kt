package com.example.muzea.utils

/**
 * Какие вложения нельзя открывать через ACTION_VIEW.
 *
 * Исполняемые файлы из чата — классический вектор социальной инженерии.
 * Приложение не должно выступать источником установки APK; файл можно
 * сохранить и открыть вручную в файловом менеджере, если пользователь
 * действительно доверяет отправителю.
 */
object AttachmentOpenPolicy {

    private val EXECUTABLE_EXTENSIONS = setOf(
        "apk", "aab", "apks", "xapk", "dex",
        "exe", "msi", "bat", "cmd", "sh"
    )

    private val EXECUTABLE_MIME_TYPES = setOf(
        "application/vnd.android.package-archive",
        "application/x-msdownload",
        "application/x-msdos-program",
        "application/x-sh"
    )

    fun isExecutable(fileName: String?, mimeType: String?): Boolean {
        val extension = fileName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return extension in EXECUTABLE_EXTENSIONS
            || mimeType?.lowercase() in EXECUTABLE_MIME_TYPES
    }
}
