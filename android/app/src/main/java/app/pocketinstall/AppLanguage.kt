package app.pocketinstall

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(val tag: String, val displayName: String, val continueLabel: String) {
    FRENCH("fr", "Français", "Continuer"),
    ENGLISH_US("en-US", "English (US)", "Continue"),
    GERMAN("de", "Deutsch", "Weiter"),
    SPANISH("es", "Español", "Continuar"),
    JAPANESE("ja", "日本語", "続ける"),
    CHINESE("zh-Hans", "中文（简体）", "继续");

    companion object {
        fun suggested(locale: Locale): AppLanguage = entries.firstOrNull {
            Locale.forLanguageTag(it.tag).language == locale.language
        } ?: ENGLISH_US
    }
}

object AppLanguages {
    private fun preferences(context: Context) = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)

    fun isConfirmed(context: Context): Boolean = preferences(context).getBoolean("confirmed", false)

    fun current(context: Context): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        val locale = if (!locales.isEmpty) locales[0] else context.resources.configuration.locales[0]
        return AppLanguage.suggested(locale ?: Locale.US)
    }

    fun choose(context: Context, language: AppLanguage): Boolean {
        // Persist before applying the locale: AppCompat may recreate the activity immediately.
        if (!preferences(context).edit().putBoolean("confirmed", true).commit()) return false
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        return true
    }
}
