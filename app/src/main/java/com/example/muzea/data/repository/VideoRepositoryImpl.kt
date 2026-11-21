package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.FinalizeVideoUploadRequest
import com.example.muzea.data.model.PresignUploadRequest
import com.example.muzea.domain.model.Video
import com.example.muzea.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.VideoRepository]. */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val presignedUploader: PresignedUploader
) : com.example.muzea.domain.repository.VideoRepository {

    override suspend fun getVideos(): Flow<Resource<List<Video>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getVideos()
            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.map { it.toDomain() }
                VideoListCache.put(content)
                emit(Resource.Success(content))
            } else {
                emit(Resource.Error("Failed to load videos: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override fun cachedVideos(): List<Video> = VideoListCache.get()

    override suspend fun getVideoById(id: Long): Flow<Resource<Video>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getVideoById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to load video: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun uploadVideo(
        title: String,
        description: String?,
        file: File,
        contentType: String,
        thumbnail: ByteArray?,
        onProgress: (Int) -> Unit
    ): Flow<Resource<Video>> = flow {
        emit(Resource.Loading())
        try {
            val presign = apiService.presignVideoUpload(
                PresignUploadRequest(contentType = contentType, size = file.length())
            )
            // 503 — на сервере не задан MINIO_PUBLIC_URL. Работаем по-старому,
            // чтобы загрузка не ломалась в стендах без публичного S3.
            if (presign.code() == 503) {
                emit(uploadMultipart(title, description, file, contentType, thumbnail, onProgress))
                return@flow
            }
            val ticket = presign.body()
            if (!presign.isSuccessful || ticket == null) {
                emit(Resource.Error("Upload failed: ${failureReason(presign)}"))
                return@flow
            }

            presignedUploader.put(ticket.uploadUrl, file, contentType, onProgress)

            val finalized = apiService.finalizeVideoUpload(
                FinalizeVideoUploadRequest(
                    objectKey = ticket.objectKey,
                    title = title,
                    description = description
                )
            )
            val video = finalized.body()
            if (finalized.isSuccessful && video != null) {
                emit(Resource.Success(video.toDomain()))
            } else {
                emit(Resource.Error("Upload failed: ${failureReason(finalized)}"))
            }
        } catch (e: UploadFailedException) {
            emit(Resource.Error(e.message ?: "Upload failed"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    /** Запасной путь для серверов без настроенного публичного MinIO. */
    private suspend fun uploadMultipart(
        title: String,
        description: String?,
        file: File,
        contentType: String,
        thumbnail: ByteArray?,
        onProgress: (Int) -> Unit
    ): Resource<Video> {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            ProgressRequestBody(file, contentType.toMediaTypeOrNull(), onProgress)
        )
        val thumbnailPart = thumbnail?.let {
            MultipartBody.Part.createFormData(
                "thumbnail",
                "thumbnail.jpeg",
                it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
        }
        val response = apiService.uploadVideo(
            title.toRequestBody(),
            description?.toRequestBody(),
            filePart,
            thumbnailPart
        )
        val video = response.body()
        return if (response.isSuccessful && video != null) {
            Resource.Success(video.toDomain())
        } else {
            Resource.Error("Upload failed: ${failureReason(response)}")
        }
    }

    private fun failureReason(response: retrofit2.Response<*>): String =
        response.message().takeIf { it.isNotBlank() } ?: "HTTP ${response.code()}"

    override suspend fun deleteVideo(id: Long): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.deleteVideo(id)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error("Delete failed: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun reportVideo(id: Long, reason: String): Boolean {
        return try {
            apiService.reportContent(
                com.example.muzea.data.model.ContentReportRequest(
                    contentType = "VIDEO",
                    contentId = id,
                    reason = reason
                )
            ).isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
