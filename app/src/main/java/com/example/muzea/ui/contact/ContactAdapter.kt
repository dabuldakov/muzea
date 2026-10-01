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
import com.example.muzea.utils.LastSeenFormatter
import com.example.muzea.utils.LocalTimeFormatter

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

            bindStatus(contact)

            loadAvatar(contact)
        }

        private fun bindStatus(contact: ContactResponse) {
            val context = binding.root.context
            if (contact.isOnline) {
                binding.tvContactStatus.setText(R.string.presence_online)
                binding.tvContactStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.green)
                )
                // Точка статусa нужна только рядом с «в сети»: у офлайна
                // подпись уже несёт время последнего визита.
                binding.viewOnlineDot.visibility = View.VISIBLE
            } else {
                binding.tvContactStatus.text = lastSeenText(contact)
                binding.tvContactStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.gray)
                )
                binding.viewOnlineDot.visibility = View.INVISIBLE
            }
        }

        /**
         * «был(а) N мин назад» — тем полезнее, чем свежее статус.
         * Дальше суток точная минута ни о чём не говорит, поэтому
         * показываем календарное время, как в остальных экранах.
         */
        private fun lastSeenText(contact: ContactResponse): String {
            val context = binding.root.context
            val iso = contact.lastSeenAt
            if (iso.isNullOrEmpty()) return context.getString(R.string.presence_offline)

            val minutes = LastSeenFormatter.minutesAgo(iso) ?: return context.getString(R.string.presence_offline)
            return when {
                minutes < 1L -> context.getString(R.string.presence_last_seen_just_now)
                minutes < 60L -> context.getString(R.string.presence_last_seen_minutes, minutes.toInt())
                minutes < 24 * 60L -> context.getString(R.string.presence_last_seen_hours, (minutes / 60L).toInt())
                else -> context.getString(
                    R.string.presence_last_seen_at,
                    LocalTimeFormatter.format(iso)
                )
            }
        }

        private fun loadAvatar(contact: ContactResponse) {
            AvatarLoader.load(binding.ivAvatar, contact.avatarUrl)
        }
    }

    /**
     * Простая отправка списка, а не submitList(null) + submitList(list).
     *
     * Обнуление списка перед следующим прогоном принудительно пересоздавало
     * все ViewHolder-ы: список мигал, анимации пропадали и позиция прокрутки
     * сбрасывалась. Именно с этим обновлением приходил и статус «в сети» —
     * список дёргался каждые 20 секунд.
     */
    fun updateList(newList: List<ContactResponse>) {
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