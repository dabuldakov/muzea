package com.example.muzea.ui.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.databinding.FragmentNewsDetailBinding
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.MediaUrl
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class NewsDetailFragment : Fragment() {

    private var _binding: FragmentNewsDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NewsViewModel
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

        // Создаем ViewModel вручную
        val tokenManager = TokenManager(requireContext())
        myUsername = tokenManager.getUsername()
        val apiService = RetrofitClient(tokenManager).apiService
        val newsRepository = NewsRepository(apiService)
        val chatApiService = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(chatApiService, ChatAuthManager(chatApiService, tokenManager))
        viewModel = NewsViewModel(newsRepository, chatRepository)

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
                    is NetworkResult.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = View.GONE
                        displayNews(result.data!!)
                    }
                    is NetworkResult.Error -> {
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
                    is NetworkResult.Loading -> binding.btnDeleteNews.isEnabled = false
                    is NetworkResult.Success -> {
                        Toast.makeText(requireContext(), "News deleted", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                    is NetworkResult.Error -> {
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

    private fun displayNews(news: NewsResponse) {
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