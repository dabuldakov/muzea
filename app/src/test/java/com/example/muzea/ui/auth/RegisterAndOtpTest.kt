package com.example.muzea.ui.auth

import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AuthRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
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

/** Robolectric+Hilt тесты регистрации и ввода кода. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class RegisterAndOtpTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val authRepository: AuthRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun register() =
        Robolectric.buildActivity(RegisterActivity::class.java).setup().get()

    @Test
    fun `register requires consent`() {
        val activity = register()
        activity.findViewById<android.widget.EditText>(R.id.etUsername).setText("morris")
        activity.findViewById<android.widget.EditText>(R.id.etEmail).setText("morris@muzea.test")

        activity.findViewById<View>(R.id.btnRegister).performClick()

        assertNotNull(activity.findViewById<android.widget.CheckBox>(R.id.cbConsent).error)
        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `register validates username length charset and email`() {
        val activity = register()
        val username = activity.findViewById<android.widget.EditText>(R.id.etUsername)
        val email = activity.findViewById<android.widget.EditText>(R.id.etEmail)
        activity.findViewById<android.widget.CheckBox>(R.id.cbConsent).isChecked = true

        username.setText("ab")
        activity.findViewById<View>(R.id.btnRegister).performClick()
        assertNotNull(username.error)

        username.setText("bad name")
        activity.findViewById<View>(R.id.btnRegister).performClick()
        assertNotNull(username.error)

        username.setText("morris")
        email.setText("bad-email")
        activity.findViewById<View>(R.id.btnRegister).performClick()
        assertNotNull(email.error)

        coVerify(exactly = 0) { authRepository.register(any(), any(), any()) }
    }

    @Test
    fun `valid registration opens the otp screen`() {
        coEvery {
            authRepository.register("morris@muzea.test", "morris", "Morris")
        } returns flowOf(Resource.Success(Unit))
        val activity = register()
        activity.findViewById<android.widget.EditText>(R.id.etUsername).setText("morris")
        activity.findViewById<android.widget.EditText>(R.id.etFullName).setText("Morris")
        activity.findViewById<android.widget.EditText>(R.id.etEmail).setText("morris@muzea.test")
        activity.findViewById<android.widget.CheckBox>(R.id.cbConsent).isChecked = true

        activity.findViewById<View>(R.id.btnRegister).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started.component?.className!!.endsWith("OtpActivity"))
    }

    @Test
    fun `login link closes the registration screen`() {
        val activity = register()

        activity.findViewById<View>(R.id.tvLogin).performClick()

        assertTrue(activity.isFinishing)
    }

    private fun otp(mode: String) =
        Robolectric.buildActivity(
            OtpActivity::class.java,
            OtpActivity.intent(
                ApplicationProvider.getApplicationContext(), "morris@muzea.test", mode
            )
        ).setup().get()

    @Test
    fun `otp requires at least four digits`() {
        val activity = otp(OtpActivity.MODE_LOGIN)
        activity.findViewById<android.widget.EditText>(R.id.etCode).setText("12")

        activity.findViewById<View>(R.id.btnVerify).performClick()

        assertNotNull(activity.findViewById<android.widget.EditText>(R.id.etCode).error)
    }

    @Test
    fun `login code verification opens the main screen`() {
        coEvery { authRepository.verifyLoginCode(any(), any()) } returns
            flowOf(Resource.Success(Unit))
        val activity = otp(OtpActivity.MODE_LOGIN)
        activity.findViewById<android.widget.EditText>(R.id.etCode).setText("1234")

        activity.findViewById<View>(R.id.btnVerify).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertTrue(started.component?.className!!.endsWith("MainActivity"))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `register mode verifies email and resend uses verification endpoint`() {
        coEvery { authRepository.verifyEmail(any(), any()) } returns
            flowOf(Resource.Success(Unit))
        coEvery { authRepository.resendVerification(any()) } returns
            flowOf(Resource.Success(Unit))
        val activity = otp(OtpActivity.MODE_REGISTER)

        activity.findViewById<View>(R.id.btnResend).performClick()
        activity.findViewById<android.widget.EditText>(R.id.etCode).setText("9876")
        activity.findViewById<View>(R.id.btnVerify).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        coVerify { authRepository.resendVerification("morris@muzea.test") }
        coVerify { authRepository.verifyEmail("morris@muzea.test", "9876") }
    }

    @Test
    fun `login resend requests a new code`() {
        coEvery { authRepository.requestLoginCode(any()) } returns
            flowOf(Resource.Success(Unit))
        val activity = otp(OtpActivity.MODE_LOGIN)

        activity.findViewById<View>(R.id.btnResend).performClick()

        coVerify { authRepository.requestLoginCode("morris@muzea.test") }
    }
}
