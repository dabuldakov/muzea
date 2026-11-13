package com.example.muzea.ui.chat

import com.example.muzea.domain.model.MessageReaction

/**
 * Раскладывает реакции по авторам: в строке получателя показываем значки,
 * которые поставил именно он, в порядке времени.
 */
object ReactionsByUser {

    const val MAX_VISIBLE = 3

    fun map(reactions: List<MessageReaction>): Map<String, List<String>> =
        reactions
            .flatMap { reaction ->
                reaction.users.map { user ->
                    val uuid = user.userUuid.orEmpty()
                    Triple(uuid, reaction.emoji, user.at.orEmpty())
                }
            }
            .filter { it.first.isNotBlank() }
            .groupBy { it.first }
            .mapValues { (_, rows) ->
                rows.sortedBy { it.third }.map { it.second }
            }

    /** Видимые значки и сколько осталось за счётчиком. */
    fun visible(emojis: List<String>, limit: Int = MAX_VISIBLE): Pair<List<String>, Int> =
        if (emojis.size <= limit) {
            emojis to 0
        } else {
            emojis.take(limit) to (emojis.size - limit)
        }
}
