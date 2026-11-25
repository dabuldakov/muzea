package com.example.muzea.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.core.Resource
import com.example.muzea.core.firstTerminal
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние статьи в детали новости. */
data class NewsDetailUiState(
    val news: News? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/** Состояние комментариев: список по возрастанию id. */
data class NewsCommentsUiState(
    val comments: List<NewsComment> = emptyList(),
    val total: Long = 0,
    val hasMore: Boolean = false,
    val nextBefore: Long? = null,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    private val newsRepository: NewsRepository
) : ViewModel() {

    private val _detailState = MutableStateFlow(NewsDetailUiState())
    val detailState: StateFlow<NewsDetailUiState> = _detailState.asStateFlow()

    private val _commentsState = MutableStateFlow(NewsCommentsUiState())
    val commentsState: StateFlow<NewsCommentsUiState> = _commentsState.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    private val _commentAdded = MutableSharedFlow<NewsComment>()
    val commentAdded: SharedFlow<NewsComment> = _commentAdded.asSharedFlow()

    private val _newsDeleted = MutableSharedFlow<Unit>()
    val newsDeleted: SharedFlow<Unit> = _newsDeleted.asSharedFlow()

    private var newsId: Long = 0
    private var initialized = false
    private var viewRegistered = false
    private var likeInFlight = false

    private companion object {
        const val COMMENTS_PAGE_SIZE = 20
    }

    fun load(id: Long) {
        if (initialized && newsId == id) return
        newsId = id
        initialized = true
        loadNews()
        loadComments()
        if (!viewRegistered) {
            viewRegistered = true
            viewModelScope.launch {
                newsRepository.registerView(id)
                // Счётчик на сервере уже увеличен: показываем это сразу, не
                // дожидаясь следующего GET.
                _detailState.value.news?.let { current ->
                    _detailState.value = _detailState.value.copy(
                        news = current.copy(views = current.views + 1)
                    )
                }
            }
        }
    }

    /** Перезагрузка после редактирования: просмотр повторно не учитываем. */
    fun reload() {
        if (newsId == 0L) return
        loadNews()
        loadComments()
    }

    private fun loadNews() {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isLoading = true, error = null)
            when (val result = newsRepository.getNewsById(newsId).firstTerminal()) {
                is Resource.Success -> _detailState.value = NewsDetailUiState(
                    news = result.data,
                    isLoading = false
                )
                is Resource.Error -> _detailState.value = _detailState.value.copy(
                    isLoading = false,
                    error = result.message
                )
                is Resource.Loading -> Unit
            }
        }
    }

    fun loadComments() {
        if (_commentsState.value.isLoading) return
        viewModelScope.launch {
            _commentsState.value = _commentsState.value.copy(isLoading = true, error = null)
            val result = newsRepository.getComments(
                newsId = newsId,
                before = null,
                size = COMMENTS_PAGE_SIZE
            ).firstTerminal()
            when (result) {
                is Resource.Success -> {
                    val page = result.data
                    _commentsState.value = if (page == null) {
                        _commentsState.value.copy(isLoading = false)
                    } else {
                        NewsCommentsUiState(
                            comments = page.comments,
                            total = page.total,
                            hasMore = page.hasMore,
                            nextBefore = page.nextBefore,
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> _commentsState.value = _commentsState.value.copy(
                    isLoading = false,
                    error = result.message
                )
                is Resource.Loading -> Unit
            }
        }
    }

    fun loadOlderComments() {
        val state = _commentsState.value
        val cursor = state.nextBefore ?: return
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            _commentsState.value = state.copy(isLoading = true, error = null)
            val result = newsRepository.getComments(
                newsId = newsId,
                before = cursor,
                size = COMMENTS_PAGE_SIZE
            ).firstTerminal()
            when (result) {
                is Resource.Success -> {
                    val page = result.data
                    if (page == null) {
                        _commentsState.value = _commentsState.value.copy(isLoading = false)
                        return@launch
                    }
                    val merged = (page.comments + _commentsState.value.comments)
                        .distinctBy { it.id }
                        .sortedBy { it.id }
                    _commentsState.value = _commentsState.value.copy(
                        comments = merged,
                        total = page.total,
                        hasMore = page.hasMore,
                        nextBefore = page.nextBefore,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    _commentsState.value = _commentsState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                    _message.emit(result.message ?: "Failed to load comments")
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun addComment(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _commentsState.value.isSending) return
        viewModelScope.launch {
            _commentsState.value = _commentsState.value.copy(isSending = true, error = null)
            when (val result = newsRepository.addComment(newsId, text).firstTerminal()) {
                is Resource.Success -> {
                    val comment = result.data
                    if (comment != null) {
                        val comments = (_commentsState.value.comments + comment)
                            .distinctBy { it.id }
                            .sortedBy { it.id }
                        _commentsState.value = _commentsState.value.copy(
                            comments = comments,
                            total = _commentsState.value.total + 1,
                            isSending = false
                        )
                        _commentAdded.emit(comment)
                    } else {
                        _commentsState.value = _commentsState.value.copy(isSending = false)
                    }
                }
                is Resource.Error -> {
                    _commentsState.value = _commentsState.value.copy(
                        isSending = false,
                        error = result.message
                    )
                    _message.emit(result.message ?: "Failed to send comment")
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun deleteComment(commentId: Long) {
        viewModelScope.launch {
            when (val result = newsRepository.deleteComment(newsId, commentId).firstTerminal()) {
                is Resource.Success -> {
                    val comments = _commentsState.value.comments.filterNot { it.id == commentId }
                    _commentsState.value = _commentsState.value.copy(
                        comments = comments,
                        total = (_commentsState.value.total - 1).coerceAtLeast(0)
                    )
                }
                is Resource.Error -> _message.emit(result.message ?: "Failed to delete comment")
                is Resource.Loading -> Unit
            }
        }
    }

    fun toggleLike() {
        val news = _detailState.value.news ?: return
        if (likeInFlight) return
        likeInFlight = true
        val targetLiked = !news.likedByMe
        val optimistic = news.copy(
            likedByMe = targetLiked,
            likes = (news.likes + if (targetLiked) 1 else -1).coerceAtLeast(0)
        )
        _detailState.value = _detailState.value.copy(news = optimistic)

        viewModelScope.launch {
            val request = if (targetLiked) {
                newsRepository.likeNews(news.id)
            } else {
                newsRepository.unlikeNews(news.id)
            }
            when (val result = request.firstTerminal()) {
                is Resource.Success -> {
                    val current = _detailState.value.news
                    if (current != null) {
                        _detailState.value = _detailState.value.copy(
                            news = current.copy(likes = result.data ?: current.likes)
                        )
                    }
                }
                is Resource.Error -> {
                    _detailState.value = _detailState.value.copy(news = news)
                    _message.emit(result.message ?: "Failed to update like")
                }
                is Resource.Loading -> Unit
            }
            likeInFlight = false
        }
    }

    fun deleteNews() {
        viewModelScope.launch {
            when (val result = newsRepository.deleteNews(newsId).firstTerminal()) {
                is Resource.Success -> _newsDeleted.emit(Unit)
                is Resource.Error -> _message.emit(result.message ?: "Failed to delete news")
                is Resource.Loading -> Unit
            }
        }
    }

    suspend fun reportNews(reason: String): Boolean =
        newsRepository.reportNews(newsId, reason)

    suspend fun reportComment(commentId: Long, reason: String): Boolean =
        newsRepository.reportComment(commentId, reason)
}
