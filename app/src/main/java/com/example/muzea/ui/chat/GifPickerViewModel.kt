package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.repository.GifRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GifTab { RECENT, CHAT, SEARCH, LIBRARY }

data class GifPickerUiState(
    val tab: GifTab = GifTab.RECENT,
    val query: String = "",
    val items: List<Gif> = emptyList(),
    val isLoading: Boolean = false,
    val endReached: Boolean = false
)

/**
 * Состояние пикера гифок: вкладки «Мои»/«Этот чат»/«Поиск», постраничная
 * догрузка (30 на страницу). Поиск идёт у провайдера, списки — с нашего API.
 */
@HiltViewModel
class GifPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gifRepository: GifRepository
) : ViewModel() {

    private val chatUuid: String = savedStateHandle.get<String>(ARG_CHAT_UUID).orEmpty()

    private val _state = MutableStateFlow(GifPickerUiState())
    val state: StateFlow<GifPickerUiState> = _state.asStateFlow()

    private var page = 0
    private var loadJob: Job? = null

    init {
        // Первая страница вкладки по умолчанию («Мои»).
        loadNextPage()
    }

    fun selectTab(tab: GifTab) {
        if (_state.value.tab == tab) return
        _state.value = _state.value.copy(tab = tab)
        reload()
    }

    fun setQuery(query: String) {
        if (_state.value.query == query) return
        _state.value = _state.value.copy(query = query)
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        page = 0
        _state.value = _state.value.copy(items = emptyList(), endReached = false)
        loadNextPage()
    }

    fun loadNextPage() {
        if (_state.value.isLoading || _state.value.endReached) return
        val tab = _state.value.tab
        val query = _state.value.query
        _state.value = _state.value.copy(isLoading = true)

        loadJob = viewModelScope.launch {
            val items = when (tab) {
                GifTab.RECENT -> gifRepository.recent(page, PAGE_SIZE)
                GifTab.CHAT -> gifRepository.chatGifs(chatUuid, page, PAGE_SIZE)
                GifTab.SEARCH -> gifRepository.search(query, page, PAGE_SIZE)
                GifTab.LIBRARY -> gifRepository.library(query, page, PAGE_SIZE)
            }
            _state.value = _state.value.copy(
                items = _state.value.items + items,
                isLoading = false,
                endReached = items.size < PAGE_SIZE
            )
            page++
        }
    }

    companion object {
        const val ARG_CHAT_UUID = "chat_uuid"
        private const val PAGE_SIZE = 30
    }
}
