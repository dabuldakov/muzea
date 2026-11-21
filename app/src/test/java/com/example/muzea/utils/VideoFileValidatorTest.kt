package com.example.muzea.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/** Проверка обрезанных MP4/MOV до загрузки. */
class VideoFileValidatorTest {

    @Test
    fun `complete mp4 is accepted`() {
        val file = mp4(mdatSize = 108, payload = 100)

        assertFalse(VideoFileValidator.isTruncated(file))
    }

    @Test
    fun `mp4 whose mdat crosses the end of file is rejected`() {
        // Ровно наш случай: moov описывает 35 МБ, а скачалась половина.
        val file = mp4(mdatSize = 208, payload = 100)

        assertTrue(VideoFileValidator.isTruncated(file))
    }

    @Test
    fun `mdat up to the end of file is valid`() {
        val file = mp4(mdatSize = 0, payload = 100)

        assertFalse(VideoFileValidator.isTruncated(file))
    }

    @Test
    fun `non mp4 container is not checked`() {
        val file = File.createTempFile("video", ".webm").apply {
            writeBytes(ByteArray(128) { 0x1A })
        }

        assertFalse(VideoFileValidator.isTruncated(file))
    }

    @Test
    fun `tiny file is rejected`() {
        val file = File.createTempFile("video", ".mp4").apply { writeBytes(ByteArray(4)) }

        assertTrue(VideoFileValidator.isTruncated(file))
    }

    /** ftyp + moov + mdat; payload — сколько байт mdat реально записано. */
    private fun mp4(mdatSize: Int, payload: Int): File {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)

        data.writeInt(24)
        data.writeBytes("ftyp")
        data.writeBytes("isom")
        data.writeInt(0x200)
        data.writeBytes("isom")
        data.writeBytes("mp41")

        data.writeInt(16)
        data.writeBytes("moov")
        data.writeInt(0)
        data.writeInt(0)

        data.writeInt(mdatSize)
        data.writeBytes("mdat")
        data.write(ByteArray(payload) { 1 })

        val file = File.createTempFile("video", ".mp4")
        file.writeBytes(out.toByteArray())
        return file
    }
}
