package com.example.muzea.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.core.firstTerminal
import com.example.muzea.domain.model.News
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.NewsRepository
import com.example.muzea.core.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Единое состояние ленты новостей. */
data class NewsFeedUiState(
    val news: List<News> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _feedState = MutableStateFlow(NewsFeedUiState())
    val feedState: StateFlow<NewsFeedUiState> = _feedState.asStateFlow()

    private val _newsDetailResult = MutableSharedFlow<Resource<News>>()
    val newsDetailResult: SharedFlow<Resource<News>> = _newsDetailResult.asSharedFlow()

    private val _createNewsResult = MutableSharedFlow<Resource<Long>>()
    val createNewsResult: SharedFlow<Resource<Long>> = _createNewsResult.asSharedFlow()

    private val _deleteNewsResult = MutableSharedFlow<Resource<Unit>>()
    val deleteNewsResult: SharedFlow<Resource<Unit>> = _deleteNewsResult.asSharedFlow()

    private var pageSize = 20
    private var isContactsLoaded = false
    private var contactUsernames = emptySet<String>()
    private var ownUsername: String? = null

    private var isLoading = false
    private var endReached = false
    private var nextPage = 0
    private val rawNewsCache = mutableListOf<News>()

    fun loadNews(
        pageSize: Int = 20,
        ownUsername: String? = null,
        forceRefreshContacts: Boolean = true,
        fromRefresh: Boolean = false
    ) {
        if (isLoading) return
        this.pageSize = pageSize
        this.ownUsername = ownUsername
        if (forceRefreshContacts) isContactsLoaded = false
        viewModelScope.launch {
            isLoading = true
            endReached = false
            nextPage = 0
            rawNewsCache.clear()
            _feedState.value = _feedState.value.copy(
                isLoading = true,
                isRefreshing = fromRefresh,
                error = null
            )

            if (!isContactsLoaded) {
                isContactsLoaded = true
                contactUsernames = loadContactUsernames()
            }

            fillFeed()
            isLoading = false
            _feedState.value = _feedState.value.copy(
                isLoading = false,
                isRefreshing = false,
                endReached = endReached
            )
        }
    }

    fun loadMoreNews() {
        if (isLoading || endReached) return
        viewModelScope.launch {
            isLoading = true
            _feedState.value = _feedState.value.copy(isLoading = true)
            fillFeed()
            isLoading = false
            _feedState.value = _feedState.value.copy(isLoading = false, endReached = endReached)
        }
    }

    /** Ошибку показали — сбрасываем, чтобы не повторялась. */
    fun consumeError() {
        _feedState.value = _feedState.value.copy(error = null)
    }

    private suspend fun loadContactUsernames(): Set<String> {
        return try {
            val result = contactRepository.loadContacts().firstTerminal()
            when (result) {
                is Resource.Success -> (result.data ?: emptyList())
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
                _feedState.value = _feedState.value.copy(error = "Failed to load news")
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
            _feedState.value = _feedState.value.copy(news = visible, error = null)

            if (visible.size >= pageSize) return
            if (endReached) return
        }
    }

    private suspend fun fetchRawPage(page: Int): List<News>? {
        val result = newsRepository.getNews(page, pageSize).firstTerminal()
        return when (result) {
            is Resource.Success -> result.data ?: emptyList()
            else -> null
        }
    }

    private fun visibleNews(): List<News> {
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
