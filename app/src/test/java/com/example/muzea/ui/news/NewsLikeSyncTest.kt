package com.example.muzea.ui.news

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.News
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.NewsRepository
import com.example.muzea.domain.usecase.NewsLikeStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Лайк должен быть общим для ленты и детальной новости без refetch. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsLikeSyncTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val newsRepository = mockk<NewsRepository>()
    private val contactRepository = mockk<ContactRepository>()

    private fun news(liked: Boolean = false, likes: Long = 2) = News(
        id = 1,
        title = "Title",
        content = "Content",
        imageUrl = null,
        relatedVideo = null,
        author = "vovan",
        publishedAt = "2026-01-01T00:00:00",
        likes = likes,
        likedByMe = liked
    )

    private fun stubFeed(detail: News = news()) {
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(Resource.Success(listOf(detail)))
        coEvery { newsRepository.getLikedNewsIds() } returns flowOf(Resource.Success(emptySet()))
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(detail))
        coEvery { newsRepository.getComments(1, null, 20) } returns flowOf(
            Resource.Success(com.example.muzea.domain.model.NewsCommentsPage(emptyList(), 0, false, null))
        )
        coEvery { newsRepository.registerView(1) } returns Unit
    }

    @Test
    fun `like from the feed is immediately visible in the detail`() = runTest {
        stubFeed()
        coEvery { newsRepository.likeNews(1) } returns flowOf(Resource.Success(3))
        val store = NewsLikeStore(newsRepository)
        val feed = NewsViewModel(newsRepository, contactRepository, store)
        val detail = NewsDetailViewModel(newsRepository, store)

        feed.loadNews(ownUsername = "vovan")
        detail.load(1)
        feed.toggleLike(1)

        assertTrue(feed.feedState.value.news.single().likedByMe)
        assertTrue(detail.detailState.value.news!!.likedByMe)
        assertEquals(3L, detail.detailState.value.news!!.likes)
    }

    @Test
    fun `like from the detail is immediately visible in the feed`() = runTest {
        stubFeed()
        coEvery { newsRepository.likeNews(1) } returns flowOf(Resource.Success(3))
        val store = NewsLikeStore(newsRepository)
        val feed = NewsViewModel(newsRepository, contactRepository, store)
        val detail = NewsDetailViewModel(newsRepository, store)

        feed.loadNews(ownUsername = "vovan")
        detail.load(1)
        detail.toggleLike()

        assertTrue(detail.detailState.value.news!!.likedByMe)
        assertTrue(feed.feedState.value.news.single().likedByMe)
        assertEquals(3L, feed.feedState.value.news.single().likes)
    }

    @Test
    fun `unlike from the detail clears the like in the feed`() = runTest {
        stubFeed(detail = news(liked = true, likes = 3))
        coEvery { newsRepository.unlikeNews(1) } returns flowOf(Resource.Success(2))
        val store = NewsLikeStore(newsRepository)
        val feed = NewsViewModel(newsRepository, contactRepository, store)
        val detail = NewsDetailViewModel(newsRepository, store)

        feed.loadNews(ownUsername = "vovan")
        detail.load(1)
        detail.toggleLike()

        assertFalse(detail.detailState.value.news!!.likedByMe)
        assertFalse(feed.feedState.value.news.single().likedByMe)
        assertEquals(2L, feed.feedState.value.news.single().likes)
        coVerify(exactly = 1) { newsRepository.unlikeNews(1) }
    }
}
