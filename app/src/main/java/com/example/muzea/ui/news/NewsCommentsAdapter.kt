package com.example.muzea.ui.news

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.databinding.ItemNewsCommentBinding
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.LocalTimeFormatter

/** Лента комментариев: старые сверху, новые снизу. */
class NewsCommentsAdapter(
    private val onCommentActions: (NewsComment) -> Unit
) : ListAdapter<NewsComment, NewsCommentsAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemNewsCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentViewHolder(binding, onCommentActions)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(
        private val binding: ItemNewsCommentBinding,
        private val onCommentActions: (NewsComment) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: NewsComment) {
            binding.tvCommentAuthor.text = comment.author
            if (comment.authorAvatarUrl.isNullOrBlank()) {
                binding.ivCommentAvatar.setImageResource(R.drawable.ic_default_avatar)
            } else {
                AvatarLoader.load(binding.ivCommentAvatar, comment.authorAvatarUrl)
            }
            binding.tvCommentTime.text = LocalTimeFormatter.format(comment.createdAt)
            binding.tvCommentContent.text = comment.content
            binding.btnCommentMore.setOnClickListener { onCommentActions(comment) }
            binding.root.setOnLongClickListener {
                onCommentActions(comment)
                true
            }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<NewsComment>() {
        override fun areItemsTheSame(oldItem: NewsComment, newItem: NewsComment): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: NewsComment, newItem: NewsComment): Boolean =
            oldItem == newItem
    }
}
