package com.example.muzea.ui.video

import android.content.Context
import android.content.Intent
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
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.MultipartBody
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

/** Robolectric+Hilt тесты загрузки видео и детального экрана. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class, ContentRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class VideoScreensTest {

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
        coEvery { videoRepository.uploadVideo(any(), any(), any(), any()) } returns
            flowOf(Resource.Success(video()))
    }

    private fun video() = Video(
        id = 1, title = "Видео", description = "Описание", url = "/v.mp4",
        thumbnailUrl = null, fileSize = 1024, duration = "00:30", views = 5,
        likes = 2, uploadedBy = "Morris", uploadedAt = "2026-01-01T00:00:00"
    )

    @Test
    fun `upload screen shows the form`() {
        val activity = Robolectric.buildActivity(VideoUploadActivity::class.java).setup().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.etTitle).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnUpload).visibility)
    }

    @Test
    fun `empty title is rejected`() {
        val activity = Robolectric.buildActivity(VideoUploadActivity::class.java).setup().get()

        activity.findViewById<View>(R.id.btnUpload).performClick()

        assertNotNull(activity.findViewById<android.widget.EditText>(R.id.etTitle).error)
    }

    @Test
    fun `upload without a file asks to select one`() {
        val activity = Robolectric.buildActivity(VideoUploadActivity::class.java).setup().get()
        activity.findViewById<android.widget.EditText>(R.id.etTitle).setText("Заголовок")

        activity.findViewById<View>(R.id.btnUpload).performClick()

        assertEquals(
            "Select a video",
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `upload with a file sends it and closes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "clip.mp4").apply { writeBytes(ByteArray(1024)) }
        val activity = Robolectric.buildActivity(VideoUploadActivity::class.java).setup().get()
        activity.findViewById<android.widget.EditText>(R.id.etTitle).setText("Заголовок")
        val field = VideoUploadActivity::class.java.getDeclaredField("selectedVideoFile")
        field.isAccessible = true
        field.set(activity, file)

        activity.findViewById<View>(R.id.btnUpload).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `video detail shows the content and finishes without auth token`() {
        coEvery { videoRepository.getVideoById(1) } returns flow {
            delay(20)
            emit(Resource.Success(video()))
        }
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            VideoDetailActivity::class.java
        ).putExtra("video_id", 1L)

        val activity = Robolectric.buildActivity(VideoDetailActivity::class.java, intent)
            .setup().get()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(60))

        assertEquals(
            "Видео",
            activity.findViewById<android.widget.TextView>(R.id.tvTitle).text.toString()
        )
        // Токена чата в тесте нет — плеер не создаётся, экран закрывается.
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `video detail with zero id closes immediately`() {
        val activity = Robolectric.buildActivity(VideoDetailActivity::class.java).setup().get()

        assertTrue(activity.isFinishing)
    }
}
