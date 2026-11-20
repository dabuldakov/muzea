package com.example.muzea.utils

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Перенос data из системного FCM-уведомления в MainActivity.
 *
 * Тап по notification+data запускает launcher (LoginActivity), а не
 * MainActivity, поэтому extras нужно сохранить и передать после входа.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PendingPushTest {

    @Before
    fun setUp() {
        PendingPush.clear()
    }

    private fun intent(vararg pairs: Pair<String, Any>): Intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            pairs.forEach { (key, value) ->
                when (value) {
                    is Long -> putExtra(key, value)
                    else -> putExtra(key, value.toString())
                }
            }
        }

    @Test
    fun `chat payload is forwarded`() {
        PendingPush.capture(intent("chatUuid" to "chat-1", "type" to "MESSAGE"))

        val next = intent()
        PendingPush.applyTo(next)

        assertEquals("chat-1", next.getStringExtra("chatUuid"))
        assertEquals("MESSAGE", next.getStringExtra("type"))
    }

    @Test
    fun `news payload is forwarded`() {
        PendingPush.capture(intent("newsId" to "42"))

        val next = intent()
        PendingPush.applyTo(next)

        assertEquals("42", next.getStringExtra("newsId"))
    }

    @Test
    fun `app own deep link extras are forwarded too`() {
        PendingPush.capture(
            intent(MainActivity.EXTRA_OPEN_CHAT_UUID to "chat-2")
        )

        val next = intent()
        PendingPush.applyTo(next)

        assertEquals("chat-2", next.getStringExtra(MainActivity.EXTRA_OPEN_CHAT_UUID))
    }

    @Test
    fun `ordinary launch does not clear a pending push`() {
        PendingPush.capture(intent("chatUuid" to "chat-1"))

        // Обычный запуск приложения без данных push (например, из лаунчера).
        PendingPush.capture(intent())

        val next = intent()
        PendingPush.applyTo(next)

        assertEquals("chat-1", next.getStringExtra("chatUuid"))
    }

    @Test
    fun `payload is applied only once`() {
        PendingPush.capture(intent("chatUuid" to "chat-1"))

        val first = intent()
        PendingPush.applyTo(first)
        val second = intent()
        PendingPush.applyTo(second)

        assertTrue(first.hasExtra("chatUuid"))
        assertFalse(second.hasExtra("chatUuid"))
    }
}
