package com.example.muzea.utils

import android.content.SharedPreferences

/**
 * In-memory SharedPreferences для JVM-тестов.
 *
 * Robolectric здесь не нужен: он поднимает отдельный classloader, и JaCoCo
 * перестаёт видеть покрытие тестируемого кода. Простая реализация интерфейса
 * быстрее и честнее для отчёта.
 */
class FakeSharedPreferences : SharedPreferences {

    private val values = LinkedHashMap<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = synchronized(values) { LinkedHashMap(values) }

    override fun getString(key: String?, defValue: String?): String? =
        synchronized(values) { values[key] as? String } ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?
    ): MutableSet<String>? = synchronized(values) {
        (values[key] as? MutableSet<String>)?.toMutableSet()
    } ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        synchronized(values) { values[key] as? Int } ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        synchronized(values) { values[key] as? Long } ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        synchronized(values) { values[key] as? Float } ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        synchronized(values) { values[key] as? Boolean } ?: defValue

    override fun contains(key: String?): Boolean = synchronized(values) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) synchronized(listeners) { listeners += listener }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) synchronized(listeners) { listeners -= listener }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val updates = LinkedHashMap<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?) = apply { updates[key] = value }

        override fun putStringSet(key: String, values: MutableSet<String>?) =
            apply { updates[key] = values?.toMutableSet() }

        override fun putInt(key: String, value: Int) = apply { updates[key] = value }

        override fun putLong(key: String, value: Long) = apply { updates[key] = value }

        override fun putFloat(key: String, value: Float) = apply { updates[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }

        override fun remove(key: String) = apply { removals += key }

        override fun clear() = apply { clearRequested = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changed = mutableSetOf<String>()
            synchronized(values) {
                if (clearRequested) {
                    changed += values.keys
                    values.clear()
                }
                changed += removals
                removals.forEach(values::remove)
                updates.forEach { (key, value) ->
                    changed += key
                    if (value == null) values.remove(key) else values[key] = value
                }
            }
            synchronized(listeners) {
                listeners.toList().forEach { listener ->
                    changed.forEach { listener.onSharedPreferenceChanged(this@FakeSharedPreferences, it) }
                }
            }
            updates.clear()
            removals.clear()
            clearRequested = false
        }
    }
}
