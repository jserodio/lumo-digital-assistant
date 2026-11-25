package com.pocketdev.lumo_digital_assistant

import android.content.Context
import android.content.SharedPreferences

/** Bridge settings (stored in SharedPreferences, no extra dependencies). */
class BridgePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("lumo_bridge", Context.MODE_PRIVATE)

    /** Copy the question to the clipboard when the assistant is invoked with text. */
    var copyToClipboard: Boolean
        get() = prefs.getBoolean(KEY_COPY, true)
        set(value) = prefs.edit().putBoolean(KEY_COPY, value).apply()

    /** Try to type and send the question inside Lumo via accessibility. */
    var autoSend: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEND, value).apply()

    /** Show a toast when redirecting. */
    var showToast: Boolean
        get() = prefs.getBoolean(KEY_TOAST, true)
        set(value) = prefs.edit().putBoolean(KEY_TOAST, value).apply()

    /** Package name chosen manually (empty = auto-detect). */
    var overridePackage: String
        get() = prefs.getString(KEY_PACKAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PACKAGE, value).apply()

    /** Interface language, "en" by default. Persisted across restarts. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, LocaleHelper.ENGLISH) ?: LocaleHelper.ENGLISH
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    private companion object {
        const val KEY_COPY = "copy_to_clipboard"
        const val KEY_AUTO_SEND = "auto_send"
        const val KEY_TOAST = "show_toast"
        const val KEY_PACKAGE = "override_package"
        const val KEY_LANGUAGE = "language"
    }
}
