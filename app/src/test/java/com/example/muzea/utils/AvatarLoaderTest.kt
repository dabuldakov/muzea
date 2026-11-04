package com.example.muzea.utils

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.example.muzea.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Покрывает [AvatarLoader] на чистой JVM: статический фасад Glide подменяется
 * MockK, а fluent-цепочка строится на relaxed-моках.
 */
class AvatarLoaderTest {

    private val requestManager = mockk<RequestManager>(relaxed = true)
    private val requestBuilder = mockk<RequestBuilder<Drawable>>(relaxed = true)
    private lateinit var view: ImageView

    @Before
    fun setUp() {
        view = mockk(relaxed = true)
        mockkStatic(Glide::class)
        every { Glide.with(any<View>()) } returns requestManager
        every { requestManager.load(any<String>()) } returns requestBuilder
        every { requestManager.load(null as String?) } returns requestBuilder
        every { requestBuilder.circleCrop() } returns requestBuilder
        every { requestBuilder.placeholder(any<Int>()) } returns requestBuilder
        every { requestBuilder.fallback(any<Int>()) } returns requestBuilder
        every { requestBuilder.error(any<Int>()) } returns requestBuilder
    }

    @After
    fun tearDown() {
        unmockkStatic(Glide::class)
    }

    @Test
    fun `loads the chat avatar with the default placeholder chain`() {
        AvatarLoader.load(view, "/api/avatars/uuid.png")

        verify(exactly = 1) { Glide.with(view) }
        verify(exactly = 1) { requestManager.load(MediaUrl.chat("/api/avatars/uuid.png")) }
        verify(exactly = 1) { requestBuilder.circleCrop() }
        verify(exactly = 1) { requestBuilder.placeholder(R.drawable.ic_default_avatar) }
        verify(exactly = 1) { requestBuilder.fallback(R.drawable.ic_default_avatar) }
        verify(exactly = 1) { requestBuilder.error(R.drawable.ic_default_avatar) }
        verify(exactly = 1) { requestBuilder.into(view) }
    }

    @Test
    fun `a null path still binds the fallback to clear recycled avatars`() {
        AvatarLoader.load(view, null)

        verify(exactly = 1) { requestManager.load(null as String?) }
        verify(exactly = 1) { requestBuilder.circleCrop() }
        verify(exactly = 1) { requestBuilder.fallback(R.drawable.ic_default_avatar) }
        verify(exactly = 1) { requestBuilder.into(view) }
    }
}
