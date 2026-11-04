package com.example.muzea.ui.chat

import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.databinding.ItemGroupMemberBinding
import com.example.muzea.domain.model.Contact
import com.example.muzea.utils.AvatarLoader
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты GroupMemberAdapter без Robolectric.
 *
 * Строку проверяем на моке [ItemGroupMemberBinding]: поля data-binding —
 * публичные final-поля без геттеров, их подставляем рефлексией. Аватары
 * не грузятся, выбор участников проверяем по нажатиям.
 */
class GroupMemberAdapterTest {

    private lateinit var root: LinearLayout
    private lateinit var name: TextView
    private lateinit var username: TextView
    private lateinit var check: CheckBox
    private lateinit var avatar: ImageView
    private lateinit var binding: ItemGroupMemberBinding

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        name = mockk(relaxed = true)
        username = mockk(relaxed = true)
        check = mockk(relaxed = true)
        avatar = mockk(relaxed = true)
        binding = mockk(relaxed = true)

        every { binding.root } returns root
        setViewField("ivAvatar", avatar)
        setViewField("tvMemberName", name)
        setViewField("tvMemberUsername", username)
        setViewField("cbSelect", check)

        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkObject(AvatarLoader)
    }

    private fun setViewField(field: String, value: View) {
        val f = ItemGroupMemberBinding::class.java.getDeclaredField(field)
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
        userUuid: String? = "u-1",
        name: String? = "Anna",
        username: String? = "anna",
        avatarUrl: String? = null
    ) = Contact(
        contactUuid = uuid,
        contactUserId = 1L,
        contactUserUuid = userUuid,
        username = username,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = avatarUrl,
        contactName = name,
        isOnline = false,
        lastSeenAt = null,
        addedAt = null
    )

    private fun adapter(onSelectionChanged: (Int) -> Unit = {}) =
        GroupMemberAdapter(onSelectionChanged)

    @Test
    fun `binds member name username and avatar`() {
        val holder = adapter().GroupMemberViewHolder(binding)
        holder.bind(contact(name = "Anna", username = "anna", avatarUrl = "/a.png"))

        verify { name.setText("Anna") }
        verify { username.setText("anna") }
        verify { AvatarLoader.load(avatar, "/a.png") }
    }

    @Test
    fun `blank username binds as empty text`() {
        val holder = adapter().GroupMemberViewHolder(binding)
        holder.bind(contact(username = null))

        verify { username.setText("") }
    }

    @Test
    fun `clicking the row selects the member and reports the count`() {
        var count: Int? = null
        val adapter = adapter { count = it }
        val holder = adapter.GroupMemberViewHolder(binding)
        holder.bind(contact(userUuid = "u-1"))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)

        assertEquals(setOf("u-1"), adapter.selectedUserUuids)
        assertEquals(1, count)
        verify { check.isChecked = true }
    }

    @Test
    fun `clicking a selected row deselects it`() {
        var count: Int? = null
        val adapter = adapter { count = it }
        val holder = adapter.GroupMemberViewHolder(binding)
        holder.bind(contact(userUuid = "u-1"))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)
        listener.captured.onClick(root)

        assertTrue(adapter.selectedUserUuids.isEmpty())
        assertEquals(0, count)
        verify { check.isChecked = false }
    }

    @Test
    fun `the checkbox toggles the member too`() {
        var count: Int? = null
        val adapter = adapter { count = it }
        val holder = adapter.GroupMemberViewHolder(binding)
        holder.bind(contact(userUuid = "u-2"))

        val listener = slot<View.OnClickListener>()
        verify { check.setOnClickListener(capture(listener)) }
        listener.captured.onClick(check)

        assertEquals(setOf("u-2"), adapter.selectedUserUuids)
        assertEquals(1, count)
        verify { check.isChecked = true }
    }

    @Test
    fun `a member without a user uuid cannot be selected`() {
        var count: Int? = null
        val adapter = adapter { count = it }
        val holder = adapter.GroupMemberViewHolder(binding)
        holder.bind(contact(userUuid = null))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)

        assertTrue(adapter.selectedUserUuids.isEmpty())
        assertNull(count)
    }

    @Test
    fun `an already selected member binds as checked`() {
        val adapter = adapter()
        val holder = adapter.GroupMemberViewHolder(binding)
        holder.bind(contact(userUuid = "u-1"))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)

        holder.bind(contact(userUuid = "u-1"))

        verify { check.isChecked = true }
    }

    @Test
    fun `reset clears the selection`() {
        val adapter = adapter()
        val holder = adapter.GroupMemberViewHolder(binding)
        holder.bind(contact(userUuid = "u-1"))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)
        assertEquals(setOf("u-1"), adapter.selectedUserUuids)

        adapter.reset()

        assertTrue(adapter.selectedUserUuids.isEmpty())
    }

    @Test
    fun `adapter starts empty`() {
        assertEquals(0, adapter().itemCount)
    }

    @Test
    fun `updateList publishes the first list synchronously`() {
        val adapter = adapter()
        enableNotifications(adapter)
        adapter.updateList(listOf(contact(uuid = "a"), contact(uuid = "b")))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder binds the item at the requested position`() {
        val adapter = adapter()
        enableNotifications(adapter)
        adapter.updateList(listOf(contact(uuid = "a", name = "Bob")))

        adapter.onBindViewHolder(adapter.GroupMemberViewHolder(binding), 0)

        verify { name.setText("Bob") }
    }

    @Test
    fun `diff callback compares uuid and contents`() {
        val callback = GroupMemberAdapter.DiffCallback()
        val a = contact(uuid = "a")
        val b = contact(uuid = "b")
        val changed = contact(uuid = "a", name = "other")

        assertTrue(callback.areItemsTheSame(a, changed))
        assertFalse(callback.areItemsTheSame(a, b))
        assertTrue(callback.areContentsTheSame(a, contact(uuid = "a")))
        assertFalse(callback.areContentsTheSame(a, changed))
    }
}
