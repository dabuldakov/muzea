package com.example.muzea.ui.chat

/** Ширина модалки: все действия должны влезать без горизонтальной прокрутки. */
object ActionsPopupWidth {

    fun calculate(
        actionsCount: Int,
        iconSize: Int,
        padding: Int,
        screenWidth: Int,
        margin: Int
    ): Int {
        val needed = actionsCount * iconSize + padding * 2
        val max = screenWidth - margin * 2
        return needed.coerceIn(padding * 2, max)
    }
}
