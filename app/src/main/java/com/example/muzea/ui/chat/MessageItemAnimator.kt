package com.example.muzea.ui.chat

import android.view.animation.AccelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * Удаление сообщения «схлопывается» в точку и гаснет — вместо пузыря с
 * надписью «сообщение удалено».
 */
class MessageItemAnimator : DefaultItemAnimator() {

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        dispatchRemoveStarting(holder)
        view.animate()
            .alpha(0f)
            .scaleX(0.05f)
            .scaleY(0.05f)
            .setDuration(REMOVE_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                dispatchRemoveFinished(holder)
            }
            .start()
        return true
    }

    private companion object {
        const val REMOVE_DURATION_MS = 320L
    }
}
