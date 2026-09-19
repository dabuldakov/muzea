package com.example.muzea.ui.contact

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.databinding.ItemContactBinding
import com.example.muzea.utils.Constants

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
            val url = contact.avatarUrl?.let { resolveUrl(it) }
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