package com.example.muzea.ui.video

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.databinding.ActivityVideoDetailBinding
import com.example.muzea.utils.Constants
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import com.example.muzea.utils.VideoPlayerHelper
import kotlinx.coroutines.launch

class VideoDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoDetailBinding
    private lateinit var viewModel: VideoViewModel
    private var player: ExoPlayer? = null
    private var videoId: Long = 0

    @UnstableApi override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tokenManager = TokenManager(applicationContext)
        val apiService = RetrofitClient(tokenManager).apiService
        val videoRepository = VideoRepository(apiService)
        viewModel = VideoViewModel(videoRepository)

        videoId = intent.getLongExtra("video_id", 0)
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
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    @UnstableApi private fun observeViewModel() {
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
                        Toast.makeText(this@VideoDetailActivity, result.message, Toast.LENGTH_LONG)
                            .show()
                        finish()
                    }
                }
            }
        }
    }

    @UnstableApi private fun displayVideo(video: com.example.muzea.data.model.VideoResponse) {
        binding.tvTitle.text = video.title
        binding.tvDescription.text = video.description ?: "No description"
        binding.tvViews.text = "${video.views} views"
        binding.tvLikes.text = "${video.likes ?: 0} likes"
        binding.tvUploader.text = "Uploaded by: ${video.uploadedBy}"
        binding.tvDate.text = video.uploadedAt

        val fullVideoUrl = Constants.BASE_URL + video.url
        initializePlayer(fullVideoUrl)
    }

    @UnstableApi private fun initializePlayer(videoUrl: String) {
        val tokenManager = TokenManager(this)
        val token = tokenManager.getToken()

        if (token != null) {
            player = VideoPlayerHelper.createPlayerWithAuth(this, videoUrl, token)
            binding.playerView.player = player
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