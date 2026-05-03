package com.example.muzea.ui.video

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.model.VideoResponse
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.databinding.ItemVideoBinding
import com.example.muzea.utils.Constants
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class VideoAdapter(
    private val onItemClick: (Long) -> Unit,
    private val lifecycleScope: kotlinx.coroutines.CoroutineScope
) : ListAdapter<VideoResponse, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding, onItemClick, lifecycleScope)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VideoViewHolder(
        private val binding: ItemVideoBinding,
        private val onItemClick: (Long) -> Unit,
        private val lifecycleScope: kotlinx.coroutines.CoroutineScope
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoResponse) {
            binding.tvTitle.text = video.title
            binding.tvViews.text = "${video.views} views"

            loadThumbnail(video)

            binding.root.setOnClickListener {
                onItemClick(video.id)
            }
        }

        private fun loadThumbnail(video: VideoResponse) {
            // Показываем заглушку
            binding.ivThumbnail.setImageResource(R.drawable.placeholder_video)

            if (video.thumbnailUrl.isNullOrEmpty()) {
                return
            }

            // Загружаем через Retrofit с авторизацией
            lifecycleScope.launch {
                val tokenManager = TokenManager(binding.root.context)
                val apiService = RetrofitClient(tokenManager).apiService
                val videoRepository = VideoRepository(apiService)

                videoRepository.downloadThumbnail(Constants.BASE_URL + video.thumbnailUrl)
                    .collect { result ->
                        when (result) {
                            is NetworkResult.Success -> {
                                binding.ivThumbnail.setImageBitmap(result.data)
                            }
                            else -> {}
                        }
                    }
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