package com.pocketdev.lumo_digital_assistant

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/**
 * Entry point for the ASSISTANT role.
 *
 * Android launches this activity when the user invokes the digital assistant
 * (corner gesture, long-press power, long-press home, or the
 * "Default apps > Digital assistant" setting). It draws nothing: it forwards
 * the invocation to Lumo and closes immediately.
 */
class AssistantEntryActivity : Activity() {

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLocale(it) })
    }

    companion object {
        const val EXTRA_TEST_QUERY = "com.pocketdev.lumo_digital_assistant.TEST_QUERY"
        const val EXTRA_LUMO_MISSING = "com.pocketdev.lumo_digital_assistant.LUMO_MISSING"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent?) {
        val prefs = BridgePrefs(this)
        val query = extractQuery(intent)
        val lumo = LumoBridge.findLumo(this, prefs.overridePackage)

        if (lumo == null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(EXTRA_LUMO_MISSING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
            return
        }

        if (query.isNotBlank() && LumoBridge.tryShareToLumo(this, lumo.packageName, query)) {
            // Lumo accepted the text directly (not the case in current versions).
            finish()
            return
        }

        val launch = LumoBridge.launchIntent(this, lumo.packageName)
        if (launch == null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(EXTRA_LUMO_MISSING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
            return
        }

        var autoSendActive = false
        if (query.isNotBlank()) {
            if (prefs.copyToClipboard) LumoBridge.copyToClipboard(this, query)
            if (prefs.autoSend && AssistAutomationService.isEnabled(this)) {
                autoSendActive = AssistAutomationService.queueQuery(query)
            }
        }

        try {
            startActivity(launch)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.lumo_launch_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (prefs.showToast && query.isNotBlank()) {
            val message = when {
                autoSendActive -> getString(R.string.toast_auto_send)
                prefs.copyToClipboard -> getString(R.string.toast_copied)
                else -> null
            }
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    /** Extracts a spoken/typed question from the assistant invocation. */
    private fun extractQuery(intent: Intent?): String {
        if (intent == null) return ""
        try {
            intent.getStringExtra(SearchManager.QUERY)?.let { if (it.isNotBlank()) return it.trim() }
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let {
                if (it.isNotBlank()) return it.toString().trim()
            }
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { if (it.isNotBlank()) return it.trim() }
            intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { if (it.isNotBlank()) return it.trim() }
            intent.getStringExtra(EXTRA_TEST_QUERY)?.let { if (it.isNotBlank()) return it.trim() }
            // Some OEMs forward the assistant structure with the selected text.
            intent.getStringExtra("assistQuery")?.let { if (it.isNotBlank()) return it.trim() }
        } catch (e: Exception) {
            // Intent without extras or with unexpected extra types.
        }
        return ""
    }
}

/** Introspection helpers used by the UI. */
internal object AssistantStatus {

    /** Package/activity the system has as assistant (Settings.Secure.assistant). */
    fun systemAssistantComponent(activity: Activity): String {
        return try {
            android.provider.Settings.Secure.getString(
                activity.contentResolver,
                "assistant",
            ) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun isUsHoldingRole(activity: Activity): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = activity.getSystemService(android.app.role.RoleManager::class.java)
                rm?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
            } else {
                systemAssistantComponent(activity).startsWith(activity.packageName)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isRoleAvailable(activity: Activity): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = activity.getSystemService(android.app.role.RoleManager::class.java)
                rm?.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT) == true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isInstalled(activity: Activity, pkg: String): Boolean {
        return try {
            activity.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
