package com.example.muzea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.databinding.ActivityLoginBinding
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.ui.MainActivity
import com.example.muzea.utils.ConsentManager
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private companion object {
        const val POLICY_URL = "https://muzea.su/privacy"
        const val TERMS_URL = "https://muzea.su/terms"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Без согласия на обработку персональных данных приложение не запускается (ст. 9 ФЗ-152).
        if (!ConsentManager.isAccepted(this)) {
            startActivity(Intent(this, ConsentActivity::class.java))
            finish()
            return
        }

        val tokenManager = TokenManager(applicationContext)
        // Уже авторизованы (в т.ч. при переходе из пуш-уведомления) — сразу на главный экран.
        if (!tokenManager.getToken().isNullOrEmpty() && !tokenManager.getUsername().isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация ViewModel ДО observeViewModel
        val apiService = RetrofitClient(tokenManager).apiService
        val authRepository = AuthRepository(apiService, tokenManager)
        viewModel = AuthViewModel(authRepository)

        setupClickListeners()
        observeViewModel()  // Теперь viewModel инициализирована
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                viewModel.login(username, password)
            } else {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.loginResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.btnLogin.isEnabled = false
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.btnLogin.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                    is NetworkResult.Error -> {
                        binding.btnLogin.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}