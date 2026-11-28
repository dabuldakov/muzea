package com.example.muzea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.muzea.R
import com.example.muzea.databinding.ActivityLoginBinding
import com.example.muzea.ui.MainActivity
import com.example.muzea.ui.common.SystemBars
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.PendingPush
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private var pendingEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Тап по системному FCM-уведомлению запускает launcher (LoginActivity).
        // Сохраняем data-extras и передадим их в MainActivity после входа.
        PendingPush.capture(intent)

        // Без согласия на обработку персональных данных приложение не запускается (ст. 9 ФЗ-152).
        if (!ConsentManager.isAccepted(this)) {
            startActivity(Intent(this, ConsentActivity::class.java))
            finish()
            return
        }

        // Сессия нового образца (есть access+refresh) → сразу на главный.
        if (isUpToDateSession()) {
            startMain()
            return
        }
        // Старая сессия (legacy-токен без refresh) не заведётся в новом входе —
        // сбрасываем и показываем экран входа с пояснением.
        if (!tokenManager.getToken().isNullOrEmpty()) {
            tokenManager.clearToken()
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)
        // На градиентном фоне нужны светлые иконки системных баров.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setupClickListeners()
        observeViewModel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PendingPush.capture(intent)
    }

    private fun startMain() {
        val next = Intent(this, MainActivity::class.java).apply {
            // Если MainActivity уже в задаче — доставит onNewIntent с extras.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingPush.applyTo(next)
        startActivity(next)
        finish()
    }

    private fun isUpToDateSession(): Boolean =
        !tokenManager.getToken().isNullOrEmpty() &&
            !tokenManager.getAuthRefreshToken().isNullOrEmpty() &&
            !tokenManager.getUsername().isNullOrEmpty()

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener { submitEmail() }
        binding.etEmail.doAfterTextChanged { binding.etEmail.error = null }
        binding.etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                submitEmail()
                true
            } else {
                false
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvLegalTerms.setOnClickListener { openUrl(getString(R.string.terms_url)) }
        binding.tvLegalPolicy.setOnClickListener { openUrl(getString(R.string.policy_url)) }
    }

    private fun submitEmail() {
        val email = binding.etEmail.text?.toString().orEmpty().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = getString(R.string.error_invalid_email)
            return
        }
        binding.etEmail.error = null
        pendingEmail = email
        viewModel.requestLoginCode(email)
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.btnLogin.isEnabled = !state.isLoading
                binding.progressBar.visibility =
                    if (state.isLoading) View.VISIBLE else View.GONE
                state.error?.let {
                    Toast.makeText(this@LoginActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.consumeError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.codeSent.collect {
                startActivity(OtpActivity.intent(this@LoginActivity, pendingEmail, OtpActivity.MODE_LOGIN))
            }
        }

        lifecycleScope.launch {
            viewModel.registrationCodeSent.collect {
                Toast.makeText(
                    this@LoginActivity, R.string.registration_code_resent, Toast.LENGTH_LONG
                ).show()
                startActivity(OtpActivity.intent(this@LoginActivity, pendingEmail, OtpActivity.MODE_REGISTER))
            }
        }
    }
}
