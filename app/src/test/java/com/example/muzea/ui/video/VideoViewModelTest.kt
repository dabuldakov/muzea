package com.example.muzea.ui.video

import com.example.muzea.data.model.VideoResponse
import com.example.muzea.data.repository.VideoRepository
import com.example.muzea.utils.NetworkResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val videoRepository = mockk<VideoRepository>()

    private fun video(id: Long, uploadedBy: String) = VideoResponse(
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

    @Test
    fun `loadVideos emits only own videos`() = runTest {
        val feed = listOf(
            video(1, "dabuldakov"),
            video(2, "maria"),
            video(3, "stranger"),
            video(4, "vovan")
        )
        coEvery { videoRepository.getVideos() } returns flowOf(NetworkResult.Success(feed))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        val success = viewModel.videosResult.first()
        assertTrue(success is NetworkResult.Success)
        assertEquals(listOf(4L), success.data!!.map { it.id })
    }

    @Test
    fun `loadVideos shows nothing when own username is unknown`() = runTest {
        val feed = listOf(video(1, "stranger"), video(2, "vovan"))
        coEvery { videoRepository.getVideos() } returns flowOf(NetworkResult.Success(feed))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = null)

        val success = viewModel.videosResult.first()
        assertTrue(success is NetworkResult.Success)
        assertTrue(success.data!!.isEmpty())
    }

    @Test
    fun `loadVideos trims own username`() = runTest {
        val feed = listOf(video(1, "stranger"), video(2, "vovan"))
        coEvery { videoRepository.getVideos() } returns flowOf(NetworkResult.Success(feed))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "  vovan  ")

        val success = viewModel.videosResult.first()
        assertTrue(success is NetworkResult.Success)
        assertEquals(listOf(2L), success.data!!.map { it.id })
    }

    @Test
    fun `loadVideos emits error when video fetch fails`() = runTest {
        coEvery { videoRepository.getVideos() } returns flowOf(NetworkResult.Error("network down"))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertTrue(viewModel.videosResult.first() is NetworkResult.Error)
    }
}
