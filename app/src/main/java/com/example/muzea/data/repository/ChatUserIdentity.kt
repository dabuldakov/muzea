package com.example.muzea.data.repository

/**
 * Кто я на чат-сервере (UUID из chat-токена).
 *
 * Вынесено в отдельную зависимость, чтобы ViewModel чата не разбирала JWT сама
 * и чтобы в тестах можно было подставить известное значение без Android-контекста.
 */
interface ChatUserIdentity {
    val userUuid: String?
}
