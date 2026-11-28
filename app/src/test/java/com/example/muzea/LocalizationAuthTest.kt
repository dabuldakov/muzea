package com.example.muzea

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Строки экранов входа, регистрации и кода должны быть во всех языках. */
class LocalizationAuthTest {

    private val authKeys = listOf(
        "login_subtitle",
        "login_title",
        "login_caption",
        "login_legal_note",
        "login_no_account",
        "login_have_account",
        "register_create_account",
        "register_caption",
        "registration_code_resent",
        "otp_title",
        "otp_subtitle",
        "otp_hint_code",
        "otp_verify",
        "otp_resend",
        "otp_code_resent",
        "action_get_code",
        "action_register",
        "hint_email",
        "hint_username",
        "hint_full_name",
        "consent_open_terms",
        "consent_open_policy",
        "consent_required",
        "error_invalid_email",
        "error_username_min",
        "error_username_charset",
        "error_enter_code"
    )

    @Test
    fun `auth strings are present and filled in every locale`() {
        val localeDirs = resDir().listFiles { file ->
            file.isDirectory && file.name.startsWith("values-") && file.name != "values-night"
        }.orEmpty()

        assertTrue("Не найдены локали", localeDirs.size >= 12)
        localeDirs.forEach { dir ->
            val xml = File(dir, "strings.xml").readText()
            authKeys.forEach { key ->
                val value = Regex(
                    """<string name="$key"[^>]*>(.*?)</string>""",
                    RegexOption.DOT_MATCHES_ALL
                ).find(xml)?.groupValues?.get(1)

                assertTrue("${dir.name}: нет строки $key", value != null)
                assertFalse("${dir.name}: пустая строка $key", value!!.isBlank())
            }
        }
    }

    private fun resDir(): File =
        listOf(File("src/main/res"), File("app/src/main/res")).first { it.exists() }
}
