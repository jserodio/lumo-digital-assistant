package com.pocketdev.lumo_digital_assistant

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * OPTIONAL (experimental) accessibility service.
 *
 * Lumo is a WebView shell and currently ignores incoming intents, so there is
 * no "official" way to feed it a question. When this service is enabled, the
 * bridge uses it to type the question into Lumo's text box and tap send. If
 * something is missing (another version, another UI), the question stays on
 * the clipboard as a safety net.
 *
 * The service is limited to Lumo packages (see
 * res/xml/accessibility_service_config.xml), so it cannot read other apps.
 */
class AssistAutomationService : AccessibilityService() {

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLocale(it) })
    }

    private val handler = Handler(Looper.getMainLooper())
    private var query: String? = null
    private var attempts = 0
    private var textWritten = false
    private var sendAttempts = 0

    private val attemptRunnable = object : Runnable {
        override fun run() {
            val currentQuery = query ?: return
            attempts++
            val done = tryInteract(currentQuery)
            if (done || attempts >= MAX_ATTEMPTS) {
                stopAutomation()
                return
            }
            handler.postDelayed(this, RETRY_DELAY_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (query == null || !isLumoPackage(pkg)) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            handler.removeCallbacks(attemptRunnable)
            handler.post(attemptRunnable)
        }
    }

    override fun onInterrupt() {
        stopAutomation()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        stopAutomation()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun isLumoPackage(pkg: String): Boolean = pkg.contains("lumo", ignoreCase = true)

    private fun startAutomation(newQuery: String) {
        stopAutomation()
        query = newQuery
        attempts = 0
        textWritten = false
        sendAttempts = 0
        handler.postDelayed(attemptRunnable, INITIAL_DELAY_MS)
    }

    private fun stopAutomation() {
        handler.removeCallbacks(attemptRunnable)
        query = null
        attempts = 0
        textWritten = false
        sendAttempts = 0
    }

    /** Returns true when there is nothing left to do. */
    private fun tryInteract(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && !isLumoPackage(pkg)) return false

        if (!textWritten) {
            val editable = findEditable(root, 0) ?: return false
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text,
                )
            }
            val written = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!written) return false
            textWritten = true
            return false
        }

        // Once typed, look for the send button (a couple of attempts).
        sendAttempts++
        val send = findSendTarget(root, 0)
        if (send != null) {
            send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val editable = findEditable(root, 0)
            if (editable != null && sendAttempts >= 2) {
                editable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                return true
            }
        }
        return false
    }

    private fun findEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_DEPTH) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            findEditable(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun findSendTarget(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_DEPTH) return null
        val haystack = buildString {
            node.contentDescription?.let { append(it).append(' ') }
            node.text?.let { append(it).append(' ') }
        }.lowercase()
        if (haystack.isNotBlank() && SEND_TOKENS.any { haystack.contains(it) }) {
            var candidate: AccessibilityNodeInfo? = node
            var hops = 0
            while (candidate != null && hops < 4) {
                if (candidate.isClickable) return candidate
                candidate = try {
                    candidate.parent
                } catch (e: Exception) {
                    null
                }
                hops++
            }
        }
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            findSendTarget(child, depth + 1)?.let { return it }
        }
        return null
    }

    companion object {
        private const val INITIAL_DELAY_MS = 700L
        private const val RETRY_DELAY_MS = 450L
        private const val MAX_ATTEMPTS = 22
        private const val MAX_DEPTH = 40

        private val SEND_TOKENS = listOf(
            "enviar", "send", "ask", "preguntar", "submit",
        )

        @Volatile
        private var instance: AssistAutomationService? = null

        /** True if the user enabled the service in accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, AssistAutomationService::class.java)
            val flat = component.flattenToString()
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: return false
                enabled.split(':').any { entry ->
                    entry.equals(flat, ignoreCase = true) ||
                        entry.equals(component.flattenToShortString(), ignoreCase = true)
                }
            } catch (e: Exception) {
                false
            }
        }

        /** True if the service is active and connected (can act now). */
        fun isConnected(): Boolean = instance != null

        /** Queues the question to type and send it in Lumo. */
        fun queueQuery(query: String): Boolean {
            val service = instance ?: return false
            return try {
                service.startAutomation(query)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
