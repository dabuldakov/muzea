package com.example.muzea.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsentManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        context = mockk()
        every { context.getSharedPreferences(any(), any()) } returns prefs
    }

    @Test
    fun `consent is not accepted by default`() {
        assertFalse(ConsentManager.isAccepted(context))
    }

    @Test
    fun `accept stores the current version`() {
        ConsentManager.accept(context)

        assertTrue(ConsentManager.isAccepted(context))
        assertTrue(prefs.getLong("accepted_at", 0L) > 0L)
    }

    @Test
    fun `revoke clears the consent`() {
        ConsentManager.accept(context)

        ConsentManager.revoke(context)

        assertFalse(ConsentManager.isAccepted(context))
    }

    @Test
    fun `older revision is not treated as accepted`() {
        prefs.edit().putInt("accepted_version", ConsentManager.VERSION - 1).commit()

        assertFalse(ConsentManager.isAccepted(context))
    }
}
