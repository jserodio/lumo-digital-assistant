package com.pocketdev.lumo_digital_assistant

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {

    private lateinit var prefs: BridgePrefs

    override fun attachBaseContext(newBase: android.content.Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLocale(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = BridgePrefs(this)
        // Make sure the enabled launcher alias matches the saved language.
        LocaleHelper.syncLauncherAlias(this, prefs.language)
        val lumoMissing =
            intent?.getBooleanExtra(AssistantEntryActivity.EXTRA_LUMO_MISSING, false) == true
        setContent {
            MaterialTheme {
                BridgeScreen(prefs = prefs, lumoMissing = lumoMissing)
            }
        }
    }
}

private data class BridgeStatus(
    val lumo: LumoBridge.LumoApp?,
    val candidates: List<String>,
    val roleAvailable: Boolean,
    val roleHeld: Boolean,
    val assistComponent: String,
    val accessibilityEnabled: Boolean,
)

private fun readStatus(activity: android.app.Activity, prefs: BridgePrefs): BridgeStatus {
    return BridgeStatus(
        lumo = LumoBridge.findLumo(activity, prefs.overridePackage),
        candidates = LumoBridge.candidatePackages(activity),
        roleAvailable = AssistantStatus.isRoleAvailable(activity),
        roleHeld = AssistantStatus.isUsHoldingRole(activity),
        assistComponent = AssistantStatus.systemAssistantComponent(activity),
        accessibilityEnabled = AssistAutomationService.isEnabled(activity),
    )
}

private fun openIntent(activity: android.app.Activity, intent: Intent) {
    try {
        activity.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No matching screen on this device.
    }
}

private fun requestAssistantRole(activity: android.app.Activity) {
    if (Build.VERSION.SDK_INT >= 29) {
        val rm = activity.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
            !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        ) {
            openIntent(activity, rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            return
        }
    }
    openIntent(activity, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BridgeScreen(prefs: BridgePrefs, lumoMissing: Boolean) {
    val activity = LocalContext.current as android.app.Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(readStatus(activity, prefs)) }
    var copyQuestion by remember { mutableStateOf(prefs.copyToClipboard) }
    var autoSend by remember { mutableStateOf(prefs.autoSend) }
    var showToast by remember { mutableStateOf(prefs.showToast) }
    var testQuery by remember { mutableStateOf("") }
    var pkgOverride by remember { mutableStateOf(prefs.overridePackage) }
    var language by remember { mutableStateOf(prefs.language) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = readStatus(activity, prefs)
                copyQuestion = prefs.copyToClipboard
                autoSend = prefs.autoSend
                showToast = prefs.showToast
                language = prefs.language
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (lumoMissing) {
                StatusCard(
                    title = stringResource(R.string.lumo_missing_title),
                    lines = listOf(stringResource(R.string.lumo_missing_line)),
                    positive = false,
                )
            }

            StatusCard(
                title = stringResource(R.string.status_title),
                lines = buildList {
                    val lumo = status.lumo
                    add(
                        if (lumo != null) {
                            val version = lumo.versionName?.let { " v$it" } ?: ""
                            stringResource(
                                R.string.status_lumo_detected,
                                lumo.label,
                                lumo.packageName + version,
                            )
                        } else {
                            stringResource(R.string.status_lumo_not_detected)
                        },
                    )
                    add(
                        when {
                            status.roleHeld -> stringResource(R.string.status_role_held)
                            status.roleAvailable -> stringResource(R.string.status_role_not_held)
                            else -> stringResource(R.string.status_role_unavailable)
                        },
                    )
                    if (status.assistComponent.isNotBlank()) {
                        add(stringResource(R.string.status_system_assistant, status.assistComponent))
                    }
                    add(
                        if (status.accessibilityEnabled) {
                            stringResource(R.string.status_accessibility_on)
                        } else {
                            stringResource(R.string.status_accessibility_off)
                        },
                    )
                },
                positive = status.lumo != null && status.roleHeld,
            )

            if (!status.roleHeld) {
                Button(
                    onClick = { requestAssistantRole(activity) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.set_assistant))
                }
            } else {
                OutlinedButton(
                    onClick = { openIntent(activity, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.open_assistant_settings))
                }
            }

            OutlinedButton(
                onClick = {
                    openIntent(activity, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_default_apps))
            }

            OutlinedButton(
                onClick = {
                    val pkg = status.lumo?.packageName
                    if (pkg != null) {
                        LumoBridge.launchIntent(activity, pkg)?.let { openIntent(activity, it) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = status.lumo != null,
            ) {
                Text(stringResource(R.string.open_lumo))
            }

            SectionTitle(stringResource(R.string.test_title))
            Text(
                text = stringResource(R.string.test_intro),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = testQuery,
                onValueChange = { testQuery = it },
                label = { Text(stringResource(R.string.test_query_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    val intent = Intent(activity, AssistantEntryActivity::class.java).apply {
                        action = Intent.ACTION_ASSIST
                        if (testQuery.isNotBlank()) {
                            putExtra(AssistantEntryActivity.EXTRA_TEST_QUERY, testQuery.trim())
                        }
                    }
                    openIntent(activity, intent)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = status.lumo != null,
            ) {
                Text(stringResource(R.string.test_button))
            }

            SectionTitle(stringResource(R.string.options_title))
            ToggleRow(
                title = stringResource(R.string.copy_title),
                subtitle = stringResource(R.string.copy_subtitle),
                checked = copyQuestion,
                onCheckedChange = {
                    copyQuestion = it
                    prefs.copyToClipboard = it
                },
            )
            ToggleRow(
                title = stringResource(R.string.auto_send_title),
                subtitle = stringResource(R.string.auto_send_subtitle),
                checked = autoSend,
                onCheckedChange = {
                    autoSend = it
                    prefs.autoSend = it
                    if (it && !status.accessibilityEnabled) {
                        openIntent(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
            )
            if (autoSend && !status.accessibilityEnabled) {
                Button(
                    onClick = {
                        openIntent(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.enable_accessibility))
                }
            }
            ToggleRow(
                title = stringResource(R.string.show_toast_title),
                subtitle = null,
                checked = showToast,
                onCheckedChange = {
                    showToast = it
                    prefs.showToast = it
                },
            )

            SectionTitle(stringResource(R.string.language_title))
            Text(
                text = stringResource(R.string.language_note),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguageButton(
                    label = "English",
                    selected = language == LocaleHelper.ENGLISH,
                    onClick = {
                        language = LocaleHelper.ENGLISH
                        prefs.language = LocaleHelper.ENGLISH
                        LocaleHelper.syncLauncherAlias(activity, LocaleHelper.ENGLISH)
                        activity.recreate()
                    },
                    modifier = Modifier.weight(1f),
                )
                LanguageButton(
                    label = "Español",
                    selected = language == LocaleHelper.SPANISH,
                    onClick = {
                        language = LocaleHelper.SPANISH
                        prefs.language = LocaleHelper.SPANISH
                        LocaleHelper.syncLauncherAlias(activity, LocaleHelper.SPANISH)
                        activity.recreate()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle(stringResource(R.string.diagnostics_title))
            Text(
                text = stringResource(
                    R.string.candidates,
                    status.candidates.joinToString().ifBlank {
                        stringResource(R.string.candidates_none)
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.assistant_component,
                    status.assistComponent.ifBlank {
                        stringResource(R.string.assistant_component_unset)
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.package_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = pkgOverride,
                onValueChange = { pkgOverride = it },
                label = { Text(stringResource(R.string.package_label)) },
                placeholder = { Text(LumoBridge.DEFAULT_PACKAGE) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    prefs.overridePackage = pkgOverride.trim()
                    status = readStatus(activity, prefs)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_package))
            }
            Button(
                onClick = { status = readStatus(activity, prefs) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.retry_detection))
            }

            SectionTitle(stringResource(R.string.adb_title))
            Text(
                text = stringResource(R.string.adb_intro),
                style = MaterialTheme.typography.bodySmall,
            )
            MonospaceBlock(
                "adb shell cmd role add-role-holder \\\n" +
                    "  android.app.role.ASSISTANT \\\n" +
                    "  com.pocketdev.lumo_digital_assistant",
            )
            Text(
                text = stringResource(R.string.adb_outro),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LanguageButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, enabled = false) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun StatusCard(title: String, lines: List<String>, positive: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (positive) "✓ $title" else title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MonospaceBlock(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(12.dp),
        )
    }
}
