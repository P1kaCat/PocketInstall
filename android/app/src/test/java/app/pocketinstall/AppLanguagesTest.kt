package app.pocketinstall

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppLanguagesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE).edit().clear().commit()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @After fun clearLocale() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test fun newInstallationRequiresAnExplicitChoice() {
        assertFalse(AppLanguages.isConfirmed(context))
        // Reading or suggesting the system locale must not silently accept onboarding.
        AppLanguages.current(context)
        assertFalse(AppLanguages.isConfirmed(context))
    }

    @Test fun confirmingEachLanguagePersistsAndAppliesItsExactTag() {
        AppLanguage.entries.forEach { language ->
            assertTrue(AppLanguages.choose(context, language))
            assertTrue(AppLanguages.isConfirmed(context))
            assertEquals(language, AppLanguages.current(context))
            assertEquals(language.tag, AppCompatDelegate.getApplicationLocales().toLanguageTags())
            val anotherContext = context.createConfigurationContext(Configuration(context.resources.configuration))
            assertTrue(AppLanguages.isConfirmed(anotherContext))
        }
    }

    @Test fun deviceLocaleSuggestionsHandleRegionsAndUnsupportedLanguages() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.suggested(Locale.forLanguageTag("de-AT")))
        assertEquals(AppLanguage.ENGLISH_US, AppLanguage.suggested(Locale.UK))
        assertEquals(AppLanguage.CHINESE, AppLanguage.suggested(Locale.forLanguageTag("zh-TW")))
        assertEquals(AppLanguage.ENGLISH_US, AppLanguage.suggested(Locale.forLanguageTag("pt-BR")))
    }

    @Test fun allSixLocalesResolveTranslatedNavigationAndFormattedResources() {
        val expected = listOf("Préparer", "Prepare", "Vorbereiten", "Preparar", "準備", "准备")
        AppLanguage.entries.forEachIndexed { index, language ->
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language.tag))
            val localized = context.createConfigurationContext(configuration)
            assertEquals(expected[index], localized.getString(R.string.prepare))
            assertTrue(localized.getString(R.string.server_address, "192.168.1.4").contains("192.168.1.4"))
            assertTrue(localized.getString(R.string.split_sizes, 64).contains("64"))
            assertTrue(localized.getString(R.string.sending_winpe, 42).contains("42 %"))
        }
    }
}
