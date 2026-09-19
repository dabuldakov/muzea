package com.example.muzea.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.AvatarResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

class AvatarViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow<NetworkResult<AvatarResponse>>(NetworkResult.Loading())
    val state = _state.asStateFlow()
    private var busy = false

    init { load() }

    fun load() = request { repository.loadAvatar() }
    fun delete() = request { repository.deleteAvatar() }

    fun upload(file: File, mimeType: String) {
        if (busy) {
            file.delete()
            return
        }
        request(file) { repository.uploadAvatar(file, mimeType) }
    }

    private fun request(file: File? = null, action: suspend () -> Flow<NetworkResult<AvatarResponse>>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                action().collect { _state.value = it }
            } finally {
                file?.delete()
                busy = false
            }
        }
    }
}
