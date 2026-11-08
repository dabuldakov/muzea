package com.example.muzea.ui.chat

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.HiltTestActivity
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.domain.repository.ChatRepository
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.repository.GifRepository
import com.example.muzea.domain.repository.MessageRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тесты настроек группы: участники, диалог, ошибка. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class GroupSettingsFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue @JvmField val chatRepository: ChatRepository = mockk(relaxed = true)
    @BindValue @JvmField val messageRepository: MessageRepository = mockk(relaxed = true)
    @BindValue @JvmField val gifRepository: GifRepository = mockk(relaxed = true)
    @BindValue @JvmField val contactRepository: ContactRepository = mockk(relaxed = true)
    @BindValue @JvmField val avatarRepository: AvatarRepository = mockk(relaxed = true)
    @BindValue @JvmField val chatSessionRepository: ChatSessionRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putString("chat_user_uuid", "me").commit()
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
        coEvery { chatRepository.loadChatParticipants("chat-1") } returns
            flowOf(Resource.Success(emptyList()))
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    private fun participant(uuid: String = "me", role: String = "OWNER") = ChatParticipant(
        userUuid = uuid,
        userId = 1,
        username = "morris",
        firstName = "Morris",
        lastName = null,
        fullName = "Morris",
        nickname = null,
        avatarUrl = null,
        role = role,
        online = true,
        lastSeenAt = null,
        joinedAt = "2026-01-01T00:00:00"
    )

    private fun launch(): GroupSettingsFragment {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = GroupSettingsFragment.newInstance("chat-1", "Баги")
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun awaitItems(list: RecyclerView, count: Int) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (list.adapter?.itemCount == count) return
            Thread.sleep(10)
        }
        assertEquals(count, list.adapter?.itemCount)
    }

    @Test
    fun `renders participants and title`() {
        coEvery { chatRepository.loadChatParticipants("chat-1") } returns
            flowOf(Resource.Success(listOf(participant())))

        val fragment = launch()
        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewParticipants)
        awaitItems(list, 1)

        assertEquals(
            "Баги",
            fragment.requireView().findViewById<android.widget.TextView>(R.id.tvTitle).text.toString()
        )
        assertEquals(View.VISIBLE, list.visibility)
    }

    @Test
    fun `owner sees the add members button`() {
        coEvery { chatRepository.loadChatParticipants("chat-1") } returns
            flowOf(Resource.Success(listOf(participant())))

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.btnAddMembers).visibility
        )
    }

    @Test
    fun `load error shows the error state`() {
        coEvery { chatRepository.loadChatParticipants("chat-1") } returns
            flowOf(Resource.Error("offline"))

        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.tvError).visibility
        )
    }

    @Test
    fun `add members opens the dialog`() {
        val fragment = launch()
        shadowOf(Looper.getMainLooper()).idle()

        fragment.requireView().findViewById<View>(R.id.btnAddMembers).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            true,
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.isShowing
        )
    }
}
