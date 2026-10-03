package com.example.muzea.data.repository

import com.example.muzea.data.api.ChatApiService
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сессия на чат-сервере: heartbeat, разлогин, удаление аккаунта и общий счётчик
 * непрочитанных. Отделено от операций с чатами и сообщениями (SRP).
 */
@Singleton
class ChatSessionRepository @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager
) {

    /**
     * Полное удаление аккаунта на чат-сервере.
     *
     * Намеренно без [authenticatedRequest]: после удаления сервер отвечает 401,
     * а повторная аутентификация в [ChatAuthManager] зарегистрировала бы заново
     * аккаунт с тем же именем. Поэтому 401 и 404 здесь считаются успехом —
     * аккаунта уже нет. true означает, что данных на сервере не осталось.
     */
    suspend fun deleteAccount(): Boolean {
        return try {
            check(chatAuthManager.isAuthenticated()) { chatAuthManager.authFailureMessage() }
            val response = apiService.deleteAccount()
            response.isSuccessful || response.code() == 401 || response.code() == 404
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Heartbeat «я на переднем плане».
     *
     * Тихий метод без Flow: ошибки здесь нечего показывать пользователю —
     * упавший heartbeat означает лишь «статус обновится чуть позже», а окно
     * TTL специально шире интервала. Возвращаем признак успеха, чтобы
     * вызывающий мог отличить сетевую проблему от штатной отправки.
     */
    suspend fun sendHeartbeat(): Boolean = try {
        if (!chatAuthManager.isAuthenticated()) {
            false
        } else {
            apiService.sendHeartbeat().isSuccessful
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /**
     * Серверный разлогин.
     *
     * Вызывается перед очисткой локального токена: пока сессия жива, сервер
     * считает пользователя онлайн, и его статус «залипнет» в чужих контактах.
     */
    suspend fun logout(): Boolean = try {
        if (chatAuthManager.isAuthenticated()) {
            apiService.logout().isSuccessful
        } else {
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Разлогин нельзя отменять из-за сети: локальные токены всё равно
        // чистим вызывающий код, сервер же догасит сессию по TTL.
        true
    }

    suspend fun getTotalUnreadCount(): Flow<NetworkResult<Long>> = flow {
        emit(NetworkResult.Loading())
        try {
            val response = chatAuthManager.authenticatedRequest { apiService.getTotalUnreadCount() }
            if (response.isSuccessful && response.body() != null) {
                emit(NetworkResult.Success(response.body()!!.count))
            } else {
                emit(NetworkResult.Error("Failed to load unread count: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(NetworkResult.Error("Network error: ${e.message}"))
        }
    }
}
