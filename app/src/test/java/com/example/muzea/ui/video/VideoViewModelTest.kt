package com.example.muzea.ui.video

import com.example.muzea.domain.model.Video
import com.example.muzea.domain.repository.VideoRepository
import com.example.muzea.core.Resource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @org.junit.Before
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

    @Test
    fun `loadVideos keeps only own videos`() = runTest {
        val feed = listOf(
            video(1, "dabuldakov"),
            video(2, "maria"),
            video(3, "stranger"),
            video(4, "vovan")
        )
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Success(feed))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertEquals(listOf(4L), viewModel.feedState.value.videos.map { it.id })
    }

    @Test
    fun `loadVideos shows nothing when own username is unknown`() = runTest {
        val feed = listOf(video(1, "stranger"), video(2, "vovan"))
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Success(feed))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = null)

        assertTrue(viewModel.feedState.value.videos.isEmpty())
    }

    @Test
    fun `loadVideos trims own username`() = runTest {
        val feed = listOf(video(1, "stranger"), video(2, "vovan"))
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Success(feed))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "  vovan  ")

        assertEquals(listOf(2L), viewModel.feedState.value.videos.map { it.id })
    }

    @Test
    fun `loadVideos exposes error when video fetch fails`() = runTest {
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Error("network down"))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertEquals("network down", viewModel.feedState.value.error)
        assertTrue(viewModel.feedState.value.videos.isEmpty())
    }

    @Test
    fun `consumed error is cleared`() = runTest {
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Error("boom"))

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")
        viewModel.consumeFeedError()

        assertNull(viewModel.feedState.value.error)
    }

    @Test
    fun `cached videos are shown before the network result`() = runTest {
        every { videoRepository.cachedVideos() } returns listOf(video(7, "vovan"))
        coEvery { videoRepository.getVideos() } returns flowOf(Resource.Loading())

        val viewModel = VideoViewModel(videoRepository)
        viewModel.loadVideos(ownUsername = "vovan")

        assertEquals(listOf(7L), viewModel.feedState.value.videos.map { it.id })
    }
}
