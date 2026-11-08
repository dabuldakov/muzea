package com.example.muzea.ui.chat

import android.os.Looper
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.fragment.app.Fragment
import com.example.muzea.HiltTestActivity
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.domain.model.Gif
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

/** Robolectric+Hilt тесты пикера гифок: вкладки и пустое состояние. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class GifPickerBottomSheetTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue @JvmField val chatRepository: ChatRepository = mockk(relaxed = true)
    @BindValue @JvmField val messageRepository: MessageRepository = mockk(relaxed = true)
    @BindValue @JvmField val gifRepository: GifRepository = mockk(relaxed = true)
    @BindValue @JvmField val contactRepository: ContactRepository = mockk(relaxed = true)
    @BindValue @JvmField val avatarRepository: AvatarRepository = mockk(relaxed = true)
    @BindValue @JvmField val chatSessionRepository: ChatSessionRepository = mockk(relaxed = true)

    class HostFragment : Fragment(), GifPickerBottomSheet.OnGifSelected {
        var selected: Gif? = null
        override fun onGifSelected(gif: Gif) {
            selected = gif
        }
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        coEvery { gifRepository.recent(0, 30) } returns emptyList()
        coEvery { gifRepository.chatGifs(any(), any(), any()) } returns emptyList()
        coEvery { gifRepository.search(any(), any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    private fun open(): Triple<HiltTestActivity, HostFragment, GifPickerBottomSheet> {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val host = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
        val sheet = GifPickerBottomSheet.newInstance("chat-1")
        host.childFragmentManager.beginTransaction().add(sheet, "gif").commitNow()
        activity.supportFragmentManager.executePendingTransactions()
        host.childFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        return Triple(activity, host, sheet)
    }

    @Test
    fun `loads the first tab and shows the empty state`() {
        val (_, _, sheet) = open()

        assertEquals(
            android.view.View.VISIBLE,
            sheet.requireView().findViewById<android.view.View>(com.example.muzea.R.id.tvGifEmpty)
                .visibility
        )
    }

    @Test
    fun `switching tab reloads with chat gifs`() {
        val (_, _, sheet) = open()
        val tabs = sheet.requireView()
            .findViewById<com.google.android.material.tabs.TabLayout>(
                com.example.muzea.R.id.tabGifTabs
            )

        tabs.getTabAt(1)!!.select()
        shadowOf(Looper.getMainLooper()).idle()

        io.mockk.coVerify { gifRepository.chatGifs("chat-1", 0, 30) }
    }
}
