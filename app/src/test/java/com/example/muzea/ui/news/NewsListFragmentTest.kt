package com.example.muzea.ui.news

import android.os.Looper
import android.view.View
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.recyclerview.widget.RecyclerView
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
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тесты ленты новостей: состояния и открытие новости. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class, ContentRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class NewsListFragmentTest {

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
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("username", "Morris")
            .commit()
        // DiffUtil у ListAdapter по умолчанию уходит в фоновый executor;
        // в Robolectric применяем его синхронно, чтобы submitList был виден сразу.
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        coEvery { contactRepository.loadContacts() } returns
            flowOf(Resource.Success(emptyList()))
        coEvery { newsRepository.getNews(any(), any()) } returns
            flowOf(Resource.Success(emptyList()))
    }

    private fun news(id: Long = 1, title: String = "Новость") = News(
        id = id,
        title = title,
        content = "Текст новости",
        imageUrl = null,
        relatedVideo = null,
        author = "Morris",
        publishedAt = "2026-01-01T00:00:00"
    )

    @org.junit.After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    private fun awaitItems(list: RecyclerView, count: Int) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (list.adapter?.itemCount == count) return
            Thread.sleep(10)
        }
        assertEquals(count, list.adapter?.itemCount)
    }

    private fun layout(list: RecyclerView) {
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 2400)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun launch(): Pair<HiltTestActivity, NewsListFragment> {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = NewsListFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    @Test
    fun `renders news`() {
        coEvery { newsRepository.getNews(0, 20) } returns
            flowOf(Resource.Success(listOf(news())))

        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewNews)
        awaitItems(list, 1)
        assertEquals(View.VISIBLE, list.visibility)
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.tvEmpty).visibility)
    }

    @Test
    fun `empty feed shows the empty state`() {
        val (_, fragment) = launch()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.tvEmpty).visibility
        )
    }

    @Test
    fun `load error shows the error state`() {
        coEvery { newsRepository.getNews(any(), any()) } returns
            flowOf(Resource.Error("offline"))

        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.tvError).visibility
        )
    }

    @Test
    fun `clicking a news card opens the detail`() {
        coEvery { newsRepository.getNews(0, 20) } returns
            flowOf(Resource.Success(listOf(news(id = 7))))

        val (activity, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewNews)
        awaitItems(list, 1)
        layout(list)
        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()

        assertEquals(7L, activity.lastNewsId)
    }

    @Test
    fun `create news fab opens the editor`() {
        val (_, fragment) = launch()

        fragment.requireView().findViewById<View>(R.id.fabAddNews).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(fragment.requireActivity()).nextStartedActivity
        assertTrue(started.component?.className!!.endsWith("CreateNewsActivity"))
    }
}
