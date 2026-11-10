package com.example.muzea.utils

import android.content.Context
import com.example.muzea.data.api.ChatApiService
import com.example.muzea.data.model.ErrorReportRequest
import com.example.muzea.data.model.ErrorReportResponse
import com.example.muzea.data.repository.ChatAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File

class ErrorReporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val prefs = FakeSharedPreferences()

    private fun context(): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns temp.root
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    private fun okResponse(): Response<ErrorReportResponse> {
        val response = mockk<Response<ErrorReportResponse>>(relaxed = true)
        every { response.isSuccessful } returns true
        return response
    }

    private fun reporter(api: ChatApiService): ErrorReporter = ErrorReporter(
        context = context(),
        api = api,
        auth = mockk<ChatAuthManager>(relaxed = true),
        tokenManager = mockk<TokenManager>(relaxed = true)
    )

    @Test
    fun `caught error goes to the buffer and uploads on next start`() = runBlocking {
        val api = mockk<ChatApiService>(relaxed = true)
        coEvery { api.uploadErrorEvents(any()) } returns okResponse()
        val reporter = reporter(api)

        ErrorReporter.logCaught("Test", IllegalStateException("boom"))
        val file = File(temp.root, "error_events.jsonl")
        assertTrue(file.length() > 0)

        val request = slot<ErrorReportRequest>()
        reporter.uploadPending()

        withTimeout(5_000) {
            while (file.length() > 0) delay(25)
        }
        coVerify { api.uploadErrorEvents(capture(request)) }
        assertEquals(1, request.captured.events.size)
        assertEquals("Test", request.captured.events.first().tag)
        assertEquals("ERROR", request.captured.events.first().level)
    }

    @Test
    fun `failed upload keeps the buffer for the next attempt`() = runBlocking {
        val api = mockk<ChatApiService>(relaxed = true)
        val failure = mockk<Response<ErrorReportResponse>>(relaxed = true)
        every { failure.isSuccessful } returns false
        coEvery { api.uploadErrorEvents(any()) } returns failure
        val reporter = reporter(api)

        ErrorReporter.logCaught("Test", IllegalStateException("boom"))
        reporter.uploadPending()
        delay(300)

        val file = File(temp.root, "error_events.jsonl")
        assertTrue(file.length() > 0)
    }

    @Test
    fun `crash handler writes the crash to the buffer`() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val caught = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> caught += error }
        try {
            val api = mockk<ChatApiService>(relaxed = true)
            val reporter = reporter(api)
            reporter.install()
            val error = RuntimeException("crash")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

            assertTrue(caught.single() === error)
            assertTrue(File(temp.root, "error_events.jsonl").length() > 0)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
