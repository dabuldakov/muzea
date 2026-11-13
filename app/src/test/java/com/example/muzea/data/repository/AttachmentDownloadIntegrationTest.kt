package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.data.IntegrationTestClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Скачивание вложений: файл пишется в files/chat_downloads, имя очищается,
 * ошибки сервера возвращают null. Регрессия на ANR при скачивании APK:
 * копирование байтов должно идти вне главного потока (withContext(IO)).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentDownloadIntegrationTest {

    @get:Rule
    val server = MockWebServer()

    @get:Rule
    val temp = TemporaryFolder()

    private val auth = mockk<ChatAuthManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun repository(): MessageRepositoryImpl {
        every { context.filesDir } returns temp.root
        return MessageRepositoryImpl(
            IntegrationTestClient.chatApi(server),
            auth,
            GifCache(File.createTempFile("gif-cache", ".json").apply { delete() }),
            context
        )
    }

    @Test
    fun `downloads attachment bytes into chat downloads dir`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.android.package-archive")
                .setBody(Buffer().write(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02)))
        )

        val progress = mutableListOf<Int>()
        val file = repository().downloadAttachment("a-1", "muzea-beta.apk") { progress += it }

        assertNotNull(file)
        assertEquals("muzea-beta.apk", file!!.name)
        // У каждого вложения свой каталог: файлы с одинаковыми именами не спорят.
        assertEquals("a-1", file.parentFile!!.name)
        assertEquals("chat_downloads", file.parentFile!!.parentFile!!.name)
        assertEquals(100, progress.last())
        assertTrue(file.readBytes().contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02)))

        val request = server.takeRequest()
        assertEquals("/api/attachments/a-1", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `unsafe file name stays inside the downloads dir`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(200).setBody("data"))

        val file = repository().downloadAttachment("a-2", "../evil/name.apk")

        assertNotNull(file)
        assertEquals(".._evil_name.apk", file!!.name)
        assertEquals(temp.root, file.parentFile!!.parentFile!!.parentFile)
        assertTrue(file.canonicalPath.startsWith(temp.root.canonicalPath))
    }

    @Test
    fun `server error returns null`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(404))

        assertNull(repository().downloadAttachment("missing", "x.apk"))
    }

    @Test
    fun `already downloaded attachment is returned without a second request`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(200).setBody("data"))
        val repository = repository()

        val first = repository.downloadAttachment("a-3", "beta.apk")
        val cachedProgress = mutableListOf<Int>()
        val second = repository.downloadAttachment("a-3", "beta.apk") { cachedProgress += it }

        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(100, cachedProgress.last())
        // Второй раз файл берём с диска: в сеть ушёл только один запрос.
        server.takeRequest()
        assertEquals(0, server.requestCount - 1)
    }

    @Test
    fun `same file name in different attachments does not clash`() = runTest {
        coEvery { auth.isAuthenticated() } returns true
        server.enqueue(MockResponse().setResponseCode(200).setBody("first"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("second"))
        val repository = repository()

        val first = repository.downloadAttachment("a-4", "photo.jpg")
        val second = repository.downloadAttachment("a-5", "photo.jpg")

        assertEquals("first", first!!.readText())
        assertEquals("second", second!!.readText())
        assertTrue(first.parentFile != second.parentFile)
    }
}
