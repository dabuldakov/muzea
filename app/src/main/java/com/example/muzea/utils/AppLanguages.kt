package com.example.muzea.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Язык приложения: код локали, самоназвание и флаг для списка выбора. */
data class AppLanguage(val tag: String, val nativeName: String, val flag: String)

/**
 * Поддерживаемые языки и переключение языка приложения (per-app language).
 * Список должен совпадать с res/xml/locales_config.xml.
 */
object AppLanguages {

    val ALL: List<AppLanguage> = listOf(
        AppLanguage("ru", "Русский", "🇷🇺"),
        AppLanguage("kk", "Қазақша", "🇰🇿"),
        AppLanguage("be", "Беларуская", "🇧🇾"),
        AppLanguage("mn", "Монгол", "🇲🇳"),
        AppLanguage("uk", "Українська", "🇺🇦"),
        AppLanguage("en", "English", "🇬🇧"),
        AppLanguage("zh-CN", "中文", "🇨🇳"),
        AppLanguage("hi", "हिन्दी", "🇮🇳"),
        AppLanguage("es", "Español", "🇪🇸"),
        AppLanguage("fr", "Français", "🇫🇷"),
        AppLanguage("ar", "العربية", "🇸🇦"),
        AppLanguage("bn", "বাংলা", "🇧🇩"),
        AppLanguage("pt", "Português", "🇵🇹"),
        AppLanguage("ur", "اردو", "🇵🇰")
    )

    /** Выбранный язык приложения либо язык системы, приведённый к нашему списку. */
    fun current(): AppLanguage {
        val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .split(",")
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
        val tag = selected ?: Locale.getDefault().toLanguageTag()
        return ALL.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: ALL.firstOrNull {
                it.tag.substringBefore('-').equals(tag.substringBefore('-'), ignoreCase = true)
            }
            ?: ALL.first()
    }

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun label(language: AppLanguage): String = "${language.flag}  ${language.nativeName}"
}
