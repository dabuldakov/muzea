package com.example.muzea.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.muzea.R
import com.example.muzea.databinding.ActivityOperatorInfoBinding
import com.example.muzea.ui.common.SystemBars

/**
 * Сведения об операторе персональных данных (ч. 1 ст. 19 ФЗ-152): ФИО, ИНН, адрес,
 * контакты для обращений и ссылки на правовые документы.
 */
class OperatorInfoActivity : AppCompatActivity() {

    private companion object {
        const val POLICY_URL = "https://muzea.su/privacy"
        const val TERMS_URL = "https://muzea.su/terms"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityOperatorInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this)

        val email = getString(R.string.operator_email)

        binding.tvOperatorEmail.setOnClickListener { open("mailto:" + email) }
        binding.tvOperatorPhone.setOnClickListener { open("tel:${getString(R.string.operator_phone)}") }
        binding.tvOperatorPolicy.setOnClickListener { open(POLICY_URL) }
        binding.tvOperatorTerms.setOnClickListener { open(TERMS_URL) }
    }

    private fun open(target: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
    }
}
