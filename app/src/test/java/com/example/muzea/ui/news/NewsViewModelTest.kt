package com.example.muzea.ui.news

import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.model.News
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.NewsRepository
import com.example.muzea.core.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

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

    private fun contact(username: String) = Contact(
        contactUuid = "uuid-$username",
        contactUserId = 1L,
        contactUserUuid = null,
        username = username,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = null,
        contactName = null,
        isOnline = false,
        lastSeenAt = null,
        addedAt = null
    )

    @Test
    fun `loadNews emits only contact and own news`() = runTest {
        val feed = listOf(
            news(1, "dabuldakov"),
            news(2, "maria"),
            news(3, "stranger"),
            news(4, "vovan")
        )
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact("dabuldakov"), contact("maria")))
        )
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(Resource.Success(feed))

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = "vovan")

        assertEquals(listOf(1L, 2L, 4L), viewModel.feedState.value.news.map { it.id })
    }

    @Test
    fun `loadNews fetches next pages until enough visible items`() = runTest {
        val strangers = (1..20).map { news(it.toLong(), "stranger") }
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact("dabuldakov")))
        )
        coEvery { newsRepository.getNews(0, 20) } returns flowOf(Resource.Success(strangers))
        coEvery { newsRepository.getNews(1, 20) } returns flowOf(
            Resource.Success(listOf(news(21, "dabuldakov"), news(22, "vovan")))
        )

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = "vovan")

        assertEquals(listOf(21L, 22L), viewModel.feedState.value.news.map { it.id })

        coVerify { newsRepository.getNews(1, 20) }
    }

    @Test
    fun `loadNews shows only own news when contacts cannot be loaded`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Error("chat backend down"))
        val feed = listOf(news(1, "stranger"), news(2, "dabuldakov"), news(3, "vovan"))
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(Resource.Success(feed))

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = "vovan")

        assertEquals(listOf(3L), viewModel.feedState.value.news.map { it.id })
    }

    @Test
    fun `loadNews emits error when news fetch fails`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flowOf(
            Resource.Success(listOf(contact("dabuldakov")))
        )
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(Resource.Error("network down"))

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = "vovan")

        assertTrue(viewModel.feedState.value.error != null)
    }

    @Test
    fun `loadNews shows nothing when contacts load throws and user is unknown`() = runTest {
        coEvery { contactRepository.loadContacts() } returns flow {
            throw IllegalStateException("boom")
        }
        val feed = listOf(news(1, "stranger"), news(2, "vovan"))
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(Resource.Success(feed))

        val viewModel = NewsViewModel(newsRepository, contactRepository)
        viewModel.loadNews(pageSize = 20, ownUsername = null)

        assertTrue(viewModel.feedState.value.news.isEmpty())
    }
}