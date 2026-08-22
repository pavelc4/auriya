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
    const val ENGLISH = "en"
    const val INDONESIAN = "id"

    private const val PREFS_NAME = "auriya_locale_pref"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"

    private val _currentLanguage = kotlinx.coroutines.flow.MutableStateFlow(SYSTEM)
    val currentLanguage: kotlinx.coroutines.flow.StateFlow<String> = _currentLanguage

    /**
     * Supported language BCP-47 tags in Auriya.
     * When new translations are contributed (e.g. values-es, values-ru),
     * simply add their tag here.
     */
    val SUPPORTED_TAGS =
        listOf(
            "en",
            "id",
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

        // 2. Dynamic generation for each supported language tag
        for (tag in SUPPORTED_TAGS) {
            when (tag.lowercase()) {
                "en" -> {
                    list.add(
                        SupportedLanguage(
                            tag = "en",
                            nativeName = "English",
                            localizedName = "",
                        ),
                    )
                }

                "id", "in" -> {
                    list.add(
                        SupportedLanguage(
                            tag = "id",
                            nativeName = "Indonesia",
                            localizedName = "",
                        ),
                    )
                }

                else -> {
                    val locale = parseLocale(tag)
                    val nativeName =
                        locale.getDisplayName(locale).substringBefore(" (").replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                        }
                    list.add(
                        SupportedLanguage(
                            tag = tag,
                            nativeName = nativeName,
                            localizedName = "",
                        ),
                    )
                }
            }
        }
        return list
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
            return saved
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                val tag = locales.get(0)?.language?.lowercase()
                return tag ?: SYSTEM
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

    private fun parseLocale(tag: String): Locale =
        when (tag.lowercase()) {
            "id", "in" -> Locale.forLanguageTag("in-ID")
            else -> Locale.forLanguageTag(tag)
        }

    private fun createLocaleList(tag: String): LocaleList =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            when (tag.lowercase()) {
                "id", "in" -> LocaleList(Locale.forLanguageTag("in-ID"), Locale.forLanguageTag("id-ID"))
                else -> LocaleList(Locale.forLanguageTag(tag))
            }
        } else {
            LocaleList.getDefault()
        }
}
