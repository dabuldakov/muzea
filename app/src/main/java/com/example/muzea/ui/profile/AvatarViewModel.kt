package com.example.muzea.ui.profile

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.core.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

@HiltViewModel
class AvatarViewModel @Inject constructor(private val repository: AvatarRepository) : ViewModel() {
    private val _state = MutableStateFlow<Resource<Avatar>>(Resource.Loading())
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

    private fun request(file: File? = null, action: suspend () -> Flow<Resource<Avatar>>) {
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
