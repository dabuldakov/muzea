package com.example.muzea.data.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProgressRequestBodyTest {

    @Test
    fun `writeTo streams the file and reports progress up to 100`() {
        val file = File.createTempFile("upload", ".bin").apply {
            writeBytes(ByteArray(250 * 1024))
            deleteOnExit()
        }
        val progress = mutableListOf<Int>()
        val body = ProgressRequestBody(file, "application/octet-stream".toMediaTypeOrNull()) {
            progress += it
        }
        val sink = Buffer()

        body.writeTo(sink)

        assertEquals("application/octet-stream", body.contentType().toString())
        assertEquals(file.length(), body.contentLength())
        assertEquals(file.length(), sink.size)
        assertEquals(100, progress.last())
        assertTrue(progress.all { it in 0..100 })
    }

    @Test
    fun `empty file does not emit progress`() {
        val file = File.createTempFile("empty", ".bin").apply { deleteOnExit() }
        val progress = mutableListOf<Int>()
        val body = ProgressRequestBody(file, null) { progress += it }

        body.writeTo(Buffer())

        assertEquals(0L, body.contentLength())
        assertTrue(progress.isEmpty())
    }
}
