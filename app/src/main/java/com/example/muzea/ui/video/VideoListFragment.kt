package com.example.muzea.ui.video

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        // Создаем ViewModel вручную
        val tokenManager = TokenManager(requireContext())
        val apiService = RetrofitClient(tokenManager).apiService
        val videoRepository = VideoRepository(apiService)
        viewModel = VideoViewModel(videoRepository)

        setupRecyclerView()
        setupFAB()
        observeViewModel()
        viewModel.loadVideos()
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter(
            onItemClick = { videoId ->
                val intent = Intent(requireContext(), VideoDetailActivity::class.java)
                intent.putExtra("video_id", videoId)
                startActivity(intent)
            },
            lifecycleScope = lifecycleScope
        )
        binding.recyclerViewVideos.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@VideoListFragment.adapter
        }
    }

    private fun setupFAB() {
        binding.fabUpload.setOnClickListener {
            startActivity(Intent(requireContext(), VideoUploadActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.videosResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = View.GONE
                        adapter.submitList(result.data)

                        if (result.data.isNullOrEmpty()) {
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.recyclerViewVideos.visibility = View.GONE
                        } else {
                            binding.tvEmpty.visibility = View.GONE
                            binding.recyclerViewVideos.visibility = View.VISIBLE
                        }
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.text = result.message
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}