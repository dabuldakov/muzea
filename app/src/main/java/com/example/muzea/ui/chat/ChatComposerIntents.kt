package com.example.muzea.ui.chat

import android.widget.EditText
import com.example.muzea.domain.model.Gif

/**
 * Действия композера: выбор вложений, GIF и вставка эмодзи в позицию курсора.
 */
internal class ChatComposerIntents(
    private val input: EditText,
    private val launchPhotos: () -> Unit,
    private val launchVideo: () -> Unit,
    private val launchFile: () -> Unit,
    private val sendGif: (Gif) -> Unit
) {

    fun onAttachmentKind(kind: AttachmentsBottomSheet.Kind) {
        when (kind) {
            AttachmentsBottomSheet.Kind.PHOTO -> launchPhotos()
            AttachmentsBottomSheet.Kind.VIDEO -> launchVideo()
            AttachmentsBottomSheet.Kind.FILE -> launchFile()
        }
    }

    fun onGifSelected(gif: Gif) = sendGif(gif)

    fun onEmojiSelected(emoji: String) {
        val start = input.selectionStart.coerceAtLeast(0)
        val end = input.selectionEnd.coerceAtLeast(start)
        val current = input.text?.toString().orEmpty()
        val updated = current.substring(0, start) + emoji + current.substring(end)
        input.setText(updated)
        input.setSelection((start + emoji.length).coerceAtMost(updated.length))
    }
}
