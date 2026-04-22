package com.example.muzea.ui.video

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.muzea.databinding.ActivityVideoUploadBinding
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class VideoUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoUploadBinding
    private lateinit var viewModel: VideoViewModel
    private var selectedVideoUri: Uri? = null
    private var selectedVideoFile: File? = null

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            displaySelectedVideoInfo(it)
        }
    }

    // Регистрация для запроса разрешений
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required to access gallery and camera", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Создаем ViewModel вручную
        val tokenManager = TokenManager(applicationContext)
        val apiService = RetrofitClient(tokenManager).apiService
        val videoRepository = VideoRepository(apiService)
        viewModel = VideoViewModel(videoRepository)

        setupToolbar()
        setupClickListeners()
        observeViewModel()
        checkPermissions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        binding.btnUpload.setOnClickListener {
            uploadVideo()
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needPermissions.isNotEmpty()) {
            permissionLauncher.launch(needPermissions)
        } else {
            // Разрешения уже есть
            Toast.makeText(this, "Permissions already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displaySelectedVideoInfo(uri: Uri) {
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    val fileName = it.getString(nameIndex)
                    val fileSize = it.getLong(sizeIndex)

                    binding.tvSelectedFile.text = "Selected: $fileName (${fileSize / 1024} KB)"
                    binding.btnUpload.isEnabled = true

                    selectedVideoFile = File(cacheDir, fileName)
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(selectedVideoFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(this, "Video selected: $fileName", Toast.LENGTH_SHORT).show()
                }
            }
            cursor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error selecting video: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun uploadVideo() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (title.isEmpty()) {
            binding.etTitle.error = "Title required"
            return
        }

        if (selectedVideoFile == null) {
            Toast.makeText(this, "Please select a video", Toast.LENGTH_SHORT).show()
            return
        }

        if (!selectedVideoFile!!.exists()) {
            Toast.makeText(this, "Video file not found", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Uploading video: ${selectedVideoFile!!.name}", Toast.LENGTH_SHORT).show()

        val requestFile = selectedVideoFile!!.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", selectedVideoFile!!.name, requestFile)

        viewModel.uploadVideo(title, if (description.isNotEmpty()) description else null, body)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uploadResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.btnUpload.isEnabled = false
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.tvProgress.text = "Uploading..."
                    }
                    is NetworkResult.Success -> {
                        binding.btnUpload.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this@VideoUploadActivity, "Upload successful!", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    is NetworkResult.Error -> {
                        binding.btnUpload.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this@VideoUploadActivity, "Upload failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}