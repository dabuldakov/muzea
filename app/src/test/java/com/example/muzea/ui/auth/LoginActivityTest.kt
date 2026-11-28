package com.example.muzea.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.di.TokenModule
import com.example.muzea.ui.MainActivity
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

/** Robolectric+Hilt тесты экрана входа: согласие, валидация, отправка кода. */
@HiltAndroidTest
@UninstallModules(TokenModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class LoginActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val authRepository: AuthRepository = mockk(relaxed = true)

    @BindValue
    @JvmField
    val tokenManager: TokenManager = mockk(relaxed = true)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun launchConsented(): LoginActivity {
        ConsentManager.accept(context)
        return Robolectric.buildActivity(LoginActivity::class.java).setup().get()
    }

    private fun LoginActivity.emailField(): EditText = findViewById(R.id.etEmail)

    private fun LoginActivity.clickLogin() {
        findViewById<View>(R.id.btnLogin).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `without consent opens the consent screen`() {
        val activity = Robolectric.buildActivity(LoginActivity::class.java).setup().get()

        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertTrue(started.component?.className!!.endsWith("ConsentActivity"))
    }

    @Test
    fun `consented user sees the login form`() {
        val activity = launchConsented()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.etEmail).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnLogin).visibility)
    }

    @Test
    fun `no password migration notice is shown`() {
        launchConsented()

        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun `form shows the new title and caption`() {
        val activity = launchConsented()

        assertEquals(
            activity.getString(R.string.login_title),
            activity.findViewById<TextView>(R.id.tvLoginTitle).text.toString()
        )
        assertEquals(
            activity.getString(R.string.login_caption),
            activity.findViewById<TextView>(R.id.tvLoginCaption).text.toString()
        )
    }

    @Test
    fun `terms and policy links open localized legal pages`() {
        val activity = launchConsented()

        activity.findViewById<View>(R.id.tvLegalTerms).performClick()
        var started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(activity.getString(R.string.terms_url), started?.data.toString())

        activity.findViewById<View>(R.id.tvLegalPolicy).performClick()
        started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(activity.getString(R.string.policy_url), started?.data.toString())
    }

    @Test
    fun `register link opens the registration screen`() {
        val activity = launchConsented()

        activity.findViewById<View>(R.id.tvRegister).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(RegisterActivity::class.java.name, started?.component?.className)
    }

    @Test
    fun `valid email is trimmed before requesting the code`() {
        coEvery { authRepository.requestLoginCode("morris@muzea.test") } returns
            flowOf(Resource.Success("SENT"))
        val activity = launchConsented()

        activity.emailField().setText("  morris@muzea.test  ")
        activity.clickLogin()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("morris@muzea.test", started?.getStringExtra("email"))
        assertEquals(OtpActivity.MODE_LOGIN, started?.getStringExtra("mode"))
    }

    @Test
    fun `loading disables the button and shows progress`() {
        coEvery { authRepository.requestLoginCode("morris@muzea.test") } returns
            flowOf(Resource.Loading())
        val activity = launchConsented()

        activity.emailField().setText("morris@muzea.test")
        activity.clickLogin()

        assertFalse(activity.findViewById<View>(R.id.btnLogin).isEnabled)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.progressBar).visibility)
    }

    @Test
    fun `server error is shown as a toast`() {
        coEvery { authRepository.requestLoginCode("morris@muzea.test") } returns
            flowOf(Resource.Error("Нет соединения"))
        val activity = launchConsented()

        activity.emailField().setText("morris@muzea.test")
        activity.clickLogin()

        assertEquals("Нет соединения", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `up to date session opens main screen`() {
        every { tokenManager.getToken() } returns "access"
        every { tokenManager.getAuthRefreshToken() } returns "refresh"
        every { tokenManager.getUsername() } returns "morris"

        val activity = launchConsented()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started?.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `invalid email shows the field error`() {
        val activity = launchConsented()

        activity.emailField().setText("not-an-email")
        activity.clickLogin()

        assertNotNull(activity.emailField().error)
    }

    @Test
    fun `email error clears as soon as the user types`() {
        val activity = launchConsented()
        val emailField = activity.emailField()

        emailField.setText("not-an-email")
        activity.clickLogin()
        assertNotNull(emailField.error)

        emailField.setText("morris@muzea.test")

        assertNull(emailField.error)
    }

    @Test
    fun `ime action on the email field submits the form`() {
        coEvery { authRepository.requestLoginCode("morris@muzea.test") } returns
            flowOf(Resource.Success("SENT"))
        val activity = launchConsented()
        val emailField = activity.emailField()

        emailField.setText("morris@muzea.test")
        emailField.onEditorAction(EditorInfo.IME_ACTION_GO)
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(OtpActivity.MODE_LOGIN, started?.getStringExtra("mode"))
    }
}
