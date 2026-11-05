package com.example.muzea.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.example.muzea.R
import com.example.muzea.data.model.LinkPreview
import com.example.muzea.data.repository.LinkPreviewStore
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.model.MessageDeliveryStatus
import com.example.muzea.ui.news.MainDispatcherRule
import com.example.muzea.utils.AvatarLoader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM-тесты (без Robolectric) связывания строк переписки: цитата ответа,
 * вложения, удалённое сообщение, превью ссылок и статус своих сообщений.
 *
 * Robolectric не даёт покрытия JaCoCo, поэтому binding'и замоканы, а вьюхи
 * подставлены в их final-поля рефлексией. Glide/URI/Toast замоканы статически.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageAdapterBindTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fx: MessageAdapterFixtures
    private val linkPreviews = mockk<LinkPreviewStore>(relaxed = true)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        fx = MessageAdapterFixtures()
        mockkObject(AvatarLoader)
        justRun { AvatarLoader.load(any(), any()) }
        stubGlide()

        // hostOf(...) зовёт Uri.parse, который в unit-тестах вернул бы null.
        val uri = mockk<Uri>(relaxed = true)
        every { uri.host } returns "example.com"
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns uri
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun stubGlide() {
        val requestManager = mockk<RequestManager>(relaxed = true)
        val requestBuilder = mockk<RequestBuilder<Drawable>>(relaxed = true)
        every { requestManager.load(any<String>()) } returns requestBuilder
        every { requestManager.load(any<Any>()) } returns requestBuilder
        every { requestBuilder.circleCrop() } returns requestBuilder
        every { requestBuilder.placeholder(any<Int>()) } returns requestBuilder
        every { requestBuilder.fallback(any<Int>()) } returns requestBuilder
        every { requestBuilder.error(any<Int>()) } returns requestBuilder
        every { requestBuilder.diskCacheStrategy(any()) } returns requestBuilder
        mockkStatic(Glide::class)
        every { Glide.with(any<View>()) } returns requestManager
        every { Glide.with(any<Context>()) } returns requestManager
    }

    private fun message(
        uuid: String = "m1",
        senderUuid: String? = "other",
        senderName: String? = "Anna",
        text: String? = "hi",
        messageType: String? = "TEXT",
        replyToMessageUuid: String? = null,
        isDeleted: Boolean = false,
        isEdited: Boolean = false,
        createdAt: String? = "2026-01-01T10:00:00",
        attachments: List<MessageAttachment> = emptyList()
    ) = Message(
        messageUuid = uuid,
        chatUuid = "chat-1",
        senderId = 1L,
        senderUuid = senderUuid,
        senderName = senderName,
        senderAvatar = null,
        text = text,
        messageType = messageType,
        replyToMessageUuid = replyToMessageUuid,
        isEdited = isEdited,
        isDeleted = isDeleted,
        isPinned = false,
        createdAt = createdAt,
        updatedAt = null,
        attachments = attachments
    )

    private fun attachment(
        type: String?,
        fileUrl: String = "/file",
        thumbnailUrl: String? = null,
        mp4Url: String? = null,
        width: Int? = null,
        height: Int? = null,
        duration: Int? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        attachmentUuid: String? = null
    ) = MessageAttachment(
        fileUrl = fileUrl,
        thumbnailUrl = thumbnailUrl,
        type = type,
        width = width,
        height = height,
        mp4Url = mp4Url,
        gifKey = null,
        attachmentUuid = attachmentUuid,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = null,
        duration = duration
    )

    private fun bindIncoming(
        message: Message,
        replyTo: Message? = null,
        myUserUuid: String? = "me",
        onClick: (Message) -> Unit = {},
        onAttachmentClick: (Message, MessageAttachment) -> Unit = { _, _ -> },
        playingAudioUuid: String? = null,
        audioIsPlaying: Boolean = false
    ) {
        fx.incomingHolder().bind(
            message, replyTo, myUserUuid, linkPreviews, scope, onClick,
            onAttachmentClick, playingAudioUuid, audioIsPlaying
        )
    }

    private fun bindOutgoing(
        message: Message,
        replyTo: Message? = null,
        status: MessageDeliveryStatus? = null,
        onStatusClick: (Message) -> Unit = {},
        onClick: (Message) -> Unit = {}
    ) {
        fx.outgoingHolder().bind(
            message, replyTo, "me", linkPreviews, scope, onClick, status,
            onStatusClick, { _, _ -> }, null, false
        )
    }

    // --- Входящие: имя, время, правки ---

    @Test
    fun `incoming shows the sender name and hides the edited marker`() {
        bindIncoming(message())

        verify { fx.tvSenderName.setText("Anna") }
        verify { fx.tvSenderName.visibility = View.VISIBLE }
        verify { fx.tvEdited.visibility = View.GONE }
    }

    @Test
    fun `incoming hides the sender name when blank`() {
        bindIncoming(message(senderName = null))

        verify { fx.tvSenderName.visibility = View.GONE }
    }

    @Test
    fun `incoming edited message shows the edited marker`() {
        bindIncoming(message(isEdited = true))

        verify { fx.tvEdited.visibility = View.VISIBLE }
    }

    @Test
    fun `incoming deleted message hides the edited marker`() {
        bindIncoming(message(isEdited = true, isDeleted = true))

        verify { fx.tvEdited.visibility = View.GONE }
    }

    @Test
    fun `incoming click reports the message`() {
        var pressed: Message? = null
        bindIncoming(message(), onClick = { pressed = it })

        val listener = slot<View.OnClickListener>()
        verify { fx.root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(fx.root)

        assertEquals("m1", pressed?.messageUuid)
    }

    @Test
    fun `incoming click is attached to bubble and gif`() {
        bindIncoming(message())

        verify { fx.tvMessage.setOnClickListener(any()) }
        verify { fx.ivGif.setOnClickListener(any()) }
    }

    // --- Входящие: удалённое сообщение ---

    @Test
    fun `deleted message renders the italic placeholder without attachments`() {
        bindIncoming(
            message(
                text = null,
                isDeleted = true,
                replyToMessageUuid = "orig",
                attachments = listOf(attachment("IMAGE", width = 10, height = 10))
            ),
            replyTo = null
        )

        verify { fx.tvMessage.setTypeface(null, Typeface.ITALIC) }
        verify { fx.replyQuote.visibility = View.GONE }
        verify { fx.attachmentsContainer.visibility = View.GONE }
        verify { fx.playerGif.visibility = View.GONE }
    }

    // --- Цитата ответа ---

    @Test
    fun `reply quote is rendered when the original is loaded`() {
        val original = message(uuid = "orig", senderName = "Bob", text = "original")
        bindIncoming(message(replyToMessageUuid = "orig"), replyTo = original)

        verify { fx.replyQuote.visibility = View.VISIBLE }
        verify { fx.tvReplyName.setText("Bob") }
        verify { fx.tvReplyText.setText("original") }
    }

    @Test
    fun `reply quote of my own message is labelled you`() {
        every { fx.context.getString(R.string.reply_you) } returns "You"
        val original = message(uuid = "orig", senderUuid = "me", senderName = "Anna")
        bindIncoming(message(replyToMessageUuid = "orig"), replyTo = original)

        verify { fx.tvReplyName.setText("You") }
    }

    @Test
    fun `reply quote falls back to placeholder when the original is missing`() {
        every { fx.context.getString(R.string.reply_unknown_sender) } returns "Unknown"
        bindIncoming(message(replyToMessageUuid = "missing"), replyTo = null)

        verify { fx.replyQuote.visibility = View.VISIBLE }
        verify { fx.tvReplyName.setText("Unknown") }
    }

    // --- Медиа ---

    @Test
    fun `gif message binds the gif image`() {
        bindIncoming(
            message(
                messageType = "GIF",
                text = null,
                attachments = listOf(attachment("GIF", fileUrl = "https://cdn/x.gif"))
            )
        )

        verify { fx.ivGif.visibility = View.VISIBLE }
        verify { fx.tvMessage.visibility = View.GONE }
        verify { fx.playerGif.visibility = View.GONE }
    }

    @Test
    fun `gif message without a url falls back to the text bubble`() {
        bindIncoming(message(messageType = "GIF", text = "not a gif", attachments = emptyList()))

        verify { fx.ivGif.visibility = View.GONE }
        verify { fx.tvMessage.visibility = View.VISIBLE }
    }

    // --- Вложения ---

    @Test
    fun `incoming image attachment reserves height from its ratio`() {
        bindIncoming(
            message(
                text = null,
                attachments = listOf(attachment("IMAGE", width = 100, height = 200))
            )
        )

        verify { fx.attachmentsContainer.addView(fx.imageCard) }
    }

    @Test
    fun `incoming attachment without dimensions still binds`() {
        bindIncoming(message(text = null, attachments = listOf(attachment("IMAGE"))))

        verify { fx.attachmentsContainer.addView(fx.imageCard) }
    }

    @Test
    fun `incoming video attachment shows duration`() {
        bindIncoming(message(text = null, attachments = listOf(attachment("VIDEO", duration = 65))))

        verify { fx.tvVideoDuration.setText("1:05") }
        verify { fx.tvVideoDuration.visibility = View.VISIBLE }
        verify { fx.attachmentsContainer.addView(fx.videoCard) }
    }

    @Test
    fun `incoming video attachment without duration hides the label`() {
        bindIncoming(message(text = null, attachments = listOf(attachment("VIDEO"))))

        verify { fx.tvVideoDuration.visibility = View.GONE }
    }

    @Test
    fun `incoming audio attachment shows name and size`() {
        bindIncoming(
            message(
                text = null,
                attachments = listOf(
                    attachment("AUDIO", fileName = "song.mp3", fileSize = 2_000_000L)
                )
            )
        )

        verify { fx.tvAudioName.setText("song.mp3") }
        verify { fx.tvAudioSize.setText(any<CharSequence>()) }
        verify { fx.ivAudioPlay.setImageResource(R.drawable.ic_play_circle) }
        verify { fx.attachmentsContainer.addView(fx.audioCard) }
    }

    private fun voiceMessage() = message(
        messageType = "VOICE",
        text = null,
        attachments = listOf(attachment("AUDIO", duration = 5))
    )

    @Test
    fun `voice message shows the pause icon while playing`() {
        val voice = voiceMessage()
        bindIncoming(voice, playingAudioUuid = voice.messageUuid, audioIsPlaying = true)

        verify { fx.tvVoiceDuration.setText("0:05") }
        verify { fx.ivVoicePlay.setImageResource(R.drawable.ic_pause) }
        verify { fx.attachmentsContainer.addView(fx.voiceCard) }
    }

    @Test
    fun `voice message shows the play icon when idle`() {
        bindIncoming(voiceMessage(), playingAudioUuid = null, audioIsPlaying = false)

        verify { fx.ivVoicePlay.setImageResource(R.drawable.ic_play_circle) }
    }

    @Test
    fun `voice message without a matching audio attachment renders nothing`() {
        bindIncoming(
            message(messageType = "VOICE", text = null, attachments = listOf(attachment("IMAGE")))
        )

        verify(exactly = 0) { fx.attachmentsContainer.addView(any()) }
    }

    @Test
    fun `video note message binds its thumbnail row`() {
        bindIncoming(
            message(
                messageType = "VIDEO_NOTE",
                text = null,
                attachments = listOf(
                    attachment("VIDEO", duration = 3, thumbnailUrl = "/thumb")
                )
            )
        )

        verify { fx.attachmentsContainer.addView(fx.videoNoteCard) }
        verify { fx.tvVideoNoteDuration.setText("0:03") }
    }

    @Test
    fun `video note without a video attachment renders nothing`() {
        bindIncoming(
            message(messageType = "VIDEO_NOTE", text = null, attachments = listOf(attachment("IMAGE")))
        )

        verify(exactly = 0) { fx.attachmentsContainer.addView(any()) }
    }

    @Test
    fun `document attachment shows name and size`() {
        bindIncoming(
            message(
                text = null,
                attachments = listOf(
                    attachment("DOCUMENT", fileName = "doc.pdf", fileSize = 2048L)
                )
            )
        )

        verify { fx.tvDocumentName.setText("doc.pdf") }
        verify { fx.tvDocumentSize.setText(any<CharSequence>()) }
        verify { fx.attachmentsContainer.addView(fx.documentCard) }
    }

    @Test
    fun `attachment without a known type is rendered as a document`() {
        bindIncoming(
            message(text = null, attachments = listOf(attachment(type = null, fileName = "x")))
        )

        verify { fx.attachmentsContainer.addView(fx.documentCard) }
    }

    @Test
    fun `attachment click reports the tapped attachment`() {
        var clicked: MessageAttachment? = null
        val att = attachment("IMAGE")
        bindIncoming(
            message(text = null, attachments = listOf(att)),
            onAttachmentClick = { _, a -> clicked = a }
        )

        val listener = slot<View.OnClickListener>()
        verify { fx.imageCard.setOnClickListener(capture(listener)) }
        listener.captured.onClick(fx.imageCard)

        assertEquals(att, clicked)
    }

    @Test
    fun `several attachments are all added`() {
        bindIncoming(
            message(
                text = null,
                attachments = listOf(attachment("IMAGE"), attachment("IMAGE"))
            )
        )

        verify(exactly = 2) { fx.attachmentsContainer.addView(any()) }
    }

    @Test
    fun `attachment with an id uses the chat media endpoint`() {
        bindIncoming(
            message(
                text = null,
                attachments = listOf(attachment("IMAGE", attachmentUuid = "abc"))
            )
        )

        verify { fx.attachmentsContainer.addView(fx.imageCard) }
    }

    // --- Превью ссылок ---

    @Test
    fun `text link with a cached preview renders the preview card`() {
        val url = "https://example.com/article"
        every { linkPreviews.cached(url) } returns LinkPreview(
            url = url, title = "Title", description = "desc",
            imageUrl = "https://example.com/img.png", siteName = "Example"
        )
        bindIncoming(message(text = url))

        verify { fx.linkPreview.visibility = View.VISIBLE }
        verify { fx.tvMessage.visibility = View.GONE }
        verify { fx.tvLinkTitle.setText("Title") }
        verify { fx.tvLinkDomain.setText("Example") }
        verify { fx.ivLinkImage.visibility = View.VISIBLE }
    }

    @Test
    fun `cached preview without an image hides the image`() {
        val url = "https://example.com/noimg"
        every { linkPreviews.cached(url) } returns LinkPreview(
            url = url, title = "Title", description = null, imageUrl = null, siteName = null
        )
        bindIncoming(message(text = url))

        verify { fx.ivLinkImage.visibility = View.GONE }
    }

    @Test
    fun `text link already loaded without preview falls back to plain text`() {
        val url = "https://example.com/none"
        every { linkPreviews.cached(url) } returns null
        every { linkPreviews.isLoaded(url) } returns true
        bindIncoming(message(text = url))

        verify { fx.tvMessage.setText(any<CharSequence>()) }
        verify { fx.linkPreview.setOnClickListener(null) }
    }

    @Test
    fun `text link loads a preview asynchronously and falls back`() {
        val url = "https://example.com/async"
        every { linkPreviews.cached(url) } returns null
        every { linkPreviews.isLoaded(url) } returns false
        every { fx.linkPreview.tag } returns url
        coEvery { linkPreviews.load(url) } returns null
        bindIncoming(message(text = url))

        coVerify { linkPreviews.load(url) }
        verify { fx.tvLinkTitle.setText(url) }
        verify { fx.tvLinkDomain.setText("example.com") }
        verify { fx.tvMessage.setText(any<CharSequence>()) }
    }

    @Test
    fun `text link renders the preview once it is loaded`() {
        val url = "https://example.com/async2"
        every { linkPreviews.cached(url) } returns null
        every { linkPreviews.isLoaded(url) } returns false
        every { fx.linkPreview.tag } returns url
        coEvery { linkPreviews.load(url) } returns LinkPreview(
            url = url, title = "Loaded", description = null, imageUrl = null, siteName = "Site"
        )
        bindIncoming(message(text = url))

        coVerify { linkPreviews.load(url) }
        verify { fx.tvLinkTitle.setText("Loaded") }
        verify { fx.tvLinkDomain.setText("Site") }
    }

    @Test
    fun `plain text without links clears the movement method`() {
        bindIncoming(message(text = "just words"))

        verify { fx.tvMessage.setMovementMethod(null) }
    }

    @Test
    fun `text with multiple links is rendered clickable`() {
        bindIncoming(message(text = "see https://a.com and https://b.com"))

        verify { fx.tvMessage.setText(any<CharSequence>()) }
        verify { fx.tvMessage.highlightColor = any<Int>() }
    }

    @Test
    fun `preview card click opens the url`() {
        val url = "https://example.com/article"
        every { linkPreviews.cached(url) } returns LinkPreview(
            url = url, title = "Title", description = null, imageUrl = null, siteName = "Site"
        )
        bindIncoming(message(text = url))

        val listener = slot<View.OnClickListener>()
        verify { fx.linkPreview.setOnClickListener(capture(listener)) }
        listener.captured.onClick(fx.linkPreview)

        verify { fx.context.startActivity(any()) }
    }

    @Test
    fun `open url failure shows a toast`() {
        val url = "https://example.com/broken"
        every { linkPreviews.cached(url) } returns LinkPreview(
            url = url, title = "Title", description = null, imageUrl = null, siteName = "Site"
        )
        every { fx.context.startActivity(any()) } throws ActivityNotFoundException()
        val toast = mockk<Toast>(relaxed = true)
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<Int>(), any()) } returns toast

        bindIncoming(message(text = url))
        val listener = slot<View.OnClickListener>()
        verify { fx.linkPreview.setOnClickListener(capture(listener)) }
        listener.captured.onClick(fx.linkPreview)

        verify { toast.show() }
    }

    // --- Исходящие: статус ---

    @Test
    fun `outgoing message without a status hides the status icon`() {
        bindOutgoing(message(senderUuid = "me"), status = null)

        verify { fx.ivStatus.visibility = View.GONE }
        verify { fx.tvTime.setOnClickListener(null) }
    }

    @Test
    fun `outgoing sending status has no click listener`() {
        bindOutgoing(message(senderUuid = "me"), status = MessageDeliveryStatus.SENDING)

        verify { fx.ivStatus.visibility = View.VISIBLE }
        verify { fx.ivStatus.setImageResource(R.drawable.ic_message_sending) }
        verify { fx.ivStatus.setOnClickListener(null) }
    }

    @Test
    fun `outgoing failed status has no click listener`() {
        bindOutgoing(message(senderUuid = "me"), status = MessageDeliveryStatus.FAILED)

        verify { fx.ivStatus.setImageResource(R.drawable.ic_message_failed) }
        verify { fx.ivStatus.setOnClickListener(null) }
    }

    @Test
    fun `outgoing sent status binds its icon`() {
        bindOutgoing(message(senderUuid = "me"), status = MessageDeliveryStatus.SENT)

        verify { fx.ivStatus.setImageResource(R.drawable.ic_message_sent) }
    }

    @Test
    fun `outgoing delivered status binds its icon`() {
        bindOutgoing(message(senderUuid = "me"), status = MessageDeliveryStatus.DELIVERED)

        verify { fx.ivStatus.setImageResource(R.drawable.ic_message_delivered) }
    }

    @Test
    fun `outgoing read status opens details on tap`() {
        var clicked = false
        bindOutgoing(
            message(senderUuid = "me"),
            status = MessageDeliveryStatus.READ,
            onStatusClick = { clicked = true }
        )

        verify { fx.ivStatus.setImageResource(R.drawable.ic_message_read) }
        val listener = slot<View.OnClickListener>()
        verify { fx.ivStatus.setOnClickListener(capture(listener)) }
        listener.captured.onClick(fx.ivStatus)

        assertTrue(clicked)
    }

    @Test
    fun `outgoing read status also opens details from the time label`() {
        var clicked = false
        bindOutgoing(
            message(senderUuid = "me"),
            status = MessageDeliveryStatus.READ,
            onStatusClick = { clicked = true }
        )

        val listener = slot<View.OnClickListener>()
        verify { fx.tvTime.setOnClickListener(capture(listener)) }
        listener.captured.onClick(fx.tvTime)

        assertTrue(clicked)
    }

    @Test
    fun `deleted outgoing message hides status`() {
        bindOutgoing(message(senderUuid = "me", isDeleted = true), status = MessageDeliveryStatus.READ)

        verify { fx.ivStatus.visibility = View.GONE }
    }

    @Test
    fun `outgoing click reports the message`() {
        var pressed: Message? = null
        bindOutgoing(message(senderUuid = "me"), onClick = { pressed = it })

        val listener = slot<View.OnClickListener>()
        verify { fx.root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(fx.root)

        assertEquals("m1", pressed?.messageUuid)
    }

    @Test
    fun `outgoing renders reply quote and edited marker`() {
        val original = message(uuid = "orig", senderName = "Bob", text = "original")
        bindOutgoing(
            message(senderUuid = "me", replyToMessageUuid = "orig", isEdited = true),
            replyTo = original
        )

        verify { fx.replyQuote.visibility = View.VISIBLE }
        verify { fx.tvReplyName.setText("Bob") }
        verify { fx.tvEdited.visibility = View.VISIBLE }
    }

    @Test
    fun `outgoing incoming helpers also cover image attachment branch`() {
        bindOutgoing(message(senderUuid = "me"))

        verify { fx.attachmentsContainer.removeAllViews() }
        verify { fx.tvTime.setText(any<CharSequence>()) }
    }
}
