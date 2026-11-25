package com.example.muzea.ui.news

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.HiltTestActivity
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.di.ContentRepositoryModule
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.domain.model.NewsCommentsPage
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.GifRepository
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.domain.repository.NewsRepository
import com.example.muzea.domain.repository.UserRepository
import com.example.muzea.domain.repository.VideoRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тесты детальной новости: рендер, ошибка, неверный id. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class, ContentRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class NewsDetailFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue @JvmField val chatRepository: ChatRepository = mockk(relaxed = true)
    @BindValue @JvmField val messageRepository: MessageRepository = mockk(relaxed = true)
    @BindValue @JvmField val gifRepository: GifRepository = mockk(relaxed = true)
    @BindValue @JvmField val contactRepository: ContactRepository = mockk(relaxed = true)
    @BindValue @JvmField val avatarRepository: AvatarRepository = mockk(relaxed = true)
    @BindValue @JvmField val chatSessionRepository: ChatSessionRepository = mockk(relaxed = true)
    @BindValue @JvmField val newsRepository: NewsRepository = mockk(relaxed = true)
    @BindValue @JvmField val videoRepository: VideoRepository = mockk(relaxed = true)
    @BindValue @JvmField val userRepository: UserRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putString("username", "Morris").commit()
    }

    private fun news() = News(
        id = 1,
        title = "Заголовок новости",
        content = "Текст новости",
        imageUrl = null,
        relatedVideo = null,
        author = "Morris",
        publishedAt = "2026-01-01T00:00:00"
    )

    private fun stubComments(comments: List<NewsComment> = emptyList()) {
        coEvery { newsRepository.getComments(1, null, 20) } returns flowOf(
            Resource.Success(NewsCommentsPage(comments, comments.size.toLong(), false, null))
        )
        coEvery { newsRepository.registerView(1) } returns Unit
    }

    private fun comment(id: Long = 2) = NewsComment(
        id = id,
        newsId = 1,
        content = "Комментарий",
        author = "bob",
        createdAt = "2026-01-01T00:00:00"
    )

    private fun layoutRecycler(fragment: NewsDetailFragment): androidx.recyclerview.widget.RecyclerView {
        val recycler = fragment.requireView()
            .findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 1080, 1920)
        shadowOf(Looper.getMainLooper()).idle()
        return recycler
    }

    private fun launch(id: Long? = 1L): NewsDetailFragment {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = NewsDetailFragment().apply {
            arguments = Bundle().apply { if (id != null) putLong("newsId", id) }
        }
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun `renders the news header`() {
        coEvery { newsRepository.getNewsById(1) } returns flow {
            delay(20)
            emit(Resource.Success(news()))
        }
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))

        val recycler = layoutRecycler(fragment)

        val header = recycler.findViewHolderForAdapterPosition(0)?.itemView
        org.junit.Assert.assertNotNull(header)
        val title = header!!.findViewById<android.widget.TextView>(R.id.tvTitle)
        assertEquals("Заголовок новости", title.text.toString())
    }

    @Test
    fun `author actions sheet opens from the toolbar`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        fragment.requireView().findViewById<View>(R.id.btnActions).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        org.junit.Assert.assertTrue(
            fragment.childFragmentManager.fragments.any { it is NewsActionsBottomSheet }
        )
    }

    @Test
    fun `sending a comment calls the repository and clears the field`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        coEvery { newsRepository.addComment(1, "Привет") } returns flowOf(
            Resource.Success(comment())
        )
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val field = fragment.requireView()
            .findViewById<android.widget.EditText>(R.id.etComment)
        field.setText("Привет")
        fragment.requireView().findViewById<View>(R.id.btnSendComment).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { newsRepository.addComment(1, "Привет") }
        assertEquals("", field.text.toString())
    }

    @Test
    fun `heart in the header toggles the like`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        coEvery { newsRepository.likeNews(1) } returns flowOf(Resource.Success(3))
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val recycler = layoutRecycler(fragment)
        recycler.findViewHolderForAdapterPosition(0)?.itemView
            ?.findViewById<View>(R.id.btnLike)
            ?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { newsRepository.likeNews(1) }
    }

    @Test
    fun `comment actions open the sheet`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        stubComments(listOf(comment()))

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val recycler = layoutRecycler(fragment)
        recycler.findViewHolderForAdapterPosition(1)?.itemView
            ?.findViewById<View>(R.id.btnCommentMore)
            ?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        org.junit.Assert.assertTrue(
            fragment.childFragmentManager.fragments.any { it is NewsActionsBottomSheet }
        )
    }

    @Test
    fun `delete action confirms and calls the repository`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        coEvery { newsRepository.deleteNews(1) } returns flowOf(Resource.Success(Unit))
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.onNewsAction(NewsActionsBottomSheet.ACTION_DELETE)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        dialog.findViewById<View>(android.R.id.button1).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { newsRepository.deleteNews(1) }
    }

    @Test
    fun `edit action starts the editor`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.onNewsAction(NewsActionsBottomSheet.ACTION_EDIT)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(shadowOf(fragment.requireActivity()).nextStartedActivityForResult)
    }

    @Test
    fun `comment delete action calls the repository`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        coEvery { newsRepository.deleteComment(1, 2) } returns flowOf(Resource.Success(Unit))
        stubComments(listOf(comment()))

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val recycler = layoutRecycler(fragment)
        recycler.findViewHolderForAdapterPosition(1)?.itemView
            ?.findViewById<View>(R.id.btnCommentMore)
            ?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val sheet = fragment.childFragmentManager.fragments
            .filterIsInstance<NewsActionsBottomSheet>()
            .first()
        val container = sheet.dialog!!.findViewById<android.widget.LinearLayout>(R.id.actionsContainer)
        container.getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        org.robolectric.shadows.ShadowDialog.getLatestDialog()
            .findViewById<View>(android.R.id.button1)
            .performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { newsRepository.deleteComment(1, 2) }
    }

    @Test
    fun `watch video starts the player`() {
        val videoNews = news().copy(
            relatedVideo = com.example.muzea.domain.model.Video(
                id = 9,
                title = "Клип",
                description = null,
                url = "/v.mp4",
                thumbnailUrl = null,
                fileSize = 1,
                duration = null,
                views = 1,
                likes = 0,
                uploadedBy = "Morris",
                uploadedAt = "2026-01-01T00:00:00"
            )
        )
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(videoNews))
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val recycler = layoutRecycler(fragment)
        recycler.findViewHolderForAdapterPosition(0)?.itemView
            ?.findViewById<View>(R.id.btnWatchVideo)
            ?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(shadowOf(fragment.requireActivity()).nextStartedActivity)
    }

    @Test
    fun `blank comment is not sent`() {
        coEvery { newsRepository.getNewsById(1) } returns flowOf(Resource.Success(news()))
        stubComments()

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.requireView().findViewById<View>(R.id.btnSendComment).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify(exactly = 0) { newsRepository.addComment(any(), any()) }
    }

    @Test
    fun `error response shows the message`() {
        stubComments()
        coEvery { newsRepository.getNewsById(1) } returns flow {
            delay(20)
            emit(Resource.Error("offline"))
        }

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))

        val error = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvError)
        assertEquals(View.VISIBLE, error.visibility)
        assertEquals("offline", error.text.toString())
    }

    @Test
    fun `missing id shows the invalid id error`() {
        val fragment = launch(id = null)

        val error = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvError)
        assertEquals(View.VISIBLE, error.visibility)
    }
}
