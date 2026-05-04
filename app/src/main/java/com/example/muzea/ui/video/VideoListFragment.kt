package com.example.muzea.ui.video

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.muzea.databinding.FragmentVideoListBinding
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class VideoListFragment : Fragment() {

    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: VideoViewModel
    private lateinit var adapter: VideoAdapter

    private companion object {
        private const val SPAN_COUNT = 2
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()
        setupRecyclerView()
        setupFAB()
        observeViewModel()
        loadVideos()
    }

    private fun initViewModel() {
        val tokenManager = TokenManager(requireContext())
        val apiService = RetrofitClient(tokenManager).apiService
        val videoRepository = VideoRepository(apiService)
        viewModel = VideoViewModel(videoRepository)
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter { videoId ->
            startActivity(Intent(requireContext(), VideoDetailActivity::class.java).apply {
                putExtra("video_id", videoId)
            })
        }

        binding.recyclerViewVideos.apply {
            layoutManager = GridLayoutManager(requireContext(), SPAN_COUNT)
            adapter = this@VideoListFragment.adapter
            setHasFixedSize(true)  // Оптимизация производительности
        }
    }

    private fun setupFAB() {
        binding.fabUpload.setOnClickListener {
            startActivity(Intent(requireContext(), VideoUploadActivity::class.java))
        }
    }

    private fun loadVideos() {
        viewModel.loadVideos()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.videosResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> showLoading(true)
                    is NetworkResult.Success -> handleSuccess(result.data)
                    is NetworkResult.Error -> handleError(result.message ?: "Unknown error")
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun handleSuccess(videos: List<com.example.muzea.data.model.VideoResponse>?) {
        showLoading(false)

        val videoList = videos ?: emptyList()
        adapter.submitList(videoList)

        if (videoList.isEmpty()) {
            showEmptyState()
        } else {
            showContentState()
        }
    }

    private fun handleError(message: String) {
        showLoading(false)

        if (adapter.itemCount == 0) {
            binding.tvError.text = message
            binding.tvError.visibility = View.VISIBLE
            binding.recyclerViewVideos.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
        } else {
            Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyState() {
        binding.tvEmpty.visibility = View.VISIBLE
        binding.recyclerViewVideos.visibility = View.GONE
        binding.tvError.visibility = View.GONE
    }

    private fun showContentState() {
        binding.tvEmpty.visibility = View.GONE
        binding.recyclerViewVideos.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Обновляем список при возвращении на экран
        loadVideos()
    }
}