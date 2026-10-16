package com.example.muzea.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.databinding.ItemGifBinding
import com.example.muzea.domain.model.Gif

/** Сетка гифок в пикере. Превью грузим с кэшем на устройстве. */
class GifPickerAdapter(
    private val onGifClick: (Gif) -> Unit
) : ListAdapter<Gif, GifPickerAdapter.GifViewHolder>(DiffCallback) {

    inner class GifViewHolder(
        private val binding: ItemGifBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gif: Gif) {
            Glide.with(binding.ivGifItem)
                .load(gif.previewUrl ?: gif.url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.ivGifItem)
            binding.ivGifItem.setOnClickListener { onGifClick(gif) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder =
        GifViewHolder(ItemGifBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: GifViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView).clear(holder.itemView.findViewById<View>(com.example.muzea.R.id.ivGifItem) as android.widget.ImageView)
    }

    private object DiffCallback : DiffUtil.ItemCallback<Gif>() {
        private fun identity(gif: Gif): String =
            gif.gifKey ?: "${gif.provider}:${gif.providerGifId}"

        override fun areItemsTheSame(oldItem: Gif, newItem: Gif): Boolean =
            identity(oldItem) == identity(newItem)

        override fun areContentsTheSame(oldItem: Gif, newItem: Gif): Boolean = oldItem == newItem
    }
}
