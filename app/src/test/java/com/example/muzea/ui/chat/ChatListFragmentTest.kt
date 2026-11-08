package com.example.muzea.ui.chat

import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.HiltTestActivity
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.domain.model.Chat
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
import io.mockk.every
import io.mockk.mockk
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
import org.robolectric.shadows.ShadowDialog

/**
 * Robolectric+Hilt тесты списка чатов: реальные fragment/VM, репозитории —
 * моки. Проверяют состояния списка и переход в переписку.
 */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ChatListFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val chatRepository: ChatRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val messageRepository: MessageRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val gifRepository: GifRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val contactRepository: ContactRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val avatarRepository: AvatarRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val chatSessionRepository: ChatSessionRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
        every { chatRepository.cachedChats() } returns emptyList()
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Success(emptyList()))
    }

    private fun chat(uuid: String = "chat-1", title: String = "Баги") = Chat(
        chatUuid = uuid,
        chatType = "GROUP",
        title = title,
        avatarUrl = null,
        createdAt = "2026-01-01T00:00:00",
        updatedAt = "2026-01-01T00:00:00",
        participantCount = 3,
        lastMessage = null,
        unreadCount = 0
    )

    private fun launch(): Pair<HiltTestActivity, ChatListFragment> {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ChatListFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    @Test
    fun `renders chats from the repository`() {
        coEvery { chatRepository.loadChats() } returns flowOf(
            Resource.Success(listOf(chat()))
        )

        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewChats)
        assertEquals(1, list.adapter?.itemCount)
        assertEquals(View.VISIBLE, list.visibility)
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.tvEmpty).visibility)
    }

    @Test
    fun `empty list shows the empty state`() {
        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.tvEmpty).visibility
        )
    }

    @Test
    fun `failed load shows the error state`() {
        coEvery { chatRepository.loadChats() } returns flowOf(
            Resource.Error("offline")
        )

        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val error = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvError)
        assertEquals(View.VISIBLE, error.visibility)
        assertEquals("offline", error.text.toString())
    }

    @Test
    fun `clicking a chat opens the conversation`() {
        coEvery { chatRepository.loadChats() } returns flowOf(
            Resource.Success(listOf(chat()))
        )
        val (activity, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.setSharedText("черновик")

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewChats)
        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()

        val opened = activity.lastConversation
        assertEquals("chat-1", opened?.chatUuid)
        assertEquals("Баги", opened?.title)
        assertEquals("черновик", opened?.draftText)
    }

    @Test
    fun `cached chats stay visible when refresh fails`() {
        every { chatRepository.cachedChats() } returns listOf(chat())
        coEvery { chatRepository.loadChats() } returns flowOf(
            Resource.Error("offline")
        )

        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewChats)
        assertEquals(View.VISIBLE, list.visibility)
        assertEquals(1, list.adapter?.itemCount)
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.tvError).visibility)
    }

    @Test
    fun `create group fab opens the dialog`() {
        val (_, fragment) = launch()

        fragment.requireView().findViewById<View>(R.id.fabCreateGroup).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(true, ShadowDialog.getLatestDialog()?.isShowing)
    }
}
