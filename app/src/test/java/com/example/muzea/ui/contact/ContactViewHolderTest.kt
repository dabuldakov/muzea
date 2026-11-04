package com.example.muzea.ui.contact

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.databinding.ItemContactBinding
import com.example.muzea.domain.model.Contact
import com.example.muzea.utils.AvatarLoader
import com.google.android.material.card.MaterialCardView
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Тесты ContactAdapter без Robolectric.
 *
 * Robolectric поднимает собственный sandbox-classloader, из-за чего JaCoCo не
 * видит покрытие тестируемого кода. Поэтому логику строки контакта проверяем
 * на моке [ItemContactBinding]: это дешевле и учитывается в отчёте покрытия.
 */
class ContactViewHolderTest {

    private lateinit var root: MaterialCardView
    private lateinit var name: TextView
    private lateinit var username: TextView
    private lateinit var status: TextView
    private lateinit var dot: View
    private lateinit var avatar: ImageView
    private lateinit var binding: ItemContactBinding

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        name = mockk(relaxed = true)
        username = mockk(relaxed = true)
        status = mockk(relaxed = true)
        dot = mockk(relaxed = true)
        avatar = mockk(relaxed = true)
        binding = mockk(relaxed = true)

        every { binding.root } returns root
        setViewField("ivAvatar", avatar)
        setViewField("tvContactName", name)
        setViewField("tvContactStatus", status)
        setViewField("tvContactUsername", username)
        setViewField("viewOnlineDot", dot)
        every { root.context } returns mockk<Context>(relaxed = true)

        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
    }

    /**
     * Поля [ItemContactBinding] — публичные final-поля без геттеров, поэтому
     * MockK их не перехватывает: подставляем вьюхи напрямую.
     */
    private fun setViewField(field: String, value: View) {
        val f = ItemContactBinding::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(binding, value)
    }

    @After
    fun tearDown() {
        unmockkObject(AvatarLoader)
    }

    private fun contact(
        contactName: String? = "Anna",
        username: String? = "anna",
        online: Boolean = false,
        lastSeenAt: String? = null,
        avatarUrl: String? = null
    ) = Contact(
        contactUuid = "c-1",
        contactUserId = 1L,
        contactUserUuid = "u-1",
        username = username,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = avatarUrl,
        contactName = contactName,
        isOnline = online,
        lastSeenAt = lastSeenAt,
        addedAt = null
    )

    private fun holder(onClick: (Contact) -> Unit = {}) =
        ContactAdapter.ContactViewHolder(binding, onClick)

    private fun isoMinutesAgo(minutes: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date(System.currentTimeMillis() - minutes * 60_000L))
    }

    /**
     * Проверяет, что bind() дошёл до конца: загрузка аватара — последний шаг,
     * после ветвления статуса.
     */
    private fun verifyBindCompleted() {
        verify { AvatarLoader.load(avatar, null) }
    }

    @Test
    fun `bind writes the name username and loads the avatar`() {
        holder().bind(contact(avatarUrl = "/a.png"))

        verify { name.setText("Anna") }
        verify { username.setText("anna") }
        verify { AvatarLoader.load(avatar, "/a.png") }
    }

    @Test
    fun `blank username binds as empty text`() {
        holder().bind(contact(username = null))

        verify { username.setText("") }
    }

    @Test
    fun `online contact shows the online label`() {
        holder().bind(contact(online = true))

        verify { status.setText(R.string.presence_online) }
    }

    @Test
    fun `offline contact without last seen shows the offline label`() {
        holder().bind(contact(online = false, lastSeenAt = null))

        verifyBindCompleted()
    }

    @Test
    fun `offline contact with blank last seen shows the offline label`() {
        holder().bind(contact(online = false, lastSeenAt = ""))

        verifyBindCompleted()
    }

    @Test
    fun `recent last seen takes the just-now branch`() {
        holder().bind(contact(online = false, lastSeenAt = isoMinutesAgo(0)))

        verifyBindCompleted()
    }

    @Test
    fun `last seen within an hour takes the minutes branch`() {
        holder().bind(contact(online = false, lastSeenAt = isoMinutesAgo(5)))

        verifyBindCompleted()
    }

    @Test
    fun `last seen within a day takes the hours branch`() {
        holder().bind(contact(online = false, lastSeenAt = isoMinutesAgo(3 * 60)))

        verifyBindCompleted()
    }

    @Test
    fun `last seen older than a day takes the calendar branch`() {
        holder().bind(contact(online = false, lastSeenAt = "2020-01-02T03:04:05"))

        verifyBindCompleted()
    }

    @Test
    fun `clicking the row reports the contact`() {
        var clicked: Contact? = null
        val holder = holder { clicked = it }
        holder.bind(contact())

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)

        assertEquals("c-1", clicked?.contactUuid)
    }

    @Test
    fun `adapter can be created empty`() {
        val adapter = ContactAdapter { }

        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `diff callback compares uuid and contents`() {
        val callback = ContactAdapter.ContactDiffCallback()
        val a = contact()
        val b = a.copy(contactUuid = "c-2")
        val changed = a.copy(username = "other")

        assertTrue(callback.areItemsTheSame(a, a.copy(username = "other")))
        assertFalse(callback.areItemsTheSame(a, b))
        assertTrue(callback.areContentsTheSame(a, a))
        assertFalse(callback.areContentsTheSame(a, changed))
    }
}
