package com.example.muzea.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.databinding.ItemNewsBinding
import com.example.muzea.utils.Constants
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class NewsAdapter(
    private val onItemClick: (Long) -> Unit,
    private val lifecycleScope: kotlinx.coroutines.CoroutineScope
) : ListAdapter<NewsResponse, NewsAdapter.NewsViewHolder>(NewsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding, onItemClick, lifecycleScope)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NewsViewHolder(
        private val binding: ItemNewsBinding,
        private val onItemClick: (Long) -> Unit,
        private val lifecycleScope: kotlinx.coroutines.CoroutineScope
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
                // Загружаем через Retrofit с авторизацией
                lifecycleScope.launch {
                    val tokenManager = TokenManager(binding.root.context)
                    val apiService = RetrofitClient(tokenManager).apiService
                    val newsRepository = NewsRepository(apiService)

                    newsRepository.downloadImage(Constants.BASE_URL + news.imageUrl)
                        .collect { result ->
                            when (result) {
                                is NetworkResult.Success -> {
                                    binding.ivImage.setImageBitmap(result.data)
                                }

                                else -> {}
                            }
                        }
                }
            } else {
                binding.ivImage.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(news.id)
            }
        }
    }

    fun updateList(newList: List<NewsResponse>) {
        submitList(null)
        submitList(newList)
    }

    fun clearItems() {
        submitList(emptyList())
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