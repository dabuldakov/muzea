package com.example.muzea.ui.chat

import android.view.View
import com.example.muzea.domain.model.Message

/**
 * Отрисовка статических частей экрана переписки: состояние загрузки, баннеры,
 * закреплённые, вложения и пустое состояние.
 */
internal class ChatConversationRenderer(
    private val stateView: ConversationStateView,
    private val composerBanner: ComposerBanner,
    private val pinnedBar: PinnedBarView,
    private val pendingAttachments: PendingAttachmentsView,
    private val emptyView: View,
    private val listView: View,
    private val errorView: View
) {

    fun renderState(state: ChatConversationUiState) {
        stateView.render(
            isLoading = state.isLoading,
            hasMessages = state.messages.isNotEmpty(),
            errorText = state.error
        )
    }

    fun renderComposerBanner(state: ChatConversationUiState) {
        composerBanner.render(replyTo = state.replyTo, editing = state.editing)
    }

    fun renderPinnedBar(state: ChatConversationUiState) {
        pinnedBar.render(state.pinnedMessages)
    }

    fun renderPendingAttachments(pending: List<PendingAttachment>) {
        pendingAttachments.render(pending)
    }

    fun renderEmptyState(messages: List<Message>) {
        if (messages.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            listView.visibility = View.GONE
            errorView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            errorView.visibility = View.GONE
        }
    }
}
