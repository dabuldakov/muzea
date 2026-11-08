package com.example.muzea.ui.news

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.di.ContentRepositoryModule
import com.example.muzea.domain.model.Video
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Robolectric+Hilt тесты редактора новости: форма, валидация, отправка. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class, ContentRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class CreateNewsActivityTest {

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
        every { videoRepository.cachedVideos() } returns emptyList()
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Success(emptyList()))
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
    }

    private fun video() = Video(
        id = 3, title = "Мой клип", description = null, url = "/v.mp4",
        thumbnailUrl = null, fileSize = 1, duration = "00:10", views = 5,
        likes = 0, uploadedBy = "Morris", uploadedAt = "2026-01-01T00:00:00"
    )

    private fun launch(): CreateNewsActivity =
        Robolectric.buildActivity(CreateNewsActivity::class.java).setup().get()

    @Test
    fun `shows the editor form`() {
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.etTitle).visibility
        )
        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.btnSubmit).visibility
        )
    }

    @Test
    fun `video spinner lists own videos`() {
        coEvery { videoRepository.getVideos() } returns
            flowOf(Resource.Success(listOf(video())))

        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val spinner = activity.findViewById<android.widget.Spinner>(R.id.spinnerVideo)
        // "None" + "Мой клип (5 views)"
        assertEquals(2, spinner.adapter.count)
    }

    @Test
    fun `empty title is rejected`() {
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()

        activity.findViewById<View>(R.id.btnSubmit).performClick()

        assertNotNull(activity.findViewById<android.widget.EditText>(R.id.etTitle).error)
    }

    @Test
    fun `filled form submits the news and closes`() {
        coEvery { newsRepository.createNews(any(), any(), any(), any()) } returns
            flowOf(Resource.Success(42L))

        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<android.widget.EditText>(R.id.etTitle).setText("Заголовок")
        activity.findViewById<android.widget.EditText>(R.id.etContent).setText("Текст")
        activity.findViewById<View>(R.id.btnSubmit).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `picked image is copied and sent with the news`() {
        coEvery { newsRepository.createNews(any(), any(), any(), any()) } returns
            flowOf(Resource.Success(42L))
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val uri = android.net.Uri.parse("content://test/photo.jpg")
        org.robolectric.Shadows.shadowOf(activity.contentResolver)
            .registerInputStream(uri, java.io.ByteArrayInputStream(ByteArray(64)))
        val method = CreateNewsActivity::class.java
            .getDeclaredMethod("handleSelectedImage", android.net.Uri::class.java)
        method.isAccessible = true
        method.invoke(activity, uri)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            activity.findViewById<View>(R.id.cardImagePreview).visibility
        )
        activity.findViewById<android.widget.EditText>(R.id.etTitle).setText("Заголовок")
        activity.findViewById<android.widget.EditText>(R.id.etContent).setText("Текст")
        activity.findViewById<View>(R.id.btnSubmit).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { newsRepository.createNews("Заголовок", "Текст", null, any()) }
    }

    @Test
    fun `missing image file is reported without submitting`() {
        coEvery { newsRepository.createNews(any(), any(), any(), any()) } returns
            flowOf(Resource.Success(42L))
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<android.widget.EditText>(R.id.etTitle).setText("Заголовок")
        activity.findViewById<android.widget.EditText>(R.id.etContent).setText("Текст")

        val field = CreateNewsActivity::class.java.getDeclaredField("selectedImageFile")
        field.isAccessible = true
        field.set(activity, File(activity.cacheDir, "gone.jpg"))

        activity.findViewById<View>(R.id.btnSubmit).performClick()

        assertTrue(!activity.isFinishing)
    }
}
