package com.example.muzea.ui.chat

import com.example.muzea.domain.model.Message
import com.example.muzea.domain.model.MessageAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Правило выбора голосового пузыря.
 *
 * Свой клиент шлёт записи типом VOICE, другие клиенты (iOS) — типом AUDIO с
 * файлом `voice_*.m4a`. До исправления такие сообщения на приёмной стороне
 * рисовались карточкой файла: только имя и размер, без волны и длительности.
 */
class VoiceAttachmentForTest {

    @Test
    fun `voice type renders the audio attachment as a voice bubble`() {
        val audio = audio("voice_1712345678.m4a", duration = 22)

        val picked = voiceAttachmentFor(message("VOICE", listOf(audio)), listOf(audio))

        assertEquals(audio, picked)
    }

    @Test
    fun `audio type with a recorded file becomes a voice bubble`() {
        val audio = audio("voice_1D4050D6-F01E-4514.m4a", duration = 13)

        val picked = voiceAttachmentFor(message("AUDIO", listOf(audio)), listOf(audio))

        assertEquals(audio, picked)
    }

    @Test
    fun `audio type is matched regardless of case`() {
        val audio = audio("VOICE_42.m4a", duration = 4)

        val picked = voiceAttachmentFor(message("audio", listOf(audio)), listOf(audio))

        assertEquals(audio, picked)
    }

    @Test
    fun `recorded file without duration still renders as a voice bubble`() {
        val audio = audio("voice_no_duration.m4a", duration = null)

        val picked = voiceAttachmentFor(message("AUDIO", listOf(audio)), listOf(audio))

        assertEquals(audio, picked)
    }

    @Test
    fun `audio file from the attachment menu stays a file card`() {
        val track = audio("track.m4a", duration = 180)

        val picked = voiceAttachmentFor(message("AUDIO", listOf(track)), listOf(track))

        assertNull(picked)
    }

    @Test
    fun `several recordings in one message stay file cards`() {
        val first = audio("voice_1.m4a", duration = 5)
        val second = audio("voice_2.m4a", duration = 7)
        val attachments = listOf(first, second)

        val picked = voiceAttachmentFor(message("AUDIO", attachments), attachments)

        assertNull(picked)
    }

    @Test
    fun `file type with an audio attachment stays a file card`() {
        val audio = audio("voice_note.m4a", duration = 9)

        val picked = voiceAttachmentFor(message("FILE", listOf(audio)), listOf(audio))

        assertNull(picked)
    }

    @Test
    fun `voice type without an audio attachment has no bubble`() {
        val image = MessageAttachment(
            fileUrl = "/file", thumbnailUrl = null, type = "IMAGE",
            width = null, height = null
        )

        val picked = voiceAttachmentFor(message("VOICE", listOf(image)), listOf(image))

        assertNull(picked)
    }

    private fun audio(name: String, duration: Int?): MessageAttachment = MessageAttachment(
        fileUrl = "/file",
        thumbnailUrl = null,
        type = "AUDIO",
        width = null,
        height = null,
        fileName = name,
        mimeType = "audio/mp4",
        duration = duration
    )

    private fun message(type: String?, attachments: List<MessageAttachment>) = Message(
        messageUuid = "m1",
        chatUuid = "chat-1",
        senderId = 1L,
        senderUuid = "other",
        senderName = "Anna",
        senderAvatar = null,
        text = null,
        messageType = type,
        replyToMessageUuid = null,
        isEdited = false,
        isDeleted = false,
        isPinned = false,
        createdAt = "2026-01-01T10:00:00",
        updatedAt = null,
        attachments = attachments
    )
}
