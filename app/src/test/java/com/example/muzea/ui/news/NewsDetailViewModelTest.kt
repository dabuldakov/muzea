package com.example.muzea.ui.news

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.domain.model.NewsCommentsPage
import com.example.muzea.domain.repository.NewsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<NewsRepository>()
    private val viewModel = NewsDetailViewModel(repository)

    private fun news(id: Long = 1L, author: String = "alice") = News(
        id = id,
        title = "Title",
        content = "Body",
        imageUrl = null,
        relatedVideo = null,
        author = author,
        publishedAt = "2026-01-01T00:00:00",
        views = 10,
        likes = 2,
        commentsCount = 0
    )

    private fun comment(id: Long, text: String = "Comment $id") = NewsComment(
        id = id,
        newsId = 1,
        content = text,
        author = "bob",
        createdAt = "2026-01-01T00:00:00"
    )

    private fun page(
        comments: List<NewsComment>,
        total: Long = comments.size.toLong(),
        hasMore: Boolean = false,
        nextBefore: Long? = null
    ) = NewsCommentsPage(comments, total, hasMore, nextBefore)

    private fun stubLoad(
        detail: News = news(),
        comments: NewsCommentsPage = page(emptyList())
    ) {
        coEvery { repository.getNewsById(1) } returns flowOf(Resource.Success(detail))
        coEvery { repository.getComments(1, null, 20) } returns flowOf(Resource.Success(comments))
        coEvery { repository.registerView(1) } returns Unit
    }

    @Test
    fun `load fills detail and comments and registers view once`() = runTest {
        stubLoad(comments = page(listOf(comment(1), comment(2)), total = 2))

        viewModel.load(1)
        viewModel.load(1)

        assertEquals("Title", viewModel.detailState.value.news?.title)
        assertEquals(listOf(1L, 2L), viewModel.commentsState.value.comments.map { it.id })
        assertEquals(2L, viewModel.commentsState.value.total)
        coVerify(exactly = 1) { repository.registerView(1) }
    }

    @Test
    fun `older comments are prepended and cursor follows`() = runTest {
        stubLoad(comments = page(listOf(comment(3)), total = 3, hasMore = true, nextBefore = 3))
        coEvery { repository.getComments(1, 3, 20) } returns flowOf(
            Resource.Success(page(listOf(comment(1), comment(2)), total = 3, hasMore = false))
        )

        viewModel.load(1)
        viewModel.loadOlderComments()

        assertEquals(listOf(1L, 2L, 3L), viewModel.commentsState.value.comments.map { it.id })
        assertEquals(3L, viewModel.commentsState.value.total)
        assertFalse(viewModel.commentsState.value.hasMore)
        assertNull(viewModel.commentsState.value.nextBefore)
    }

    @Test
    fun `addComment trims text appends and increments total`() = runTest {
        stubLoad()
        coEvery { repository.addComment(1, "Привет") } returns flowOf(
            Resource.Success(comment(5, "Привет"))
        )

        viewModel.load(1)
        viewModel.addComment("  Привет  ")

        assertEquals(listOf(5L), viewModel.commentsState.value.comments.map { it.id })
        assertEquals(1L, viewModel.commentsState.value.total)
        assertFalse(viewModel.commentsState.value.isSending)
    }

    @Test
    fun `blank comment is not sent`() = runTest {
        stubLoad()

        viewModel.load(1)
        viewModel.addComment("   ")

        coVerify(exactly = 0) { repository.addComment(any(), any()) }
    }

    @Test
    fun `deleteComment removes the row and decrements total`() = runTest {
        stubLoad(comments = page(listOf(comment(1), comment(2)), total = 2))
        coEvery { repository.deleteComment(1, 1) } returns flowOf(Resource.Success(Unit))

        viewModel.load(1)
        viewModel.deleteComment(1)

        assertEquals(listOf(2L), viewModel.commentsState.value.comments.map { it.id })
        assertEquals(1L, viewModel.commentsState.value.total)
    }

    @Test
    fun `like is optimistic and takes the server counter`() = runTest {
        stubLoad()
        coEvery { repository.likeNews(1) } returns flowOf(Resource.Success(5))

        viewModel.load(1)
        viewModel.toggleLike()

        assertTrue(viewModel.detailState.value.news!!.likedByMe)
        assertEquals(5L, viewModel.detailState.value.news!!.likes)
    }

    @Test
    fun `like error rolls the state back`() = runTest {
        stubLoad()
        coEvery { repository.likeNews(1) } returns flowOf(Resource.Error("offline"))

        viewModel.load(1)
        viewModel.toggleLike()

        assertFalse(viewModel.detailState.value.news!!.likedByMe)
        assertEquals(2L, viewModel.detailState.value.news!!.likes)
    }

    @Test
    fun `unlike is optimistically applied`() = runTest {
        stubLoad(detail = news().copy(likedByMe = true, likes = 3))
        coEvery { repository.unlikeNews(1) } returns flowOf(Resource.Success(2))

        viewModel.load(1)
        viewModel.toggleLike()

        assertFalse(viewModel.detailState.value.news!!.likedByMe)
        assertEquals(2L, viewModel.detailState.value.news!!.likes)
    }

    @Test
    fun `deleteNews emits an event`() = runTest {
        stubLoad()
        coEvery { repository.deleteNews(1) } returns flowOf(Resource.Success(Unit))

        viewModel.load(1)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.newsDeleted.first() }
        viewModel.deleteNews()

        assertEquals(Unit, event.await())
    }


    @Test
    fun `detail error is exposed`() = runTest {
        coEvery { repository.getNewsById(1) } returns flowOf(Resource.Error("offline"))
        coEvery { repository.getComments(1, null, 20) } returns flowOf(
            Resource.Success(page(emptyList()))
        )
        coEvery { repository.registerView(1) } returns Unit

        viewModel.load(1)

        assertEquals("offline", viewModel.detailState.value.error)
        assertNull(viewModel.detailState.value.news)
        assertFalse(viewModel.detailState.value.isLoading)
    }

    @Test
    fun `comments error is exposed without losing the article`() = runTest {
        coEvery { repository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        coEvery { repository.getComments(1, null, 20) } returns flowOf(Resource.Error("no comments"))
        coEvery { repository.registerView(1) } returns Unit

        viewModel.load(1)

        assertEquals("Title", viewModel.detailState.value.news?.title)
        assertEquals("no comments", viewModel.commentsState.value.error)
        assertFalse(viewModel.commentsState.value.isLoading)
    }

    @Test
    fun `older comments error restores loading flag`() = runTest {
        stubLoad(comments = page(listOf(comment(3)), total = 3, hasMore = true, nextBefore = 3))
        coEvery { repository.getComments(1, 3, 20) } returns flowOf(Resource.Error("offline"))

        viewModel.load(1)
        viewModel.loadOlderComments()

        assertFalse(viewModel.commentsState.value.isLoading)
        assertEquals("offline", viewModel.commentsState.value.error)
        assertEquals(listOf(3L), viewModel.commentsState.value.comments.map { it.id })
    }

    @Test
    fun `add comment error restores sending flag`() = runTest {
        stubLoad()
        coEvery { repository.addComment(1, "Hi") } returns flowOf(Resource.Error("offline"))

        viewModel.load(1)
        viewModel.addComment("Hi")

        assertFalse(viewModel.commentsState.value.isSending)
        assertEquals("offline", viewModel.commentsState.value.error)
        assertTrue(viewModel.commentsState.value.comments.isEmpty())
    }

    @Test
    fun `delete comment error keeps the row`() = runTest {
        stubLoad(comments = page(listOf(comment(1), comment(2)), total = 2))
        coEvery { repository.deleteComment(1, 1) } returns flowOf(Resource.Error("offline"))

        viewModel.load(1)
        viewModel.deleteComment(1)

        assertEquals(listOf(1L, 2L), viewModel.commentsState.value.comments.map { it.id })
        assertEquals(2L, viewModel.commentsState.value.total)
    }

    @Test
    fun `unlike error rolls the state back`() = runTest {
        stubLoad(detail = news().copy(likedByMe = true, likes = 3))
        coEvery { repository.unlikeNews(1) } returns flowOf(Resource.Error("offline"))

        viewModel.load(1)
        viewModel.toggleLike()

        assertTrue(viewModel.detailState.value.news!!.likedByMe)
        assertEquals(3L, viewModel.detailState.value.news!!.likes)
    }

    @Test
    fun `duplicate comment is not appended twice`() = runTest {
        stubLoad(comments = page(listOf(comment(5)), total = 1))
        coEvery { repository.addComment(1, "Hi") } returns flowOf(
            Resource.Success(comment(5, "Hi"))
        )

        viewModel.load(1)
        viewModel.addComment("Hi")

        assertEquals(listOf(5L), viewModel.commentsState.value.comments.map { it.id })
        assertEquals(2L, viewModel.commentsState.value.total)
    }

    @Test
    fun `report methods delegate to repository`() = runTest {
        stubLoad()
        viewModel.load(1)
        coEvery { repository.reportNews(1, "SPAM") } returns true
        coEvery { repository.reportComment(2, "ABUSE") } returns false

        assertTrue(viewModel.reportNews("SPAM"))
        assertFalse(viewModel.reportComment(2, "ABUSE"))
    }

    @Test
    fun `reload does not register the view twice`() = runTest {
        stubLoad()

        viewModel.load(1)
        viewModel.reload()

        coVerify(exactly = 1) { repository.registerView(1) }
    }
}
