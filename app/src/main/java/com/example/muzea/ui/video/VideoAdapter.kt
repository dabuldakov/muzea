package com.example.muzea.ui.video

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.muzea.databinding.ItemVideoBinding
import com.example.muzea.data.model.VideoResponse

class VideoAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<VideoResponse, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VideoViewHolder(
        private val binding: ItemVideoBinding,
        private val onItemClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoResponse) {
            binding.tvTitle.text = video.title
            binding.tvViews.text = "${video.views} views"

            if (!video.thumbnailUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(video.thumbnailUrl)
                    .centerCrop()
                    .into(binding.ivThumbnail)
            }

            binding.root.setOnClickListener {
                onItemClick(video.id)
            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoResponse>() {
        override fun areItemsTheSame(oldItem: VideoResponse, newItem: VideoResponse): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoResponse, newItem: VideoResponse): Boolean {
            return oldItem == newItem
        }
    }
}