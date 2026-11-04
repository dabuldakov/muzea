package com.example.muzea.ui.video

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.ViewTarget
import com.example.muzea.R
import com.example.muzea.databinding.ItemVideoBinding
import com.example.muzea.domain.model.Video
import com.google.android.material.card.MaterialCardView
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Тесты VideoAdapter без Robolectric.
 *
 * Строку проверяем на моке [ItemVideoBinding]: поля data-binding —
 * публичные final-поля без геттеров, их подставляем рефлексией.
 * Glide подменяется — изображения в JVM-тестах не грузятся.
 */
class VideoAdapterTest {

    private lateinit var root: MaterialCardView
    private lateinit var tvTitle: TextView
    private lateinit var tvViews: TextView
    private lateinit var ivThumbnail: ImageView
    private lateinit var binding: ItemVideoBinding
    private lateinit var viewsContext: Context

    private lateinit var requestManager: RequestManager
    private lateinit var requestBuilder: RequestBuilder<Drawable>

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        tvTitle = mockk(relaxed = true)
        tvViews = mockk(relaxed = true)
        ivThumbnail = mockk(relaxed = true)
        binding = mockk(relaxed = true)
        viewsContext = mockk(relaxed = true)

        every { binding.root } returns root
        setViewField("tvTitle", tvTitle)
        setViewField("tvViews", tvViews)
        setViewField("ivThumbnail", ivThumbnail)
        every { root.context } returns mockk<Context>(relaxed = true)
        every { tvViews.context } returns viewsContext

        stubGlide()
    }

    @After
    fun tearDown() {
        unmockkStatic(Glide::class)
    }

    /** Поля [ItemVideoBinding] — публичные final-поля без геттеров. */
    private fun setViewField(field: String, value: View) {
        val f = ItemVideoBinding::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(binding, value)
    }

    /**
     * В юнит-тестах android.jar — заглушка: поле `mObservers` у
     * `android.database.Observable` не инициализировано, поэтому любой
     * `notify*` падает с NPE. Подставляем пустой список наблюдателей.
     */
    private fun enableNotifications(adapter: RecyclerView.Adapter<*>) {
        val observable = RecyclerView.Adapter::class.java
            .getDeclaredField("mObservable").apply { isAccessible = true }.get(adapter)
        var cls: Class<*>? = observable.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField("mObservers")
                f.isAccessible = true
                f.set(observable, ArrayList<Any>())
                return
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
    }

    private fun stubGlide() {
        requestManager = mockk(relaxed = true)
        requestBuilder = mockk(relaxed = true)
        every { requestManager.load(any<String>()) } returns requestBuilder
        every { requestManager.load(any<Any>()) } returns requestBuilder
        every { requestBuilder.centerCrop() } returns requestBuilder
        every { requestBuilder.diskCacheStrategy(any()) } returns requestBuilder
        every { requestBuilder.placeholder(any<Int>()) } returns requestBuilder
        every { requestBuilder.error(any<Int>()) } returns requestBuilder
        every { requestBuilder.into(any<ImageView>()) } returns
            mockk<ViewTarget<ImageView, Drawable>>(relaxed = true)
        mockkStatic(Glide::class)
        every { Glide.with(any<View>()) } returns requestManager
        every { Glide.with(any<Context>()) } returns requestManager
    }

    private fun video(
        id: Long = 1L,
        uploadedBy: String = "vovan",
        views: Int = 42,
        thumbnailUrl: String? = "https://example.com/thumb$id.jpg"
    ) = Video(
        id = id,
        title = "Title $id",
        description = null,
        url = "https://example.com/video$id",
        thumbnailUrl = thumbnailUrl,
        fileSize = null,
        duration = null,
        views = views,
        likes = null,
        uploadedBy = uploadedBy,
        uploadedAt = "2026-01-01T00:00:00"
    )

    private fun holder(onClick: (Long) -> Unit = {}) =
        VideoAdapter.VideoViewHolder(binding, onClick)

    @Test
    fun `binds title and formatted views`() {
        every { viewsContext.getString(R.string.video_views_format, 42) } returns "42 views"

        holder().bind(video(1))

        verify { tvTitle.setText("Title 1") }
        verify { tvViews.setText("42 views") }
    }

    @Test
    fun `bind loads the thumbnail through glide`() {
        holder().bind(video(1))

        verify { requestManager.load("https://example.com/thumb1.jpg") }
        verify { requestBuilder.into(ivThumbnail) }
    }

    @Test
    fun `relative thumbnail url is resolved against the main server`() {
        holder().bind(video(1, thumbnailUrl = "/api/videos/thumb/1.jpg"))

        verify { requestManager.load("https://api-muzea.su/api/videos/thumb/1.jpg") }
    }

    @Test
    fun `clicking the row reports the video id`() {
        var clicked: Long? = null
        val holder = holder { clicked = it }
        holder.bind(video(77))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)

        assertEquals(77L, clicked)
    }

    @Test
    fun `starts empty and counts submitted items`() {
        val adapter = VideoAdapter { }
        enableNotifications(adapter)

        assertEquals(0, adapter.itemCount)

        adapter.submitList(listOf(video(1), video(2)))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder binds the item at the requested position`() {
        val adapter = VideoAdapter { }
        enableNotifications(adapter)
        adapter.submitList(listOf(video(9)))

        adapter.onBindViewHolder(VideoAdapter.VideoViewHolder(binding, { }), 0)

        verify { tvTitle.setText("Title 9") }
    }

    @Test
    fun `submitList replaces the previous list`() {
        val adapter = VideoAdapter { }
        enableNotifications(adapter)
        adapter.submitList(listOf(video(1)))
        adapter.submitList(listOf(video(2), video(3)))

        assertEquals(2, adapter.itemCount)
    }
}
