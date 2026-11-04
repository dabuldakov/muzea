package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import com.example.muzea.data.model.AddContactRequest
import com.example.muzea.data.model.ChatUserResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.model.PresenceResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ContactRepositoryImplTest {

    private val api = mockk<ChatApiService>()
    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val repository = ContactRepositoryImpl(api, auth)

    @Test
    fun `loadContacts returns an error when the chat token is missing`() = runTest {
        coEvery { auth.isAuthenticated() } returns false

        val last = repository.loadContacts().toList().last()

        assertTrue((last as Resource.Error).message!!.contains("Chat auth failed"))
        coVerify(exactly = 0) { api.getContacts() }
    }

    @Test
    fun `loadContacts maps the server response`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getContacts() } returns httpOk(listOf(contact("u1", "Anna")))

        val last = repository.loadContacts().toList().last()

        assertEquals(listOf("u1"), (last as Resource.Success).data!!.map { it.contactUserUuid })
    }

    @Test
    fun `loadContacts retries after 401`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getContacts() } returnsMany listOf(
            httpError(401),
            httpOk(listOf(contact("u1", "Anna")))
        )

        val last = repository.loadContacts().toList().last()

        assertTrue(last is Resource.Success)
        coVerify(exactly = 2) { api.getContacts() }
        verify(exactly = 1) { auth.invalidate() }
    }

    @Test
    fun `loadContacts reports a server failure`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getContacts() } returns httpError(500)

        val last = repository.loadContacts().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load contacts"))
    }

    @Test
    fun `loadContacts reports a network exception`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getContacts() } throws IOException("offline")

        val last = repository.loadContacts().toList().last()

        assertEquals("Network error: offline", (last as Resource.Error).message)
    }

    @Test
    fun `addContactByUsername first resolves the user then adds contact`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getUserByUsername("anna") } returns httpOk(user("uuid-anna", "anna"))
        val request = slot<AddContactRequest>()
        coEvery { api.addContact(capture(request)) } returns httpOk(contact("uuid-anna", "Anna"))

        val last = repository.addContactByUsername("anna").toList().last()

        assertEquals("uuid-anna", request.captured.contactUserUuid)
        assertEquals("uuid-anna", (last as Resource.Success).data!!.contactUserUuid)
    }

    @Test
    fun `addContactByUsername stops when the user does not exist`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getUserByUsername("ghost") } returns httpError(404)

        val last = repository.addContactByUsername("ghost").toList().last()

        assertEquals("User not found: ghost", (last as Resource.Error).message)
        coVerify(exactly = 0) { api.addContact(any()) }
    }

    @Test
    fun `addContactByUsername reports a failed add request`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        coEvery { api.getUserByUsername("anna") } returns httpOk(user("uuid-anna", "anna"))
        coEvery { api.addContact(any()) } returns httpError(409)

        val last = repository.addContactByUsername("anna").toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to add contact"))
    }

    @Test
    fun `loadPresence returns empty map for blank input without a request`() = runTest {
        val result = repository.loadPresence(listOf("", "  "))

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { api.getPresence(any()) }
    }

    @Test
    fun `loadPresence removes duplicates and keeps the response`() = runTest {
        coEvery { api.getPresence(any()) } returns httpOk(
            listOf(PresenceResponse("u1", online = true, lastSeenAt = null))
        )

        val result = repository.loadPresence(listOf("u1", "u1", ""))

        assertEquals(setOf("u1"), result.keys)
        assertTrue(result.getValue("u1").online)
        coVerify(exactly = 1) { api.getPresence(listOf("u1")) }
    }

    @Test
    fun `loadPresence splits large lists into request chunks`() = runTest {
        val chunks = mutableListOf<List<String>>()
        coEvery { api.getPresence(capture(chunks)) } answers {
            val uuids = firstArg<List<String>>()
            httpOk(uuids.map { PresenceResponse(it, online = false, lastSeenAt = null) })
        }
        val uuids = (1..101).map { "u$it" }

        val result = repository.loadPresence(uuids)

        assertEquals(2, chunks.size)
        assertEquals(100, chunks[0].size)
        assertEquals(1, chunks[1].size)
        assertEquals(101, result.size)
    }

    @Test
    fun `loadPresence keeps partial data when one chunk fails`() = runTest {
        var call = 0
        coEvery { api.getPresence(any()) } answers {
            call++
            if (call == 1) throw IOException("boom")
            httpOk(listOf(PresenceResponse("u101", online = true, lastSeenAt = null)))
        }

        val result = repository.loadPresence((1..101).map { "u$it" })

        assertEquals(setOf("u101"), result.keys)
    }

    private fun contact(uuid: String, name: String) = ContactResponse(
        contactUuid = "contact-$uuid",
        contactUserId = 1L,
        contactUserUuid = uuid,
        username = name.lowercase(),
        firstName = null,
        lastName = null,
        fullName = name,
        avatarUrl = null,
        contactName = name,
        isOnline = false,
        lastSeenAt = null,
        addedAt = null
    )

    private fun user(uuid: String, username: String) = ChatUserResponse(
        userUuid = uuid,
        username = username,
        firstName = null,
        lastName = null,
        fullName = null,
        avatarUrl = null,
        isOnline = false
    )
}
