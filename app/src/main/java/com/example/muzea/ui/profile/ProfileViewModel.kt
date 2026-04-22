package com.example.muzea.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.UserResponse
import com.example.muzea.data.repository.UserRepository
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _userProfileResult = MutableSharedFlow<NetworkResult<UserResponse>>()
    val userProfileResult: SharedFlow<NetworkResult<UserResponse>> = _userProfileResult.asSharedFlow()

    private val _updateProfileResult = MutableSharedFlow<NetworkResult<UserResponse>>()
    val updateProfileResult: SharedFlow<NetworkResult<UserResponse>> = _updateProfileResult.asSharedFlow()

    private var currentUserId: Long = 0

    fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getCurrentUser().collect { result ->
                if (result is NetworkResult.Success) {
                    // TODO: Получите реальный ID пользователя
                    currentUserId = 1
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

    fun logout() {
        tokenManager.clearToken()
    }
}