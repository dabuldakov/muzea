package com.example.muzea.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.muzea.R
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.databinding.ItemNewsBinding
import com.example.muzea.utils.Constants

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

            // Изображение новости
            loadImage(news)
        }

        private fun loadImage(news: NewsResponse) {
            if (!news.imageUrl.isNullOrEmpty()) {
                binding.ivImage.visibility = View.VISIBLE
                Glide.with(binding.root.context)
                    .load(Constants.BASE_URL + news.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.placeholder_image)
                    .into(binding.ivImage)
            } else {
                binding.ivImage.visibility = View.GONE
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