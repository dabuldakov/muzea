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
        @Part("title") title: String,
        @Part("description") description: String?,
        @Part file: MultipartBody.Part
    ): Response<VideoResponse>

    @GET("/api/videos/stream/{fileName}")
    @Streaming
    suspend fun streamVideo(
        @Path("fileName") fileName: String
    ): Response<okhttp3.ResponseBody>

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

    @POST("/api/news")
    @FormUrlEncoded
    suspend fun createNews(
        @Field("title") title: String,
        @Field("content") content: String,
        @Field("videoId") videoId: Long?
    ): Response<NewsResponse>

    // User endpoints
    @GET("/api/users/me")
    suspend fun getCurrentUser(): Response<UserResponse>

    @PUT("/api/users/{id}")
    suspend fun updateUser(
        @Path("id") id: Long,
        @Body request: UpdateUserRequest
    ): Response<UserResponse>

    @GET
    @Streaming
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}