package com.example.muzea.ui.profile

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<AvatarRepository>()

    @Test
    fun `loads the avatar on creation`() = runTest {
        coEvery { repository.loadAvatar() } returns flowOf(Resource.Success(Avatar("https://a/1.jpg")))

        val viewModel = AvatarViewModel(repository)

        assertEquals("https://a/1.jpg", (viewModel.state.value as Resource.Success).data!!.avatarUrl)
    }

    @Test
    fun `delete publishes the repository result`() = runTest {
        coEvery { repository.loadAvatar() } returns flowOf(Resource.Success(Avatar("https://a/1.jpg")))
        coEvery { repository.deleteAvatar() } returns flowOf(Resource.Success(Avatar(null)))
        val viewModel = AvatarViewModel(repository)

        viewModel.delete()

        assertEquals(null, (viewModel.state.value as Resource.Success).data!!.avatarUrl)
    }

    @Test
    fun `upload deletes the temporary file after completion`() = runTest {
        coEvery { repository.loadAvatar() } returns flowOf(Resource.Success(Avatar(null)))
        coEvery { repository.uploadAvatar(any(), any()) } returns
            flowOf(Resource.Success(Avatar("https://a/2.jpg")))
        val viewModel = AvatarViewModel(repository)
        val file = File.createTempFile("avatar", ".jpg").apply { deleteOnExit() }

        viewModel.upload(file, "image/jpeg")

        assertFalse(file.exists())
        coVerify(exactly = 1) { repository.uploadAvatar(file, "image/jpeg") }
    }

    @Test
    fun `upload while busy deletes the file without a request`() = runTest {
        // Загрузка профиля «висит», поэтому busy остаётся true.
        coEvery { repository.loadAvatar() } returns flow {
            emit(Resource.Loading())
            awaitCancellation()
        }
        val viewModel = AvatarViewModel(repository)
        val file = File.createTempFile("avatar", ".jpg").apply { deleteOnExit() }

        viewModel.upload(file, "image/jpeg")

        assertFalse(file.exists())
        coVerify(exactly = 0) { repository.uploadAvatar(any(), any()) }
    }

    @Test
    fun `state reflects a failed upload`() = runTest {
        coEvery { repository.loadAvatar() } returns flowOf(Resource.Success(Avatar(null)))
        coEvery { repository.uploadAvatar(any(), any()) } returns flowOf(Resource.Error("boom"))
        val viewModel = AvatarViewModel(repository)
        val file = File.createTempFile("avatar", ".jpg").apply { deleteOnExit() }

        viewModel.upload(file, "image/jpeg")

        assertTrue(viewModel.state.value is Resource.Error)
    }
}
