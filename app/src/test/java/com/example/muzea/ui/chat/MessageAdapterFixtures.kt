package com.example.muzea.ui.chat

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.ui.PlayerView
import com.example.muzea.databinding.ItemAttachmentAudioBinding
import com.example.muzea.databinding.ItemAttachmentDocumentBinding
import com.example.muzea.databinding.ItemAttachmentImageBinding
import com.example.muzea.databinding.ItemAttachmentVideoBinding
import com.example.muzea.databinding.ItemAttachmentVideoNoteBinding
import com.example.muzea.databinding.ItemAttachmentVoiceBinding
import com.example.muzea.databinding.ItemMessageIncomingBinding
import com.example.muzea.databinding.ItemMessageOutgoingBinding
import com.google.android.material.card.MaterialCardView
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

/**
 * Собирает моки сгенерированных binding'ов строк чата без Robolectric.
 *
 * Поля binding'ов — публичные final-поля без геттеров, relaxed-мок их не
 * инициализирует. Поэтому подставляем вьюхи вручную через рефлексию. Это
 * позволяет вызывать `bind()` держателей и функции-хелперы адаптера в обычном
 * JVM-тесте, покрытие которого видит JaCoCo (Robolectric — нет).
 */
internal class MessageAdapterFixtures {

    val context: Context = mockk(relaxed = true)

    val root: LinearLayout = mockk(relaxed = true)
    val ivAvatar: ImageView = mockk(relaxed = true)
    val ivGif: ImageView = mockk(relaxed = true)
    val ivLinkImage: ImageView = mockk(relaxed = true)
    val ivStatus: ImageView = mockk(relaxed = true)
    val linkPreview: LinearLayout = mockk(relaxed = true)
    val replyQuote: LinearLayout = mockk(relaxed = true)
    val attachmentsContainer: LinearLayout = mockk(relaxed = true)
    val reactionsContainer: LinearLayout = mockk(relaxed = true)
    val tvMessage: TextView = mockk(relaxed = true)
    val tvSenderName: TextView = mockk(relaxed = true)
    val tvReplyName: TextView = mockk(relaxed = true)
    val tvReplyText: TextView = mockk(relaxed = true)
    val tvTime: TextView = mockk(relaxed = true)
    val tvEdited: TextView = mockk(relaxed = true)
    val tvPower: TextView = mockk(relaxed = true)
    val tvLinkTitle: TextView = mockk(relaxed = true)
    val tvLinkDomain: TextView = mockk(relaxed = true)
    val playerGif: PlayerView = mockk(relaxed = true)

    val incomingBinding: ItemMessageIncomingBinding = mockk(relaxed = true)
    val outgoingBinding: ItemMessageOutgoingBinding = mockk(relaxed = true)

    val imageAttachment: ItemAttachmentImageBinding = mockk(relaxed = true)
    val videoAttachment: ItemAttachmentVideoBinding = mockk(relaxed = true)
    val videoNoteAttachment: ItemAttachmentVideoNoteBinding = mockk(relaxed = true)
    val audioAttachment: ItemAttachmentAudioBinding = mockk(relaxed = true)
    val voiceAttachment: ItemAttachmentVoiceBinding = mockk(relaxed = true)
    val documentAttachment: ItemAttachmentDocumentBinding = mockk(relaxed = true)

    val imageCard: MaterialCardView = mockk(relaxed = true)
    val videoCard: MaterialCardView = mockk(relaxed = true)
    val videoNoteCard: MaterialCardView = mockk(relaxed = true)
    val audioCard: MaterialCardView = mockk(relaxed = true)
    val voiceCard: MaterialCardView = mockk(relaxed = true)
    val documentCard: MaterialCardView = mockk(relaxed = true)

    val ivImage: ImageView = mockk(relaxed = true)
    val ivVideoThumb: ImageView = mockk(relaxed = true)
    val ivVideoNoteThumb: ImageView = mockk(relaxed = true)
    val ivAudioPlay: ImageView = mockk(relaxed = true)
    val ivVoicePlay: ImageView = mockk(relaxed = true)
    val tvVideoDuration: TextView = mockk(relaxed = true)
    val tvVideoNoteDuration: TextView = mockk(relaxed = true)
    val tvAudioName: TextView = mockk(relaxed = true)
    val tvAudioSize: TextView = mockk(relaxed = true)
    val tvVoiceDuration: TextView = mockk(relaxed = true)
    val tvDocumentName: TextView = mockk(relaxed = true)
    val tvDocumentSize: TextView = mockk(relaxed = true)

    init {
        // Код часто зовёт getString(...) для подписей/заглушек.
        every { context.getString(any<Int>()) } returns "text"

        // Все вьюхи считают своим контекстом один и тот же мок.
        every { root.context } returns context
        every { tvMessage.context } returns context
        every { replyQuote.context } returns context
        every { attachmentsContainer.context } returns context
        every { linkPreview.context } returns context
        every { ivGif.context } returns context

        every { incomingBinding.root } returns root
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "ivAvatar", ivAvatar)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "ivGif", ivGif)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "playerGif", playerGif)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "linkPreview", linkPreview)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "ivLinkImage", ivLinkImage)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvLinkTitle", tvLinkTitle)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvLinkDomain", tvLinkDomain)
        inject(
            ItemMessageIncomingBinding::class.java, incomingBinding,
            "attachmentsContainer", attachmentsContainer
        )
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "reactionsContainer", reactionsContainer)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvMessage", tvMessage)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvReplyName", tvReplyName)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvReplyText", tvReplyText)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "replyQuote", replyQuote)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvSenderName", tvSenderName)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvEdited", tvEdited)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvPower", tvPower)
        inject(ItemMessageIncomingBinding::class.java, incomingBinding, "tvTime", tvTime)

        every { outgoingBinding.root } returns root
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "ivGif", ivGif)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "playerGif", playerGif)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "linkPreview", linkPreview)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "ivLinkImage", ivLinkImage)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "ivStatus", ivStatus)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvLinkTitle", tvLinkTitle)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvLinkDomain", tvLinkDomain)
        inject(
            ItemMessageOutgoingBinding::class.java, outgoingBinding,
            "attachmentsContainer", attachmentsContainer
        )
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "reactionsContainer", reactionsContainer)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvMessage", tvMessage)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvReplyName", tvReplyName)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvReplyText", tvReplyText)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "replyQuote", replyQuote)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvEdited", tvEdited)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvPower", tvPower)
        inject(ItemMessageOutgoingBinding::class.java, outgoingBinding, "tvTime", tvTime)

        mockkStatic(ItemAttachmentImageBinding::class)
        inject(ItemAttachmentImageBinding::class.java, imageAttachment, "ivImage", ivImage)
        every { imageAttachment.root } returns imageCard
        every { ItemAttachmentImageBinding.inflate(any(), any(), any()) } returns imageAttachment

        mockkStatic(ItemAttachmentVideoBinding::class)
        inject(ItemAttachmentVideoBinding::class.java, videoAttachment, "ivThumb", ivVideoThumb)
        inject(ItemAttachmentVideoBinding::class.java, videoAttachment, "tvDuration", tvVideoDuration)
        every { videoAttachment.root } returns videoCard
        every { ItemAttachmentVideoBinding.inflate(any(), any(), any()) } returns videoAttachment

        mockkStatic(ItemAttachmentVideoNoteBinding::class)
        inject(
            ItemAttachmentVideoNoteBinding::class.java, videoNoteAttachment,
            "ivThumb", ivVideoNoteThumb
        )
        inject(
            ItemAttachmentVideoNoteBinding::class.java, videoNoteAttachment,
            "tvDuration", tvVideoNoteDuration
        )
        every { videoNoteAttachment.root } returns videoNoteCard
        every { ItemAttachmentVideoNoteBinding.inflate(any(), any(), any()) } returns
            videoNoteAttachment

        mockkStatic(ItemAttachmentAudioBinding::class)
        inject(ItemAttachmentAudioBinding::class.java, audioAttachment, "ivPlay", ivAudioPlay)
        inject(ItemAttachmentAudioBinding::class.java, audioAttachment, "tvName", tvAudioName)
        inject(ItemAttachmentAudioBinding::class.java, audioAttachment, "tvSize", tvAudioSize)
        every { audioAttachment.root } returns audioCard
        every { ItemAttachmentAudioBinding.inflate(any(), any(), any()) } returns audioAttachment

        mockkStatic(ItemAttachmentVoiceBinding::class)
        inject(ItemAttachmentVoiceBinding::class.java, voiceAttachment, "ivPlay", ivVoicePlay)
        inject(ItemAttachmentVoiceBinding::class.java, voiceAttachment, "tvDuration", tvVoiceDuration)
        every { voiceAttachment.root } returns voiceCard
        every { ItemAttachmentVoiceBinding.inflate(any(), any(), any()) } returns voiceAttachment

        mockkStatic(ItemAttachmentDocumentBinding::class)
        inject(ItemAttachmentDocumentBinding::class.java, documentAttachment, "tvName", tvDocumentName)
        inject(ItemAttachmentDocumentBinding::class.java, documentAttachment, "tvSize", tvDocumentSize)
        every { documentAttachment.root } returns documentCard
        every { ItemAttachmentDocumentBinding.inflate(any(), any(), any()) } returns
            documentAttachment
    }

    fun incomingHolder() = MessageAdapter.IncomingViewHolder(incomingBinding)

    fun outgoingHolder() = MessageAdapter.OutgoingViewHolder(outgoingBinding)

    private fun inject(clazz: Class<*>, target: Any, field: String, value: Any) {
        val f = clazz.getDeclaredField(field)
        f.isAccessible = true
        f.set(target, value)
    }
}
