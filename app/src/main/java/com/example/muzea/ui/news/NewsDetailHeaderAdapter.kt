package com.example.muzea.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.databinding.ItemNewsDetailHeaderBinding
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.Video
import com.example.muzea.utils.CountFormatter
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.MediaUrl

/** Шапка детали новости: фото, текст, метрики, видео и заголовок комментариев. */
class NewsDetailHeaderAdapter(
    private val onLikeClick: () -> Unit,
    private val onLoadOlderComments: () -> Unit,
    private val onWatchVideo: (Video) -> Unit
) : RecyclerView.Adapter<NewsDetailHeaderAdapter.HeaderViewHolder>() {

    data class HeaderUi(
        val news: News,
        val commentsTotal: Long,
        val hasMoreComments: Boolean,
        val isLoadingComments: Boolean
    )

    private var header: HeaderUi? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemNewsDetailHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HeaderViewHolder(binding, onLikeClick, onLoadOlderComments, onWatchVideo)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        header?.let { holder.bind(it) }
    }

    override fun getItemCount(): Int = if (header == null) 0 else 1

    fun submit(value: HeaderUi) {
        val hadHeader = header != null
        header = value
        if (hadHeader) {
            notifyItemChanged(0)
        } else {
            notifyItemInserted(0)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemNewsDetailHeaderBinding,
        private val onLikeClick: () -> Unit,
        private val onLoadOlderComments: () -> Unit,
        private val onWatchVideo: (Video) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ui: HeaderUi) {
            val context = binding.root.context
            val news = ui.news

            binding.tvTitle.text = news.title
            binding.tvContent.text = news.content
            binding.tvAuthor.text = context.getString(R.string.news_by_format, news.author)
            binding.tvDate.text = LocalTimeFormatter.format(news.publishedAt)

            binding.tvLikes.text = CountFormatter.format(context, news.likes)
            binding.tvViews.text = CountFormatter.format(context, news.views)
            binding.tvComments.text = CountFormatter.format(context, ui.commentsTotal)

            binding.btnLike.isSelected = news.likedByMe
            binding.btnLike.setImageResource(
                if (news.likedByMe) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            binding.btnLike.contentDescription = context.getString(
                if (news.likedByMe) R.string.action_unlike else R.string.action_like
            )
            binding.btnLike.setOnClickListener { onLikeClick() }

            binding.tvCommentsTitle.text = context.getString(
                R.string.news_comments_title_format,
                CountFormatter.format(context, ui.commentsTotal)
            )

            val showLoadOlder = ui.hasMoreComments
            binding.btnLoadOlderComments.visibility =
                if (showLoadOlder) View.VISIBLE else View.GONE
            binding.btnLoadOlderComments.isEnabled = !ui.isLoadingComments
            binding.btnLoadOlderComments.setOnClickListener { onLoadOlderComments() }
            binding.commentsProgress.visibility =
                if (ui.isLoadingComments) View.VISIBLE else View.GONE
            binding.tvCommentsEmpty.visibility =
                if (ui.commentsTotal == 0L && !ui.isLoadingComments) View.VISIBLE else View.GONE

            renderImage(news)
            renderVideo(news.relatedVideo)
        }

        private fun renderImage(news: News) {
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

        private fun renderVideo(video: Video?) {
            if (video == null) {
                binding.videoCard.visibility = View.GONE
                return
            }
            binding.videoCard.visibility = View.VISIBLE
            binding.tvVideoTitle.text = video.title
            binding.btnWatchVideo.setOnClickListener { onWatchVideo(video) }
        }
    }
}
