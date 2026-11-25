# Lumo as Digital Assistant

A bridge app for Android that lets **Proton Lumo** be opened from the digital
assistant gesture (long-press power, corner gesture, or long-press home).

## Download

Grab the latest signed APK from the
**[Releases page](https://github.com/jserodio/lumo-digital-assistant/releases/latest)**:

```
https://github.com/jserodio/lumo-digital-assistant/releases/latest/download/lumo-assistant-bridge.apk
```

Install it (the link above always points at the newest build):

```bash
adb install -r lumo-assistant-bridge.apk
```

Requirements: Android 9+ (`minSdk 28`), Proton Lumo already installed.

> The APK is signed with the key in `keystore/lumo-release.jks` so releases can
> be updated in place. Builds before v1.1 used the throwaway debug key: if you
> have one of those, uninstall it once before installing a release.

## The problem (and why this app is needed)

Android decides which apps appear under **Settings › Apps › Default apps ›
Digital assistant** using the system role `android.app.role.ASSISTANT`. To
qualify for that role, an app must declare an exported activity with this
filter in its manifest:

```xml
<intent-filter>
    <action android:name="android.intent.action.ASSIST" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

Checking Lumo's real manifest (`me.proton.android.lumo`, version 2.x, open
source at `ProtonLumo/android-lumo`) shows that it **only declares
`MAIN`/`LAUNCHER`**: it has no `ASSIST` filter, does not accept `ACTION_SEND`,
and its `MainActivity` ignores incoming intents. It is a WebView shell that
loads `https://lumo.proton.me`.

Consequences:

- The system never offers Lumo as a digital assistant, so **the role cannot be
  pointed at Lumo directly** without modifying and re-signing Proton's app.
- Even if you change `Settings.Secure.assistant` via ADB, the system would
  launch Lumo but only open it, with no way to pass a question to it.

## The solution: a bridge that holds the role

This app **does** declare the assistant role and, when invoked by the system,
opens Lumo immediately. Flow:

1. `AssistantEntryActivity` receives `ACTION_ASSIST` (transparent, no UI,
   closes immediately).
2. It finds Lumo by known package (`me.proton.android.lumo`, ...) or
   dynamically among launchable apps whose name/package contains "lumo".
3. If the invocation carries a question (voice/keyboard):
   - If Lumo ever accepts text via `ACTION_SEND`, it is sent directly (not
     declared today, but the code is ready).
   - Otherwise it is copied to the clipboard and, optionally, typed inside
     Lumo with the accessibility service (experimental).
4. It launches Lumo with its launcher intent and finishes.

## Usage

```bash
# build the signed release APK
gradle :app:assembleRelease

# or a debug build
gradle :app:assembleDebug --offline
```

In the app:

1. **Set as digital assistant** → accept the system dialog. (Equivalent to
   Settings › Default apps › Digital assistant and choosing "Lumo Assistant
   Bridge".)
2. **Test** → runs the same path as the gesture without waiting for it.
3. Optional: enable **Copy question to clipboard** and/or **Auto-type the
   question in Lumo** (accessibility).

If your ROM does not show the role dialog:

```bash
adb shell cmd role add-role-holder android.app.role.ASSISTANT com.pocketdev.lumo_digital_assistant
```

To check what the system has right now:

```bash
adb shell settings get secure assistant
adb shell am start -a android.intent.action.ASSIST
```

## Language

The interface is **English by default**. Open **Language** in the app and
choose **Español** to switch everything to Spanish; the choice is saved in
SharedPreferences and survives restarts.

The app name on the home screen changes too. Two launcher aliases with fixed
labels are used (`Lumo Assistant Bridge` / `Lumo como asistente`) and the
matching one is enabled when you switch language, so the name changes even if
the system locale is different from the in-app language.

## Options

| Option | What it does |
| --- | --- |
| Copy question to clipboard | Lumo does not accept external text, so you just paste (long-press) into its text box. |
| Auto-type (experimental) | Accessibility service limited to `me.proton.android.lumo`: types the question and taps send. If it cannot find the box/button, nothing breaks. |
| Show a toast when redirecting | Informational toast. |

## Known limitations

- **The question does not travel natively**: Lumo ignores intents, so the
  clipboard or accessibility is used instead.
- The accessibility service depends on how the WebView exposes its nodes; if
  Proton changes the UI, it may not find the send button (it degrades to just
  opening Lumo + copying).
- On a locked screen the system may not show Lumo until you unlock.
- On Android 9 and older there is no `RoleManager` for assistants; use the ADB
  command above (the system still launches the `ASSIST` activity).

## Structure

```
app/src/main/
├─ AndroidManifest.xml            ASSISTANT role, <queries>, accessibility service
├─ java/.../MainActivity.kt       status/settings UI (Compose)
├─ java/.../AssistantEntryActivity.kt  assistant entry point → forwards to Lumo
├─ java/.../LumoBridge.kt         Lumo detection, intents, clipboard
├─ java/.../AssistAutomationService.kt optional service (type/send)
├─ java/.../BridgePrefs.kt        preferences (including language)
├─ java/.../LocaleHelper.kt       language preference, locale, launcher aliases
├─ res/values/strings.xml         English strings (default)
├─ res/values-es/strings.xml      Spanish strings
└─ res/xml/accessibility_service_config.xml  (limited to Lumo package)
```

## Alternative without this app

Declaring the `ASSIST` filter inside Lumo itself would require recompiling and
re-signing Proton's APK (the project is GPL-3.0 and public:
`https://github.com/ProtonLumo/android-lumo`). That would be the definitive
solution, but you would lose Play Store updates.

## Building from source

```bash
git clone https://github.com/jserodio/lumo-digital-assistant.git
cd lumo-digital-assistant
gradle :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Toolchain: AGP 8.11.0, Kotlin 1.9.22, `compileSdk`/`targetSdk` 36, Java 17.

Release signing uses `keystore/lumo-release.jks`, which is committed on purpose:
this is a sideload-only app, so what matters is that every release is signed by
the same key and updates install in place. Override it by creating a
`keystore.properties` (git-ignored) with `storeFile`, `storePassword`,
`keyAlias` and `keyPassword`.

Notes for contributors working on this from the physical device are in
[AGENTS.md](AGENTS.md).

## License

[MIT](LICENSE). Not affiliated with Proton AG. "Proton Lumo" is a trademark of
Proton AG.

