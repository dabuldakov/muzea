package com.example.muzea.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class NewsViewModel(
    private val newsRepository: NewsRepository
) : ViewModel() {

    private val _newsResult = MutableSharedFlow<NetworkResult<List<NewsResponse>>>()
    val newsResult: SharedFlow<NetworkResult<List<NewsResponse>>> = _newsResult.asSharedFlow()

    private val _newsDetailResult = MutableSharedFlow<NetworkResult<NewsResponse>>()
    val newsDetailResult: SharedFlow<NetworkResult<NewsResponse>> = _newsDetailResult.asSharedFlow()

    private val _createNewsResult = MutableSharedFlow<NetworkResult<NewsResponse>>()
    val createNewsResult: SharedFlow<NetworkResult<NewsResponse>> = _createNewsResult.asSharedFlow()

    fun loadNews(page: Int = 0, size: Int = 20) {
        viewModelScope.launch {
            newsRepository.getNews(page, size).collect { result ->
                _newsResult.emit(result)
            }
        }
    }

    fun loadNewsById(id: Long) {
        viewModelScope.launch {
            newsRepository.getNewsById(id).collect { result ->
                _newsDetailResult.emit(result)
            }
        }
    }

    fun createNews(title: String, content: String, videoId: Long?) {
        viewModelScope.launch {
            newsRepository.createNews(title, content, videoId).collect { result ->
                _createNewsResult.emit(result)
            }
        }
    }
}