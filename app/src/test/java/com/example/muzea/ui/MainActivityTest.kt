package com.example.muzea.ui

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AppUpdate
import com.example.muzea.data.repository.AppUpdateRepository
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.data.repository.OutboxSender
import com.example.muzea.data.repository.UpdateCheckResult
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
import com.example.muzea.ui.chat.ChatListFragment
import com.example.muzea.ui.contact.ContactListFragment
import com.example.muzea.ui.news.NewsListFragment
import com.example.muzea.ui.profile.ProfileFragment
import com.example.muzea.ui.video.VideoListFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
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

/** Robolectric+Hilt тесты MainActivity: вкладки и бейджи. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class, ContentRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class MainActivityTest {

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
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("username", "Morris").commit()

        every { chatRepository.cachedChats() } returns emptyList()
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Success(emptyList()))
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
        coEvery { contactRepository.loadPresence(any()) } returns emptyMap()
        coEvery { newsRepository.getNews(any(), any()) } returns flowOf(Resource.Success(emptyList()))
        every { videoRepository.cachedVideos() } returns emptyList()
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Success(emptyList()))
        coEvery { chatSessionRepository.getTotalUnreadCount() } returns
            flowOf(Resource.Success(0L))
        coEvery { chatSessionRepository.sendHeartbeat() } returns true
        every { messageRepository.cachedMessages(any()) } returns emptyList()
        coEvery { messageRepository.loadMessages(any()) } returns flowOf(Resource.Success(emptyList()))
        coEvery { messageRepository.loadMessagesAfter(any(), any()) } returns
            flowOf(Resource.Success(emptyList()))
        coEvery { messageRepository.loadChatReceipts(any()) } returns emptyList()
        coEvery { messageRepository.loadPinned(any()) } returns emptyList()
        coEvery { messageRepository.markMessagesAsRead(any(), any()) } returns true
        coEvery { messageRepository.markMessagesAsDelivered(any(), any()) } returns true
        coEvery { newsRepository.getNewsById(any()) } returns flowOf(Resource.Error("none"))
        every { outboxSender.events } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { outboxSender.pendingForChat(any()) } returns emptyList()
        coEvery { userRepository.getCurrentUser() } returns flowOf(
            Resource.Success(
                User(1, "morris", "Morris", null, "USER", null, true)
            )
        )
        coEvery { authRepository.getCurrentAuthUser() } returns null
        every { appUpdateRepository.availableUpdate } returns MutableStateFlow<AppUpdate?>(null)
        every { appUpdateRepository.currentVersionName } returns "1.7.28"
        coEvery { appUpdateRepository.checkForUpdate() } returns UpdateCheckResult.UpToDate
    }

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun select(activity: MainActivity, itemId: Int) {
        activity.findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = itemId
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `opens on the news tab`() {
        val activity = launch()
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            activity.supportFragmentManager.findFragmentByTag("tab_news") is NewsListFragment
        )
    }

    @Test
    fun `switches between all tabs`() {
        val activity = launch()
        activity.supportFragmentManager.executePendingTransactions()

        select(activity, R.id.chatFragment)
        assertTrue(activity.supportFragmentManager.findFragmentByTag("tab_chat") is ChatListFragment)

        select(activity, R.id.contactFragment)
        assertTrue(
            activity.supportFragmentManager.findFragmentByTag("tab_contacts") is ContactListFragment
        )

        select(activity, R.id.videoListFragment)
        assertTrue(
            activity.supportFragmentManager.findFragmentByTag("tab_videos") is VideoListFragment
        )

        select(activity, R.id.profileFragment)
        assertTrue(
            activity.supportFragmentManager.findFragmentByTag("tab_profile") is ProfileFragment
        )
    }

    @Test
    fun `chat deep link opens the conversation`() {
        val intent = android.content.Intent(
            ApplicationProvider.getApplicationContext(), MainActivity::class.java
        ).putExtra(MainActivity.EXTRA_OPEN_CHAT_UUID, "chat-1")
            .putExtra(MainActivity.EXTRA_OPEN_CHAT_TITLE, "Баги")

        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            activity.supportFragmentManager.fragments.any {
                it is com.example.muzea.ui.chat.ChatConversationFragment
            }
        )
        assertEquals(
            android.view.View.GONE,
            activity.findViewById<BottomNavigationView>(R.id.bottomNavigation).visibility
        )

        activity.onBackPressedDispatcher.onBackPressed()
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            android.view.View.VISIBLE,
            activity.findViewById<BottomNavigationView>(R.id.bottomNavigation).visibility
        )
    }

    @Test
    fun `news deep link opens the news detail`() {
        val intent = android.content.Intent(
            ApplicationProvider.getApplicationContext(), MainActivity::class.java
        ).putExtra(MainActivity.EXTRA_OPEN_NEWS_ID, 7L)

        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            activity.supportFragmentManager.fragments.any {
                it is com.example.muzea.ui.news.NewsDetailFragment
            }
        )
    }

    @Test
    fun `shared text selects the chat tab`() {
        val intent = android.content.Intent(
            ApplicationProvider.getApplicationContext(), MainActivity::class.java
        ).putExtra(MainActivity.EXTRA_SHARED_TEXT, "https://muzea.su")

        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            R.id.chatFragment,
            activity.findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId
        )
    }

    @Test
    fun `unread badge is shown for new messages`() {
        coEvery { chatSessionRepository.getTotalUnreadCount() } returns
            flowOf(Resource.Success(5L))
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val badge = activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
            .getBadge(R.id.chatFragment)
        assertTrue(badge != null && badge.isVisible)
    }
}
