package com.example.muzea.data.repository

import com.example.muzea.core.DefaultDispatcherProvider
import com.example.muzea.core.DispatcherProvider
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.mapper.toDomain
import com.example.muzea.data.model.AvatarResponse
import com.example.muzea.domain.model.Avatar
import com.example.muzea.core.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [com.example.muzea.domain.repository.AvatarRepository]. */
@Singleton
class AvatarRepositoryImpl @Inject constructor(
    private val apiService: ChatApiService,
    private val chatAuthManager: ChatAuthManager,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : com.example.muzea.domain.repository.AvatarRepository {

    override suspend fun loadAvatar(): Flow<Resource<Avatar>> = avatarRequest {
        val response = chatAuthManager.authenticatedRequest { apiService.getMyProfile() }
        check(response.isSuccessful && response.body() != null) { "Could not load avatar (${response.code()})" }
        Avatar(response.body()!!.avatarUrl)
    }

    override suspend fun uploadAvatar(file: File, mimeType: String): Flow<Resource<Avatar>> = avatarRequest {
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mimeType.toMediaType()))
        val response = chatAuthManager.authenticatedRequest { apiService.uploadAvatar(part) }
        check(response.isSuccessful && response.body()?.avatarUrl != null) {
            "Could not upload avatar (${response.code()}). Use a JPEG or PNG image up to 5 MB."
        }
        response.body()!!.toDomain()
    }

    override suspend fun deleteAvatar(): Flow<Resource<Avatar>> = avatarRequest {
        val response = chatAuthManager.authenticatedRequest { apiService.deleteAvatar() }
        check(response.isSuccessful) { "Could not delete avatar (${response.code()})" }
        AvatarResponse(null).toDomain()
    }

    override suspend fun uploadChatAvatar(
        chatUuid: String,
        file: File,
        mimeType: String
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        try {
            if (!chatAuthManager.isAuthenticated()) {
                emit(Resource.Error(chatAuthManager.authFailureMessage()))
                return@flow
            }

            val part = MultipartBody.Part.createFormData(
                "file", file.name, file.asRequestBody(mimeType.toMediaType())
            )
            var response = apiService.uploadChatAvatar(chatUuid, part)
            if (response.code() == 401) {
                chatAuthManager.invalidate()
                if (chatAuthManager.isAuthenticated()) {
                    response = apiService.uploadChatAvatar(chatUuid, part)
                }
            }

            if (response.isSuccessful && response.body() != null) {
                emit(Resource.Success(response.body()!!.string().trim()))
            } else {
                emit(
                    Resource.Error(
                        "Failed to upload avatar (${response.code()}). Use a JPEG or PNG image up to 5 MB."
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error("Network error: ${e.message}"))
        }
    }.flowOn(dispatchers.io)

    private fun avatarRequest(action: suspend () -> Avatar): Flow<Resource<Avatar>> = flow {
        emit(Resource.Loading())
        try {
            emit(Resource.Success(action()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Avatar request failed"))
        }
    }.flowOn(dispatchers.io)
}
