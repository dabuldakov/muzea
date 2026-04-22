package com.example.muzea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.databinding.ActivityRegisterBinding
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.AuthRepository
import com.example.muzea.ui.MainActivity
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация ViewModel
        val tokenManager = TokenManager(applicationContext)
        val apiService = RetrofitClient(tokenManager).apiService
        val authRepository = AuthRepository(apiService, tokenManager)
        viewModel = AuthViewModel(authRepository)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val fullName = binding.etFullName.text.toString().trim()

            if (validateInput(username, email, password, confirmPassword, fullName)) {
                viewModel.register(username, email, password, fullName)
            }
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        fullName: String
    ): Boolean {
        if (username.length < 3) {
            binding.etUsername.error = "Username must be at least 3 characters"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Valid email required"
            return false
        }
        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            return false
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }
        if (fullName.isEmpty()) {
            binding.etFullName.error = "Full name required"
            return false
        }
        return true
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.registerResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.btnRegister.isEnabled = false
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.btnRegister.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this@RegisterActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finish()
                    }
                    is NetworkResult.Error -> {
                        binding.btnRegister.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}