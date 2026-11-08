package com.example.muzea.ui.profile

import android.os.Looper
import android.view.View
import com.example.muzea.HiltTestActivity
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AppUpdate
import com.example.muzea.data.repository.AppUpdateRepository
import com.example.muzea.data.repository.OutboxSender
import com.example.muzea.data.repository.UpdateCheckResult
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.di.ContentRepositoryModule
import com.example.muzea.domain.model.User
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
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Robolectric+Hilt тесты профиля: рендер, редактирование, выход. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class, ContentRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ProfileFragmentTest {

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
    @BindValue @JvmField val authRepository: AuthRepository = mockk(relaxed = true)
    @BindValue @JvmField val appUpdateRepository: AppUpdateRepository = mockk(relaxed = true)
    @BindValue @JvmField val outboxSender: OutboxSender = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
        coEvery { userRepository.getCurrentUser() } returns flowOf(
            Resource.Success(
                User(
                    id = 1,
                    userName = "morris",
                    fullName = "Morris",
                    avatarUrl = null,
                    role = "USER",
                    createdAt = null,
                    enabled = true
                )
            )
        )
        coEvery { authRepository.getCurrentAuthUser() } returns null
        every { appUpdateRepository.availableUpdate } returns MutableStateFlow<AppUpdate?>(null)
        every { appUpdateRepository.currentVersionName } returns "1.7.28"
        coEvery { appUpdateRepository.checkForUpdate() } returns UpdateCheckResult.UpToDate
    }

    private fun launch(): HiltTestActivity {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ProfileFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun `renders the current user`() {
        val activity = launch()

        val username = activity.findViewById<android.widget.TextView>(R.id.tvUsername)
        assertEquals("@morris", username.text.toString())
        val fullName = activity.findViewById<android.widget.EditText>(R.id.etFullName)
        assertEquals("Morris", fullName.text.toString())
    }

    @Test
    fun `edit button enables the name field`() {
        val activity = launch()

        val name = activity.findViewById<android.widget.EditText>(R.id.etFullName)
        activity.findViewById<View>(R.id.btnEditProfile).performClick()

        assertTrue(name.isEnabled)
    }

    @Test
    fun `logout opens the login screen`() {
        val activity = launch()

        activity.findViewById<View>(R.id.btnLogout).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started.component?.className!!.endsWith("LoginActivity"))
    }
}
