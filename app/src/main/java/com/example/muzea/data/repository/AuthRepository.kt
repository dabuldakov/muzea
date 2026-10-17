package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.AuthServiceApi
import com.example.muzea.data.model.AuthServiceError
import com.example.muzea.data.model.AuthServiceLogoutRequest
import com.example.muzea.data.model.AuthServiceOtpRequest
import com.example.muzea.data.model.AuthServiceOtpVerifyRequest
import com.example.muzea.data.model.AuthServiceRegisterRequest
import com.example.muzea.data.model.AuthServiceTokenResponse
import com.example.muzea.data.model.AuthServiceVerifyEmailRequest
import com.example.muzea.utils.TokenManager
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Response

/**
 * Регистрация и вход через единый IdP (auth-service): passwordless по email-коду.
 * Успешный вход/подтверждение сохраняют пару access+refresh; refresh ротируется
 * интерцептором [com.example.muzea.data.api.TokenRefreshInterceptor].
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authServiceApi: AuthServiceApi,
    private val tokenManager: TokenManager
) {

    /** Регистрация: создаёт пользователя и отправляет код на email. */
    suspend fun register(email: String, username: String, displayName: String?): Flow<Resource<Unit>> =
        request { authServiceApi.register(AuthServiceRegisterRequest(email, username, displayName)) }

    /** Повторная отправка кода подтверждения регистрации. */
    suspend fun resendVerification(email: String): Flow<Resource<Unit>> =
        request { authServiceApi.resendVerification(AuthServiceOtpRequest(email)) }

    /** Подтверждение email кодом: активирует аккаунт и сразу логинит. */
    suspend fun verifyEmail(email: String, code: String): Flow<Resource<Unit>> =
        tokenRequest {
            authServiceApi.verifyEmail(AuthServiceVerifyEmailRequest(email, code, deviceId()))
        }

    /** Запрос кода для входа (passwordless). */
    suspend fun requestLoginCode(email: String): Flow<Resource<Unit>> =
        request { authServiceApi.requestLoginCode(AuthServiceOtpRequest(email)) }

    /** Вход по коду из письма. */
    suspend fun verifyLoginCode(email: String, code: String): Flow<Resource<Unit>> =
        tokenRequest {
            authServiceApi.verifyLoginCode(AuthServiceOtpVerifyRequest(email, code, deviceId()))
        }

    /** Разлогин: сначала локальная очистка (безопасно для UI), затем отзыв сессии. */
    suspend fun logout() {
        val refresh = tokenManager.getAuthRefreshToken()
        tokenManager.clearToken()
        if (!refresh.isNullOrEmpty()) {
            try {
                authServiceApi.logout(AuthServiceLogoutRequest(refresh))
            } catch (e: Exception) {
                // Некритично: refresh всё равно истечёт, либо ротация его отзовёт.
            }
        }
    }

    fun isLoggedIn(): Boolean = !tokenManager.getToken().isNullOrEmpty()

    /** Удаляет аккаунт в IdP (auth-service). Возвращает успех. */
    suspend fun deleteAccount(): Boolean = try {
        authServiceApi.deleteMe().isSuccessful
    } catch (e: Exception) {
        false
    }

    private fun deviceId(): String? = tokenManager.getDeviceId()

    /** Запрос без токенов в ответе (код отправлен и т.п.). */
    private suspend fun request(action: suspend () -> Response<*>): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = action()
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(errorMessage(response)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Нет соединения. Проверьте интернет."))
        }
    }

    /** Запрос, возвращающий пару токенов: сохраняем её при успехе. */
    private suspend fun tokenRequest(
        action: suspend () -> Response<AuthServiceTokenResponse>
    ): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = action()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                persist(body)
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(errorMessage(response)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Нет соединения. Проверьте интернет."))
        }
    }

    private fun persist(body: AuthServiceTokenResponse) {
        tokenManager.saveToken(body.accessToken)
        tokenManager.saveAuthRefreshToken(body.refreshToken)
        body.user?.username?.let { tokenManager.saveUsername(it) }
        body.user?.email?.let { tokenManager.saveEmail(it) }
    }

    private fun errorMessage(response: Response<*>): String {
        val raw = try {
            response.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        if (!raw.isNullOrBlank()) {
            val parsed = try {
                Gson().fromJson(raw, AuthServiceError::class.java)
            } catch (e: Exception) {
                null
            }
            parsed?.message?.takeIf { it.isNotBlank() }?.let { return localize(it) }
        }
        return "Ошибка (${response.code()})"
    }

    private fun localize(message: String): String = when {
        message.contains("Invalid code") -> "Неверный код"
        message.contains("expired", ignoreCase = true) -> "Код истёк. Запросите новый"
        message.contains("Too many", ignoreCase = true) -> "Слишком много попыток. Попробуйте позже"
        message.contains("already registered") -> "Этот email уже зарегистрирован"
        message.contains("already taken") -> "Этот username уже занят"
        message.contains("Please wait", ignoreCase = true) -> "Подождите перед повторной отправкой"
        else -> message
    }
}
