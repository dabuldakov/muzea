package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import com.example.muzea.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Одна шторка для эмодзи и GIF: сетка эмодзи + переход к пикеру гифок. */
class EmojiGifBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onEmojiSelected(emoji: String)
        fun onOpenGif()
    }

    private val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
        "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
        "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
        "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖",
        "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡",
        "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰",
        "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶",
        "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮",
        "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴",
        "👍", "👎", "👌", "🤌", "✌️", "🤞", "🤟", "🤘",
        "👏", "🙌", "🙏", "🤝", "💪", "🫶", "❤️", "🧡",
        "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💯",
        "🔥", "⚡", "🌟", "✨", "🎉", "🎊", "🎁", "🏆",
        "🍀", "☕", "🍕", "🎮", "🎵", "🚀", "🌙", "☀️"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_emoji_gif, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val grid = view.findViewById<GridLayout>(R.id.emojiGrid)
        val size = (44 * resources.displayMetrics.density).toInt()
        emojis.forEach { emoji ->
            val cell = TextView(requireContext()).apply {
                text = emoji
                textSize = 26f
                gravity = android.view.Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                }
                setOnClickListener {
                    (parentFragment as? Listener)?.onEmojiSelected(emoji)
                    dismiss()
                }
            }
            grid.addView(cell)
        }
        view.findViewById<View>(R.id.btnOpenGif).setOnClickListener {
            (parentFragment as? Listener)?.onOpenGif()
            dismiss()
        }
    }

    companion object {
        fun newInstance(): EmojiGifBottomSheet = EmojiGifBottomSheet()
    }
}
