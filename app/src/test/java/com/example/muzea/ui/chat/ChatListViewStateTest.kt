package com.example.muzea.ui.chat

import com.example.muzea.data.model.ChatResponse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Регрессия: список чатов скрывался навсегда.
 *
 * [androidx.recyclerview.widget.ListAdapter.submitList] обновляет currentList
 * асинхронно, поэтому сразу после вызова адаптер ещё пуст. Отрисовка, которая
 * опиралась на adapter.currentList, помечала список пустым и прятала его.
 * Раньше это чинилось повторной отрисовкой (SharedFlow ре-эмитил каждый ответ),
 * но StateFlow схлопывает равные значения, и баг стал постоянным.
 */
class ChatListViewStateTest {

    private fun chat(uuid: String) = ChatResponse(
        chatUuid = uuid,
        chatType = "PRIVATE",
        title = "Chat $uuid",
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2L,
        lastMessage = null,
        unreadCount = 0L
    )

    @Test
    fun `server response shows the list even though adapter is not updated yet`() {
        val state = chatListViewState(
            chats = listOf(chat("a"), chat("b"), chat("c")),
            isLoading = false,
            error = null
        )

        assertEquals(ChatListViewState.LIST, state)
    }

    @Test
    fun `first server response is enough to show the list`() {
        // Именно этот сценарий ломался: список уже есть, показываем его сразу.
        val state = chatListViewState(chats = listOf(chat("a")), isLoading = false, error = null)

        assertEquals(ChatListViewState.LIST, state)
    }

    @Test
    fun `cached chats win over loading indicator`() {
        val state = chatListViewState(chats = listOf(chat("a")), isLoading = true, error = null)

        assertEquals(ChatListViewState.LIST, state)
    }

    @Test
    fun `cached chats win over error`() {
        val state = chatListViewState(
            chats = listOf(chat("a")),
            isLoading = false,
            error = "network down"
        )

        assertEquals(ChatListViewState.LIST, state)
    }

    @Test
    fun `loading is shown only when there is nothing to display`() {
        val state = chatListViewState(chats = emptyList(), isLoading = true, error = null)

        assertEquals(ChatListViewState.LOADING, state)
    }

    @Test
    fun `error is shown when there is nothing to display`() {
        val state = chatListViewState(chats = emptyList(), isLoading = false, error = "boom")

        assertEquals(ChatListViewState.ERROR, state)
    }

    @Test
    fun `loading wins over error while first load is running`() {
        val state = chatListViewState(chats = emptyList(), isLoading = true, error = "boom")

        assertEquals(ChatListViewState.LOADING, state)
    }

    @Test
    fun `empty placeholder is shown when load finished with nothing`() {
        val state = chatListViewState(chats = emptyList(), isLoading = false, error = null)

        assertEquals(ChatListViewState.EMPTY, state)
    }
}