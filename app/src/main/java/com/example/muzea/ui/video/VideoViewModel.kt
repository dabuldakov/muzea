package com.example.muzea.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Video
import com.example.muzea.domain.repository.VideoRepository
import com.example.muzea.core.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import javax.inject.Inject

/** Единое состояние ленты видео для экрана. */
data class VideoFeedUiState(
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow(VideoFeedUiState())
    val feedState: StateFlow<VideoFeedUiState> = _feedState.asStateFlow()

    private val _videoDetailResult = MutableSharedFlow<Resource<Video>>()
    val videoDetailResult: SharedFlow<Resource<Video>> = _videoDetailResult.asSharedFlow()

    private val _uploadResult = MutableSharedFlow<Resource<Video>>()
    val uploadResult: SharedFlow<Resource<Video>> = _uploadResult.asSharedFlow()

    private val _deleteResult = MutableSharedFlow<Resource<Unit>>()
    val deleteResult: SharedFlow<Resource<Unit>> = _deleteResult.asSharedFlow()

    private var feedJob: Job? = null
    private var currentUsername: String? = null

    fun loadVideos(ownUsername: String? = null) {
        if (ownUsername != null) currentUsername = ownUsername
        if (feedJob?.isActive == true) return

        // Сначала отдаём кэш, чтобы вкладка показалась мгновенно, и только
        // затем идём в сеть за свежими данными.
        if (_feedState.value.videos.isEmpty()) {
            val cached = videoRepository.cachedVideos()
            if (cached.isNotEmpty()) {
                _feedState.value = _feedState.value.copy(videos = visibleVideos(cached, currentUsername))
            }
        }

        feedJob = viewModelScope.launch {
            _feedState.value = _feedState.value.copy(isLoading = _feedState.value.videos.isEmpty())
            videoRepository.getVideos().collect { result ->
                when (result) {
                    is Resource.Success -> _feedState.value = _feedState.value.copy(
                        videos = visibleVideos(result.data ?: emptyList(), currentUsername),
                        isLoading = false,
                        error = null
                    )
                    is Resource.Error -> _feedState.value = _feedState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    /** Ошибку показали (тост) — сбрасываем, чтобы не повторялась. */
    fun consumeFeedError() {
        _feedState.value = _feedState.value.copy(error = null)
    }

    private fun visibleVideos(raw: List<Video>, ownUsername: String?): List<Video> {
        return VideoFeedFilter.filterOwn(raw, ownUsername)
    }

    fun loadVideoById(id: Long) {
        viewModelScope.launch {
            videoRepository.getVideoById(id).collect { result ->
                _videoDetailResult.emit(result)
            }
        }
    }

    fun uploadVideo(
        title: String,
        description: String?,
        filePart: MultipartBody.Part,
        thumbnailPart: MultipartBody.Part?
    ) {
        viewModelScope.launch {
            videoRepository.uploadVideo(title, description, filePart, thumbnailPart).collect { result ->
                _uploadResult.emit(result)
            }
        }
    }

    fun deleteVideo(id: Long) {
        viewModelScope.launch {
            videoRepository.deleteVideo(id).collect { result ->
                _deleteResult.emit(result)
            }
        }
    }
}
