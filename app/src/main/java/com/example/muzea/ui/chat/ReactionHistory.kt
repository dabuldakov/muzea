package com.example.muzea.ui.chat

import android.content.Context

/** Полный набор реакций и последние использованные (до 15). */
object ReactionHistory {

    val FULL = listOf(
        "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏",
        "🙏", "💯", "😍", "🤔", "😡", "🥰", "😎", "🤝",
        "✅", "❌", "👀", "🤯", "😴", "💜", "⚡", "🚀"
    )

    private const val PREFS = "ui_prefs"
    private const val KEY = "recent_reactions"
    private const val MAX = 15
    private const val SEP = "|"

    fun recent(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        val stored = raw?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()
        return if (stored.isEmpty()) FULL.take(MAX) else stored.take(MAX)
    }

    fun record(context: Context, emoji: String) {
        val updated = (listOf(emoji) + recent(context).filter { it != emoji }).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, updated.joinToString(SEP)).apply()
    }
}
