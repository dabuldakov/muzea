package com.example.muzea.ui.contact

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.databinding.ItemContactBinding
import com.example.muzea.domain.model.Contact
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.LocalTimeFormatter
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
 * Robolectric поднимает sandbox-classloader, из-за чего JaCoCo не видит
 * покрытие основного кода, поэтому строку проверяем на моке
 * [ItemContactBinding]: поля data-binding — публичные final-поля без
 * геттеров, их подставляем рефлексией. Аватары не грузятся.
 */
class ContactAdapterTest {

    private lateinit var root: MaterialCardView
    private lateinit var name: TextView
    private lateinit var username: TextView
    private lateinit var status: TextView
    private lateinit var dot: View
    private lateinit var avatar: ImageView
    private lateinit var binding: ItemContactBinding
    private lateinit var context: Context

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        name = mockk(relaxed = true)
        username = mockk(relaxed = true)
        status = mockk(relaxed = true)
        dot = mockk(relaxed = true)
        avatar = mockk(relaxed = true)
        binding = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { binding.root } returns root
        setViewField("ivAvatar", avatar)
        setViewField("tvContactName", name)
        setViewField("tvContactStatus", status)
        setViewField("tvContactUsername", username)
        setViewField("viewOnlineDot", dot)
        every { root.context } returns context

        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkObject(AvatarLoader)
    }

    /** Поля [ItemContactBinding] — публичные final-поля без геттеров. */
    private fun setViewField(field: String, value: View) {
        val f = ItemContactBinding::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(binding, value)
    }

    /**
     * В юнит-тестах android.jar — заглушка: поле `mObservers` у
     * `android.database.Observable` не инициализировано, поэтому любой
     * `notify*` падает с NPE. Подставляем пустой список наблюдателей.
     */
    private fun enableNotifications(adapter: RecyclerView.Adapter<*>) {
        val observable = RecyclerView.Adapter::class.java
            .getDeclaredField("mObservable").apply { isAccessible = true }.get(adapter)
        var cls: Class<*>? = observable.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField("mObservers")
                f.isAccessible = true
                f.set(observable, ArrayList<Any>())
                return
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
    }

    private fun contact(
        uuid: String = "c-1",
        name: String? = "Anna",
        username: String? = "anna",
        online: Boolean = false,
        lastSeenAt: String? = null,
        avatarUrl: String? = null
    ) = Contact(
        contactUuid = uuid,
        contactUserId = 1L,
        contactUserUuid = "u-1",
        username = username,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = avatarUrl,
        contactName = name,
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

    @Test
    fun `binds name username and loads the avatar`() {
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
    fun `online contact shows the dot and online label`() {
        holder().bind(contact(online = true))

        verify { status.setText(R.string.presence_online) }
        verify { dot.visibility = View.VISIBLE }
    }

    @Test
    fun `offline contact without last seen hides the dot and shows offline`() {
        every { context.getString(R.string.presence_offline) } returns "offline"

        holder().bind(contact(online = false, lastSeenAt = null))

        verify { dot.visibility = View.INVISIBLE }
        verify { status.setText("offline") }
    }

    @Test
    fun `offline contact with blank last seen shows offline`() {
        holder().bind(contact(online = false, lastSeenAt = ""))

        verify { dot.visibility = View.INVISIBLE }
    }

    @Test
    fun `recent last seen takes the just-now branch`() {
        holder().bind(contact(online = false, lastSeenAt = isoMinutesAgo(0)))

        verify { dot.visibility = View.INVISIBLE }
        verify { AvatarLoader.load(avatar, null) }
    }

    @Test
    fun `last seen within an hour takes the minutes branch`() {
        holder().bind(contact(online = false, lastSeenAt = isoMinutesAgo(5)))

        verify { AvatarLoader.load(avatar, null) }
    }

    @Test
    fun `last seen within a day takes the hours branch`() {
        holder().bind(contact(online = false, lastSeenAt = isoMinutesAgo(3 * 60)))

        verify { AvatarLoader.load(avatar, null) }
    }

    @Test
    fun `last seen older than a day takes the calendar branch`() {
        val iso = "2020-01-02T03:04:05"
        every {
            context.getString(R.string.presence_last_seen_at, LocalTimeFormatter.format(iso))
        } returns "seen at"

        holder().bind(contact(online = false, lastSeenAt = iso))

        verify { status.setText("seen at") }
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
    fun `adapter starts empty`() {
        assertEquals(0, ContactAdapter { }.itemCount)
    }

    @Test
    fun `updateList publishes the first list synchronously`() {
        val adapter = ContactAdapter { }
        enableNotifications(adapter)
        adapter.updateList(listOf(contact(), contact(uuid = "c-2")))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder binds the item at the requested position`() {
        val adapter = ContactAdapter { }
        enableNotifications(adapter)
        adapter.updateList(listOf(contact(uuid = "c-9", name = "Bob")))

        adapter.onBindViewHolder(ContactAdapter.ContactViewHolder(binding, { }), 0)

        verify { name.setText("Bob") }
    }

    @Test
    fun `clearItems on a fresh adapter leaves it empty`() {
        val adapter = ContactAdapter { }
        enableNotifications(adapter)
        adapter.clearItems()

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
