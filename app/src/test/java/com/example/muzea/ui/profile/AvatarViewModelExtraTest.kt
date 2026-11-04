package com.example.muzea.ui.profile

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Avatar
import com.example.muzea.domain.repository.AvatarRepository
import com.example.muzea.ui.news.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Дополнительные тесты AvatarViewModel: защита от параллельных запросов —
 * load/delete/upload во время активной операции не трогают репозиторий.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AvatarViewModelExtraTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<AvatarRepository>()

    @Test
    fun `load and delete are ignored while another request is in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.loadAvatar() } returns flow {
            emit(Resource.Loading())
            gate.await()
            emit(Resource.Success(Avatar(null)))
        }
        val viewModel = AvatarViewModel(repository)
        val file = File.createTempFile("avatar", ".jpg").apply { deleteOnExit() }

        viewModel.load()
        viewModel.delete()
        viewModel.upload(file, "image/jpeg")

        // Единственный поход в сеть — стартовая загрузка из init.
        coVerify(exactly = 1) { repository.loadAvatar() }
        coVerify(exactly = 0) { repository.deleteAvatar() }
        coVerify(exactly = 0) { repository.uploadAvatar(any(), any()) }
        assertFalse(file.exists())

        gate.complete(Unit)
    }

    @Test
    fun `a new request is allowed after the previous one finishes`() = runTest {
        coEvery { repository.loadAvatar() } returns
            kotlinx.coroutines.flow.flowOf(Resource.Success(Avatar("https://a/1.jpg")))
        coEvery { repository.deleteAvatar() } returns
            kotlinx.coroutines.flow.flowOf(Resource.Success(Avatar(null)))
        val viewModel = AvatarViewModel(repository)

        viewModel.delete()
        viewModel.load()

        coVerify(exactly = 2) { repository.loadAvatar() }
        coVerify(exactly = 1) { repository.deleteAvatar() }
    }
}
