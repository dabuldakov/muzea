package com.example.muzea.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.model.MessageResponse
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.ChatTimeFormatter
import com.example.muzea.databinding.ItemMessageIncomingBinding
import com.example.muzea.databinding.ItemMessageOutgoingBinding

class MessageAdapter(
    private val myUserUuid: String?
) : ListAdapter<MessageResponse, RecyclerView.ViewHolder>(MessageDiffCallback()) {

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

    private fun isMine(message: MessageResponse): Boolean {
        val sender = message.senderUuid ?: return false
        return myUserUuid != null && sender == myUserUuid
    }

    class IncomingViewHolder(
        private val binding: ItemMessageIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageResponse) {
            AvatarLoader.load(binding.ivAvatar, message.senderAvatar)
            binding.tvMessage.text = message.text ?: ""

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

        fun bind(message: MessageResponse) {
            binding.tvMessage.text = message.text ?: ""
            binding.tvTime.text = formatTime(message.createdAt)
        }
    }

    fun updateList(newList: List<MessageResponse>) {
        submitList(newList)
    }

    companion object {
        private const val TYPE_INCOMING = 0
        private const val TYPE_OUTGOING = 1

        private fun formatTime(iso: String?): String {
            return ChatTimeFormatter.format(iso)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageResponse>() {
        override fun areItemsTheSame(oldItem: MessageResponse, newItem: MessageResponse): Boolean {
            return oldItem.messageUuid == newItem.messageUuid
        }

        override fun areContentsTheSame(oldItem: MessageResponse, newItem: MessageResponse): Boolean {
            return oldItem == newItem
        }
    }
}
