package com.example.muzea.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние экрана входа/регистрации/ввода кода. */
data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Код отправлен на email → перейти к экрану ввода кода. */
    private val _codeSent = MutableSharedFlow<Unit>()
    val codeSent: SharedFlow<Unit> = _codeSent.asSharedFlow()

    /** Аккаунт не подтверждён: ушёл код регистрации, открываем экран подтверждения. */
    private val _registrationCodeSent = MutableSharedFlow<Unit>()
    val registrationCodeSent: SharedFlow<Unit> = _registrationCodeSent.asSharedFlow()

    /** Успешный вход/подтверждение → на главный экран. */
    private val _authenticated = MutableSharedFlow<Unit>()
    val authenticated: SharedFlow<Unit> = _authenticated.asSharedFlow()

    fun register(email: String, username: String, fullName: String) =
        run({ authRepository.register(email, username, fullName.ifBlank { null }) }, sendsCode = true)

    fun requestLoginCode(email: String) {
        viewModelScope.launch {
            authRepository.requestLoginCode(email).collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.value = AuthUiState(isLoading = true)
                    is Resource.Error -> _uiState.value =
                        AuthUiState(isLoading = false, error = result.message)
                    is Resource.Success -> {
                        if (result.data == "UNVERIFIED") {
                            // Пароль не нужен, но email не подтверждён: вместо
                            // «код отправлен» шлём код регистрации и ведём на
                            // экран подтверждения.
                            resendRegistrationCode(email)
                        } else {
                            _uiState.value = AuthUiState(isLoading = false)
                            _codeSent.emit(Unit)
                        }
                    }
                }
            }
        }
    }

    private suspend fun resendRegistrationCode(email: String) {
        authRepository.resendVerification(email).collect { result ->
            when (result) {
                is Resource.Loading -> _uiState.value = AuthUiState(isLoading = true)
                is Resource.Error -> _uiState.value =
                    AuthUiState(isLoading = false, error = result.message)
                is Resource.Success -> {
                    _uiState.value = AuthUiState(isLoading = false)
                    _registrationCodeSent.emit(Unit)
                }
            }
        }
    }

    fun resendVerification(email: String) =
        run({ authRepository.resendVerification(email) }, sendsCode = true)

    fun verifyLoginCode(email: String, code: String) =
        run({ authRepository.verifyLoginCode(email, code) }, sendsCode = false)

    fun verifyEmail(email: String, code: String) =
        run({ authRepository.verifyEmail(email, code) }, sendsCode = false)

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun run(action: suspend () -> Flow<Resource<Unit>>, sendsCode: Boolean) {
        viewModelScope.launch {
            action().collect { result ->
                when (result) {
                    is Resource.Loading ->
                        _uiState.value = AuthUiState(isLoading = true, error = null)

                    is Resource.Success -> {
                        _uiState.value = AuthUiState(isLoading = false)
                        if (sendsCode) _codeSent.emit(Unit) else _authenticated.emit(Unit)
                    }

                    is Resource.Error ->
                        _uiState.value = AuthUiState(isLoading = false, error = result.message)
                }
            }
        }
    }
}
