package com.example.muzea.utils

/**
 * Чистит текст ошибки перед сохранением в буфер и отправкой на сервер.
 *
 * В сообщениях и стек-трейсах могут случайно оказаться почта, телефон или
 * токен (например, из ответа сервера или URL). Мы не хотим хранить это в
 * отчётах, поэтому заменяем такие фрагменты на метки.
 */
internal object ErrorSanitizer {

    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phone = Regex("""(?<!\d)(?:\+7|8)[\s(-]*\d{3}[\s)-]*\d{3}[\s-]*\d{2}[\s-]*\d{2}(?!\d)""")
    private val bearer = Regex("""(?i)bearer\s+[A-Za-z0-9._\-]+""")
    private val jwt = Regex("""[A-Za-z0-9_\-]{16,}\.[A-Za-z0-9_\-]{16,}\.[A-Za-z0-9_\-]{16,}""")
    private val longToken = Regex("""(?i)(token|password|secret|api[_-]?key)\s*[=:]\s*[^\s,;"']+""")

    fun sanitize(text: String): String {
        var out = text
        out = email.replace(out, "[email]")
        out = phone.replace(out, "[phone]")
        out = bearer.replace(out, "Bearer [token]")
        out = jwt.replace(out, "[token]")
        out = longToken.replace(out) { match ->
            val key = match.groupValues[1]
            "$key=[hidden]"
        }
        return out
    }
}
