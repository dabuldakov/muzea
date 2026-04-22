package com.example.muzea.ui.video

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.databinding.ActivityVideoDetailBinding
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.utils.Constants
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import com.example.muzea.utils.VideoPlayerHelper
import com.google.android.exoplayer2.ExoPlayer
import kotlinx.coroutines.launch

class VideoDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoDetailBinding
    private lateinit var viewModel: VideoViewModel
    private var player: ExoPlayer? = null
    private var videoId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Создаем ViewModel вручную
        val tokenManager = TokenManager(applicationContext)
        val apiService = RetrofitClient(tokenManager).apiService
        val videoRepository = VideoRepository(apiService)
        viewModel = VideoViewModel(videoRepository)

        videoId = intent.getLongExtra("video_id", 0)
        if (videoId == 0L) {
            videoId = intent.getLongExtra("videoId", 0)
        }

        if (videoId == 0L) {
            Toast.makeText(this, "Invalid video", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        observeViewModel()
        viewModel.loadVideoById(videoId)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.videoDetailResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        displayVideo(result.data!!)
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this@VideoDetailActivity, result.message, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun displayVideo(video: com.example.muzea.data.model.VideoResponse) {
        binding.tvTitle.text = video.title
        binding.tvDescription.text = video.description ?: "No description"
        binding.tvViews.text = "${video.views} views"
        binding.tvLikes.text = "${video.likes} likes"
        binding.tvUploader.text = "Uploaded by: ${video.uploadedBy}"
        binding.tvDate.text = video.uploadedAt

        // Формируем полный URL для видео
        val fullVideoUrl = Constants.BASE_URL + video.url

        // Получаем токен
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken()

        if (token != null) {
            // Используем упрощенный метод
            player = VideoPlayerHelper.createPlayerWithAuth(this, fullVideoUrl, token)
            binding.playerView.player = player
            player?.playWhenReady = true
        } else {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoPlayerHelper.releasePlayer(player)
    }
}