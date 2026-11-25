package com.example.muzea.ui.news

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.ViewTarget
import com.example.muzea.databinding.ItemNewsDetailHeaderBinding
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.Video
import com.google.android.material.button.MaterialButton
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

class NewsDetailHeaderAdapterTest {

    private lateinit var binding: ItemNewsDetailHeaderBinding
    private lateinit var root: LinearLayout
    private lateinit var ivImage: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvContent: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvLikes: TextView
    private lateinit var tvViews: TextView
    private lateinit var tvComments: TextView
    private lateinit var btnLike: ImageButton
    private lateinit var tvCommentsTitle: TextView
    private lateinit var btnLoadOlder: MaterialButton
    private lateinit var commentsProgress: ProgressBar
    private lateinit var tvCommentsEmpty: TextView
    private lateinit var videoCard: MaterialCardView
    private lateinit var tvVideoTitle: TextView
    private lateinit var btnWatchVideo: MaterialButton

    private lateinit var requestManager: RequestManager
    private lateinit var requestBuilder: RequestBuilder<Drawable>

    @Before
    fun setUp() {
        binding = mockk(relaxed = true)
        root = mockk(relaxed = true)
        ivImage = mockk(relaxed = true)
        tvTitle = mockk(relaxed = true)
        tvContent = mockk(relaxed = true)
        tvAuthor = mockk(relaxed = true)
        tvDate = mockk(relaxed = true)
        tvLikes = mockk(relaxed = true)
        tvViews = mockk(relaxed = true)
        tvComments = mockk(relaxed = true)
        btnLike = mockk(relaxed = true)
        tvCommentsTitle = mockk(relaxed = true)
        btnLoadOlder = mockk(relaxed = true)
        commentsProgress = mockk(relaxed = true)
        tvCommentsEmpty = mockk(relaxed = true)
        videoCard = mockk(relaxed = true)
        tvVideoTitle = mockk(relaxed = true)
        btnWatchVideo = mockk(relaxed = true)

        every { binding.root } returns root
        every { root.context } returns mockk<Context>(relaxed = true)
        setField("ivImage", ivImage)
        setField("tvTitle", tvTitle)
        setField("tvContent", tvContent)
        setField("tvAuthor", tvAuthor)
        setField("tvDate", tvDate)
        setField("tvLikes", tvLikes)
        setField("tvViews", tvViews)
        setField("tvComments", tvComments)
        setField("btnLike", btnLike)
        setField("tvCommentsTitle", tvCommentsTitle)
        setField("btnLoadOlderComments", btnLoadOlder)
        setField("commentsProgress", commentsProgress)
        setField("tvCommentsEmpty", tvCommentsEmpty)
        setField("videoCard", videoCard)
        setField("tvVideoTitle", tvVideoTitle)
        setField("btnWatchVideo", btnWatchVideo)

        requestManager = mockk(relaxed = true)
        requestBuilder = mockk(relaxed = true)
        every { requestManager.load(any<String>()) } returns requestBuilder
        every { requestBuilder.fitCenter() } returns requestBuilder
        every { requestBuilder.diskCacheStrategy(any()) } returns requestBuilder
        every { requestBuilder.placeholder(any<Int>()) } returns requestBuilder
        every { requestBuilder.error(any<Int>()) } returns requestBuilder
        every { requestBuilder.into(any<ImageView>()) } returns
            mockk<ViewTarget<ImageView, Drawable>>(relaxed = true)
        mockkStatic(Glide::class)
        every { Glide.with(any<View>()) } returns requestManager
        every { Glide.with(any<Context>()) } returns requestManager
    }

    @After
    fun tearDown() {
        unmockkStatic(Glide::class)
    }

    private fun setField(name: String, value: View) {
        val field = ItemNewsDetailHeaderBinding::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(binding, value)
    }

    private fun video() = Video(
        id = 3,
        title = "Клип",
        description = null,
        url = "/v.mp4",
        thumbnailUrl = null,
        fileSize = 1,
        duration = null,
        views = 5,
        likes = 1,
        uploadedBy = "Morris",
        uploadedAt = "2026-01-01T00:00:00"
    )

    private fun news() = News(
        id = 1,
        title = "Заголовок",
        content = "Текст",
        imageUrl = "/api/news/image/a.jpg",
        relatedVideo = video(),
        author = "Morris",
        publishedAt = "2026-01-01T00:00:00",
        views = 340,
        likes = 12,
        commentsCount = 5,
        likedByMe = true
    )

    private fun holder(
        onLike: () -> Unit = {},
        onOlder: () -> Unit = {},
        onVideo: (Video) -> Unit = {}
    ) = NewsDetailHeaderAdapter.HeaderViewHolder(binding, onLike, onOlder, onVideo)

    @Test
    fun `binds article metrics and visible controls`() {
        val ui = NewsDetailHeaderAdapter.HeaderUi(
            news = news(),
            commentsTotal = 5,
            hasMoreComments = true,
            isLoadingComments = false
        )
        holder().bind(ui)

        verify { tvTitle.setText("Заголовок") }
        verify { tvLikes.setText("12") }
        verify { tvViews.setText("340") }
        verify { tvComments.setText("5") }
        verify { btnLike.isSelected = true }
        verify { ivImage.visibility = View.VISIBLE }
        verify { videoCard.visibility = View.VISIBLE }
        verify { btnLoadOlder.visibility = View.VISIBLE }
        verify { tvCommentsEmpty.visibility = View.GONE }
    }

    @Test
    fun `without image or video hides them and shows empty comments`() {
        val ui = NewsDetailHeaderAdapter.HeaderUi(
            news = news().copy(
                imageUrl = null,
                relatedVideo = null,
                likedByMe = false,
                views = 1,
                likes = 2,
                commentsCount = 0
            ),
            commentsTotal = 0,
            hasMoreComments = false,
            isLoadingComments = false
        )
        holder().bind(ui)

        verify { ivImage.visibility = View.GONE }
        verify { videoCard.visibility = View.GONE }
        verify { btnLoadOlder.visibility = View.GONE }
        verify { tvCommentsEmpty.visibility = View.VISIBLE }
    }

    @Test
    fun `actions call their callbacks`() {
        var liked = false
        var older = false
        var watched: Video? = null
        val adapterHolder = holder(
            onLike = { liked = true },
            onOlder = { older = true },
            onVideo = { watched = it }
        )
        adapterHolder.bind(
            NewsDetailHeaderAdapter.HeaderUi(news(), 1, true, false)
        )

        val likeListener = slot<View.OnClickListener>()
        verify { btnLike.setOnClickListener(capture(likeListener)) }
        likeListener.captured.onClick(btnLike)

        val olderListener = slot<View.OnClickListener>()
        verify { btnLoadOlder.setOnClickListener(capture(olderListener)) }
        olderListener.captured.onClick(btnLoadOlder)

        val videoListener = slot<View.OnClickListener>()
        verify { btnWatchVideo.setOnClickListener(capture(videoListener)) }
        videoListener.captured.onClick(btnWatchVideo)

        assertEquals(true, liked)
        assertEquals(true, older)
        assertEquals(3L, watched?.id)
    }
}
