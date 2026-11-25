package com.pocketdev.lumo_digital_assistant

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import java.util.Locale

/**
 * Keeps the in-app language independent from the system locale.
 *
 * The preference is stored in [BridgePrefs] (English by default) and applied in
 * `attachBaseContext()` of every entry point, so Compose, Toasts and the
 * accessibility service all see the same language.
 */
object LocaleHelper {

    const val ENGLISH = "en"
    const val SPANISH = "es"

    private const val ALIAS_EN = "com.pocketdev.lumo_digital_assistant.MainActivityEn"
    private const val ALIAS_ES = "com.pocketdev.lumo_digital_assistant.MainActivityEs"

    /** Returns a context whose resources are loaded in the selected language. */
    fun applyLocale(context: Context): Context {
        val language = BridgePrefs(context).language
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * The launcher reads the label from the enabled activity alias. Two aliases
     * with fixed labels let the app name change without relying on the system
     * locale (which the app cannot modify from outside its process).
     */
    fun syncLauncherAlias(context: Context, language: String) {
        val enable: String
        val disable: String
        if (language == SPANISH) {
            enable = ALIAS_ES
            disable = ALIAS_EN
        } else {
            enable = ALIAS_EN
            disable = ALIAS_ES
        }
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            ComponentName(context, enable),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            ComponentName(context, disable),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
