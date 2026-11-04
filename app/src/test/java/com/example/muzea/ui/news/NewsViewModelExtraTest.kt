package com.example.muzea.ui.news

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.News
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.NewsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Дополнительные тесты NewsViewModel: покрывают пагинацию, переиспользование
 * контактов и разбор имён контактов, не задетыми NewsViewModelTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelExtraTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val newsRepository = mockk<NewsRepository>()
    private val contactRepository = mockk<ContactRepository>()

    private fun news(id: Long, author: String) = News(
        id = id,
        title = "Title $id",
        content = "Content $id",
        imageUrl = null,
        relatedVideo = null,
        author = author,
        publishedAt = "2026-01-01T00:00:00"
    )

    private fun contact(username: String?, contactName: String?) = Contact(
        contactUuid = "uuid-${username ?: contactName}",
        contactUserId = 1L,
        contactUserUuid = null,
        username = username,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = null,
        contactName = contactName,
        isOnline = false,
        lastSeenAt = null,
        addedAt = null
    )

    @Test
    fun `loadMoreNews fetches the next page when the feed is not finished`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
        coEvery { newsRepository.getNews(0, 2) } returns flowOf(
            Resource.Success(listOf(news(1, "vovan"), news(2, "vovan")))
        )
        coEvery { newsRepository.getNews(1, 2) } returns flowOf(
            Resource.Success(listOf(news(3, "vovan")))
        )

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 2, ownUsername = "vovan")

        assertEquals(listOf(1L, 2L), viewModel.feedState.value.news.map { it.id })
        assertFalse(viewModel.feedState.value.endReached)

        viewModel.loadMoreNews()

        assertEquals(listOf(1L, 2L, 3L), viewModel.feedState.value.news.map { it.id })
        assertTrue(viewModel.feedState.value.endReached)
        assertFalse(viewModel.feedState.value.isLoading)
        coVerify(exactly = 1) { newsRepository.getNews(1, 2) }
    }

    @Test
    fun `loadMoreNews does nothing while a load is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
        coEvery { newsRepository.getNews(any(), any()) } returns flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(emptyList()))
        }

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 2, ownUsername = "vovan")

        assertTrue(viewModel.feedState.value.isLoading)

        viewModel.loadMoreNews()

        coVerify(exactly = 1) { newsRepository.getNews(any(), any()) }

        gate.complete(Unit)
    }

    @Test
    fun `contacts fall back to contact name and skip blank entries`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(
                listOf(
                    contact(username = null, contactName = " maria "),
                    contact(username = "   ", contactName = null),
                    contact(username = null, contactName = null)
                )
            )
        )
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(
            Resource.Success(listOf(news(1, "maria"), news(2, "stranger")))
        )

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = null)

        assertEquals(listOf(1L), viewModel.feedState.value.news.map { it.id })
    }

    @Test
    fun `contacts are reused when a refresh does not force reload`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact(username = "maria", contactName = null)))
        )
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(
            Resource.Success(listOf(news(1, "maria")))
        )

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(ownUsername = "vovan")
        viewModel.loadNews(ownUsername = "vovan", forceRefreshContacts = false)

        assertEquals(listOf(1L), viewModel.feedState.value.news.map { it.id })
        coVerify(exactly = 1) { contactRepository.loadContacts() }
    }

    @Test
    fun `empty page marks the end of the feed`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
        coEvery { newsRepository.getNews(0, 20) } returns flowOf(Resource.Success(emptyList()))

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = "vovan")

        assertTrue(viewModel.feedState.value.endReached)
        assertTrue(viewModel.feedState.value.news.isEmpty())
        assertNull(viewModel.feedState.value.error)
    }

    @Test
    fun `loadNews stops after collecting enough visible items`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact(username = "a", contactName = null)))
        )
        coEvery { newsRepository.getNews(0, 3) } returns flowOf(
            Resource.Success(listOf(news(1, "a"), news(2, "a"), news(3, "a")))
        )

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 3, ownUsername = "vovan")

        assertEquals(listOf(1L, 2L, 3L), viewModel.feedState.value.news.map { it.id })
        assertFalse(viewModel.feedState.value.endReached)
        coVerify(exactly = 1) { newsRepository.getNews(any(), any()) }
    }

    @Test
    fun `loading-only contacts flow leaves only own news`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Loading())
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(
            Resource.Success(listOf(news(1, "stranger"), news(2, "vovan")))
        )

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = "vovan")

        assertEquals(listOf(2L), viewModel.feedState.value.news.map { it.id })
    }
}
