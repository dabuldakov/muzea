package com.example.muzea.ui.video

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.muzea.databinding.FragmentVideoListBinding
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VideoListFragment : Fragment() {

    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private lateinit var adapter: VideoAdapter
    private var myUsername: String? = null

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
        myUsername = tokenManager.getUsername()
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
        viewModel.loadVideos(myUsername)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.feedState.collect { renderFeed(it) }
        }
    }

    private fun renderFeed(state: VideoFeedUiState) {
        // Спиннер только если показать нечего: при кэше фоновое обновление
        // не должно мигать поверх списка.
        binding.progressBar.visibility =
            if (state.isLoading && state.videos.isEmpty()) View.VISIBLE else View.GONE

        adapter.submitList(state.videos)

        if (state.videos.isEmpty()) {
            binding.tvError.visibility = if (state.error != null) View.VISIBLE else View.GONE
            state.error?.let { binding.tvError.text = it }
            binding.tvEmpty.visibility = if (state.error == null) View.VISIBLE else View.GONE
            binding.recyclerViewVideos.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.tvError.visibility = View.GONE
            binding.recyclerViewVideos.visibility = View.VISIBLE
            state.error?.let {
                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT).show()
                viewModel.consumeFeedError()
            }
        }
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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Вкладка живёт постоянно и при переключении не получает onResume,
        // поэтому догружаем данные в фоне при каждом показе.
        if (!hidden) loadVideos()
    }
}