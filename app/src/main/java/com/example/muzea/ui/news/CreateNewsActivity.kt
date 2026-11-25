package com.example.muzea.ui.news

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.muzea.R
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.Video
import com.example.muzea.databinding.ActivityCreateNewsBinding
import com.example.muzea.utils.MediaUrl
import com.example.muzea.ui.video.VideoViewModel
import com.example.muzea.core.Resource
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import com.example.muzea.ui.common.SystemBars

@AndroidEntryPoint
class CreateNewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateNewsBinding
    private val newsViewModel: NewsViewModel by viewModels()
    private val videoViewModel: VideoViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private var selectedVideoId: Long? = null
    private var selectedImageFile: File? = null
    private var videosList: List<Video> = emptyList()
    private var isLoadingVideos = false
    private var myUsername: String? = null

    private var newsId: Long = 0L
    private val isEditMode: Boolean get() = newsId != 0L
    private var initialVideoId: Long? = null
    private var currentImageUrl: String? = null
    private var removeImage = false
    private var editLoaded = false

    companion object {
        private const val IMAGE_PREFIX = "news_image_"
        private const val IMAGE_EXTENSION = ".jpg"
        private const val DRAFT_DIR = "news_draft"
        const val EXTRA_NEWS_ID = "extra_news_id"

        fun intent(context: android.content.Context, newsId: Long): android.content.Intent =
            android.content.Intent(context, CreateNewsActivity::class.java)
                .putExtra(EXTRA_NEWS_ID, newsId)
    }

    // Регистрация для выбора изображения из галереи
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)

        newsId = intent.getLongExtra(EXTRA_NEWS_ID, 0L)
        initViewModels()
        setupUI()
        loadVideos()
        if (isEditMode) loadNewsForEdit()
    }

    private fun initViewModels() {
        myUsername = tokenManager.getUsername()
    }

    private fun setupUI() {
        setupToolbar()
        setupClickListeners()
        observeViewModels()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        val title = getString(
            if (isEditMode) R.string.news_edit_title else R.string.create_news_title
        )
        supportActionBar?.title = title
        binding.toolbar.title = title
        binding.btnSubmit.setText(
            if (isEditMode) R.string.news_save else R.string.create_news_submit
        )
    }

    private fun setupClickListeners() {
        TooltipCompat.setTooltipText(
            binding.btnSelectImage,
            getString(R.string.create_news_select_image)
        )
        binding.btnSubmit.setOnClickListener { createNews() }
        binding.btnSelectImage.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnRemoveImage.setOnClickListener { clearImage() }
    }

    private fun loadNewsForEdit() {
        lifecycleScope.launch {
            newsViewModel.loadNewsById(newsId)
            newsViewModel.newsDetailResult.collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val news = result.data ?: return@collect
                        if (editLoaded) return@collect
                        editLoaded = true
                        prefill(news)
                    }
                    is Resource.Error -> Toast.makeText(
                        this@CreateNewsActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun prefill(news: News) {
        binding.etTitle.setText(news.title)
        binding.etContent.setText(news.content)
        currentImageUrl = news.imageUrl
        initialVideoId = news.relatedVideo?.id
        selectedVideoId = news.relatedVideo?.id
        renderCurrentImage()
        applyInitialVideoSelection()
    }

    private fun renderCurrentImage() {
        val url = currentImageUrl
        if (url.isNullOrEmpty()) {
            binding.cardImagePreview.visibility = android.view.View.GONE
            binding.tvImageFileName.text = getString(R.string.create_news_no_image)
            binding.btnRemoveImage.visibility = android.view.View.GONE
            return
        }
        binding.cardImagePreview.visibility = android.view.View.VISIBLE
        Glide.with(this)
            .load(MediaUrl.main(url))
            .fitCenter()
            .into(binding.ivImagePreview)
        binding.tvImageFileName.text = getString(R.string.news_current_image)
        binding.btnRemoveImage.visibility = android.view.View.VISIBLE
    }

    private fun clearImage() {
        selectedImageFile?.delete()
        selectedImageFile = null
        currentImageUrl = null
        removeImage = true
        binding.cardImagePreview.visibility = android.view.View.GONE
        binding.tvImageFileName.text = getString(R.string.create_news_no_image)
        binding.btnRemoveImage.visibility = android.view.View.GONE
    }

    private fun applyInitialVideoSelection() {
        val targetId = initialVideoId ?: return
        if (videosList.isEmpty()) return
        val index = videosList.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            binding.spinnerVideo.setSelection(index + 1, false)
            selectedVideoId = targetId
        }
    }

    private fun handleSelectedImage(uri: Uri) {
        displayImagePreview(uri)
        copyImageToCache(uri)
    }

    private fun displayImagePreview(uri: Uri) {
        binding.cardImagePreview.visibility = android.view.View.VISIBLE
        Glide.with(this)
            .load(uri)
            .fitCenter()
            .into(binding.ivImagePreview)
    }

    private fun copyImageToCache(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri)
            // Черновик держим в filesDir, а не в cacheDir: система (в т.ч.
            // Samsung Device Care) и «Очистить кэш» в профиле вычищают
            // cacheDir, и выбранная картинка исчезала до отправки.
            val draftDir = File(filesDir, DRAFT_DIR).apply { mkdirs() }
            draftDir.listFiles()?.forEach { it.delete() }
            val imageFile =
                File(draftDir, "$IMAGE_PREFIX${System.currentTimeMillis()}$IMAGE_EXTENSION")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }

            selectedImageFile = imageFile
            currentImageUrl = null
            removeImage = false
            binding.tvImageFileName.text = fileName
            binding.tvImageFileName.setTextColor(ContextCompat.getColor(this, R.color.teal_200))
            binding.btnRemoveImage.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_copy_image_format, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "image_${System.currentTimeMillis()}.jpg"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun loadVideos() {
        if (isLoadingVideos) return
        isLoadingVideos = true
        showProgressBar(true)

        lifecycleScope.launch {
            videoViewModel.loadVideos(myUsername)
            videoViewModel.feedState.collect { state ->
                if (state.isLoading) return@collect
                isLoadingVideos = false
                showProgressBar(false)
                videosList = state.videos
                setupVideoSpinner()
                state.error?.let { message ->
                    Toast.makeText(
                        this@CreateNewsActivity,
                        "Could not load videos: $message",
                        Toast.LENGTH_SHORT
                    ).show()
                    videoViewModel.consumeFeedError()
                }
            }
        }
    }

    private fun setupVideoSpinner() {
        val videoTitles = if (videosList.isEmpty()) {
            listOf("None (no videos available)")
        } else {
            listOf("None") + videosList.map { "${it.title} (${it.views} views)" }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, videoTitles).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerVideo.onItemSelectedListener = null
        binding.spinnerVideo.adapter = adapter
        applyInitialVideoSelection()
        binding.spinnerVideo.onItemSelectedListener = createSpinnerListener()
    }

    private fun createSpinnerListener() =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                selectedVideoId = when {
                    position == 0 || videosList.isEmpty() -> null
                    else -> videosList[position - 1].id
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedVideoId = null
            }
        }

    private fun createNews() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()

        val image = selectedImageFile?.takeIf { it.exists() && it.length() > 0 }
        if (selectedImageFile != null && image == null) {
            // Файл пропал, пока заполняли форму: просим выбрать заново.
            selectedImageFile = null
            binding.cardImagePreview.visibility = android.view.View.GONE
            binding.tvImageFileName.text = ""
            Toast.makeText(this, R.string.error_image_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        when {
            title.isEmpty() -> binding.etTitle.error = getString(R.string.error_title_required)
            content.isEmpty() -> binding.etContent.error = getString(R.string.error_content_required)
            isEditMode -> newsViewModel.updateNews(
                id = newsId,
                title = title,
                content = content,
                videoId = selectedVideoId,
                removeVideo = selectedVideoId == null && initialVideoId != null,
                imageFile = image,
                removeImage = image == null && removeImage
            )
            else -> newsViewModel.createNews(title, content, selectedVideoId, image)
        }
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            newsViewModel.createNewsResult.collect { result ->
                when (result) {
                    is Resource.Loading -> setLoadingState(true)
                    is Resource.Success -> handleSuccess()
                    is Resource.Error -> {
                        android.util.Log.e("CreateNews", "Error: ${result.message}")
                        handleError(result.message ?: "Unknown error")
                    }
                }
            }
        }
        lifecycleScope.launch {
            newsViewModel.updateNewsResult.collect { result ->
                when (result) {
                    is Resource.Loading -> setLoadingState(true)
                    is Resource.Success -> handleSuccess()
                    is Resource.Error -> {
                        android.util.Log.e("EditNews", "Error: ${result.message}")
                        handleError(result.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSubmit.isEnabled = !isLoading
        binding.btnSelectImage.isEnabled = !isLoading
        binding.progressBar.visibility =
            if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvProgress.text = if (isLoading) {
            getString(if (isEditMode) R.string.news_saving else R.string.news_creating)
        } else {
            ""
        }
    }

    private fun handleSuccess() {
        setLoadingState(false)
        Toast.makeText(
            this,
            if (isEditMode) R.string.news_updated else R.string.news_created,
            Toast.LENGTH_LONG
        ).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun deleteDraftImage() {
        selectedImageFile?.delete()
        selectedImageFile = null
        File(filesDir, DRAFT_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun handleError(message: String) {
        setLoadingState(false)
        Toast.makeText(
            this,
            getString(
                if (isEditMode) R.string.news_update_failed_format
                else R.string.news_create_failed_format,
                message
            ),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showProgressBar(show: Boolean) {
        binding.progressBar.visibility =
            if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        deleteDraftImage()
    }
}