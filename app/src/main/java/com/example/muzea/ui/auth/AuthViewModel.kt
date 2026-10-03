package com.example.muzea.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние экрана входа/регистрации. */
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

    /** Одноразовое событие: успешный вход/регистрация → навигация на главный. */
    private val _authenticated = MutableSharedFlow<Unit>()
    val authenticated: SharedFlow<Unit> = _authenticated.asSharedFlow()

    fun login(username: String, password: String) = run { authRepository.login(username, password) }

    fun register(username: String, email: String, password: String, fullName: String) =
        run { authRepository.register(username, email, password, fullName) }

    fun logout() {
        authRepository.logout()
    }

    /** Ошибку показали — сбрасываем, чтобы не повторялась. */
    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun run(action: suspend () -> kotlinx.coroutines.flow.Flow<NetworkResult<String>>) {
        viewModelScope.launch {
            action().collect { result ->
                when (result) {
                    is NetworkResult.Loading -> _uiState.value =
                        AuthUiState(isLoading = true, error = null)

                    is NetworkResult.Success -> {
                        _uiState.value = AuthUiState(isLoading = false)
                        _authenticated.emit(Unit)
                    }

                    is NetworkResult.Error -> _uiState.value =
                        AuthUiState(isLoading = false, error = result.message)
                }
            }
        }
    }
}
