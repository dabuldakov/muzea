package com.example.muzea.ui.contact

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.databinding.ItemContactBinding
import com.example.muzea.utils.AvatarLoader

class ContactAdapter(
    private val onItemClick: (ContactResponse) -> Unit
) : ListAdapter<ContactResponse, ContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ContactViewHolder(
        private val binding: ItemContactBinding,
        private val onItemClick: (ContactResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ContactResponse) {
            binding.root.setOnClickListener { onItemClick(contact) }

            binding.tvContactName.text = contact.displayName()
            binding.tvContactUsername.text = contact.username ?: ""

            binding.tvContactStatus.text = if (contact.isOnline) "online" else "offline"
            val statusColor = if (contact.isOnline) {
                ContextCompat.getColor(binding.root.context, R.color.green)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.gray)
            }
            binding.tvContactStatus.setTextColor(statusColor)

            loadAvatar(contact)
        }

        private fun loadAvatar(contact: ContactResponse) {
            AvatarLoader.load(binding.ivAvatar, contact.avatarUrl)
        }
    }

    fun updateList(newList: List<ContactResponse>) {
        submitList(null)
        submitList(newList)
    }

    fun clearItems() {
        submitList(emptyList())
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<ContactResponse>() {
        override fun areItemsTheSame(oldItem: ContactResponse, newItem: ContactResponse): Boolean {
            return oldItem.contactUuid == newItem.contactUuid
        }

        override fun areContentsTheSame(oldItem: ContactResponse, newItem: ContactResponse): Boolean {
            return oldItem == newItem
        }
    }
}
