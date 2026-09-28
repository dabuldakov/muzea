package com.example.muzea.ui.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.R
import com.example.muzea.data.model.UserResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.data.repository.UserRepository
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val application: Application,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _userProfileResult = MutableSharedFlow<NetworkResult<UserResponse>>()
    val userProfileResult: SharedFlow<NetworkResult<UserResponse>> = _userProfileResult.asSharedFlow()

    private val _updateProfileResult = MutableSharedFlow<NetworkResult<UserResponse>>()
    val updateProfileResult: SharedFlow<NetworkResult<UserResponse>> = _updateProfileResult.asSharedFlow()

    private val _deleteAccountResult = MutableSharedFlow<NetworkResult<Unit>>()
    val deleteAccountResult: SharedFlow<NetworkResult<Unit>> = _deleteAccountResult.asSharedFlow()

    private var currentUserId: Long = 0

    fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getCurrentUser().collect { result ->
                val user = (result as? NetworkResult.Success)?.data
                if (user != null) {
                    currentUserId = user.id
                }
                _userProfileResult.emit(result)
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
            val chatErased = chatRepository.deleteAccount()
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

    fun logout() {
        tokenManager.clearToken()
    }
}
