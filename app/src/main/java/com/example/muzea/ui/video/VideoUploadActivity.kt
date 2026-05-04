package com.example.muzea.ui.video

import android.Manifest
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
    private var selectedVideoFile: File? = null

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { copyVideoToCache(it) } }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { !it }) {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tokenManager = TokenManager(applicationContext)
        val apiService = RetrofitClient(tokenManager).apiService
        viewModel = VideoViewModel(VideoRepository(apiService))

        setupToolbar()
        setupClickListeners()
        observeViewModel()
        checkPermissions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        binding.btnSelectVideo.setOnClickListener { pickVideoLauncher.launch("video/*") }
        binding.btnUpload.setOnClickListener { uploadVideo() }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needPermissions.isNotEmpty()) permissionLauncher.launch(needPermissions)
    }

    private fun copyVideoToCache(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            val cacheFile = File(cacheDir, "video_${System.currentTimeMillis()}_$fileName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }

            if (cacheFile.exists() && cacheFile.length() > 0) {
                selectedVideoFile = cacheFile
                binding.tvSelectedFile.text = "Selected: ${cacheFile.name} (${cacheFile.length() / 1024} KB)"
                binding.btnUpload.isEnabled = true
            } else {
                Toast.makeText(this, "Failed to copy video", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "video_${System.currentTimeMillis()}.mp4"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun uploadVideo() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        when {
            title.isEmpty() -> binding.etTitle.error = "Title required"
            selectedVideoFile == null -> Toast.makeText(this, "Select video", Toast.LENGTH_SHORT).show()
            else -> {
                val body = MultipartBody.Part.createFormData(
                    "file",
                    selectedVideoFile!!.name,
                    selectedVideoFile!!.asRequestBody("video/mp4".toMediaTypeOrNull())
                )
                viewModel.uploadVideo(title, description.takeIf { it.isNotEmpty() }, body)
            }
        }
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