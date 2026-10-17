package com.example.muzea.ui.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.R
import com.example.muzea.databinding.ActivityLoginBinding
import com.example.muzea.ui.MainActivity
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.TokenManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private companion object {
        const val POLICY_URL = "https://muzea.su/privacy"
        const val TERMS_URL = "https://muzea.su/terms"
        const val PREFS = "app_prefs"
        const val KEY_REAUTH_NOTICE_SHOWN = "reauth_notice_shown_v1"
    }

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private var pendingEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Без согласия на обработку персональных данных приложение не запускается (ст. 9 ФЗ-152).
        if (!ConsentManager.isAccepted(this)) {
            startActivity(Intent(this, ConsentActivity::class.java))
            finish()
            return
        }

        // Сессия нового образца (есть access+refresh) → сразу на главный.
        if (isUpToDateSession()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        // Старая сессия (legacy-токен без refresh) не заведётся в новом входе —
        // сбрасываем и показываем экран входа с пояснением.
        if (!tokenManager.getToken().isNullOrEmpty()) {
            tokenManager.clearToken()
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
        showReauthNoticeIfNeeded()
    }

    private fun isUpToDateSession(): Boolean =
        !tokenManager.getToken().isNullOrEmpty() &&
            !tokenManager.getAuthRefreshToken().isNullOrEmpty() &&
            !tokenManager.getUsername().isNullOrEmpty()

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                pendingEmail = email
                viewModel.requestLoginCode(email)
            } else {
                binding.etEmail.error = "Введите корректный email"
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvLegalTerms.setOnClickListener { openLegalDocument(TERMS_URL) }
        binding.tvLegalPolicy.setOnClickListener { openLegalDocument(POLICY_URL) }
    }

    private fun openLegalDocument(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }

    /**
     * Одноразовая подсказка после перехода на вход по email: существующим
     * пользователям нужно «зарегистрироваться» (привязать аккаунт) с прежними
     * email и username.
     */
    private fun showReauthNoticeIfNeeded() {
        val prefs: SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REAUTH_NOTICE_SHOWN, false)) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reauth_notice_title)
            .setMessage(R.string.reauth_notice_body)
            .setCancelable(false)
            .setPositiveButton(R.string.reauth_notice_register) { _, _ ->
                prefs.edit().putBoolean(KEY_REAUTH_NOTICE_SHOWN, true).apply()
                startActivity(Intent(this, RegisterActivity::class.java))
            }
            .setNegativeButton(R.string.reauth_notice_ok) { _, _ ->
                prefs.edit().putBoolean(KEY_REAUTH_NOTICE_SHOWN, true).apply()
            }
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.btnLogin.isEnabled = !state.isLoading
                binding.progressBar.visibility =
                    if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
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
    }
}
