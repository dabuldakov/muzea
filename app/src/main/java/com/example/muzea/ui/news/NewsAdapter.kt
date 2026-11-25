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
import com.example.muzea.utils.CountFormatter
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.MediaUrl

class NewsAdapter(
    private val onItemClick: (Long) -> Unit,
    private val onLikeClick: (Long) -> Unit
) : ListAdapter<News, NewsAdapter.NewsViewHolder>(NewsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding, onItemClick, onLikeClick)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NewsViewHolder(
        private val binding: ItemNewsBinding,
        private val onItemClick: (Long) -> Unit,
        private val onLikeClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(news: News) {
            val context = binding.root.context
            binding.tvTitle.text = news.title
            binding.tvContent.text = news.content
            binding.tvAuthor.text = news.author
            binding.tvDate.text = LocalTimeFormatter.formatDate(news.publishedAt)

            binding.tvLikes.text = CountFormatter.format(context, news.likes)
            binding.tvViews.text = CountFormatter.format(context, news.views)
            binding.tvComments.text = CountFormatter.format(context, news.commentsCount)

            binding.btnLike.isSelected = news.likedByMe
            binding.btnLike.setImageResource(
                if (news.likedByMe) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            binding.btnLike.contentDescription = context.getString(
                if (news.likedByMe) R.string.action_unlike else R.string.action_like
            )
            binding.btnLike.setOnClickListener { onLikeClick(news.id) }

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
                    .fitCenter()
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
