package com.example.muzea.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.databinding.ItemChatBinding
import com.example.muzea.utils.Constants

class ChatAdapter(
    private val onItemClick: (String) -> Unit
) : ListAdapter<ChatResponse, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(
        private val binding: ItemChatBinding,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatResponse) {
            binding.tvChatTitle.text = chat.title ?: "Chat"

            val lastMessage = chat.lastMessage
            if (lastMessage != null && !lastMessage.text.isNullOrEmpty()) {
                val prefix = lastMessage.senderName?.let { "$it: " } ?: ""
                binding.tvLastMessage.text = prefix + lastMessage.text
            } else {
                binding.tvLastMessage.text = "No messages yet"
            }

            val time = lastMessage?.createdAt
            if (!time.isNullOrEmpty()) {
                binding.tvChatTime.text = formatTime(time)
                binding.tvChatTime.visibility = View.VISIBLE
            } else {
                binding.tvChatTime.visibility = View.GONE
            }

            val unreadCount = chat.unreadCount ?: 0
            if (unreadCount > 0) {
                binding.tvUnreadCount.text = unreadCount.toString()
                binding.tvUnreadCount.visibility = View.VISIBLE
            } else {
                binding.tvUnreadCount.visibility = View.GONE
            }

            loadAvatar(chat)

            binding.root.setOnClickListener {
                onItemClick(chat.chatUuid)
            }
        }

        private fun loadAvatar(chat: ChatResponse) {
            val url = chat.avatarUrl?.let { resolveUrl(it) }
            if (!url.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(url)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(binding.ivAvatar)
            }
        }

        private fun resolveUrl(url: String): String {
            return if (url.startsWith("http")) url else Constants.CHAT_BASE_URL.trimEnd('/') + url
        }

        private fun formatTime(iso: String): String {
            return try {
                val dateTime = iso.substring(0, 19)
                val parts = dateTime.split("T")
                parts[0] + " " + parts[1].substring(0, 5)
            } catch (e: Exception) {
                iso
            }
        }
    }

    fun updateList(newList: List<ChatResponse>) {
        submitList(null)
        submitList(newList)
    }

    fun clearItems() {
        submitList(emptyList())
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatResponse>() {
        override fun areItemsTheSame(oldItem: ChatResponse, newItem: ChatResponse): Boolean {
            return oldItem.chatUuid == newItem.chatUuid
        }

        override fun areContentsTheSame(oldItem: ChatResponse, newItem: ChatResponse): Boolean {
            return oldItem == newItem
        }
    }
}