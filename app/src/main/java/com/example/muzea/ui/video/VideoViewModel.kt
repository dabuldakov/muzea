package com.example.muzea.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.VideoResponse
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class VideoViewModel(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _videosResult = MutableSharedFlow<NetworkResult<List<VideoResponse>>>(replay = 1)
    val videosResult: SharedFlow<NetworkResult<List<VideoResponse>>> = _videosResult.asSharedFlow()

    private val _videoDetailResult = MutableSharedFlow<NetworkResult<VideoResponse>>()
    val videoDetailResult: SharedFlow<NetworkResult<VideoResponse>> = _videoDetailResult.asSharedFlow()

    private val _uploadResult = MutableSharedFlow<NetworkResult<VideoResponse>>()
    val uploadResult: SharedFlow<NetworkResult<VideoResponse>> = _uploadResult.asSharedFlow()

    private var isLoading = false

    fun loadVideos(ownUsername: String? = null) {
        if (isLoading) return
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

    private fun visibleVideos(raw: List<VideoResponse>, ownUsername: String?): List<VideoResponse> {
        return VideoFeedFilter.filterOwn(raw, ownUsername)
    }

    fun loadVideoById(id: Long) {
        viewModelScope.launch {
            videoRepository.getVideoById(id).collect { result ->
                _videoDetailResult.emit(result)
            }
        }
    }

    fun uploadVideo(title: String, description: String?, filePart: MultipartBody.Part) {
        viewModelScope.launch {
            videoRepository.uploadVideo(title, description, filePart).collect { result ->
                _uploadResult.emit(result)
            }
        }
    }
}
