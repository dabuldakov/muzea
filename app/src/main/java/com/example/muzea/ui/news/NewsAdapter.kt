package com.example.muzea.ui.news

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.muzea.databinding.ItemNewsBinding
import com.example.muzea.data.model.NewsResponse

class NewsAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<NewsResponse, NewsAdapter.NewsViewHolder>(NewsDiffCallback()) {

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

        fun bind(news: NewsResponse) {
            binding.tvTitle.text = news.title
            binding.tvContent.text = news.content
            binding.tvAuthor.text = news.author
            binding.tvDate.text = news.publishedAt.substring(0, 10)

            if (!news.imageUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(news.imageUrl)
                    .centerCrop()
                    .into(binding.ivImage)
            }

            binding.root.setOnClickListener {
                onItemClick(news.id)
            }
        }
    }

    class NewsDiffCallback : DiffUtil.ItemCallback<NewsResponse>() {
        override fun areItemsTheSame(oldItem: NewsResponse, newItem: NewsResponse): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NewsResponse, newItem: NewsResponse): Boolean {
            return oldItem == newItem
        }
    }
}