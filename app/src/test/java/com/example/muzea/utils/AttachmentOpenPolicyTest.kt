package com.example.muzea.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentOpenPolicyTest {

    @Test
    fun `apk by extension and mime is executable`() {
        assertTrue(AttachmentOpenPolicy.isExecutable("muzea.apk", null))
        assertTrue(AttachmentOpenPolicy.isExecutable("Muzea.APK", null))
        assertTrue(AttachmentOpenPolicy.isExecutable("file.bin",
            "application/vnd.android.package-archive"))
        assertTrue(AttachmentOpenPolicy.isExecutable("setup.exe", null))
        assertTrue(AttachmentOpenPolicy.isExecutable("script.SH", null))
    }

    @Test
    fun `ordinary attachments are not executable`() {
        assertFalse(AttachmentOpenPolicy.isExecutable("photo.jpg", "image/jpeg"))
        assertFalse(AttachmentOpenPolicy.isExecutable("clip.mp4", "video/mp4"))
        assertFalse(AttachmentOpenPolicy.isExecutable("doc.pdf", "application/pdf"))
        assertFalse(AttachmentOpenPolicy.isExecutable(null, null))
        assertFalse(AttachmentOpenPolicy.isExecutable("archive.zip", "application/zip"))
    }
}
