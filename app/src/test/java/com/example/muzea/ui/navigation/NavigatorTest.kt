package com.example.muzea.ui.navigation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Навигационный контракт: значения по умолчанию пробрасываются в реализацию,
 * а расширение [navigator] находит хост-активность или падает, если её нет.
 */
class NavigatorTest {

    @Test
    fun `default arguments are forwarded to the implementation`() {
        val navigator = mockk<Navigator>(relaxed = true)

        navigator.openConversation("chat-1", "Title", null, 3L)

        verify {
            navigator.openConversation("chat-1", "Title", null, 3L, null, null)
        }
    }

    @Test
    fun `extension resolves the host activity`() {
        val host = mockkClass(
            FragmentActivity::class,
            moreInterfaces = arrayOf(Navigator::class)
        )
        val fragment = mockk<Fragment>()
        every { fragment.requireActivity() } returns host

        assertSame(host, fragment.navigator)
    }

    @Test
    fun `extension fails when the host does not implement the contract`() {
        val fragment = mockk<Fragment>()
        every { fragment.requireActivity() } returns mockk<FragmentActivity>(relaxed = true)

        assertThrows(IllegalStateException::class.java) { fragment.navigator }
    }
}
