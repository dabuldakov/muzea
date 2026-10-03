package com.example.muzea.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.domain.model.News
import com.example.muzea.databinding.ItemNewsBinding
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.MediaUrl

class NewsAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<News, NewsAdapter.NewsViewHolder>(NewsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NewsViewHolder(
        private val binding: ItemNewsBinding,
        private val onItemClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(news: News) {
            binding.tvTitle.text = news.title
            binding.tvContent.text = news.content
            binding.tvAuthor.text = news.author
            binding.tvDate.text = LocalTimeFormatter.formatDate(news.publishedAt)

            loadImage(news)

            binding.root.setOnClickListener {
                onItemClick(news.id)
            }
        }

        private fun loadImage(news: News) {
            val imageUrl = MediaUrl.main(news.imageUrl)

            if (!imageUrl.isNullOrEmpty()) {
                binding.ivImage.visibility = View.VISIBLE
                Glide.with(binding.root.context)
                    .load(imageUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .into(binding.ivImage)
            } else {
                binding.ivImage.visibility = View.GONE
            }
        }
    }

    fun updateList(newList: List<News>) {
        submitList(newList)
    }

    fun clearItems() {
        submitList(emptyList())
    }

    class NewsDiffCallback : DiffUtil.ItemCallback<News>() {
        override fun areItemsTheSame(oldItem: News, newItem: News): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: News, newItem: News): Boolean {
            return oldItem == newItem
        }
    }
}