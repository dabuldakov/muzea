package com.example.muzea.data.repository

import com.example.muzea.data.api.ApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.CreateNewsCommentRequest
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.domain.model.NewsCommentsPage
import com.example.muzea.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.NewsRepository]. */
@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : com.example.muzea.domain.repository.NewsRepository {

    override suspend fun getNews(page: Int, size: Int): Flow<Resource<List<News>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getNews(page, size)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.content.map { it.toDomain() }))
            } else {
                emit(Resource.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun getNewsById(id: Long): Flow<Resource<News>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getNewsById(id)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else {
                emit(Resource.Error("Failed to load news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun createNews(
        title: String,
        content: String,
        videoId: Long?,
        imageFile: File?
    ): Flow<Resource<Long>> = flow {
        emit(Resource.Loading())
        try {
            val imagePart = imageFile?.let {
                val requestFile = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", it.name, requestFile)
            }

            val response = apiService.createNews(title.toRequestBody(), content.toRequestBody(), videoId, imagePart)
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.id))
            } else {
                emit(Resource.Error("Failed to create news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun updateNews(
        id: Long,
        title: String,
        content: String,
        videoId: Long?,
        removeVideo: Boolean,
        imageFile: File?,
        removeImage: Boolean
    ): Flow<Resource<News>> = flow {
        emit(Resource.Loading())
        try {
            val imagePart = imageFile?.let {
                val requestFile = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", it.name, requestFile)
            }

            val response = apiService.updateNews(
                id = id,
                title = title.toRequestBody(),
                content = content.toRequestBody(),
                videoId = videoId,
                removeVideo = removeVideo,
                removeImage = removeImage,
                image = imagePart
            )
            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.toDomain()))
            } else if (response.code() == 403) {
                emit(Resource.Error("Only the author can edit this news"))
            } else {
                emit(Resource.Error("Failed to update news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun deleteNews(id: Long): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.deleteNews(id)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else if (response.code() == 403) {
                emit(Resource.Error("Only the author can delete this news"))
            } else {
                emit(Resource.Error("Failed to delete news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun likeNews(id: Long): Flow<Resource<Long>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.likeNews(id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(Resource.Success(body.likes))
            } else {
                emit(Resource.Error("Failed to like news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun unlikeNews(id: Long): Flow<Resource<Long>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.unlikeNews(id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(Resource.Success(body.likes))
            } else {
                emit(Resource.Error("Failed to unlike news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun getLikedNewsIds(): Flow<Resource<Set<Long>>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getLikedNewsIds()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(Resource.Success(body.likedIds.toSet()))
            } else {
                emit(Resource.Error("Failed to load liked news: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun registerView(id: Long) {
        try {
            apiService.registerNewsView(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Просмотры не критичны для экрана: молча игнорируем.
        }
    }

    override suspend fun getComments(
        newsId: Long,
        before: Long?,
        size: Int
    ): Flow<Resource<NewsCommentsPage>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.getNewsComments(newsId, before, size)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(Resource.Success(body.toDomain()))
            } else {
                emit(Resource.Error("Failed to load comments: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun addComment(newsId: Long, content: String): Flow<Resource<NewsComment>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.addNewsComment(
                newsId,
                CreateNewsCommentRequest(content)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                emit(Resource.Success(body.toDomain()))
            } else {
                emit(Resource.Error("Failed to add comment: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun deleteComment(newsId: Long, commentId: Long): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading())
        try {
            val response = apiService.deleteNewsComment(newsId, commentId)
            if (response.isSuccessful) {
                emit(Resource.Success(Unit))
            } else if (response.code() == 403) {
                emit(Resource.Error("Only the comment or news author can delete this comment"))
            } else {
                emit(Resource.Error("Failed to delete comment: ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }

    override suspend fun reportNews(id: Long, reason: String): Boolean {
        return try {
            apiService.reportContent(
                com.example.muzea.data.model.ContentReportRequest(
                    contentType = "NEWS",
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

    override suspend fun reportComment(commentId: Long, reason: String): Boolean {
        return try {
            apiService.reportContent(
                com.example.muzea.data.model.ContentReportRequest(
                    contentType = "NEWS_COMMENT",
                    contentId = commentId,
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
