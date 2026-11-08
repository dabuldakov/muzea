package com.example.muzea.ui.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.utils.ConsentManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Robolectric+Hilt тесты экрана входа: согласие, валидация, отправка кода. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class LoginActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val authRepository: AuthRepository = mockk(relaxed = true)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("reauth_notice_shown_v1", true)
            .commit()
    }

    private fun launch(): LoginActivity =
        Robolectric.buildActivity(LoginActivity::class.java).setup().get()

    @Test
    fun `without consent opens the consent screen`() {
        val activity = launch()

        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertTrue(started.component?.className!!.endsWith("ConsentActivity"))
    }

    @Test
    fun `consented user sees the login form`() {
        ConsentManager.accept(context)

        val activity = launch()

        assertEquals(
            android.view.View.VISIBLE,
            activity.findViewById<android.view.View>(R.id.etEmail).visibility
        )
        assertEquals(
            android.view.View.VISIBLE,
            activity.findViewById<android.view.View>(R.id.btnLogin).visibility
        )
    }

    @Test
    fun `invalid email shows the field error`() {
        ConsentManager.accept(context)
        val activity = launch()

        activity.findViewById<android.widget.EditText>(R.id.etEmail).setText("not-an-email")
        activity.findViewById<android.view.View>(R.id.btnLogin).performClick()

        assertNotNull(activity.findViewById<android.widget.EditText>(R.id.etEmail).error)
    }

    @Test
    fun `valid email requests a login code and opens OTP`() {
        ConsentManager.accept(context)
        coEvery { authRepository.requestLoginCode("morris@muzea.test") } returns
            flowOf(Resource.Success(Unit))
        val activity = launch()

        activity.findViewById<android.widget.EditText>(R.id.etEmail)
            .setText("morris@muzea.test")
        activity.findViewById<android.view.View>(R.id.btnLogin).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertTrue(started.component?.className!!.endsWith("OtpActivity"))
    }
}
