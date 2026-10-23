package com.example.muzea.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.R
import com.example.muzea.databinding.ActivityRegisterBinding
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

    private var pendingEmail: String = ""

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
            val fullName = binding.etFullName.text.toString().trim()

            if (validateInput(username, email)) {
                pendingEmail = email
                viewModel.register(email, username, fullName)
            }
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun openLegalDocument(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }

    private fun validateInput(username: String, email: String): Boolean {
        if (username.length < 3) {
            binding.etUsername.error = getString(R.string.error_username_min)
            return false
        }
        if (!username.matches(Regex("^[A-Za-z0-9_.]+$"))) {
            binding.etUsername.error = getString(R.string.error_username_charset)
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = getString(R.string.error_invalid_email)
            return false
        }
        return true
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.btnRegister.isEnabled = !state.isLoading
                binding.progressBar.visibility =
                    if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
                state.error?.let {
                    Toast.makeText(this@RegisterActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.consumeError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.codeSent.collect {
                startActivity(OtpActivity.intent(this@RegisterActivity, pendingEmail, OtpActivity.MODE_REGISTER))
            }
        }
    }
}
