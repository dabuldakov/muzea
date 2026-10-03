package com.example.muzea.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.domain.model.Chat
import com.example.muzea.databinding.ItemChatBinding
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.ChatTimeFormatter

class ChatAdapter(
    private val onItemClick: (String) -> Unit
) : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

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

        fun bind(chat: Chat) {
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

        private fun loadAvatar(chat: Chat) {
            AvatarLoader.load(binding.ivAvatar, chat.avatarUrl)
        }

        private fun formatTime(iso: String): String {
            return ChatTimeFormatter.format(iso)
        }
    }

    /**
     * Простая отправка списка, а не submitList(null) + submitList(list).
     *
     * Обнуление списка перед следующим прогоном пересоздавало все ViewHolder-ы,
     * и список чатов мигал при каждом фоновом обновлении (каждые 8 секунд).
     * DiffUtil сам точечно обновляет изменившиеся строки.
     */
    fun updateList(newList: List<Chat>) {
        submitList(newList)
    }

    fun clearItems() {
        submitList(emptyList())
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem.chatUuid == newItem.chatUuid
        }

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem == newItem
        }
    }
}
