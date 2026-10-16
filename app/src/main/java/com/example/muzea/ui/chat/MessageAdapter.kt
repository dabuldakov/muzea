package com.example.muzea.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.domain.model.Message
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.ChatTimeFormatter
import com.example.muzea.databinding.ItemMessageIncomingBinding
import com.example.muzea.databinding.ItemMessageOutgoingBinding

class MessageAdapter(
    private val myUserUuid: String?
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (isMine(message)) TYPE_OUTGOING else TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_OUTGOING) {
            OutgoingViewHolder(
                ItemMessageOutgoingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            IncomingViewHolder(
                ItemMessageIncomingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is OutgoingViewHolder -> holder.bind(message)
            is IncomingViewHolder -> holder.bind(message)
        }
    }

    private fun isMine(message: Message): Boolean {
        val sender = message.senderUuid ?: return false
        return myUserUuid != null && sender == myUserUuid
    }

    class IncomingViewHolder(
        private val binding: ItemMessageIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            AvatarLoader.load(binding.ivAvatar, message.senderAvatar)
            binding.tvMessage.text = message.text ?: ""
            binding.tvMessage.setOnLongClickListener { view ->
                copyMessageToClipboard(view, message.text ?: "")
                true
            }
            bindGif(binding.tvMessage, binding.ivGif, message)

            if (!message.senderName.isNullOrEmpty()) {
                binding.tvSenderName.text = message.senderName
                binding.tvSenderName.visibility = View.VISIBLE
            } else {
                binding.tvSenderName.visibility = View.GONE
            }

            binding.tvTime.text = formatTime(message.createdAt)
        }
    }

    class OutgoingViewHolder(
        private val binding: ItemMessageOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.tvMessage.text = message.text ?: ""
            binding.tvMessage.setOnLongClickListener { view ->
                copyMessageToClipboard(view, message.text ?: "")
                true
            }
            bindGif(binding.tvMessage, binding.ivGif, message)
            binding.tvTime.text = formatTime(message.createdAt)
        }
    }

    fun updateList(newList: List<Message>) {
        submitList(newList)
    }

    companion object {
        private const val TYPE_INCOMING = 0
        private const val TYPE_OUTGOING = 1

        private fun formatTime(iso: String?): String {
            return ChatTimeFormatter.format(iso)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.messageUuid == newItem.messageUuid
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}

private fun gifUrl(message: Message): String? =
    message.attachments
        .firstOrNull { it.type?.equals("GIF", ignoreCase = true) == true }
        ?.fileUrl

/**
 * Если сообщение — гифка, показываем её (Glide, анимированный GIF, кэш на
 * устройстве) вместо текста. Иначе — обычный текстовый бабл.
 */
private fun bindGif(textView: TextView, imageView: ImageView, message: Message) {
    val url = gifUrl(message)
    if (url.isNullOrBlank()) {
        imageView.visibility = View.GONE
        textView.visibility = View.VISIBLE
        return
    }
    textView.visibility = View.GONE
    imageView.visibility = View.VISIBLE
    Glide.with(imageView)
        .load(url)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(imageView)
}

private fun copyMessageToClipboard(view: View, text: String) {
    if (text.isBlank()) return
    val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    Toast.makeText(view.context, R.string.message_copied, Toast.LENGTH_SHORT).show()
}
