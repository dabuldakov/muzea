package com.example.muzea.data.repository

import com.example.muzea.core.Resource
import com.example.muzea.data.api.ApiService
import com.example.muzea.data.httpError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Ветки ошибок/отмены бэкенд-репозиториев новостей, пользователя и видео —
 * ровно те строки, которые не покрывают существующие happy-path тесты.
 */
class BackendRepositoryErrorPathsTest {

    private suspend fun cancels(block: suspend () -> Unit): Boolean =
        try {
            block()
            false
        } catch (e: CancellationException) {
            true
        }

    // ============================ NewsRepositoryImpl ============================

    private val newsApi = mockk<ApiService>()
    private val news = NewsRepositoryImpl(newsApi)

    @Test
    fun getNewsReportsANetworkError() = runTest {
        coEvery { newsApi.getNews(0, 20) } throws IOException("offline")

        val last = news.getNews().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun getNewsByIdReportsAFailure() = runTest {
        coEvery { newsApi.getNewsById(1L) } returns httpError(500)

        val last = news.getNewsById(1L).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load news"))
    }

    @Test
    fun getNewsByIdPropagatesCancellation() = runTest {
        coEvery { newsApi.getNewsById(1L) } throws CancellationException("stop")

        assertTrue(cancels { news.getNewsById(1L).toList() })
    }

    @Test
    fun createNewsPropagatesCancellation() = runTest {
        coEvery { newsApi.createNews(any(), any(), any(), any()) } throws
            CancellationException("stop")

        assertTrue(cancels { news.createNews("t", "c", null, null).toList() })
    }

    @Test
    fun createNewsReportsANetworkError() = runTest {
        coEvery { newsApi.createNews(any(), any(), any(), any()) } throws IOException("offline")

        val last = news.createNews("t", "c", null, null).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun deleteNewsReportsAGenericFailure() = runTest {
        coEvery { newsApi.deleteNews(1L) } returns httpError(500)

        val last = news.deleteNews(1L).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to delete news"))
    }

    @Test
    fun deleteNewsPropagatesCancellation() = runTest {
        coEvery { newsApi.deleteNews(1L) } throws CancellationException("stop")

        assertTrue(cancels { news.deleteNews(1L).toList() })
    }

    @Test
    fun deleteNewsReportsANetworkError() = runTest {
        coEvery { newsApi.deleteNews(1L) } throws IOException("offline")

        val last = news.deleteNews(1L).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun reportNewsPropagatesCancellation() = runTest {
        coEvery { newsApi.reportContent(any()) } throws CancellationException("stop")

        assertTrue(cancels { news.reportNews(1L, "spam") })
    }

    // ============================ UserRepositoryImpl ============================

    private val userApi = mockk<ApiService>()
    private val users = UserRepositoryImpl(userApi)

    @Test
    fun getCurrentUserPropagatesCancellation() = runTest {
        coEvery { userApi.getCurrentUser() } throws CancellationException("stop")

        assertTrue(cancels { users.getCurrentUser().toList() })
    }

    @Test
    fun updateUserPropagatesCancellation() = runTest {
        coEvery { userApi.updateUser(any(), any()) } throws CancellationException("stop")

        assertTrue(cancels { users.updateUser(7L, "V", true).toList() })
    }

    @Test
    fun updateUserReportsANetworkError() = runTest {
        coEvery { userApi.updateUser(any(), any()) } throws IOException("offline")

        val last = users.updateUser(7L, "V", true).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun deleteAccountPropagatesCancellation() = runTest {
        coEvery { userApi.deleteUser(7L) } throws CancellationException("stop")

        assertTrue(cancels { users.deleteAccount(7L).toList() })
    }

    @Test
    fun deleteAccountReportsANetworkError() = runTest {
        coEvery { userApi.deleteUser(7L) } throws IOException("offline")

        val last = users.deleteAccount(7L).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    // ============================ VideoRepositoryImpl ===========================

    private val videoApi = mockk<ApiService>()
    private val videos = VideoRepositoryImpl(videoApi)

    @Test
    fun getVideosPropagatesCancellation() = runTest {
        coEvery { videoApi.getVideos() } throws CancellationException("stop")

        assertTrue(cancels { videos.getVideos().toList() })
    }

    @Test
    fun getVideosReportsANetworkError() = runTest {
        coEvery { videoApi.getVideos() } throws IOException("offline")
        VideoListCache.clear()

        val last = videos.getVideos().toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun getVideoByIdReportsAFailure() = runTest {
        coEvery { videoApi.getVideoById(1L) } returns httpError(500)

        val last = videos.getVideoById(1L).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Failed to load video"))
    }

    @Test
    fun getVideoByIdPropagatesCancellation() = runTest {
        coEvery { videoApi.getVideoById(1L) } throws CancellationException("stop")

        assertTrue(cancels { videos.getVideoById(1L).toList() })
    }

    @Test
    fun uploadVideoPropagatesCancellation() = runTest {
        coEvery { videoApi.uploadVideo(any(), any(), any(), any()) } throws
            CancellationException("stop")

        assertTrue(
            cancels {
                videos.uploadVideo("t", null, mockk<MultipartBody.Part>(), null).toList()
            }
        )
    }

    @Test
    fun uploadVideoReportsANetworkError() = runTest {
        coEvery { videoApi.uploadVideo(any(), any(), any(), any()) } throws IOException("offline")

        val last = videos.uploadVideo("t", null, mockk<MultipartBody.Part>(), null).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun deleteVideoPropagatesCancellation() = runTest {
        coEvery { videoApi.deleteVideo(1L) } throws CancellationException("stop")

        assertTrue(cancels { videos.deleteVideo(1L).toList() })
    }

    @Test
    fun deleteVideoReportsANetworkError() = runTest {
        coEvery { videoApi.deleteVideo(1L) } throws IOException("offline")

        val last = videos.deleteVideo(1L).toList().last()

        assertTrue((last as Resource.Error).message!!.startsWith("Network error"))
    }

    @Test
    fun reportVideoPropagatesCancellation() = runTest {
        coEvery { videoApi.reportContent(any()) } throws CancellationException("stop")

        assertTrue(cancels { videos.reportVideo(1L, "spam") })
    }
}
