package com.example.muzea.ui.chat

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.databinding.ItemParticipantBinding
import com.example.muzea.domain.model.ChatParticipant
import com.example.muzea.utils.AvatarLoader
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты ParticipantAdapter без Robolectric.
 *
 * Строку проверяем на моке [ItemParticipantBinding]: поля data-binding —
 * публичные final-поля без геттеров, их подставляем рефлексией. Аватары
 * не грузятся.
 */
class ParticipantAdapterTest {

    private lateinit var root: LinearLayout
    private lateinit var name: TextView
    private lateinit var username: TextView
    private lateinit var role: TextView
    private lateinit var avatar: ImageView
    private lateinit var binding: ItemParticipantBinding
    private lateinit var context: Context

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        name = mockk(relaxed = true)
        username = mockk(relaxed = true)
        role = mockk(relaxed = true)
        avatar = mockk(relaxed = true)
        binding = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { binding.root } returns root
        setViewField("ivAvatar", avatar)
        setViewField("tvName", name)
        setViewField("tvUsername", username)
        setViewField("tvRole", role)
        every { root.context } returns context

        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
    }

    @After
    fun tearDown() {
        unmockkObject(AvatarLoader)
    }

    private fun setViewField(field: String, value: View) {
        val f = ItemParticipantBinding::class.java.getDeclaredField(field)
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

    private fun participant(
        userUuid: String? = "u-1",
        username: String? = "anna",
        fullName: String? = null,
        role: String? = "MEMBER",
        avatarUrl: String? = null
    ) = ChatParticipant(
        userUuid = userUuid,
        userId = 1L,
        username = username,
        firstName = null,
        lastName = null,
        fullName = fullName,
        nickname = null,
        avatarUrl = avatarUrl,
        role = role,
        online = false,
        lastSeenAt = null,
        joinedAt = null
    )

    private fun holder(myUuid: String? = "me", participant: ChatParticipant) =
        ParticipantAdapter(myUuid).ParticipantViewHolder(binding).also { it.bind(participant) }

    @Test
    fun `display name prefers the full name`() {
        holder(participant = participant(fullName = "Anna P", username = "anna"))

        verify { name.setText("Anna P") }
    }

    @Test
    fun `display name falls back to the username`() {
        holder(participant = participant(fullName = null, username = "anna"))

        verify { name.setText("anna") }
    }

    @Test
    fun `display name falls back to the placeholder`() {
        holder(participant = participant(fullName = null, username = null))

        verify { name.setText("Participant") }
    }

    @Test
    fun `another users username is prefixed with an at sign`() {
        holder(myUuid = "me", participant = participant(userUuid = "other", username = "bob"))

        verify { username.setText("@bob") }
    }

    @Test
    fun `own participant shows you`() {
        holder(myUuid = "me", participant = participant(userUuid = "me", username = "bob"))

        verify { username.setText("You") }
    }

    @Test
    fun `missing username without a known self binds empty`() {
        holder(myUuid = null, participant = participant(userUuid = "other", username = null))

        verify { username.setText("") }
    }

    @Test
    fun `owner role shows the badge`() {
        every { context.getString(R.string.group_role_owner) } returns "Owner"

        holder(participant = participant(role = "OWNER"))

        verify { role.visibility = View.VISIBLE }
        verify { role.setText("Owner") }
    }

    @Test
    fun `non owner role hides the badge`() {
        holder(participant = participant(role = "MEMBER"))

        verify { role.visibility = View.GONE }
    }

    @Test
    fun `loads the participant avatar`() {
        holder(participant = participant(avatarUrl = "/a.png"))

        verify { AvatarLoader.load(avatar, "/a.png") }
    }

    @Test
    fun `adapter starts empty`() {
        assertEquals(0, ParticipantAdapter("me").itemCount)
    }

    @Test
    fun `updateList publishes the first list synchronously`() {
        val adapter = ParticipantAdapter("me")
        enableNotifications(adapter)
        adapter.updateList(listOf(participant(userUuid = "a"), participant(userUuid = "b")))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder binds the item at the requested position`() {
        val adapter = ParticipantAdapter("me")
        enableNotifications(adapter)
        adapter.updateList(listOf(participant(userUuid = "a", fullName = "Bob")))

        adapter.onBindViewHolder(adapter.ParticipantViewHolder(binding), 0)

        verify { name.setText("Bob") }
    }

    @Test
    fun `diff callback compares user uuid and contents`() {
        val callback = ParticipantAdapter.DiffCallback()
        val a = participant(userUuid = "a")
        val b = participant(userUuid = "b")
        val changed = participant(userUuid = "a", fullName = "other")

        assertTrue(callback.areItemsTheSame(a, changed))
        assertFalse(callback.areItemsTheSame(a, b))
        assertTrue(callback.areContentsTheSame(a, participant(userUuid = "a")))
        assertFalse(callback.areContentsTheSame(a, changed))
    }
}
