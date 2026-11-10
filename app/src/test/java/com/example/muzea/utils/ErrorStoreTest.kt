package com.example.muzea.utils

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ErrorStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val prefs = mutableMapOf<String, String?>()

    private fun context(): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns temp.root
        every { context.getSharedPreferences(any(), any()) } returns object : SharedPreferences {
            override fun getString(key: String?, defValue: String?) = prefs[key] ?: defValue
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                override fun putString(key: String?, value: String?) = apply { prefs[key.orEmpty()] = value }
                override fun remove(key: String?) = apply { prefs.remove(key.orEmpty()) }
                override fun clear() = apply { prefs.clear() }
                override fun putLong(key: String?, value: Long) = this
                override fun putInt(key: String?, value: Int) = this
                override fun putBoolean(key: String?, value: Boolean) = this
                override fun putFloat(key: String?, value: Float) = this
                override fun putStringSet(key: String?, values: MutableSet<String>?) = this
                override fun commit() = true
                override fun apply() = Unit
            }
            override fun getAll() = prefs
            override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
            override fun getInt(key: String?, defValue: Int) = defValue
            override fun getLong(key: String?, defValue: Long) = defValue
            override fun getFloat(key: String?, defValue: Float) = defValue
            override fun getBoolean(key: String?, defValue: Boolean) = defValue
            override fun contains(key: String?) = prefs.containsKey(key)
            override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?
            ) = Unit
            override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?
            ) = Unit
        }
        return context
    }

    private fun event(id: String) = ErrorEvent(
        eventId = id,
        level = "ERROR",
        tag = "test",
        message = "boom",
        stack = "stack",
        appVersion = "1.7.30",
        versionCode = 64,
        buildType = "release",
        device = "test",
        os = "Android 16"
    )

    @Test
    fun `events are appended and read in order`() {
        val store = ErrorStore(context())

        store.append(event("a"))
        store.append(event("b"))

        assertEquals(listOf("a", "b"), store.pending().map { it.eventId })
    }

    @Test
    fun `remove deletes only successfully sent events`() {
        val store = ErrorStore(context())
        store.append(event("a"))
        store.append(event("b"))
        store.append(event("c"))

        store.remove(setOf("a", "c"))

        assertEquals(listOf("b"), store.pending().map { it.eventId })
    }

    @Test
    fun `buffer lives in filesDir and cursor survives`() {
        val context = context()
        val store = ErrorStore(context)
        store.append(event("a"))
        store.lastUploadedAt = "2026-11-10T10:00:00Z"

        assertTrue(File(temp.root, "error_events.jsonl").exists())
        assertEquals("2026-11-10T10:00:00Z", ErrorStore(context).lastUploadedAt)
    }
}
