package com.example.muzea.ui.chat

import android.app.Application
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PendingAttachmentsViewTest {

    private val context: Context = androidx.appcompat.view.ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Muzea
    )

    private fun view(
        onRemove: (String) -> Unit = {},
        onRetry: (String) -> Unit = {}
    ): Triple<PendingAttachmentsView, View, LinearLayout> {
        val scroll = View(context)
        val container = LinearLayout(context)
        val subject = PendingAttachmentsView(
            inflater = LayoutInflater.from(context),
            scroll = scroll,
            container = container,
            onRemove = onRemove,
            onRetry = onRetry
        )
        return Triple(subject, scroll, container)
    }

    private fun pending(
        id: String = "p1",
        error: String? = null,
        uploaded: Boolean = false,
        progress: Int = 0
    ) = PendingAttachment(
        id = id,
        name = "file-$id.jpg",
        size = 100,
        mimeType = "image/jpeg",
        type = "IMAGE",
        error = error,
        progress = progress
    ).let { item ->
        if (uploaded) item.copy(uploaded = mockUploaded()) else item
    }

    private fun mockUploaded() = com.example.muzea.domain.model.MessageAttachment(
        fileUrl = "https://cdn/1.jpg",
        thumbnailUrl = null,
        type = "IMAGE",
        width = 1,
        height = 1
    )

    @Test
    fun `empty list hides the strip`() {
        val (subject, scroll, container) = view()
        subject.render(emptyList())
        assertEquals(View.GONE, scroll.visibility)
        assertEquals(0, container.childCount)
    }

    @Test
    fun `uploading item shows progress`() {
        val (subject, scroll, container) = view()
        subject.render(listOf(pending(progress = 40)))
        assertEquals(View.VISIBLE, scroll.visibility)
        assertEquals(1, container.childCount)
        val row = container.getChildAt(0)
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.pbUploading).visibility)
        assertEquals(View.GONE, row.findViewById<View>(R.id.tvError).visibility)
    }

    @Test
    fun `uploaded item hides progress`() {
        val (subject, _, container) = view()
        subject.render(listOf(pending(uploaded = true)))
        val row = container.getChildAt(0)
        assertEquals(View.GONE, row.findViewById<View>(R.id.pbUploading).visibility)
    }

    @Test
    fun `error item shows text and retry`() {
        val (subject, _, container) = view(onRetry = { retried = it })
        retried = null
        subject.render(listOf(pending(error = "нет сети")))

        val row = container.getChildAt(0)
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.tvError).visibility)
        row.performClick()
        assertEquals("p1", retried)
    }

    @Test
    fun `remove button reports the item`() {
        val (subject, _, container) = view(onRemove = { removed = it })
        removed = null
        subject.render(listOf(pending()))
        container.getChildAt(0).findViewById<View>(R.id.ivRemove).performClick()
        assertEquals("p1", removed)
    }

    private companion object {
        var retried: String? = null
        var removed: String? = null
    }
}
