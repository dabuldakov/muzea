package com.example.muzea.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.data.model.ChatParticipantResponse
import com.example.muzea.databinding.ItemParticipantBinding
import com.example.muzea.utils.AvatarLoader

class ParticipantAdapter(
    private val myUserUuid: String?
) : ListAdapter<ChatParticipantResponse, ParticipantAdapter.ParticipantViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val binding = ItemParticipantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ParticipantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ParticipantViewHolder(
        private val binding: ItemParticipantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(participant: ChatParticipantResponse) {
            binding.tvName.text = participant.displayName()
            binding.tvUsername.text = if (myUserUuid != null && participant.userUuid == myUserUuid) {
                "You"
            } else {
                participant.username?.let { "@$it" } ?: ""
            }
            if (participant.role == "OWNER") {
                binding.tvRole.visibility = View.VISIBLE
                binding.tvRole.text = "Owner"
            } else {
                binding.tvRole.visibility = View.GONE
            }
            AvatarLoader.load(binding.ivAvatar, participant.avatarUrl)
        }
    }

    fun updateList(newList: List<ChatParticipantResponse>) {
        submitList(newList)
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatParticipantResponse>() {
        override fun areItemsTheSame(
            oldItem: ChatParticipantResponse,
            newItem: ChatParticipantResponse
        ): Boolean {
            return oldItem.userUuid == newItem.userUuid
        }

        override fun areContentsTheSame(
            oldItem: ChatParticipantResponse,
            newItem: ChatParticipantResponse
        ): Boolean {
            return oldItem == newItem
        }
    }
}