package com.example.muzea.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.muzea.R
import com.example.muzea.databinding.ActivityConsentBinding
import com.example.muzea.utils.ConsentManager

/**
 * Экран согласия на обработку персональных данных. Показывается при первом запуске
 * и повторно, если редакция согласия изменилась (ConsentManager.VERSION).
 * Пока согласие не принято, остальные экраны приложения недоступны.
 */
class ConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvConsentBody.text = getString(R.string.consent_body, getString(R.string.operator_email))

        binding.tvOpenTerms.setOnClickListener { openUrl(getString(R.string.terms_url)) }
        binding.tvOpenPolicy.setOnClickListener { openUrl(getString(R.string.policy_url)) }

        binding.btnAccept.setOnClickListener {
            ConsentManager.accept(this)
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }

        binding.btnDecline.setOnClickListener { finish() }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
