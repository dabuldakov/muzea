package com.example.muzea.ui.video

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.databinding.ActivityVideoDetailBinding
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import com.example.muzea.utils.VideoPlayerHelper
import kotlinx.coroutines.launch

class VideoDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoDetailBinding
    private lateinit var viewModel: VideoViewModel
    private var player: ExoPlayer? = null
    private var videoId: Long = 0
    private var isFullscreen = false
    private var originalPlayerHeight = 0

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

        binding.btnDelete.setOnClickListener { confirmDelete() }
        setupToolbar()
        setupFullscreen()
        setupBackPressHandling()
        observeViewModel()
        viewModel.loadVideoById(videoId)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete video")
            .setMessage("Are you sure you want to delete this video?")
            .setPositiveButton("Delete") { _, _ ->
                binding.btnDelete.isEnabled = false
                viewModel.deleteVideo(videoId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupFullscreen() {
        binding.playerView.setFullscreenButtonClickListener { toggleFullscreen() }
    }

    private fun toggleFullscreen() {
        if (isFullscreen) exitFullscreen() else enterFullscreen()
    }

    private fun enterFullscreen() {
        isFullscreen = true
        binding.toolbar.visibility = View.GONE
        binding.contentScroll.visibility = View.GONE

        val params = binding.playerContainer.layoutParams
        originalPlayerHeight = params.height
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        binding.playerContainer.layoutParams = params

        hideSystemUi()
    }

    private fun exitFullscreen() {
        if (!isFullscreen) return
        isFullscreen = false
        binding.toolbar.visibility = View.VISIBLE
        binding.contentScroll.visibility = View.VISIBLE

        val params = binding.playerContainer.layoutParams
        params.height = if (originalPlayerHeight > 0) originalPlayerHeight
        else (DEFAULT_PLAYER_HEIGHT_DP * resources.displayMetrics.density).toInt()
        binding.playerContainer.layoutParams = params

        showSystemUi()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
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

        lifecycleScope.launch {
            viewModel.deleteResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> Unit
                    is NetworkResult.Success -> {
                        Toast.makeText(this@VideoDetailActivity, "Video deleted", Toast.LENGTH_SHORT)
                            .show()
                        finish()
                    }

                    is NetworkResult.Error -> {
                        binding.btnDelete.isEnabled = true
                        Toast.makeText(this@VideoDetailActivity, result.message, Toast.LENGTH_LONG)
                            .show()
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
        binding.tvDate.text = LocalTimeFormatter.format(video.uploadedAt)

        val myUsername = TokenManager(this).getUsername()
        binding.btnDelete.visibility =
            if (myUsername != null && video.uploadedBy.trim() == myUsername.trim()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        val fullVideoUrl = video.getFullVideoUrl(com.example.muzea.utils.Constants.BASE_URL)
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
        if (isFullscreen) {
            exitFullscreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoPlayerHelper.releasePlayer(player)
    }

    private companion object {
        private const val DEFAULT_PLAYER_HEIGHT_DP = 240
    }
}