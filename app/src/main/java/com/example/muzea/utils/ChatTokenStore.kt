package com.example.muzea.utils

/**
 * Хранилище учётных данных и JWT-токена чат-сервера.
 * Выделено как интерфейс, чтобы логика аутентификации чата
 * (ChatAuthManager) была тестируемой без Android-окружения.
 */
interface ChatTokenStore {
    fun getUsername(): String?
    fun getPassword(): String?
    fun getEmail(): String?
    fun getChatToken(): String?
    fun getChatTokenUser(): String?
    fun saveChatToken(token: String)
    fun saveChatTokenUser(username: String)
    fun clearChatToken()
}