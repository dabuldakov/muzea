package com.example.muzea.ui.video

import com.example.muzea.R
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.muzea.databinding.ActivityVideoUploadBinding
import com.example.muzea.core.Resource
import com.example.muzea.utils.ErrorReporter
import com.example.muzea.utils.VideoFileValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import com.example.muzea.ui.common.SystemBars
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@AndroidEntryPoint
class VideoUploadActivity : AppCompatActivity() {

    private companion object {
        const val DEFAULT_VIDEO_TYPE = "video/mp4"
        const val COPY_BUFFER_BYTES = 64 * 1024

        /**
         * Повторы с паузами: облачный провайдер Google Фото может отдавать
         * файл по мере скачивания. Ждём, пока размер растёт; если два раза
         * подряд пришёл один и тот же кусок — дальше не тянем.
         */
        val COPY_RETRY_DELAYS_MS = longArrayOf(0, 2_000, 5_000, 10_000, 15_000)
    }

    /** Результат копирования выбранного видео во временный файл. */
    private data class VideoCopyResult(val file: File?, val errorRes: Int? = null)

    private lateinit var binding: ActivityVideoUploadBinding
    private val viewModel: VideoViewModel by viewModels()
    private var selectedVideoFile: File? = null
    private var selectedContentType: String = DEFAULT_VIDEO_TYPE
    private var thumbnailBytes: ByteArray? = null

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { copyVideoToCache(it) } }

    /**
     * Запасной выбор файла через системный «Файлы». Фотопикер по умолчанию
     * подмешивает облако Google Фото, а не все видео там целые — здесь можно
     * взять файл из «Загрузок», Telegram, Movies и т.п.
     */
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { copyVideoToCache(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)

        setupToolbar()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        binding.btnSelectVideo.setOnClickListener {
            pickVideoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
        binding.btnSelectFile.setOnClickListener { openDevicePicker() }
        binding.btnUpload.setOnClickListener { uploadVideo() }
    }

    private fun openDevicePicker() {
        runCatching { pickFileLauncher.launch(arrayOf("video/*")) }
    }

    /**
     * Копирует выбранное видео во временный файл. Провайдер (облачная галерея,
     * кэш мессенджера) иногда отдаёт поток короче файла, поэтому копию сверяем
     * с заявленным размером и структурой MP4; при обрыве пробуем ещё раз.
     */
    private fun copyVideoToCache(uri: Uri) {
        binding.btnUpload.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        binding.tvProgress.text = getString(R.string.video_uploading)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { copyToDraft(uri) }

            binding.progressBar.visibility = View.GONE
            binding.tvProgress.text = ""

            val file = result.file
            if (file == null) {
                if (result.errorRes == R.string.video_copy_truncated) {
                    showTruncatedDialog()
                } else {
                    val message = getString(result.errorRes ?: R.string.video_copy_failed)
                    Toast.makeText(
                        this@VideoUploadActivity,
                        getString(R.string.error_prefix, message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            selectedVideoFile = file
            selectedContentType = resolveContentType(uri, file.name)
            binding.tvSelectedFile.text =
                getString(R.string.video_selected_format, file.name, file.length() / 1024)
            binding.btnUpload.isEnabled = true
            extractThumbnail(file)
        }
    }

    private fun showTruncatedDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.video_copy_truncated)
            .setPositiveButton(R.string.video_select_from_device) { _, _ -> openDevicePicker() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private suspend fun copyToDraft(uri: Uri): VideoCopyResult {
        val (fileName, _) = queryVideoMeta(uri)
        // filesDir, а не cacheDir: cacheDir вычищается системой и кнопкой
        // «Очистить кэш», из-за чего выбранное видео пропадало до отправки.
        val uploadDir = File(filesDir, "video_draft").apply { mkdirs() }
        val draft = File(uploadDir, "video_${System.currentTimeMillis()}_$fileName")

        var previousSize = -1L
        var stablePolls = 0
        var lastReason = "unknown"
        var lastCopied = -1L
        var lastDeclared = -1L
        var lastAtoms = "n/a"
        val attempts = mutableListOf<Long>()

        for ((index, delayMs) in COPY_RETRY_DELAYS_MS.withIndex()) {
            if (delayMs > 0) delay(delayMs)

            val declared = queryVideoMeta(uri).second
            val startedAt = SystemClock.elapsedRealtime()
            val copied = readIntoDraft(uri, draft)
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            attempts += copied ?: -1L

            val atoms = if (copied != null && copied > 0) {
                VideoFileValidator.describe(draft)
            } else {
                "n/a"
            }
            Log.i(
                "VideoCopy",
                "attempt=${index + 1} copied=$copied declared=$declared " +
                    "elapsedMs=$elapsed source=${uri.path?.take(120)} atoms=$atoms"
            )

            lastCopied = copied ?: -1L
            lastDeclared = declared
            lastAtoms = atoms

            if (copied != null && copied > 0) {
                val truncated = VideoFileValidator.isTruncated(draft)
                val shortCopy = declared > 0 && copied < declared
                if (!truncated && !shortCopy) {
                    return VideoCopyResult(draft, null)
                }
                lastReason = if (truncated) "truncated" else "short"

                // Провайдер отдаёт один и тот же кусок: либо он ещё качает
                // в фоне, либо это стабильная частичная копия. Ждём, только
                // пока размер растёт.
                if (copied == previousSize) {
                    stablePolls++
                    if (stablePolls >= 2) break
                } else {
                    stablePolls = 0
                }
                previousSize = copied
            } else {
                lastReason = if (copied == null) "unreadable" else "empty"
                if (copied == previousSize) break
                previousSize = copied ?: -1L
            }
            draft.delete()
        }

        reportCopyFailure(
            lastReason,
            lastDeclared,
            lastCopied,
            "atoms=$lastAtoms attempts=${attempts.joinToString(",")}"
        )
        val errorRes = if (lastReason == "truncated") {
            R.string.video_copy_truncated
        } else {
            R.string.video_copy_failed
        }
        return VideoCopyResult(null, errorRes)
    }

    private fun readIntoDraft(uri: Uri, draft: File): Long? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(draft).use { output ->
                // Крупный буфер: облачные провайдеры отдают поток через pipe
                // и могут оборвать его, если читать слишком медленно.
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
            draft.length()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        draft.delete()
        throw e
    } catch (e: Exception) {
        null
    }

    private fun reportCopyFailure(
        reason: String,
        declaredSize: Long,
        copied: Long,
        details: String? = null
    ) {
        val suffix = details?.let { " $it" } ?: ""
        ErrorReporter.logCaught(
            "VideoCopy",
            IllegalStateException(
                "copy failed: reason=$reason declared=$declaredSize copied=$copied$suffix"
            )
        )
    }

    /** Имя и заявленный размер файла у провайдера; размер может быть неизвестен. */
    private fun queryVideoMeta(uri: Uri): Pair<String, Long> {
        var fileName = "video_${System.currentTimeMillis()}.mp4"
        var size = -1L
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    fileName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return fileName to size
    }

    /**
     * MIME для presigned PUT: сервер принимает только видео-типы и по нему же
     * выбирает расширение объекта. ContentResolver точнее расширения, но для
     * content:// из некоторых галерей тип бывает пустым — тогда смотрим имя.
     */
    private fun resolveContentType(uri: Uri, fileName: String): String {
        val fromResolver = contentResolver.getType(uri)
            ?.takeIf { it.startsWith("video/") }
        if (fromResolver != null) return fromResolver

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?.takeIf { it.startsWith("video/") }
            ?: DEFAULT_VIDEO_TYPE
    }

    private fun extractThumbnail(file: File) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { extractFrameBytes(file) }
            if (bytes != null && bytes.isNotEmpty()) {
                thumbnailBytes = bytes
                Glide.with(this@VideoUploadActivity)
                    .load(bytes)
                    .centerCrop()
                    .into(binding.ivPreview)
                binding.ivPreview.visibility = View.VISIBLE
            } else {
                thumbnailBytes = null
                binding.ivPreview.visibility = View.GONE
            }
        }
    }

    private fun extractFrameBytes(file: File): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val frame = retriever.getFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: return null
                return ByteArrayOutputStream().use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun uploadVideo() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        val video = selectedVideoFile?.takeIf { it.exists() && it.length() > 0 }
        if (selectedVideoFile != null && video == null) {
            selectedVideoFile = null
            binding.btnUpload.isEnabled = false
            binding.tvSelectedFile.text = ""
            Toast.makeText(this, R.string.error_video_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        when {
            title.isEmpty() -> binding.etTitle.error = getString(R.string.error_title_required)
            selectedVideoFile == null -> Toast.makeText(this, R.string.video_select_required, Toast.LENGTH_SHORT).show()
            else -> {
                viewModel.uploadVideo(
                    title = title,
                    description = description.takeIf { it.isNotEmpty() },
                    file = video!!,
                    contentType = selectedContentType,
                    thumbnail = thumbnailBytes
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        selectedVideoFile?.delete()
        File(filesDir, "video_draft").listFiles()?.forEach { it.delete() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uploadProgress.collect { percent ->
                if (percent == null) return@collect
                binding.progressBar.isIndeterminate = false
                binding.progressBar.max = 100
                binding.progressBar.progress = percent
                binding.tvProgress.text = "${getString(R.string.video_uploading)} $percent%"
            }
        }

        lifecycleScope.launch {
            viewModel.uploadResult.collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        binding.btnUpload.isEnabled = false
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.progressBar.isIndeterminate = false
                        binding.progressBar.max = 100
                        binding.progressBar.progress = 0
                        binding.tvProgress.text = getString(R.string.video_uploading)
                    }
                    is Resource.Success -> {
                        binding.btnUpload.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.tvProgress.text = ""
                        Toast.makeText(this@VideoUploadActivity, R.string.video_upload_success, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    is Resource.Error -> {
                        binding.btnUpload.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.tvProgress.text = ""
                        Toast.makeText(this@VideoUploadActivity, getString(R.string.video_upload_failed_format, result.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}