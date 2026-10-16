package com.example.muzea.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.example.muzea.domain.model.Gif
import com.example.muzea.domain.repository.GifRepository
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GifPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<GifRepository>()
    private val chatUuid = "chat-1"

    private fun gif(id: String) = Gif("klipy", id, null, "u$id", "p$id", 1, 1)

    private fun viewModel() = GifPickerViewModel(
        SavedStateHandle(mapOf(GifPickerViewModel.ARG_CHAT_UUID to chatUuid)),
        repository
    )

    @Test
    fun `default tab loads my recent gifs`() {
        coEvery { repository.recent(0, 30) } returns listOf(gif("a"))

        val vm = viewModel()

        assertEquals(GifTab.RECENT, vm.state.value.tab)
        assertEquals(listOf("a"), vm.state.value.items.map { it.providerGifId })
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `chat tab loads gifs of the chat with its uuid`() {
        coEvery { repository.recent(0, 30) } returns emptyList()
        coEvery { repository.chatGifs(chatUuid, 0, 30) } returns listOf(gif("c"))

        val vm = viewModel()
        vm.selectTab(GifTab.CHAT)

        assertEquals(listOf("c"), vm.state.value.items.map { it.providerGifId })
        coVerify { repository.chatGifs(chatUuid, 0, 30) }
    }

    @Test
    fun `search tab queries provider`() {
        coEvery { repository.recent(0, 30) } returns emptyList()
        coEvery { repository.search("", 0, 30) } returns emptyList()
        coEvery { repository.search("cat", 0, 30) } returns listOf(gif("s1"))

        val vm = viewModel()
        vm.selectTab(GifTab.SEARCH)
        vm.setQuery("cat")

        assertEquals(listOf("s1"), vm.state.value.items.map { it.providerGifId })
        coVerify { repository.search("cat", 0, 30) }
    }

    @Test
    fun `full page keeps loading and short page stops`() {
        val firstPage = (1..30).map { gif("p$it") }
        coEvery { repository.recent(0, 30) } returns firstPage
        coEvery { repository.recent(1, 30) } returns listOf(gif("last"))

        val vm = viewModel()
        assertFalse(vm.state.value.endReached)

        vm.loadNextPage()

        assertEquals(31, vm.state.value.items.size)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `does not load again when history ended`() {
        coEvery { repository.recent(0, 30) } returns listOf(gif("only"))

        val vm = viewModel()
        vm.loadNextPage()
        vm.loadNextPage()

        coVerify(exactly = 1) { repository.recent(0, 30) }
    }
}
