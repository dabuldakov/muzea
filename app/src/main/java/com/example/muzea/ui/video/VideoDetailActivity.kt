package com.example.muzea.ui.video

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.muzea.R
import com.example.muzea.databinding.ActivityVideoDetailBinding
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.core.Resource
import com.example.muzea.ui.common.ReportDialog
import com.example.muzea.ui.common.SystemBars
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@AndroidEntryPoint
class VideoDetailActivity : AppCompatActivity(), VideoActionsBottomSheet.Listener {

    private lateinit var binding: ActivityVideoDetailBinding
    private val viewModel: VideoViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private var player: ExoPlayer? = null
    private var videoId: Long = 0
    private var isFullscreen = false
    private var originalPlayerHeight = 0
    private var isMine = false
    private var isDeleting = false

    @Inject
    lateinit var playbackController: VideoPlaybackController

    @UnstableApi override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)

        videoId = intent.getLongExtra("video_id", 0)
        if (videoId == 0L) {
            Toast.makeText(this, R.string.video_invalid, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupFullscreen()
        setupBackPressHandling()
        observeViewModel()
        viewModel.loadVideoById(videoId)
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.video_delete_title)
            .setMessage(R.string.video_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                isDeleting = true
                invalidateOptionsMenu()
                viewModel.deleteVideo(videoId)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun reportVideo(reason: String) {
        lifecycleScope.launch {
            val ok = viewModel.reportVideo(videoId, reason)
            Toast.makeText(
                this@VideoDetailActivity,
                if (ok) R.string.report_sent else R.string.report_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.video_detail_title)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_video_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_video_actions -> {
            VideoActionsBottomSheet.forVideo(canDelete = isMine)
                .show(supportFragmentManager, "video_actions")
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onVideoAction(actionId: String) {
        when (actionId) {
            VideoActionsBottomSheet.ACTION_DELETE -> confirmDelete()
            VideoActionsBottomSheet.ACTION_REPORT ->
                ReportDialog.show(this) { reason -> reportVideo(reason) }
        }
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

    internal fun enterFullscreen() {
        if (isFullscreen) return
        isFullscreen = true
        binding.toolbar.visibility = View.GONE
        binding.contentScroll.visibility = View.GONE

        val params = binding.playerContainer.layoutParams
        originalPlayerHeight = params.height
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        binding.playerContainer.layoutParams = params

        applyFullscreenOrientation(player)
        hideSystemUi()
    }

    internal fun exitFullscreen() {
        if (!isFullscreen) return
        isFullscreen = false
        binding.toolbar.visibility = View.VISIBLE
        binding.contentScroll.visibility = View.VISIBLE

        val params = binding.playerContainer.layoutParams
        params.height = if (originalPlayerHeight > 0) originalPlayerHeight
        else (DEFAULT_PLAYER_HEIGHT_DP * resources.displayMetrics.density).toInt()
        binding.playerContainer.layoutParams = params

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemUi()
    }

    /**
     * В полноэкранном режиме держим ту ориентацию, в которой видео занимает
     * максимум места. Без этого на телефоне в портрете горизонтальное видео
     * оставалось узкой полосой по центру — казалось, что экран не развернулся.
     */
    internal fun applyFullscreenOrientation(width: Int, height: Int) {
        val orientation = fullscreenOrientation(width, height)
        if (requestedOrientation != orientation) {
            requestedOrientation = orientation
        }
    }

    internal fun applyFullscreenOrientation(player: Player?) {
        applyFullscreenOrientation(
            player?.videoSize?.width ?: 0,
            player?.videoSize?.height ?: 0
        )
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showSystemUi() {
        // Оставляем edge-to-edge: отступы контента пересчитывает SystemBars,
        // когда системные панели снова становятся видимыми.
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    @UnstableApi private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.videoDetailResult.collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }

                    is Resource.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        displayVideo(result.data!!)
                    }

                    is Resource.Error -> {
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
                    is Resource.Loading -> Unit
                    is Resource.Success -> {
                        playbackController.close()
                        Toast.makeText(this@VideoDetailActivity, R.string.video_deleted, Toast.LENGTH_SHORT)
                            .show()
                        finish()
                    }

                    is Resource.Error -> {
                        isDeleting = false
                        invalidateOptionsMenu()
                        Toast.makeText(this@VideoDetailActivity, result.message, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    @UnstableApi private fun displayVideo(video: com.example.muzea.domain.model.Video) {
        binding.tvTitle.text = video.title
        binding.tvDescription.text = video.description ?: getString(R.string.video_no_description)
        binding.tvViews.text = getString(R.string.video_views_format, video.views)
        binding.tvLikes.text = getString(R.string.video_likes_format, video.likes ?: 0)
        binding.tvUploader.text = getString(R.string.label_uploaded_by) + video.uploadedBy
        binding.tvDate.text = LocalTimeFormatter.format(video.uploadedAt)

        val myUsername = tokenManager.getUsername()
        isMine = myUsername != null && video.uploadedBy.trim() == myUsername.trim()

        initializePlayer(video)
    }

    @UnstableApi private fun initializePlayer(video: com.example.muzea.domain.model.Video) {
        val newPlayer = playbackController.play(video)
        if (newPlayer == null) {
            // Контент и действия остаются доступны; без токена просто нет
            // воспроизведения (например, сессия истекла).
            Toast.makeText(this, R.string.auth_required, Toast.LENGTH_SHORT).show()
            return
        }
        newPlayer.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (isFullscreen) applyFullscreenOrientation(videoSize.width, videoSize.height)
            }
        })
        player = newPlayer
        binding.playerView.player = newPlayer
    }

    override fun onResume() {
        super.onResume()
        playbackController.setDetailVisible(true)
        if (player == null && playbackController.activeVideo.value?.id == videoId) {
            player = playbackController.player
        }
        if (binding.playerView.player == null && player != null) {
            binding.playerView.player = player
        }
    }

    override fun onPause() {
        super.onPause()
        // Видео продолжает играть: при возврате на вкладки его покажет mini-плеер.
        playbackController.setDetailVisible(false)
        if (isFullscreen) {
            exitFullscreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackController.setDetailVisible(false)
        binding.playerView.player = null
        // Плеер принадлежит VideoPlaybackController и не освобождается здесь.
    }

    private companion object {
        private const val DEFAULT_PLAYER_HEIGHT_DP = 240
    }

    /**
     * Ориентация для полноэкранного видео: горизонтальное — ландшафт,
     * вертикальное — портрет. Пока размер видео не известен, ничего не меняем.
     */
    internal fun fullscreenOrientation(width: Int, height: Int): Int = when {
        width <= 0 || height <= 0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        width > height -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
}
