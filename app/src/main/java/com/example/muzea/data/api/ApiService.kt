package com.example.muzea.data.api

import com.example.muzea.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth endpoints
    @POST("/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<AuthResponse>

    @POST("/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    // Video endpoints
    @GET("/api/videos")
    suspend fun getVideos(): Response<List<VideoResponse>>

    @GET("/api/videos/{id}")
    suspend fun getVideoById(
        @Path("id") id: Long
    ): Response<VideoResponse>

    @Multipart
    @POST("/api/videos/upload")
    suspend fun uploadVideo(
        @Part("title") title: okhttp3.RequestBody,
        @Part("description") description: okhttp3.RequestBody?,
        @Part file: MultipartBody.Part,
        @Part thumbnail: MultipartBody.Part?
    ): Response<VideoResponse>

    @GET("/api/videos/stream/{fileName}")
    @Streaming
    suspend fun streamVideo(
        @Path("fileName") fileName: String
    ): Response<okhttp3.ResponseBody>

    @DELETE("/api/videos/{id}")
    suspend fun deleteVideo(
        @Path("id") id: Long
    ): Response<Unit>

    // News endpoints
    @GET("/api/news")
    suspend fun getNews(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PageResponse<NewsResponse>>

    @GET("/api/news/{id}")
    suspend fun getNewsById(
        @Path("id") id: Long
    ): Response<NewsResponse>

    @Multipart
    @POST("/api/news")
    suspend fun createNews(
        @Part("title") title: okhttp3.RequestBody,
        @Part("content") content: okhttp3.RequestBody,
        @Part("videoId") videoId: Long?,
        @Part image: MultipartBody.Part?
    ): Response<NewsCreateResponse>

    @DELETE("/api/news/{id}")
    suspend fun deleteNews(
        @Path("id") id: Long
    ): Response<Unit>

    // User endpoints
    @GET("/api/users/me")
    suspend fun getCurrentUser(): Response<UserResponse>

    @PUT("/api/users/{id}")
    suspend fun updateUser(
        @Path("id") id: Long,
        @Body request: UpdateUserRequest
    ): Response<UserResponse>

    /** Удаление аккаунта и всех связанных персональных данных (отзыв согласия, ст. 14 ФЗ-152). */
    @DELETE("/api/users/{id}")
    suspend fun deleteUser(
        @Path("id") id: Long
    ): Response<Unit>

    @GET
    @Streaming
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}