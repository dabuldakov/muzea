package com.example.muzea.ui.chat

import android.os.Looper
import android.view.View
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
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
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
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

/** Robolectric+Hilt тесты выбора чата для пересылки. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ForwardChatPickerBottomSheetTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue @JvmField val chatRepository: ChatRepository = mockk(relaxed = true)
    @BindValue @JvmField val messageRepository: MessageRepository = mockk(relaxed = true)
    @BindValue @JvmField val gifRepository: GifRepository = mockk(relaxed = true)
    @BindValue @JvmField val contactRepository: ContactRepository = mockk(relaxed = true)
    @BindValue @JvmField val avatarRepository: AvatarRepository = mockk(relaxed = true)
    @BindValue @JvmField val chatSessionRepository: ChatSessionRepository = mockk(relaxed = true)

    class HostFragment : Fragment(), ForwardChatPickerBottomSheet.OnChatForwardTargetSelected {
        var target: Chat? = null
        override fun onForwardTargetSelected(chat: Chat) {
            target = chat
        }
    }

    private lateinit var activity: HiltTestActivity
    private lateinit var host: HostFragment

    @Before
    fun setUp() {
        hiltRule.inject()
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        host = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
        coEvery { chatRepository.loadChats() } returns flowOf(
            Resource.Success(listOf(chat("chat-2"), chat("chat-1")))
        )
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    private fun chat(uuid: String) = Chat(
        chatUuid = uuid, chatType = "GROUP", title = "Чат $uuid", avatarUrl = null,
        createdAt = null, updatedAt = null, participantCount = 2,
        lastMessage = null, unreadCount = 0
    )

    private fun sheet(): ForwardChatPickerBottomSheet {
        val sheet = ForwardChatPickerBottomSheet.newInstance("chat-1")
        host.childFragmentManager.beginTransaction().add(sheet, "forward").commitNow()
        activity.supportFragmentManager.executePendingTransactions()
        host.childFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        return sheet
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
    fun `excludes the current chat and lists the rest`() {
        val sheet = sheet()
        val list = sheet.requireView().findViewById<RecyclerView>(R.id.rvForwardChats)
        awaitItems(list, 1)

        assertEquals(View.VISIBLE, list.visibility)
    }

    @Test
    fun `clicking a chat returns it to the listener`() {
        val sheet = sheet()
        val list = sheet.requireView().findViewById<RecyclerView>(R.id.rvForwardChats)
        awaitItems(list, 1)
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 2000)
        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()

        assertEquals("chat-2", host.target?.chatUuid)
        assertTrue(!sheet.isVisible)
    }
}
