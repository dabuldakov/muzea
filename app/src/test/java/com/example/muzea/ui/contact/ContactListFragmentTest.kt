package com.example.muzea.ui.contact

import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.HiltTestActivity
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.di.ChatRepositoryModule
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Contact
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тесты списка контактов: состояния и переход в чат. */
@HiltAndroidTest
@UninstallModules(ChatRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class ContactListFragmentTest {

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
        coEvery { contactRepository.loadContacts() } returns
            flowOf(Resource.Success(emptyList()))
        coEvery { contactRepository.loadPresence(any()) } returns emptyMap()
    }

    private fun contact(uuid: String = "user-1", name: String = "Morris") = Contact(
        contactUuid = "c-1",
        contactUserId = 1,
        contactUserUuid = uuid,
        username = name.lowercase(),
        firstName = name,
        lastName = null,
        fullName = name,
        avatarUrl = null,
        contactName = null,
        isOnline = true,
        lastSeenAt = null,
        addedAt = "2026-01-01T00:00:00"
    )

    private fun chat() = Chat(
        chatUuid = "private-1",
        chatType = "PRIVATE",
        title = "Morris",
        avatarUrl = null,
        createdAt = null,
        updatedAt = null,
        participantCount = 2,
        lastMessage = null,
        unreadCount = 0
    )

    private fun launch(): Pair<HiltTestActivity, ContactListFragment> {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val fragment = ContactListFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    @Test
    fun `renders contacts`() {
        coEvery { contactRepository.loadContacts() } returns
            flowOf(Resource.Success(listOf(contact())))

        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewContacts)
        assertEquals(1, list.adapter?.itemCount)
        assertEquals(View.VISIBLE, list.visibility)
        assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.tvEmpty).visibility)
    }

    @Test
    fun `empty contacts show the empty state`() {
        val (_, fragment) = launch()

        assertEquals(
            View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.tvEmpty).visibility
        )
    }

    @Test
    fun `load error shows the error state`() {
        coEvery { contactRepository.loadContacts() } returns
            flowOf(Resource.Error("offline"))

        val (_, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()

        val error = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvError)
        assertEquals(View.VISIBLE, error.visibility)
        assertEquals("offline", error.text.toString())
    }

    @Test
    fun `clicking a contact opens the existing private chat`() {
        coEvery { contactRepository.loadContacts() } returns
            flowOf(Resource.Success(listOf(contact())))
        coEvery { chatRepository.findPrivateChatWith("user-1") } returns chat()

        val (activity, fragment) = launch()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.requireView().findViewById<RecyclerView>(R.id.recyclerViewContacts)
            .findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("private-1", activity.lastConversation?.chatUuid)
        assertEquals("Morris", activity.lastConversation?.title)
    }
}
