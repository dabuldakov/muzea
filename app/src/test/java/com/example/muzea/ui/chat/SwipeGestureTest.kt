package com.example.muzea.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeGestureTest {

    private val threshold = 100f

    @Test
    fun `horizontal swipe closes the viewer`() {
        assertEquals(SwipeDirection.LEFT, SwipeGesture.classify(-200f, 30f, threshold))
        assertEquals(SwipeDirection.RIGHT, SwipeGesture.classify(200f, -30f, threshold))
    }

    @Test
    fun `vertical swipe flips the image`() {
        assertEquals(SwipeDirection.UP, SwipeGesture.classify(20f, -200f, threshold))
        assertEquals(SwipeDirection.DOWN, SwipeGesture.classify(-20f, 200f, threshold))
    }

    @Test
    fun `short move is not a swipe`() {
        assertEquals(SwipeDirection.NONE, SwipeGesture.classify(20f, 30f, threshold))
        assertEquals(SwipeDirection.NONE, SwipeGesture.classify(-90f, 90f, threshold))
    }

    @Test
    fun `dominant axis wins`() {
        assertEquals(SwipeDirection.UP, SwipeGesture.classify(120f, -300f, threshold))
        assertEquals(SwipeDirection.RIGHT, SwipeGesture.classify(300f, -120f, threshold))
    }
}
