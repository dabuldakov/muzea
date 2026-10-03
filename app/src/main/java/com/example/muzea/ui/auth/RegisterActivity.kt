package com.example.muzea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.R
import com.example.muzea.databinding.ActivityRegisterBinding
import com.example.muzea.ui.MainActivity
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private companion object {
        const val POLICY_URL = "https://muzea.su/privacy"
        const val TERMS_URL = "https://muzea.su/terms"
    }

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.tvRegisterTerms.setOnClickListener { openLegalDocument(TERMS_URL) }
        binding.tvRegisterPolicy.setOnClickListener { openLegalDocument(POLICY_URL) }

        binding.btnRegister.setOnClickListener {
            if (!binding.cbConsent.isChecked) {
                binding.cbConsent.error = getString(R.string.consent_required)
                Toast.makeText(this, R.string.consent_required, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

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

    private fun openLegalDocument(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
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