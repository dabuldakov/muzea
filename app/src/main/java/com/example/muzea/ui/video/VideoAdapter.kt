package com.example.muzea.ui.video

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.data.model.VideoResponse
import com.example.muzea.databinding.ItemVideoBinding
import com.example.muzea.utils.MediaUrl

class VideoAdapter(
    private val onItemClick: (Long) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private var videos: List<VideoResponse> = emptyList()

    fun submitList(newList: List<VideoResponse>) {
        videos = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount(): Int = videos.size

    class VideoViewHolder(
        private val binding: ItemVideoBinding,
        private val onItemClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoResponse) {
            with(binding) {
                tvTitle.text = video.title
                tvViews.text = "${video.views} views"
                loadThumbnail(video)
            }
            itemView.setOnClickListener { onItemClick(video.id) }
        }

        private fun loadThumbnail(video: VideoResponse) {
            val thumbnailUrl = MediaUrl.main(video.thumbnailUrl)

            Glide.with(binding.root.context)
                .load(thumbnailUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.placeholder_video)
                .error(R.drawable.placeholder_video)
                .into(binding.ivThumbnail)
        }
    }
}