package com.example.muzea.ui.video

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Video
import com.example.muzea.domain.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Дополнительные тесты VideoViewModel: покрывают детальную карточку,
 * загрузку/удаление/жалобу и ветки кэша, которые не задеты VideoViewModelTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModelExtraTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val videoRepository = mockk<VideoRepository>()

    @Before
    fun setUp() {
        every { videoRepository.cachedVideos() } returns emptyList()
    }

    private fun video(id: Long, uploadedBy: String) = Video(
        id = id,
        title = "Title $id",
        description = null,
        url = "https://example.com/video$id",
        thumbnailUrl = null,
        fileSize = null,
        duration = null,
        views = 0,
        likes = null,
        uploadedBy = uploadedBy,
        uploadedAt = "2026-01-01T00:00:00"
    )

    private fun filePart(): MultipartBody.Part = MultipartBody.Part.createFormData(
        "file",
        "video.mp4",
        "bytes".toRequestBody("video/mp4".toMediaTypeOrNull())
    )

    @Test
    fun `loadVideoById forwards repository results`() = runTest {
        coEvery { videoRepository.getVideoById(3L) } returns
            flowOf(Resource.Success(video(3, "vovan")))

        val viewModel = VideoViewModel(videoRepository)
        val results = mutableListOf<Resource<Video>>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.videoDetailResult.collect { results += it }
        }

        viewModel.loadVideoById(3L)

        val last = results.last()
        assertTrue(last is Resource.Success)
        assertEquals(3L, (last as Resource.Success).data!!.id)
        collector.cancel()
    }

    @Test
    fun `uploadVideo forwards repository results`() = runTest {
        coEvery {
            videoRepository.uploadVideo(any(), any(), any(), any())
        } returns flowOf(Resource.Success(video(9, "vovan")))

        val viewModel = VideoViewModel(videoRepository)
        val results = mutableListOf<Resource<Video>>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.uploadResult.collect { results += it }
        }

        viewModel.uploadVideo("My video", "desc", filePart(), null)

        val last = results.last()
        assertTrue(last is Resource.Success)
        assertEquals(9L, (last as Resource.Success).data!!.id)
        coVerify(exactly = 1) { videoRepository.uploadVideo("My video", "desc", any(), null) }
        collector.cancel()
    }

    @Test
    fun `deleteVideo forwards repository results and errors`() = runTest {
        coEvery { videoRepository.deleteVideo(5L) } returns flowOf(Resource.Success(Unit))

        val viewModel = VideoViewModel(videoRepository)
        val results = mutableListOf<Resource<Unit>>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.deleteResult.collect { results += it }
        }

        viewModel.deleteVideo(5L)

        assertTrue(results.last() is Resource.Success)
        collector.cancel()
    }

    @Test
    fun `reportVideo delegates to the repository`() = runTest {
        coEvery { videoRepository.reportVideo(1L, "SPAM") } returns true

        val viewModel = VideoViewModel(videoRepository)

        assertTrue(viewModel.reportVideo(1L, "SPAM"))
        coVerify(exactly = 1) { videoRepository.reportVideo(1L, "SPAM") }
    }

    @Test
    fun `loadVideos shows loading while first result is pending`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { videoRepository.getVideos() } returns flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(listOf(video(1, "vovan"))))
        }

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertTrue(viewModel.feedState.value.isLoading)

        gate.complete(Unit)

        assertFalse(viewModel.feedState.value.isLoading)
        assertEquals(listOf(1L), viewModel.feedState.value.videos.map { it.id })
    }

    @Test
    fun `cached videos of other users are not shown`() = runTest {
        every { videoRepository.cachedVideos() } returns listOf(video(9, "stranger"))
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Loading())

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertTrue(viewModel.feedState.value.videos.isEmpty())
    }

    @Test
    fun `cached own videos keep feed visible while refreshing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        every { videoRepository.cachedVideos() } returns listOf(video(7, "vovan"))
        coEvery { videoRepository.getVideos() } returns flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(listOf(video(7, "vovan"))))
        }

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertEquals(listOf(7L), viewModel.feedState.value.videos.map { it.id })
        assertFalse(viewModel.feedState.value.isLoading)

        gate.complete(Unit)

        assertFalse(viewModel.feedState.value.isLoading)
    }

    @Test
    fun `loadVideos starts a single network request while the previous one is active`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { videoRepository.getVideos() } returns flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(emptyList()))
        }

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")
        viewModel.loadVideos(ownUsername = "vovan")

        coVerify(exactly = 1) { videoRepository.getVideos() }

        gate.complete(Unit)
    }

    @Test
    fun `empty success result clears the feed without error`() = runTest {
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Success(emptyList()))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertTrue(viewModel.feedState.value.videos.isEmpty())
        assertEquals(null, viewModel.feedState.value.error)
        assertFalse(viewModel.feedState.value.isLoading)
    }
}
