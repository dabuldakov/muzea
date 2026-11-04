package com.example.muzea.ui.news

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
import com.example.muzea.databinding.ItemNewsBinding
import com.example.muzea.domain.model.News
import com.example.muzea.utils.LocalTimeFormatter
import com.google.android.material.card.MaterialCardView
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты NewsAdapter без Robolectric.
 *
 * Robolectric поднимает sandbox-classloader, из-за чего JaCoCo не видит
 * покрытие основного кода, поэтому строку проверяем на моке [ItemNewsBinding]:
 * поля data-binding — публичные final-поля без геттеров, их подставляем
 * рефлексией. Glide подменяется, изображения в JVM-тестах не грузятся.
 */
class NewsAdapterTest {

    private lateinit var root: MaterialCardView
    private lateinit var tvTitle: TextView
    private lateinit var tvContent: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvDate: TextView
    private lateinit var ivImage: ImageView
    private lateinit var binding: ItemNewsBinding

    private lateinit var requestManager: RequestManager
    private lateinit var requestBuilder: RequestBuilder<Drawable>

    @Before
    fun setUp() {
        root = mockk(relaxed = true)
        tvTitle = mockk(relaxed = true)
        tvContent = mockk(relaxed = true)
        tvAuthor = mockk(relaxed = true)
        tvDate = mockk(relaxed = true)
        ivImage = mockk(relaxed = true)
        binding = mockk(relaxed = true)

        every { binding.root } returns root
        setViewField("tvTitle", tvTitle)
        setViewField("tvContent", tvContent)
        setViewField("tvAuthor", tvAuthor)
        setViewField("tvDate", tvDate)
        setViewField("ivImage", ivImage)
        every { root.context } returns mockk<Context>(relaxed = true)

        stubGlide()
    }

    @After
    fun tearDown() {
        unmockkStatic(Glide::class)
    }

    /** Поля [ItemNewsBinding] — публичные final-поля без геттеров. */
    private fun setViewField(field: String, value: View) {
        val f = ItemNewsBinding::class.java.getDeclaredField(field)
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

    private fun news(
        id: Long = 1L,
        imageUrl: String? = null,
        author: String = "vovan"
    ) = News(
        id = id,
        title = "Title $id",
        content = "Content $id",
        imageUrl = imageUrl,
        relatedVideo = null,
        author = author,
        publishedAt = "2026-01-01T00:00:00"
    )

    private fun holder(onClick: (Long) -> Unit = {}) =
        NewsAdapter.NewsViewHolder(binding, onClick)

    @Test
    fun `binds title content author and date`() {
        holder().bind(news(1))

        verify { tvTitle.setText("Title 1") }
        verify { tvContent.setText("Content 1") }
        verify { tvAuthor.setText("vovan") }
        verify { tvDate.setText(LocalTimeFormatter.formatDate("2026-01-01T00:00:00")) }
    }

    @Test
    fun `missing image hides the image view`() {
        holder().bind(news(1, imageUrl = null))

        verify { ivImage.visibility = View.GONE }
    }

    @Test
    fun `blank image url hides the image view`() {
        holder().bind(news(1, imageUrl = "   "))

        verify { ivImage.visibility = View.GONE }
    }

    @Test
    fun `present image shows the view and loads it`() {
        holder().bind(news(1, imageUrl = "https://example.com/a.jpg"))

        verify { ivImage.visibility = View.VISIBLE }
        verify { requestManager.load("https://example.com/a.jpg") }
        verify { requestBuilder.into(ivImage) }
    }

    @Test
    fun `relative image url is resolved against the main server`() {
        holder().bind(news(1, imageUrl = "/api/news/image/1.jpg"))

        verify { requestManager.load("https://api-muzea.su/api/news/image/1.jpg") }
    }

    @Test
    fun `clicking the row reports the news id`() {
        var clicked: Long? = null
        val holder = holder { clicked = it }
        holder.bind(news(55))

        val listener = slot<View.OnClickListener>()
        verify { root.setOnClickListener(capture(listener)) }
        listener.captured.onClick(root)

        assertEquals(55L, clicked)
    }

    @Test
    fun `adapter starts empty`() {
        assertEquals(0, NewsAdapter { }.itemCount)
    }

    @Test
    fun `updateList publishes the first list synchronously`() {
        val adapter = NewsAdapter { }
        enableNotifications(adapter)
        adapter.updateList(listOf(news(1), news(2)))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder binds the item at the requested position`() {
        val adapter = NewsAdapter { }
        enableNotifications(adapter)
        adapter.updateList(listOf(news(7)))

        adapter.onBindViewHolder(NewsAdapter.NewsViewHolder(binding, { }), 0)

        verify { tvTitle.setText("Title 7") }
    }

    @Test
    fun `clearItems on a fresh adapter leaves it empty`() {
        val adapter = NewsAdapter { }
        enableNotifications(adapter)
        adapter.clearItems()

        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `diff callback compares ids and contents`() {
        val callback = NewsAdapter.NewsDiffCallback()

        assertTrue(callback.areItemsTheSame(news(1), news(1, author = "other")))
        assertFalse(callback.areItemsTheSame(news(1), news(2)))
        assertTrue(callback.areContentsTheSame(news(1), news(1)))
        assertFalse(callback.areContentsTheSame(news(1), news(1, author = "other")))
    }
}
