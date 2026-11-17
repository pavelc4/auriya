package dev.auriya.app.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import dev.auriya.app.R
import java.util.Locale

data class SupportedLanguage(
    val tag: String,
    val nativeName: String,
    val localizedName: String,
)

object LocaleHelper {
    const val SYSTEM = "system"

    private const val PREFS_NAME = "auriya_locale_pref"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"

    private val _currentLanguage = kotlinx.coroutines.flow.MutableStateFlow(SYSTEM)
    val currentLanguage: kotlinx.coroutines.flow.StateFlow<String> = _currentLanguage

    /**
     * Supported language BCP-47 tags in Auriya, synchronized with Crowdin target languages.
     */
    val SUPPORTED_TAGS =
        listOf(
            "en",
            "id",
            "zh-CN",
            "zh-TW",
            "ja",
            "ko",
            "ru",
            "de",
            "fr",
            "es-ES",
            "pt-BR",
            "pt-PT",
            "it",
            "tr",
            "uk",
            "vi",
            "pl",
            "ar",
            "cs",
            "el",
            "hu",
            "nl",
            "ro",
            "sv-SE",
            "da",
            "fi",
            "no",
            "ca",
            "af",
            "sr",
        )

    fun getSystemLocale(): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources
                .getSystem()
                .configuration.locales
                .get(0) ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale ?: Locale.getDefault()
        }

    fun getSystemLocaleList(): LocaleList =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList.getDefault()
        }

    fun getSupportedLanguages(context: Context): List<SupportedLanguage> {
        val list = mutableListOf<SupportedLanguage>()

        // 1. Follow System option
        list.add(
            SupportedLanguage(
                tag = SYSTEM,
                nativeName = context.getString(R.string.language_system),
                localizedName = context.getString(R.string.language_system_desc),
            ),
        )

        // 2. Dynamic generation for all supported language tags
        for (tag in SUPPORTED_TAGS) {
            list.add(
                SupportedLanguage(
                    tag = tag,
                    nativeName = getLanguageNativeName(context, tag),
                    localizedName = getLanguageLocalizedName(context, tag),
                ),
            )
        }
        return list
    }

    fun getLanguageNativeName(context: Context, tag: String): String {
        val clean = tag.trim().lowercase().replace('_', '-')
        return when (clean) {
            "zh", "zh-cn" -> context.getString(R.string.language_zh_name)
            "zh-tw" -> "繁體中文"
            "en" -> context.getString(R.string.language_en_name)
            "id", "in" -> context.getString(R.string.language_id_name)
            else -> {
                val locale = parseLocale(tag)
                val raw = locale.getDisplayName(locale)
                raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            }
        }
    }

    fun getLanguageLocalizedName(context: Context, tag: String): String {
        val clean = tag.trim().lowercase().replace('_', '-')
        return when (clean) {
            "zh", "zh-cn" -> context.getString(R.string.language_zh_desc)
            "zh-tw" -> "Traditional Chinese"
            "en" -> context.getString(R.string.language_en_desc)
            "id", "in" -> context.getString(R.string.language_id_desc)
            else -> {
                val locale = parseLocale(tag)
                val raw = locale.getDisplayName(Locale.getDefault())
                raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    }

    fun getLanguageDisplayName(context: Context, tag: String): String {
        if (tag == SYSTEM || tag.isEmpty()) {
            return context.getString(R.string.language_system)
        }
        return getLanguageNativeName(context, tag)
    }

    fun setLocale(
        context: Context,
        languageCode: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_LANGUAGE, languageCode).apply()

        val isSystem = languageCode == SYSTEM || languageCode.isEmpty()
        val targetTag = if (isSystem) "" else languageCode

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (isSystem) {
                localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                val localeList = createLocaleList(targetTag)
                localeManager?.applicationLocales = localeList
            }
        }

        val targetLocale = if (isSystem) getSystemLocale() else parseLocale(targetTag)
        Locale.setDefault(targetLocale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(targetLocale)
        config.setLayoutDirection(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = if (isSystem) getSystemLocaleList() else createLocaleList(targetTag)
            config.setLocales(localeList)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        _currentLanguage.value = languageCode
    }

    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SELECTED_LANGUAGE, null)
        if (!saved.isNullOrEmpty()) {
            return normalizeTag(saved)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return SUPPORTED_TAGS.firstOrNull { tag ->
                    val target = parseLocale(tag)
                    locales.getFirstMatch(arrayOf(target.toLanguageTag(), target.language, tag)) != null
                } ?: SYSTEM
            }
        }
        return SYSTEM
    }

    fun wrapContext(
        context: Context,
        languageCode: String? = null,
    ): Context {
        val savedLang = languageCode ?: getCurrentLanguage(context)
        val isSystem = savedLang == SYSTEM || savedLang.isEmpty()
        val targetLocale = if (isSystem) getSystemLocale() else parseLocale(savedLang)
        Locale.setDefault(targetLocale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        config.setLayoutDirection(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = if (isSystem) getSystemLocaleList() else createLocaleList(savedLang)
            config.setLocales(localeList)
        }
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        return context.createConfigurationContext(config)
    }

    fun applySavedLocale(context: Context) {
        val saved = getCurrentLanguage(context)
        _currentLanguage.value = saved
        val isSystem = saved == SYSTEM || saved.isEmpty()
        val targetLocale = if (isSystem) getSystemLocale() else parseLocale(saved)
        Locale.setDefault(targetLocale)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(targetLocale)
        config.setLayoutDirection(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = if (isSystem) getSystemLocaleList() else createLocaleList(saved)
            config.setLocales(localeList)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun parseLocale(tag: String): Locale {
        val clean = tag.trim().replace('_', '-')
        return when (clean.lowercase()) {
            "id", "in" -> Locale.forLanguageTag("in-ID")
            "zh", "cn", "zh-cn" -> Locale.forLanguageTag("zh-CN")
            "zh-tw", "tw" -> Locale.forLanguageTag("zh-TW")
            "pt-br" -> Locale.forLanguageTag("pt-BR")
            "pt-pt" -> Locale.forLanguageTag("pt-PT")
            "es-es" -> Locale.forLanguageTag("es-ES")
            "sv-se" -> Locale.forLanguageTag("sv-SE")
            else -> Locale.forLanguageTag(clean)
        }
    }

    fun createLocaleList(tag: String): LocaleList =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val primary = parseLocale(tag)
            val clean = tag.trim().lowercase().replace('_', '-')
            val locales =
                when (clean) {
                    "id", "in" -> arrayOf(primary, Locale.forLanguageTag("id-ID"))
                    "zh", "zh-cn" -> arrayOf(primary, Locale.forLanguageTag("zh-Hans"))
                    "zh-tw" -> arrayOf(primary, Locale.forLanguageTag("zh-Hant"))
                    else -> arrayOf(primary)
                }
            LocaleList(*locales)
        } else {
            LocaleList.getDefault()
        }

    private fun normalizeTag(tag: String): String {
        val clean = tag.trim().replace('_', '-')
        return when (clean.lowercase()) {
            "zh", "cn" -> "zh-CN"
            "in" -> "id"
            else -> tag
        }
    }
}
