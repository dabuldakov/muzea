package com.example.muzea.data.api

import com.example.muzea.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

@Suppress("TooManyFunctions")
interface ApiService {

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

    /** Шаг 1 прямой загрузки: presigned PUT в MinIO. */
    @POST("/api/videos/presign-upload")
    suspend fun presignVideoUpload(
        @Body request: PresignUploadRequest
    ): Response<PresignUploadResponse>

    /** Шаг 2: сообщаем objectKey после успешной загрузки в MinIO. */
    @POST("/api/videos")
    suspend fun finalizeVideoUpload(
        @Body request: FinalizeVideoUploadRequest
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

    @Multipart
    @PUT("/api/news/{id}")
    suspend fun updateNews(
        @Path("id") id: Long,
        @Part("title") title: okhttp3.RequestBody,
        @Part("content") content: okhttp3.RequestBody,
        @Part("videoId") videoId: Long?,
        @Part("removeVideo") removeVideo: Boolean,
        @Part("removeImage") removeImage: Boolean,
        @Part image: MultipartBody.Part?
    ): Response<NewsResponse>

    @DELETE("/api/news/{id}")
    suspend fun deleteNews(
        @Path("id") id: Long
    ): Response<Unit>

    @POST("/api/news/{id}/like")
    suspend fun likeNews(
        @Path("id") id: Long
    ): Response<NewsLikeResponse>

    @DELETE("/api/news/{id}/like")
    suspend fun unlikeNews(
        @Path("id") id: Long
    ): Response<NewsLikeResponse>

    @GET("/api/news/likes/me")
    suspend fun getLikedNewsIds(): Response<LikedNewsResponse>

    @POST("/api/news/{id}/view")
    suspend fun registerNewsView(
        @Path("id") id: Long
    ): Response<Unit>

    @GET("/api/news/{id}/comments")
    suspend fun getNewsComments(
        @Path("id") newsId: Long,
        @Query("before") before: Long?,
        @Query("size") size: Int
    ): Response<NewsCommentsResponse>

    @POST("/api/news/{id}/comments")
    suspend fun addNewsComment(
        @Path("id") newsId: Long,
        @Body request: CreateNewsCommentRequest
    ): Response<NewsCommentResponse>

    @DELETE("/api/news/{id}/comments/{commentId}")
    suspend fun deleteNewsComment(
        @Path("id") newsId: Long,
        @Path("commentId") commentId: Long
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

    /** Пожаловаться на новость или видео (UGC). */
    @POST("/api/reports")
    suspend fun reportContent(
        @Body request: ContentReportRequest
    ): Response<Unit>

    @GET
    @Streaming
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}