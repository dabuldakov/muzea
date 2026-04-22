package com.example.muzea.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginResult = MutableSharedFlow<NetworkResult<String>>()
    val loginResult: SharedFlow<NetworkResult<String>> = _loginResult.asSharedFlow()

    private val _registerResult = MutableSharedFlow<NetworkResult<String>>()
    val registerResult: SharedFlow<NetworkResult<String>> = _registerResult.asSharedFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            authRepository.login(username, password).collect { result ->
                _loginResult.emit(result)
            }
        }
    }

    fun register(username: String, email: String, password: String, fullName: String) {
        viewModelScope.launch {
            authRepository.register(username, email, password, fullName).collect { result ->
                _registerResult.emit(result)
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}