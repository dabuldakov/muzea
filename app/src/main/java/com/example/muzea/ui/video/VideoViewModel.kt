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

    private val _videosResult = MutableSharedFlow<NetworkResult<List<VideoResponse>>>()
    val videosResult: SharedFlow<NetworkResult<List<VideoResponse>>> = _videosResult.asSharedFlow()

    private val _videoDetailResult = MutableSharedFlow<NetworkResult<VideoResponse>>()
    val videoDetailResult: SharedFlow<NetworkResult<VideoResponse>> = _videoDetailResult.asSharedFlow()

    private val _uploadResult = MutableSharedFlow<NetworkResult<VideoResponse>>()
    val uploadResult: SharedFlow<NetworkResult<VideoResponse>> = _uploadResult.asSharedFlow()

    fun loadVideos() {
        viewModelScope.launch {
            videoRepository.getVideos().collect { result ->
                _videosResult.emit(result)
            }
        }
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