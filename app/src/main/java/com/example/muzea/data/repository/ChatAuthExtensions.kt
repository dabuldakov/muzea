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
    return retryOnceAfterUnauthorized(action)
}

/**
 * Повторяет запрос ровно один раз после 401.
 *
 * Единая точка для всех чат-репозиториев: раньше этот блок копировался в
 * каждый метод и легко расходился при правках. Если после инвалидации токена
 * чат всё ещё недоступен, возвращается первый ответ — вызывающий код сам
 * превратит его в понятную ошибку.
 */
internal suspend fun <T> ChatAuthManager.retryOnceAfterUnauthorized(
    action: suspend () -> retrofit2.Response<T>
): retrofit2.Response<T> {
    val first = action()
    if (first.code() != 401) return first
    invalidate()
    if (!isAuthenticated()) return first
    return action()
}
