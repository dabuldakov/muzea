package com.example.muzea.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.muzea.R
import com.example.muzea.databinding.ActivityOtpBinding
import com.example.muzea.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Ввод одноразового кода из письма. Используется и для входа (passwordless),
 * и для подтверждения email при регистрации — режим задаётся [EXTRA_MODE].
 */
@AndroidEntryPoint
class OtpActivity : AppCompatActivity() {

    companion object {
        const val MODE_LOGIN = "login"
        const val MODE_REGISTER = "register"

        private const val EXTRA_EMAIL = "email"
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, email: String, mode: String): Intent =
            Intent(context, OtpActivity::class.java)
                .putExtra(EXTRA_EMAIL, email)
                .putExtra(EXTRA_MODE, mode)
    }

    private lateinit var binding: ActivityOtpBinding
    private val viewModel: AuthViewModel by viewModels()

    private lateinit var email: String
    private lateinit var mode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOGIN

        binding.tvOtpSubtitle.text = getString(R.string.otp_subtitle, email)

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.length < 4) {
                binding.etCode.error = "Введите код из письма"
                return@setOnClickListener
            }
            if (mode == MODE_LOGIN) {
                viewModel.verifyLoginCode(email, code)
            } else {
                viewModel.verifyEmail(email, code)
            }
        }

        binding.btnResend.setOnClickListener {
            if (mode == MODE_LOGIN) viewModel.requestLoginCode(email)
            else viewModel.resendVerification(email)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.btnVerify.isEnabled = !state.isLoading
                binding.btnResend.isEnabled = !state.isLoading
                binding.progressBar.visibility =
                    if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE
                state.error?.let {
                    Toast.makeText(this@OtpActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.consumeError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.codeSent.collect {
                Toast.makeText(this@OtpActivity, "Код отправлен повторно", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            viewModel.authenticated.collect {
                startActivity(Intent(this@OtpActivity, MainActivity::class.java))
                finishAffinity()
            }
        }
    }
}
