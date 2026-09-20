package com.example.muzea.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.databinding.ItemGroupMemberBinding
import com.example.muzea.utils.AvatarLoader

class GroupMemberAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<ContactResponse, GroupMemberAdapter.GroupMemberViewHolder>(DiffCallback()) {

    private val selectedUuids = mutableSetOf<String>()

    val selectedUserUuids: Set<String>
        get() = selectedUuids.toSet()

    fun reset() {
        selectedUuids.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupMemberViewHolder {
        val binding = ItemGroupMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GroupMemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupMemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupMemberViewHolder(
        private val binding: ItemGroupMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ContactResponse) {
            binding.tvMemberName.text = contact.displayName()
            binding.tvMemberUsername.text = contact.username ?: ""
            AvatarLoader.load(binding.ivAvatar, contact.avatarUrl)

            val contactUuid = contact.contactUserUuid ?: ""
            binding.cbSelect.isChecked = selectedUuids.contains(contactUuid)
            binding.root.setOnClickListener {
                toggle(contactUuid)
            }
            binding.cbSelect.setOnClickListener {
                toggle(contactUuid)
            }
        }

        private fun toggle(contactUuid: String) {
            if (contactUuid.isEmpty()) return
            if (!selectedUuids.add(contactUuid)) {
                selectedUuids.remove(contactUuid)
            }
            binding.cbSelect.isChecked = selectedUuids.contains(contactUuid)
            onSelectionChanged(selectedUuids.size)
        }
    }

    fun updateList(newList: List<ContactResponse>) {
        submitList(null)
        submitList(newList)
    }

    class DiffCallback : DiffUtil.ItemCallback<ContactResponse>() {
        override fun areItemsTheSame(oldItem: ContactResponse, newItem: ContactResponse): Boolean {
            return oldItem.contactUuid == newItem.contactUuid
        }

        override fun areContentsTheSame(oldItem: ContactResponse, newItem: ContactResponse): Boolean {
            return oldItem == newItem
        }
    }
}