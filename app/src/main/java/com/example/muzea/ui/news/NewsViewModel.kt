package com.example.muzea.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.NewsCreateResponse
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class NewsViewModel(
    private val newsRepository: NewsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _newsResult = MutableSharedFlow<NetworkResult<List<NewsResponse>>>(replay = 1)
    val newsResult: SharedFlow<NetworkResult<List<NewsResponse>>> = _newsResult.asSharedFlow()

    private val _newsDetailResult = MutableSharedFlow<NetworkResult<NewsResponse>>()
    val newsDetailResult: SharedFlow<NetworkResult<NewsResponse>> = _newsDetailResult.asSharedFlow()

    private val _createNewsResult = MutableSharedFlow<NetworkResult<NewsCreateResponse>>()
    val createNewsResult: SharedFlow<NetworkResult<NewsCreateResponse>> = _createNewsResult.asSharedFlow()

    private val _deleteNewsResult = MutableSharedFlow<NetworkResult<Unit>>()
    val deleteNewsResult: SharedFlow<NetworkResult<Unit>> = _deleteNewsResult.asSharedFlow()

    private var pageSize = 20

    private var isContactsLoaded = false
    private var contactUsernames = emptySet<String>()
    private var ownUsername: String? = null

    private var isLoading = false
    private var endReached = false
    private var nextPage = 0
    private val rawNewsCache = mutableListOf<NewsResponse>()

    fun loadNews(pageSize: Int = 20, ownUsername: String? = null, forceRefreshContacts: Boolean = true) {
        if (isLoading) return
        this.pageSize = pageSize
        this.ownUsername = ownUsername
        if (forceRefreshContacts) isContactsLoaded = false
        viewModelScope.launch {
            isLoading = true
            endReached = false
            nextPage = 0
            rawNewsCache.clear()

            if (!isContactsLoaded) {
                isContactsLoaded = true
                contactUsernames = loadContactUsernames()
            }

            fillFeed()
            isLoading = false
        }
    }

    fun loadMoreNews() {
        if (isLoading || endReached) return
        viewModelScope.launch {
            isLoading = true
            fillFeed()
            isLoading = false
        }
    }

    private suspend fun loadContactUsernames(): Set<String> {
        return try {
            val result = chatRepository.loadContacts().firstTerminal()
            when (result) {
                is NetworkResult.Success -> (result.data ?: emptyList())
                    .mapNotNull { (it.username ?: it.contactName)?.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                else -> emptySet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private suspend fun fillFeed() {
        while (!endReached) {
            val items = fetchRawPage(nextPage)
            if (items == null) {
                _newsResult.emit(NetworkResult.Error("Failed to load news"))
                return
            }

            if (items.isEmpty()) {
                endReached = true
            } else {
                rawNewsCache.addAll(items)
                nextPage++
                if (items.size < pageSize) endReached = true
            }

            val visible = visibleNews()
            _newsResult.emit(NetworkResult.Success(visible))

            if (visible.size >= pageSize) return
            if (endReached) return
        }
    }

    private suspend fun fetchRawPage(page: Int): List<NewsResponse>? {
        val result = newsRepository.getNews(page, pageSize).firstTerminal()
        return when (result) {
            is NetworkResult.Success -> result.data ?: emptyList()
            else -> null
        }
    }

    private suspend fun <T> Flow<NetworkResult<T>>.firstTerminal(): NetworkResult<T>? {
        var result: NetworkResult<T>? = null
        collect { r ->
            if (r is NetworkResult.Success || r is NetworkResult.Error) {
                result = r
            }
        }
        return result
    }

    private fun visibleNews(): List<NewsResponse> {
        return NewsFeedFilter.filterByContacts(rawNewsCache, contactUsernames, ownUsername)
    }

    fun loadNewsById(id: Long) {
        viewModelScope.launch {
            newsRepository.getNewsById(id).collect { result ->
                _newsDetailResult.emit(result)
            }
        }
    }

    fun createNews(title: String, content: String, videoId: Long?, imageFile: File?) {
        viewModelScope.launch {
            newsRepository.createNews(title, content, videoId, imageFile).collect { result ->
                _createNewsResult.emit(result)
            }
        }
    }

    fun deleteNews(id: Long) {
        viewModelScope.launch {
            newsRepository.deleteNews(id).collect { result ->
                _deleteNewsResult.emit(result)
            }
        }
    }
}