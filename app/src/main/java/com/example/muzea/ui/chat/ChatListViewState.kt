package com.example.muzea.ui.chat

import com.example.muzea.data.model.ChatResponse

/** Что показывать на экране списка чатов. */
enum class ChatListViewState {
    /** Есть что показать: список. */
    LIST,

    /** Показать нечего, показать заглушку «чатов нет». */
    EMPTY,

    /** Показать нечего, показать текст ошибки. */
    ERROR,

    /** Показать нечего и данные ещё идут: показать индикатор. */
    LOADING
}

/**
 * Решает, что показывать на экране списка чатов.
 *
 * Считается от переданного списка, а **не** от `RecyclerView.adapter.currentList`:
 * `ListAdapter.submitList()` обновляет список асинхронно, поэтому сразу после
 * вызова адаптер ещё пуст. Если опираться на него, список от первого же
 * ответа сервера помечается пустым и прячется. Раньше это маскировалось тем,
 * что `SharedFlow` ре-эмитил каждый ответ и вторая отрисовка всё чинила;
 * `StateFlow` схлопывает равные значения, и после перехода на него пустое
 * состояние залипало уже навсегда.
 */
internal fun chatListViewState(
    chats: List<ChatResponse>,
    isLoading: Boolean,
    error: String?
): ChatListViewState = when {
    chats.isNotEmpty() -> ChatListViewState.LIST
    isLoading -> ChatListViewState.LOADING
    error != null -> ChatListViewState.ERROR
    else -> ChatListViewState.EMPTY
}