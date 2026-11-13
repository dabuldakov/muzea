package com.example.muzea.ui.chat

/**
 * Геометрия раскрытия карточки. Карточка не меняет положение: край со стороны
 * сообщения закрепляется, а расти она может только в свободную сторону.
 */
object PopupResize {

    /** Растём вверх, если карточка стоит над точкой касания. */
    fun growsUp(popupY: Int, popupHeight: Int, tapY: Int): Boolean =
        popupY + popupHeight <= tapY

    /**
     * Y окна для промежуточной высоты. При росте вверх закреплён нижний край,
     * вниз — верхний; остальное прижимается к экрану.
     */
    fun yForHeight(
        growsUp: Boolean,
        edgeAnchor: Int,
        height: Int,
        screenHeight: Int,
        margin: Int,
        topLimit: Int = margin
    ): Int {
        val raw = if (growsUp) edgeAnchor - height else edgeAnchor
        val minY = topLimit.coerceAtLeast(margin)
        val maxY = (screenHeight - height - margin).coerceAtLeast(minY)
        return raw.coerceIn(minY, maxY)
    }
}
