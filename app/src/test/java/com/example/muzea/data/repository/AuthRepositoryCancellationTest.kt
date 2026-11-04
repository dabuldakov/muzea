package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.api.AuthServiceApi
import com.example.muzea.data.model.AuthServiceMessage
import com.example.muzea.utils.TokenManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Ветки [AuthRepository], которые не покрыты happy-path тестами: проброс
 * отмены в запросах к IdP и устойчивость к поломанному errorBody.
 */
class AuthRepositoryCancellationTest {

    private val context = mockk<Context>()
    private val api = mockk<AuthServiceApi>()
    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        every { context.getString(R.string.error_no_connection) } returns "no connection"
        every { context.getString(R.string.error_generic, *anyVararg()) } returns "generic"
        every { tokenManager.getDeviceId() } returns "device-1"
        repository = AuthRepository(api, tokenManager, context)
    }

    private suspend fun cancels(block: suspend () -> Unit): Boolean =
        try {
            block()
            false
        } catch (e: CancellationException) {
            true
        }

    @Test
    fun getCurrentAuthUserPropagatesCancellation() = runTest {
        coEvery { api.me() } throws CancellationException("stop")

        assertTrue(cancels { repository.getCurrentAuthUser() })
    }

    @Test
    fun requestPropagatesCancellation() = runTest {
        coEvery { api.requestLoginCode(any()) } throws CancellationException("stop")

        assertTrue(cancels { repository.requestLoginCode("a@b.c").toList() })
    }

    @Test
    fun tokenRequestPropagatesCancellation() = runTest {
        coEvery { api.verifyLoginCode(any()) } throws CancellationException("stop")

        assertTrue(cancels { repository.verifyLoginCode("a@b.c", "1234").toList() })
    }

    @Test
    fun tokenRequestReportsNoConnectionOnFailure() = runTest {
        coEvery { api.verifyLoginCode(any()) } throws IOException("offline")

        val last = repository.verifyLoginCode("a@b.c", "1234").toList().last()

        assertEquals("no connection", (last as Resource.Error).message)
    }

    @Test
    fun aBrokenErrorBodyFallsBackToTheGenericMessage() = runTest {
        val body = mockk<ResponseBody>()
        every { body.string() } throws IOException("broken stream")
        val response = mockk<Response<AuthServiceMessage>>()
        every { response.isSuccessful } returns false
        every { response.errorBody() } returns body
        every { response.code() } returns 503
        every { response.message() } returns "err"
        coEvery { api.requestLoginCode(any()) } returns response

        val last = repository.requestLoginCode("a@b.c").toList().last()

        assertEquals("generic", (last as Resource.Error).message)
    }
}
