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
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.NetworkQuality
import com.example.muzea.utils.NetworkStatus
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

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

    @BindValue
    @JvmField
    val networkMonitor: NetworkMonitor = mockk(relaxed = true)

    private val networkStatus = kotlinx.coroutines.flow.MutableStateFlow(
        NetworkStatus(NetworkQuality.GOOD, metered = false)
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        every { chatRepository.cachedChats() } returns emptyList()
        coEvery { chatRepository.loadChats() } returns flowOf(Resource.Success(emptyList()))
        coEvery { contactRepository.loadContacts() } returns flowOf(Resource.Success(emptyList()))
        every { networkMonitor.status } returns networkStatus
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

    private fun firstEditText(view: android.view.View): android.widget.EditText? {
        if (view is android.widget.EditText) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                firstEditText(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    @Test
    fun `create group sends the title and opens the members dialog`() {
        coEvery { chatRepository.createGroupChat("Тест", any()) } returns
            flowOf(Resource.Success(chat(title = "Тест")))
        val (_, fragment) = launch()

        fragment.requireView().findViewById<View>(R.id.fabCreateGroup).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        firstEditText(dialog.window!!.decorView)?.setText("Тест")
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { chatRepository.createGroupChat("Тест", emptyList()) }
        assertEquals(true, ShadowDialog.getLatestDialog()?.isShowing)
    }

    @Test
    fun `create group fab opens the dialog`() {
        val (_, fragment) = launch()

        fragment.requireView().findViewById<View>(R.id.fabCreateGroup).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(true, ShadowDialog.getLatestDialog()?.isShowing)
    }

    @Test
    fun `hidden list stops polling chats and resumes when shown again`() {
        val loads = mutableListOf<Unit>()
        coEvery { chatRepository.loadChats() } answers {
            loads += Unit
            flowOf(Resource.Success(emptyList()))
        }

        val (activity, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()
        val afterLaunch = loads.size
        assertTrue(afterLaunch >= 1)

        // Открыли переписку — вкладка списка скрыта. Раньше она продолжала
        // тянуть список каждые 8 секунд за спиной.
        activity.supportFragmentManager.beginTransaction().hide(fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(afterLaunch, loads.size)

        // Вернулись назад — опрос возобновляется.
        activity.supportFragmentManager.beginTransaction().show(fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertTrue(loads.size > afterLaunch)
    }
}
