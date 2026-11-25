package com.pocketdev.lumo_digital_assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * Locates the official Proton Lumo app and builds the intents to open it.
 *
 * Lumo (me.proton.android.lumo) is a WebView shell that only declares
 * MAIN/LAUNCHER: it does not declare android.intent.action.ASSIST (which is why
 * Android does not offer it as a digital assistant), it does not accept
 * ACTION_SEND, and its MainActivity ignores incoming intents. Therefore the
 * only way to "route" the assistant to Lumo is to launch it explicitly from
 * this bridge app.
 */
object LumoBridge {

    /** Known Lumo packages (Play Store / F-Droid / alternative builds). */
    private val KNOWN_PACKAGES = listOf(
        "me.proton.android.lumo",
        "ch.proton.lumo",
        "proton.lumo",
        "me.proton.lumo",
    )

    const val DEFAULT_PACKAGE = "me.proton.android.lumo"

    data class LumoApp(
        val packageName: String,
        val label: String,
        val versionName: String?,
        val isLaunchable: Boolean,
    )

    fun isInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun toLumoApp(context: Context, packageName: String): LumoApp? {
        val pm = context.packageManager
        val info = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val label = pm.getApplicationLabel(info).toString()
        val versionName = try {
            pm.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        return LumoApp(
            packageName = packageName,
            label = label,
            versionName = versionName,
            isLaunchable = pm.getLaunchIntentForPackage(packageName) != null,
        )
    }

    @Suppress("DEPRECATION")
    private fun launchableApps(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                pm.queryIntentActivities(intent, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Candidate packages, ordered: known ones first, then detected ones. */
    fun candidatePackages(context: Context): List<String> {
        val result = LinkedHashMap<String, String>()
        for (pkg in KNOWN_PACKAGES) {
            if (isInstalled(context, pkg)) result[pkg] = pkg
        }
        val pm = context.packageManager
        for (info in launchableApps(pm)) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                ""
            }
            val haystack = (pkg + " " + label).lowercase()
            if (!haystack.contains("lumo")) continue
            val looksLikeProton = haystack.contains("proton")
            // Proton-looking packages go before generic "Lumo" apps.
            if (looksLikeProton) result[pkg] = pkg else result.putIfAbsent(pkg, pkg)
        }
        return result.keys.toList()
    }

    fun findLumo(context: Context, preferredPackage: String? = null): LumoApp? {
        if (!preferredPackage.isNullOrBlank()) {
            toLumoApp(context, preferredPackage)?.let { return it }
        }
        for (pkg in candidatePackages(context)) {
            toLumoApp(context, pkg)?.let { return it }
        }
        return null
    }

    /** Normal launch intent (MAIN/LAUNCHER) for Lumo. */
    fun launchIntent(context: Context, packageName: String): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return intent
    }

    /**
     * If Lumo ever accepts text through ACTION_SEND, use it so the question
     * travels with the app. It does not declare it today, so this returns false.
     */
    @Suppress("DEPRECATION")
    fun tryShareToLumo(context: Context, packageName: String, query: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, query)
            setPackage(packageName)
        }
        val resolved = try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.queryIntentActivities(
                    send,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                context.packageManager.queryIntentActivities(send, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (resolved.isNullOrEmpty()) return false
        return try {
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(send)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        if (text.isBlank()) return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.clipboard_label), text),
            )
        } catch (e: Exception) {
            // Ignored: the clipboard may be restricted.
        }
    }
}
