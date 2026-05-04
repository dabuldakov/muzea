package com.example.muzea.ui.news

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.muzea.R
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.model.VideoResponse
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.databinding.ActivityCreateNewsBinding
import com.example.muzea.ui.video.VideoViewModel
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class CreateNewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateNewsBinding
    private lateinit var newsViewModel: NewsViewModel
    private lateinit var videoViewModel: VideoViewModel

    private var selectedVideoId: Long? = null
    private var selectedImageFile: File? = null
    private var videosList: List<VideoResponse> = emptyList()
    private var isLoadingVideos = false

    private companion object {
        private const val IMAGE_PREFIX = "news_image_"
        private const val IMAGE_EXTENSION = ".jpg"
    }

    // Регистрация для выбора изображения из галереи
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }

    // Регистрация для разрешений
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { !it }) {
            Toast.makeText(this, "Storage permission required to select images", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViewModels()
        setupUI()
        loadVideos()
    }

    private fun initViewModels() {
        val tokenManager = TokenManager(applicationContext)
        val apiService = RetrofitClient(tokenManager).apiService

        newsViewModel = NewsViewModel(NewsRepository(apiService))
        videoViewModel = VideoViewModel(VideoRepository(apiService))
    }

    private fun setupUI() {
        setupToolbar()
        setupClickListeners()
        observeViewModels()
        checkPermissions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        binding.btnSubmit.setOnClickListener { createNews() }
        binding.btnSelectImage.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    private fun handleSelectedImage(uri: Uri) {
        displayImagePreview(uri)
        copyImageToCache(uri)
    }

    private fun displayImagePreview(uri: Uri) {
        binding.cardImagePreview.visibility = android.view.View.VISIBLE
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(binding.ivImagePreview)
    }

    private fun copyImageToCache(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri)
            val cacheFile =
                File(cacheDir, "$IMAGE_PREFIX${System.currentTimeMillis()}$IMAGE_EXTENSION")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            selectedImageFile = cacheFile
            binding.tvImageFileName.text = fileName
            binding.tvImageFileName.setTextColor(ContextCompat.getColor(this, R.color.teal_200))
        } catch (e: Exception) {
            Toast.makeText(this, "Error copying image: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions)
        }
    }

    private fun loadVideos() {
        if (isLoadingVideos) return
        isLoadingVideos = true
        showProgressBar(true)

        lifecycleScope.launch {
            videoViewModel.loadVideos()
            videoViewModel.videosResult.collect { result ->
                isLoadingVideos = false
                showProgressBar(false)

                when (result) {
                    is NetworkResult.Success -> {
                        videosList = result.data ?: emptyList()
                        setupVideoSpinner()
                    }

                    is NetworkResult.Error -> {
                        setupVideoSpinner()
                        Toast.makeText(
                            this@CreateNewsActivity,
                            "Could not load videos: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {}
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

        binding.spinnerVideo.adapter = adapter
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

        when {
            title.isEmpty() -> binding.etTitle.error = "Title is required"
            content.isEmpty() -> binding.etContent.error = "Content is required"
            else -> newsViewModel.createNews(title, content, selectedVideoId, selectedImageFile)
        }
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            newsViewModel.createNewsResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> setLoadingState(true)
                    is NetworkResult.Success -> handleSuccess()
                    is NetworkResult.Error -> {
                        android.util.Log.e("CreateNews", "Error: ${result.message}")
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
        binding.tvProgress.text = if (isLoading) "Creating news..." else ""
    }

    private fun handleSuccess() {
        setLoadingState(false)
        Toast.makeText(this, "News created successfully!", Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun handleError(message: String) {
        setLoadingState(false)
        Toast.makeText(this, "Failed to create news: $message", Toast.LENGTH_LONG).show()
    }

    private fun showProgressBar(show: Boolean) {
        binding.progressBar.visibility =
            if (show) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        // Очищаем временные файлы
        selectedImageFile?.delete()
    }
}