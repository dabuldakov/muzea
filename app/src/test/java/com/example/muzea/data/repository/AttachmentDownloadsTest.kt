package com.example.muzea.data.repository

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Robolectric-тест публикации файла в системные «Загрузки». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AttachmentDownloadsTest {

    @Test
    fun `file is published into downloads`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "beta.apk").apply { writeBytes(ByteArray(32)) }

        val published = AttachmentDownloads.publish(
            context,
            file,
            "muzea-beta.apk",
            "application/vnd.android.package-archive"
        )

        assertTrue(published)

    }
}
