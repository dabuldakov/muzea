package com.example.muzea.ui.video

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Video
import com.example.muzea.domain.repository.VideoRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _videosResult = MutableSharedFlow<NetworkResult<List<Video>>>(replay = 1)
    val videosResult: SharedFlow<NetworkResult<List<Video>>> = _videosResult.asSharedFlow()

    private val _videoDetailResult = MutableSharedFlow<NetworkResult<Video>>()
    val videoDetailResult: SharedFlow<NetworkResult<Video>> = _videoDetailResult.asSharedFlow()

    private val _uploadResult = MutableSharedFlow<NetworkResult<Video>>()
    val uploadResult: SharedFlow<NetworkResult<Video>> = _uploadResult.asSharedFlow()

    private val _deleteResult = MutableSharedFlow<NetworkResult<Unit>>()
    val deleteResult: SharedFlow<NetworkResult<Unit>> = _deleteResult.asSharedFlow()

    private var isLoading = false

    fun loadVideos(ownUsername: String? = null) {
        if (isLoading) return

        // Сначала отдаём кэш, чтобы вкладка показалась мгновенно, и только
        // затем идём в сеть за свежими данными.
        if (_videosResult.replayCache.isEmpty()) {
            val cached = videoRepository.cachedVideos()
            if (cached.isNotEmpty()) {
                _videosResult.tryEmit(NetworkResult.Success(visibleVideos(cached, ownUsername)))
            }
        }

        viewModelScope.launch {
            isLoading = true
            _videosResult.emit(NetworkResult.Loading())

            videoRepository.getVideos().collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        val raw = result.data ?: emptyList()
                        _videosResult.emit(NetworkResult.Success(visibleVideos(raw, ownUsername)))
                    }
                    is NetworkResult.Error -> _videosResult.emit(result)
                    is NetworkResult.Loading -> Unit
                }
            }
            isLoading = false
        }
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

    fun uploadVideo(title: String, description: String?, filePart: MultipartBody.Part, thumbnailPart: MultipartBody.Part?) {
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
