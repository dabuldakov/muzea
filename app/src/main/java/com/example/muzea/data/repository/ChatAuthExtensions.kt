package com.example.muzea.data.repository

/**
 * Общие для чат-репозиториев операции аутентификации.
 *
 * Раньше эти хелперы были приватными в god-репозитории; после разбиения они
 * нужны нескольким репозиториям, поэтому вынесены сюда.
 */
internal fun ChatAuthManager.authFailureMessage(): String =
    "Chat auth failed. " + (lastFailureMessage ?: "Please log in again.")

/**
 * Выполняет запрос с проверкой чат-аутентификации и одной повторной попыткой
 * после инвалидации токена (401).
 */
internal suspend fun <T> ChatAuthManager.authenticatedRequest(
    action: suspend () -> retrofit2.Response<T>
): retrofit2.Response<T> {
    check(isAuthenticated()) { authFailureMessage() }
    var response = action()
    if (response.code() == 401) {
        invalidate()
        check(isAuthenticated()) { authFailureMessage() }
        response = action()
    }
    return response
}
