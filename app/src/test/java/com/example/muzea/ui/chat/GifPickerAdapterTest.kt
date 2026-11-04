package com.example.muzea.ui.chat

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.ViewTarget
import com.example.muzea.R
import com.example.muzea.databinding.ItemGifBinding
import com.example.muzea.domain.model.Gif
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
 * Тесты GifPickerAdapter без Robolectric.
 *
 * Строку проверяем на моке [ItemGifBinding]: поле data-binding —
 * публичный final-поле без геттера, подставляем рефлексией. Glide
 * подменяется, превью в JVM-тестах не грузятся.
 */
class GifPickerAdapterTest {

    private lateinit var ivGif: ImageView
    private lateinit var binding: ItemGifBinding

    private lateinit var requestManager: RequestManager
    private lateinit var requestBuilder: RequestBuilder<Drawable>

    @Before
    fun setUp() {
        ivGif = mockk(relaxed = true)
        binding = mockk(relaxed = true)

        every { binding.root } returns ivGif
        setViewField("ivGifItem", ivGif)

        stubGlide()
    }

    @After
    fun tearDown() {
        unmockkStatic(Glide::class)
    }

    /** Поле [ItemGifBinding] — публичное final-поле без геттера. */
    private fun setViewField(field: String, value: View) {
        val f = ItemGifBinding::class.java.getDeclaredField(field)
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
        every { requestBuilder.diskCacheStrategy(any()) } returns requestBuilder
        every { requestBuilder.into(any<ImageView>()) } returns
            mockk<ViewTarget<ImageView, Drawable>>(relaxed = true)
        mockkStatic(Glide::class)
        every { Glide.with(any<View>()) } returns requestManager
        every { Glide.with(any<Context>()) } returns requestManager
    }

    private fun gif(
        provider: String = "klipy",
        providerGifId: String = "g1",
        url: String = "https://cdn.example/u.gif",
        previewUrl: String? = null,
        gifKey: String? = null
    ) = Gif(
        provider = provider,
        providerGifId = providerGifId,
        title = null,
        url = url,
        previewUrl = previewUrl,
        width = null,
        height = null,
        mp4Url = null,
        gifKey = gifKey
    )

    private fun bind(gif: Gif, onClick: (Gif) -> Unit = {}): GifPickerAdapter.GifViewHolder {
        val holder = GifPickerAdapter(onClick).GifViewHolder(binding)
        holder.bind(gif)
        return holder
    }

    @Test
    fun `binds the gif url through glide`() {
        bind(gif(url = "https://cdn.example/u.gif"))

        verify { requestManager.load("https://cdn.example/u.gif") }
        verify { requestBuilder.into(ivGif) }
    }

    @Test
    fun `preview url is preferred over the gif url`() {
        bind(
            gif(
                url = "https://cdn.example/u.gif",
                previewUrl = "https://cdn.example/p.gif"
            )
        )

        verify { requestManager.load("https://cdn.example/p.gif") }
    }

    @Test
    fun `relative preview url is resolved against the chat server`() {
        bind(gif(url = "https://cdn.example/u.gif", previewUrl = "/media/p.gif"))

        verify { requestManager.load("https://chat-muzea.su/media/p.gif") }
    }

    @Test
    fun `click on the tile reports the gif`() {
        var clicked: Gif? = null
        val gif = gif(url = "https://cdn.example/u.gif")
        bind(gif) { clicked = it }

        val listener = slot<View.OnClickListener>()
        verify { ivGif.setOnClickListener(capture(listener)) }
        listener.captured.onClick(ivGif)

        assertEquals(gif, clicked)
    }

    @Test
    fun `recycling clears the glide request`() {
        every { ivGif.findViewById<View>(R.id.ivGifItem) } returns ivGif
        val adapter = GifPickerAdapter { }
        val holder = adapter.GifViewHolder(binding)
        holder.bind(gif())

        adapter.onViewRecycled(holder)

        verify { requestManager.clear(ivGif) }
    }

    @Test
    fun `adapter starts empty`() {
        assertEquals(0, GifPickerAdapter { }.itemCount)
    }

    @Test
    fun `submitList publishes the first list synchronously`() {
        val adapter = GifPickerAdapter { }
        enableNotifications(adapter)
        adapter.submitList(listOf(gif(providerGifId = "a"), gif(providerGifId = "b")))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder binds the item at the requested position`() {
        val adapter = GifPickerAdapter { }
        enableNotifications(adapter)
        adapter.submitList(listOf(gif(url = "https://cdn.example/one.gif")))
        val holder = adapter.GifViewHolder(binding)

        adapter.onBindViewHolder(holder, 0)

        verify { requestManager.load("https://cdn.example/one.gif") }
    }

    @Test
    fun `submitList with a single item counts it`() {
        val adapter = GifPickerAdapter { }
        enableNotifications(adapter)
        adapter.submitList(listOf(gif()))

        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `diff callback compares gif identity and contents`() {
        val a = gif(gifKey = "k1")
        val sameKey = gif(gifKey = "k1")
        val otherKey = gif(gifKey = "k2")
        val byProvider = gif(provider = "p", providerGifId = "x", gifKey = null)
        val sameByProvider = gif(provider = "p", providerGifId = "x", gifKey = null)
        val otherByProvider = gif(provider = "p", providerGifId = "y", gifKey = null)

        assertTrue(invokeDiff("areItemsTheSame", a, sameKey))
        assertFalse(invokeDiff("areItemsTheSame", a, otherKey))
        assertTrue(invokeDiff("areItemsTheSame", byProvider, sameByProvider))
        assertFalse(invokeDiff("areItemsTheSame", byProvider, otherByProvider))
        assertTrue(invokeDiff("areContentsTheSame", a, sameKey))
        assertFalse(invokeDiff("areContentsTheSame", a, otherKey))
    }

    private fun invokeDiff(method: String, old: Gif, new: Gif): Boolean {
        val cls = Class.forName("com.example.muzea.ui.chat.GifPickerAdapter\$DiffCallback")
        val instance = cls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        val m = cls.getMethod(method, Gif::class.java, Gif::class.java)
        m.isAccessible = true
        return m.invoke(instance, old, new) as Boolean
    }
}
