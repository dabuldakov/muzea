package com.example.muzea.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.data.model.NewsCreateResponse
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class NewsViewModel(
    private val newsRepository: NewsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _newsResult = MutableSharedFlow<NetworkResult<List<NewsResponse>>>()
    val newsResult: SharedFlow<NetworkResult<List<NewsResponse>>> = _newsResult.asSharedFlow()

    private val _newsDetailResult = MutableSharedFlow<NetworkResult<NewsResponse>>()
    val newsDetailResult: SharedFlow<NetworkResult<NewsResponse>> = _newsDetailResult.asSharedFlow()

    private val _createNewsResult = MutableSharedFlow<NetworkResult<NewsCreateResponse>>()
    val createNewsResult: SharedFlow<NetworkResult<NewsCreateResponse>> = _createNewsResult.asSharedFlow()

    private var pageSize = 20

    private var isContactsLoaded = false
    private var contactUsernames = emptySet<String>()
    private var ownUsername: String? = null
    private var filterByContacts = false

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
                filterByContacts = loadContactUsernames()
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

    private suspend fun loadContactUsernames(): Boolean {
        return try {
            val result = chatRepository.loadContacts()
                .filter { it is NetworkResult.Success || it is NetworkResult.Error }
                .first()
            when (result) {
                is NetworkResult.Success -> {
                    contactUsernames = (result.data ?: emptyList())
                        .mapNotNull { it.username }
                        .toSet()
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
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

            val shouldStop = endReached ||
                visible.size >= pageSize ||
                (filterByContacts && contactUsernames.isEmpty() && ownUsername == null)
            if (shouldStop) return
        }
    }

    private suspend fun fetchRawPage(page: Int): List<NewsResponse>? {
        val result = newsRepository.getNews(page, pageSize)
            .filter { it is NetworkResult.Success || it is NetworkResult.Error }
            .first()
        return when (result) {
            is NetworkResult.Success -> result.data ?: emptyList()
            else -> null
        }
    }

    private fun visibleNews(): List<NewsResponse> {
        if (!filterByContacts) return rawNewsCache.toList()
        return rawNewsCache.filter { it.author in contactUsernames || it.author == ownUsername }
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
}