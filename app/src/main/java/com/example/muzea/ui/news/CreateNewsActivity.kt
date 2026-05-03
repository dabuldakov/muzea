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
    private var videosList: List<VideoResponse> = emptyList()
    private var isLoadingVideos = false

    // Для изображения
    private var selectedImageFile: File? = null
    private var selectedImageUri: Uri? = null

    // Регистрация для выбора изображения из галереи
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            displaySelectedImage(it)
            copyImageToCache(it)
        }
    }

    // Регистрация для разрешений
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Storage permission required to select images", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateNewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация ViewModels
        val tokenManager = TokenManager(applicationContext)
        val apiService = RetrofitClient(tokenManager).apiService
        val newsRepository = NewsRepository(apiService)
        val videoRepository = VideoRepository(apiService)

        newsViewModel = NewsViewModel(newsRepository)
        videoViewModel = VideoViewModel(videoRepository)

        setupToolbar()
        setupClickListeners()
        observeViewModels()
        checkPermissions()

        // Загружаем список видео для выбора
        loadVideos()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.btnSubmit.setOnClickListener {
            createNews()
        }

        binding.btnSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needPermissions.isNotEmpty()) {
            permissionLauncher.launch(needPermissions)
        }
    }

    private fun displaySelectedImage(uri: Uri) {
        binding.cardImagePreview.visibility = android.view.View.VISIBLE
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(binding.ivImagePreview)
    }

    private fun copyImageToCache(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri)
            val cacheFile = File(cacheDir, "news_image_${System.currentTimeMillis()}.jpg")

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            selectedImageFile = cacheFile
            binding.tvImageFileName.text = fileName
            binding.tvImageFileName.setTextColor(ContextCompat.getColor(this, R.color.teal_200))

        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun loadVideos() {
        if (isLoadingVideos) return
        isLoadingVideos = true

        lifecycleScope.launch {
            videoViewModel.loadVideos()
            videoViewModel.videosResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }

                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        videosList = result.data ?: emptyList()
                        setupVideoSpinner()
                        isLoadingVideos = false
                    }

                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = android.view.View.GONE
                        setupVideoSpinner()
                        Toast.makeText(
                            this@CreateNewsActivity,
                            "Could not load videos: ${result.message}", Toast.LENGTH_SHORT
                        ).show()
                        isLoadingVideos = false
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

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, videoTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerVideo.adapter = adapter

        binding.spinnerVideo.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    selectedVideoId = if (position == 0 || videosList.isEmpty()) {
                        null
                    } else {
                        videosList[position - 1].id
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    selectedVideoId = null
                }
            }
    }

    private fun createNews() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()

        if (title.isEmpty()) {
            binding.etTitle.error = "Title is required"
            return
        }

        if (content.isEmpty()) {
            binding.etContent.error = "Content is required"
            return
        }

        newsViewModel.createNews(title, content, selectedVideoId, selectedImageFile)
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            newsViewModel.createNewsResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.btnSubmit.isEnabled = false
                        binding.btnSelectImage.isEnabled = false
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.tvProgress.text = "Creating news..."
                    }

                    is NetworkResult.Success -> {
                        binding.btnSubmit.isEnabled = true
                        binding.btnSelectImage.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(
                            this@CreateNewsActivity,
                            "News created successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    }

                    is NetworkResult.Error -> {
                        binding.btnSubmit.isEnabled = true
                        binding.btnSelectImage.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(
                            this@CreateNewsActivity,
                            "Failed to create news: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}