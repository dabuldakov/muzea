package com.example.muzea.ui.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.R
import com.example.muzea.domain.model.User
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.domain.repository.UserRepository
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние экрана профиля. */
data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val application: Application,
    private val userRepository: UserRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _profileState = MutableStateFlow(ProfileUiState())
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _updateProfileResult = MutableSharedFlow<NetworkResult<User>>()
    val updateProfileResult: SharedFlow<NetworkResult<User>> = _updateProfileResult.asSharedFlow()

    private val _deleteAccountResult = MutableSharedFlow<NetworkResult<Unit>>()
    val deleteAccountResult: SharedFlow<NetworkResult<Unit>> = _deleteAccountResult.asSharedFlow()

    private var currentUserId: Long = 0

    fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getCurrentUser().collect { result ->
                when (result) {
                    is NetworkResult.Loading ->
                        _profileState.value = _profileState.value.copy(isLoading = true, error = null)

                    is NetworkResult.Success -> {
                        val user = result.data
                        if (user != null) currentUserId = user.id
                        _profileState.value = ProfileUiState(user = user, isLoading = false)
                    }

                    is NetworkResult.Error ->
                        _profileState.value = _profileState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                }
            }
        }
    }

    fun updateUserProfile(fullName: String, email: String) {
        viewModelScope.launch {
            userRepository.updateUser(currentUserId, fullName, email).collect { result ->
                _updateProfileResult.emit(result)
            }
        }
    }

    /**
     * Удаление аккаунта: сначала чат-сервер (сообщения, контакты, вложения,
     * FCM-токены), затем основной (новости, видео, профиль). Локальное хранилище
     * и согласие на обработку персональных данных чистим только когда оба
     * сервера подтвердили удаление — иначе пользователь потерял бы пароль и не
     * смог повторить попытку.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            _deleteAccountResult.emit(NetworkResult.Loading())
            val chatErased = chatSessionRepository.deleteAccount()
            val result = userRepository.deleteAccount(currentUserId)
                .filterNot { it is NetworkResult.Loading }
                .first()
            if (result is NetworkResult.Success && chatErased) {
                tokenManager.clearAll()
                ConsentManager.revoke(application)
            }
            _deleteAccountResult.emit(
                if (result is NetworkResult.Success && !chatErased) {
                    NetworkResult.Error(application.getString(R.string.delete_account_chat_failed))
                } else {
                    result
                }
            )
        }
    }

    /**
     * Разлогин.
     *
     * Сначала чат-сервер, и только потом чистим локальный токен. Порядок
     * обязателен: запрос /logout несёт access-токен, а после clearToken()
     * сервер его уже не проверит и сессия останется жить — пользователь
     * продолжит светиться «в сети» у всех контактов до истечения TTL.
     *
     * Даже при сбое сети уходим на экран входа: локально пользователь
     * вышел, а серверную сессию снимет TTL.
     */
    fun logout() {
        viewModelScope.launch {
            chatSessionRepository.logout()
            tokenManager.clearToken()
        }
    }
}
