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
    fun `renders the news and owner actions`() {
        coEvery { newsRepository.getNewsById(1) } returns flow {
            delay(20)
            emit(Resource.Success(news()))
        }

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))

        val title = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvTitle)
        assertEquals("Заголовок новости", title.text.toString())
        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.btnDeleteNews).visibility
        )
        assertEquals(
            View.GONE,
            fragment.requireView().findViewById<View>(R.id.btnReportNews).visibility
        )
    }

    @Test
    fun `error response shows the message`() {
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
