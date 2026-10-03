package com.example.muzea.ui.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.databinding.FragmentNewsDetailBinding
import com.example.muzea.domain.model.News
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.MediaUrl
import com.example.muzea.core.Resource
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NewsDetailFragment : Fragment() {

    private var _binding: FragmentNewsDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private var newsId: Long = 0
    private var myUsername: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myUsername = tokenManager.getUsername()

        newsId = arguments?.getLong("newsId", 0) ?: 0

        if (newsId != 0L) {
            viewModel.loadNewsById(newsId)
            observeViewModel()
        } else {
            binding.tvError.text = "Invalid news ID"
            binding.tvError.visibility = View.VISIBLE
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.newsDetailResult.collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is Resource.Success -> {
                        binding.progressBar.visibility = View.GONE
                        displayNews(result.data!!)
                    }
                    is Resource.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.text = result.message
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteNewsResult.collect { result ->
                when (result) {
                    is Resource.Loading -> binding.btnDeleteNews.isEnabled = false
                    is Resource.Success -> {
                        Toast.makeText(requireContext(), "News deleted", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                    is Resource.Error -> {
                        binding.btnDeleteNews.isEnabled = true
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete news")
            .setMessage("Delete this news permanently?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteNews(newsId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun displayNews(news: News) {
        binding.tvTitle.text = news.title
        binding.tvContent.text = news.content
        binding.tvAuthor.text = "By: ${news.author}"
        binding.tvDate.text = LocalTimeFormatter.format(news.publishedAt)

        val canDelete = !myUsername.isNullOrEmpty() && news.author == myUsername
        binding.btnDeleteNews.visibility = if (canDelete) View.VISIBLE else View.GONE
        binding.btnDeleteNews.isEnabled = true
        binding.btnDeleteNews.setOnClickListener { confirmDelete() }

        if (!news.imageUrl.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(MediaUrl.main(news.imageUrl))
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .into(binding.ivImage)
        }

        if (news.relatedVideo != null) {
            binding.videoCard.visibility = View.VISIBLE
            binding.tvVideoTitle.text = news.relatedVideo.title
            binding.btnWatchVideo.setOnClickListener {
                val intent = android.content.Intent(requireContext(), com.example.muzea.ui.video.VideoDetailActivity::class.java)
                intent.putExtra("video_id", news.relatedVideo.id)
                intent.putExtra("video_url", news.relatedVideo.url)
                intent.putExtra("video_title", news.relatedVideo.title)
                startActivity(intent)
            }
        } else {
            binding.videoCard.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}