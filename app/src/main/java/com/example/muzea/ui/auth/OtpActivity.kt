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
import com.example.muzea.utils.PendingPush
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.muzea.ui.common.SystemBars

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

    private val cooldown = ResendCooldown()
    private var cooldownJob: Job? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOGIN

        binding.tvOtpSubtitle.text = getString(R.string.otp_subtitle, email)

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            if (code.length < 4) {
                binding.etCode.error = getString(R.string.error_enter_code)
                return@setOnClickListener
            }
            if (mode == MODE_LOGIN) {
                viewModel.verifyLoginCode(email, code)
            } else {
                viewModel.verifyEmail(email, code)
            }
        }

        binding.btnResend.setOnClickListener {
            // Сервер не даст новый код раньше, чем через минуту: не тратим
            // запрос и не показываем 429.
            if (cooldown.active || loading) return@setOnClickListener
            startCooldown()
            if (mode == MODE_LOGIN) viewModel.requestLoginCode(email)
            else viewModel.resendVerification(email)
        }

        observeViewModel()
        // Код только что отправлен предыдущим экраном — сразу показываем
        // обратный отсчёт, а не активную кнопку.
        startCooldown()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                loading = state.isLoading
                binding.btnVerify.isEnabled = !state.isLoading
                renderResend()
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
                Toast.makeText(this@OtpActivity, R.string.otp_code_resent, Toast.LENGTH_SHORT).show()
                startCooldown()
            }
        }

        lifecycleScope.launch {
            viewModel.authenticated.collect {
                // Если вход начался с тапа по push, после авторизации сразу
                // открываем нужный чат/новость.
                val next = Intent(this@OtpActivity, MainActivity::class.java)
                PendingPush.applyTo(next)
                startActivity(next)
                finishAffinity()
            }
        }
    }

    private fun startCooldown() {
        cooldown.start()
        renderResend()
        cooldownJob?.cancel()
        cooldownJob = lifecycleScope.launch {
            while (isActive && cooldown.active) {
                delay(1_000)
                cooldown.tick()
                renderResend()
            }
        }
    }

    private fun renderResend() {
        binding.btnResend.isEnabled = !cooldown.active && !loading
        binding.btnResend.text = if (cooldown.active) {
            getString(R.string.otp_resend_in, cooldown.seconds)
        } else {
            getString(R.string.otp_resend)
        }
    }

    override fun onDestroy() {
        cooldownJob?.cancel()
        super.onDestroy()
    }
}
