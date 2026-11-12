package com.example.muzea.ui.chat

/** Порядок реакций: сначала последние использованные, затем остальные. */
object ReactionOrder {

    fun ordered(recent: List<String>, full: List<String>): List<String> =
        recent + full.filterNot { it in recent }
}
