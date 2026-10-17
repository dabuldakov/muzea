package com.example.muzea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.databinding.ActivityLoginBinding
import com.example.muzea.ui.MainActivity
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private companion object {
        const val POLICY_URL = "https://muzea.su/privacy"
        const val TERMS_URL = "https://muzea.su/terms"
    }

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Без согласия на обработку персональных данных приложение не запускается (ст. 9 ФЗ-152).
        if (!ConsentManager.isAccepted(this)) {
            startActivity(Intent(this, ConsentActivity::class.java))
            finish()
            return
        }

        // Уже авторизованы (в т.ч. при переходе из пуш-уведомления) — сразу на главный экран.
        if (!tokenManager.getToken().isNullOrEmpty() && !tokenManager.getUsername().isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

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

    private var pendingEmail: String = ""

    private fun openLegalDocument(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
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
