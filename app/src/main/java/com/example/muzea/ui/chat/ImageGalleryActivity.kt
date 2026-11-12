package com.example.muzea.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.databinding.ActivityImageGalleryBinding
import com.example.muzea.ui.common.SystemBars
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.LocalTimeFormatter
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Полноэкранный просмотр картинок чата.
 *
 * Свайп влево/вправо закрывает, вверх/вниз листает картинки по ленте.
 * В шапке — автор, дата и действия: скачать, ответить, переслать.
 */
@AndroidEntryPoint
class ImageGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageGalleryBinding
    private lateinit var images: ArrayList<ViewerImage>
    private var index = 0
    private var closing = false

    @Inject
    lateinit var tokenManager: TokenManager

    private var downX = 0f
    private var downY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)
        // Открываем не «выездом» экрана, а масштабированием из центра.
        overridePendingTransition(0, 0)

        images = intent.getSerializableExtra(EXTRA_IMAGES) as? ArrayList<ViewerImage>
            ?: arrayListOf()
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (images.size - 1).coerceAtLeast(0))
        if (images.isEmpty()) {
            finish()
            return
        }

        setupHeaderActions()
        setupGestures()
        showImage(animate = false)
        playOpenAnimation()
    }

    private fun current(): ViewerImage? = images.getOrNull(index)

    private fun setupHeaderActions() {
        binding.btnDownload.setOnClickListener {
            current()?.let { download(it) }
        }
        binding.btnReply.setOnClickListener {
            finishWithAction(ACTION_REPLY)
        }
        binding.btnForward.setOnClickListener {
            finishWithAction(ACTION_FORWARD)
        }
    }

    private fun finishWithAction(action: String) {
        val messageUuid = current()?.messageUuid ?: return
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_ACTION, action)
                .putExtra(EXTRA_MESSAGE_UUID, messageUuid)
        )
        closeWithAnimation()
    }

    private fun setupGestures() {
        val threshold = resources.displayMetrics.density * SWIPE_THRESHOLD_DP
        binding.ivImage.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    when (SwipeGesture.classify(event.x - downX, event.y - downY, threshold)) {
                        SwipeDirection.LEFT, SwipeDirection.RIGHT -> closeWithAnimation()
                        SwipeDirection.UP -> showNext()
                        SwipeDirection.DOWN -> showPrevious()
                        SwipeDirection.NONE -> Unit
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun showNext() {
        if (index >= images.lastIndex) return
        index++
        showImage(animate = true)
    }

    private fun showPrevious() {
        if (index <= 0) return
        index--
        showImage(animate = true)
    }

    private fun showImage(animate: Boolean) {
        val image = current() ?: return
        binding.tvSenderName.text = image.senderName.orEmpty()
        binding.tvDate.text = LocalTimeFormatter.format(image.createdAt)
        AvatarLoader.load(binding.ivSenderAvatar, image.senderAvatar)
        binding.progress.visibility = View.VISIBLE

        val request = Glide.with(this)
            .load(image.url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
        if (animate) {
            binding.ivImage.animate().alpha(0f).setDuration(SWITCH_FADE_MS).withEndAction {
                request.into(binding.ivImage)
                binding.ivImage.animate().alpha(1f).setDuration(SWITCH_FADE_MS).start()
                binding.progress.visibility = View.GONE
            }.start()
        } else {
            request.into(binding.ivImage)
            binding.progress.visibility = View.GONE
        }
    }

    private fun playOpenAnimation() {
        binding.ivImage.apply {
            scaleX = OPEN_SCALE
            scaleY = OPEN_SCALE
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(OPEN_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        binding.header.apply {
            alpha = 0f
            translationY = -height.toFloat() / 2f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(OPEN_DURATION_MS)
                .start()
        }
    }

    private fun closeWithAnimation() {
        if (closing) return
        closing = true
        binding.header.animate().alpha(0f).setDuration(CLOSE_DURATION_MS).start()
        binding.ivImage.animate()
            .scaleX(OPEN_SCALE)
            .scaleY(OPEN_SCALE)
            .alpha(0f)
            .setDuration(CLOSE_DURATION_MS)
            .withEndAction { finishOnce() }
            .start()
    }

    private fun finishOnce() {
        if (isFinishing) return
        finish()
        overridePendingTransition(0, 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeWithAnimation()
    }

    private fun download(image: ViewerImage) {
        val fileName = image.fileName?.takeIf { it.isNotBlank() }
            ?: "muzea-${System.currentTimeMillis()}.jpg"
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(image.url))
            .setTitle(fileName)
            .setMimeType("image/*")
            .setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS, fileName
            )
        tokenManager.getToken()?.let { request.addRequestHeader("Authorization", "Bearer $it") }
        (getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager)
            .enqueue(request)
        android.widget.Toast.makeText(
            this, R.string.attachment_saved_downloads, android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_IMAGES = "images"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_MESSAGE_UUID = "message_uuid"

        const val ACTION_REPLY = "reply"
        const val ACTION_FORWARD = "forward"

        private const val SWIPE_THRESHOLD_DP = 72f
        private const val OPEN_SCALE = 0.6f
        private const val OPEN_DURATION_MS = 220L
        private const val CLOSE_DURATION_MS = 160L
        private const val SWITCH_FADE_MS = 90L

        /** Результат просмотрщика: какое действие выбрал пользователь. */
        fun actionOf(intent: Intent?): Pair<String, String>? {
            val action = intent?.getStringExtra(EXTRA_ACTION) ?: return null
            val uuid = intent.getStringExtra(EXTRA_MESSAGE_UUID) ?: return null
            return action to uuid
        }

        fun intent(context: Context, images: List<ViewerImage>, startIndex: Int): Intent =
            Intent(context, ImageGalleryActivity::class.java).apply {
                putExtra(EXTRA_IMAGES, ArrayList(images))
                putExtra(EXTRA_INDEX, startIndex)
            }

        fun start(context: Context, images: List<ViewerImage>, startIndex: Int) {
            context.startActivity(intent(context, images, startIndex))
        }
    }
}
