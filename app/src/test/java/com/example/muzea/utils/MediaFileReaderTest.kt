package com.example.muzea.utils

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.InvocationTargetException

/**
 * Покрывает чистые ветки [MediaFileReader] и подготовку файла.
 *
 * ContentResolver/Context подменяются MockK, чтобы не зависеть от реального
 * Android-рантайма и не трогать ParcelFileDescriptor (требует --add-opens).
 * Robolectric не используется: он не даёт покрытия для JaCoCo.
 */
class MediaFileReaderTest {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var filesDir: File
    private lateinit var reader: MediaFileReader

    @Before
    fun setUp() {
        filesDir = createTempDir("media-reader")
        context = mockk(relaxed = true)
        resolver = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.contentResolver } returns resolver
        every { context.filesDir } returns filesDir
        every { context.getString(any()) } returns "error"
        reader = MediaFileReader(context)
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    private fun invokePrivate(name: String, vararg args: Any?): Any? {
        val method = MediaFileReader::class.java.declaredMethods
            .first { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true
        return try {
            method.invoke(reader, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun cursor(name: String, size: Long?): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 1
        every { cursor.getString(0) } returns name
        every { cursor.isNull(1) } returns (size == null)
        if (size != null) every { cursor.getLong(1) } returns size
        return cursor
    }

    private fun emptyCursor(): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns false
        return cursor
    }

    private fun cursorWithoutColumns(): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(any()) } returns -1
        return cursor
    }

    private fun imageUri(name: String = "photo.png"): Uri {
        val uri = mockk<Uri>()
        every { uri.lastPathSegment } returns name
        return uri
    }

    // --- чистые хелперы ------------------------------------------------------------------------

    @Test
    fun `typeOf classifies mime prefixes`() {
        assertEquals("IMAGE", invokePrivate("typeOf", "image/png", "a.png"))
        assertEquals("VIDEO", invokePrivate("typeOf", "video/mp4", "a.mp4"))
        assertEquals("AUDIO", invokePrivate("typeOf", "audio/mpeg", "a.mp3"))
        assertEquals("DOCUMENT", invokePrivate("typeOf", "application/pdf", "a.pdf"))
    }

    @Test
    fun `typeOf rejects a document with a disallowed extension`() {
        val error = runCatching {
            invokePrivate("typeOf", "application/octet-stream", "a.exe")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `checkSize accepts files at the limit and rejects larger ones`() {
        invokePrivate("checkSize", "IMAGE", 50L * 1024 * 1024)
        invokePrivate("checkSize", "VIDEO", 200L * 1024 * 1024)
        invokePrivate("checkSize", "AUDIO", 50L * 1024 * 1024)
        invokePrivate("checkSize", "DOCUMENT", 100L * 1024 * 1024)

        assertTrue(runCatching { invokePrivate("checkSize", "IMAGE", 50L * 1024 * 1024 + 1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { invokePrivate("checkSize", "VIDEO", 200L * 1024 * 1024 + 1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { invokePrivate("checkSize", "AUDIO", 50L * 1024 * 1024 + 1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { invokePrivate("checkSize", "DOCUMENT", 100L * 1024 * 1024 + 1) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `isAllowedDocument checks the extension whitelist`() {
        assertEquals(true, invokePrivate("isAllowedDocument", "report.pdf"))
        assertEquals(true, invokePrivate("isAllowedDocument", "notes.TXT"))
        assertEquals(false, invokePrivate("isAllowedDocument", "virus.exe"))
        assertEquals(false, invokePrivate("isAllowedDocument", "noext"))
    }

    @Test
    fun `sanitizeName replaces path and control characters`() {
        assertEquals("dir_file_name_.txt", invokePrivate("sanitizeName", "dir/file:name?.txt"))
        assertEquals("file", invokePrivate("sanitizeName", "   "))
        assertEquals(120, (invokePrivate("sanitizeName", "a".repeat(200)) as String).length)
    }

    @Test
    fun `mimeByExtension resolves known extensions only`() {
        assertEquals("image/png", invokePrivate("mimeByExtension", "a.PNG"))
        assertEquals("video/mp4", invokePrivate("mimeByExtension", "a.mp4"))
        assertNull(invokePrivate("mimeByExtension", "noext"))
        assertNull(invokePrivate("mimeByExtension", null))
    }

    // --- prepare ------------------------------------------------------------------------------

    @Test
    fun `prepare copies an image and reports its metadata`() = runTest {
        val uri = imageUri("photo.png")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(uri, null, null, null, null) } returns cursor("photo.png", 3L)
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))

        val prepared = reader.prepare(uri)

        assertEquals("IMAGE", prepared.type)
        assertTrue(prepared.name.endsWith("photo.png"))
        assertTrue(prepared.file.exists())
        assertEquals(3L, prepared.size)
        reader.cleanup(prepared)
        assertFalse(prepared.file.exists())
    }

    @Test
    fun `prepare handles a text document`() = runTest {
        val uri = imageUri("notes.txt")
        every { resolver.getType(uri) } returns "text/plain"
        every { resolver.query(uri, null, null, null, null) } returns cursor("notes.txt", 5L)
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(104, 101, 108, 108, 111))

        val prepared = reader.prepare(uri)

        assertEquals("DOCUMENT", prepared.type)
        assertEquals("text/plain", prepared.mimeType)
        assertTrue(prepared.file.exists())
        reader.cleanup(prepared)
    }

    @Test
    fun `prepare rejects a file with an unknown type`() = runTest {
        val uri = imageUri("blob")
        every { resolver.getType(uri) } returns null

        val error = runCatching { reader.prepare(uri) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `prepare infers the mime from the file extension when the resolver has none`() = runTest {
        val uri = imageUri("photo.PNG")
        every { resolver.getType(uri) } returns null
        every { resolver.query(uri, null, null, null, null) } returns null
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1))

        val prepared = reader.prepare(uri)

        assertEquals("IMAGE", prepared.type)
        assertEquals("image/png", prepared.mimeType)
    }

    @Test
    fun `prepare reads the size from an asset descriptor when metadata is missing`() = runTest {
        val uri = imageUri("photo.png")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(uri, null, null, null, null) } returns null
        val descriptor = mockk<AssetFileDescriptor>(relaxed = true)
        every { descriptor.length } returns 99L
        every { resolver.openAssetFileDescriptor(uri, "r") } returns descriptor
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1, 2))

        val prepared = reader.prepare(uri)

        // Файл реально скопирован, поэтому размер берётся из него.
        assertEquals(2L, prepared.size)
    }

    @Test
    fun `prepare rejects a file whose stream cannot be opened`() = runTest {
        val uri = imageUri("photo.png")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(uri, null, null, null, null) } returns null
        every { resolver.openAssetFileDescriptor(uri, "r") } returns null
        every { resolver.openInputStream(uri) } returns null

        val error = runCatching { reader.prepare(uri) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `prepare tolerates a failing metadata query`() = runTest {
        val uri = imageUri("photo.png")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(uri, null, null, null, null) } throws IllegalStateException("boom")
        every { resolver.openAssetFileDescriptor(uri, "r") } returns null
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1))

        val prepared = reader.prepare(uri)

        assertEquals("IMAGE", prepared.type)
    }

    @Test
    fun `prepare falls back to the path segment for an empty cursor`() = runTest {
        val uri = imageUri("photo.png")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(uri, null, null, null, null) } returns emptyCursor()
        every { resolver.openAssetFileDescriptor(uri, "r") } returns null
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1))

        val prepared = reader.prepare(uri)

        assertEquals("photo.png", prepared.name)
    }

    @Test
    fun `prepare falls back to the path segment when the cursor lacks columns`() = runTest {
        val uri = imageUri("photo.png")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(uri, null, null, null, null) } returns cursorWithoutColumns()
        every { resolver.openAssetFileDescriptor(uri, "r") } returns null
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1))

        val prepared = reader.prepare(uri)

        assertEquals("photo.png", prepared.name)
    }

    @Test
    fun `prepare handles a video and reports null metadata without a real decoder`() = runTest {
        val uri = imageUri("clip.mp4")
        every { resolver.getType(uri) } returns "video/mp4"
        every { resolver.query(uri, null, null, null, null) } returns cursor("clip.mp4", 10L)
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))

        val prepared = reader.prepare(uri)

        assertEquals("VIDEO", prepared.type)
        assertNull(prepared.width)
        assertNull(prepared.height)
        assertNull(prepared.duration)
        assertNull(prepared.thumbnail)
    }

    @Test
    fun `prepare handles an audio file`() = runTest {
        val uri = imageUri("song.mp3")
        every { resolver.getType(uri) } returns "audio/mpeg"
        every { resolver.query(uri, null, null, null, null) } returns cursor("song.mp3", 10L)
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))

        val prepared = reader.prepare(uri)

        assertEquals("AUDIO", prepared.type)
        assertNull(prepared.duration)
    }

    @Test
    fun `prepare sweeps stale drafts from the upload dir`() = runTest {
        val uploadDir = File(filesDir, "chat_uploads").apply { mkdirs() }
        val stale = File(uploadDir, "stale.bin").apply { writeText("old") }
        stale.setLastModified(System.currentTimeMillis() - 25L * 60 * 60 * 1000)
        val fresh = File(uploadDir, "fresh.bin").apply { writeText("new") }

        val uri = imageUri("photo.png")
        every { resolver.getType(uri) } returns "image/png"
        every { resolver.query(uri, null, null, null, null) } returns cursor("photo.png", 1L)
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(byteArrayOf(1))

        reader.prepare(uri)

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
    }

    // --- cleanup ------------------------------------------------------------------------------

    @Test
    fun `cleanup ignores a null attachment`() {
        reader.cleanup(null)
    }

    @Test
    fun `cleanup deletes the attachment and its thumbnail`() {
        val file = File(filesDir, "a.bin").apply { writeText("a") }
        val thumb = File(filesDir, "t.jpg").apply { writeText("t") }
        val prepared = PreparedAttachment(file, thumb, "a.bin", 1L, "application/octet-stream", "DOCUMENT", null, null, null)

        reader.cleanup(prepared)

        assertFalse(file.exists())
        assertFalse(thumb.exists())
    }

    private fun createTempDir(prefix: String): File =
        File.createTempFile(prefix, "").let {
            it.delete()
            it.mkdirs()
            it
        }
}
