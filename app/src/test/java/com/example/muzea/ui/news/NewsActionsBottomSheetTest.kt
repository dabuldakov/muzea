package com.example.muzea.ui.news

import android.os.Looper
import android.widget.LinearLayout
import com.example.muzea.HiltTestActivity
import com.example.muzea.R
import org.junit.Assert.assertEquals
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class NewsActionsBottomSheetTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `author sheet renders edit and delete actions`() {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val sheet = NewsActionsBottomSheet.forNews(canEdit = true)
        sheet.show(activity.supportFragmentManager, "actions")
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = sheet.dialog
        assertNotNull(dialog)
        val container = dialog!!.findViewById<LinearLayout>(R.id.actionsContainer)
        assertEquals(2, container.childCount)
    }

    @Test
    fun `reader sheet renders report action`() {
        val activity = Robolectric.buildActivity(HiltTestActivity::class.java).setup().get()
        val sheet = NewsActionsBottomSheet.forComment(canDelete = false)
        sheet.show(activity.supportFragmentManager, "actions")
        shadowOf(Looper.getMainLooper()).idle()

        val container = sheet.dialog!!.findViewById<LinearLayout>(R.id.actionsContainer)
        assertEquals(1, container.childCount)
    }
}
