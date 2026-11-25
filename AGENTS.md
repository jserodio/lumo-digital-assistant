# Building and installing this app

Notes for any agent (or human) picking up this project. Everything here was
learned the hard way on the actual target device; read it before you start so you
don't rediscover it.

## Target device

- Xiaomi **24129PN74G** (HyperOS / Android 16), the phone this app was built for.
- Proton Lumo is installed as `me.proton.android.lumo`.

## 1. Check the wireless-debugging link FIRST

The development container has **no USB access**. The only way to reach the phone
is **wireless debugging over TCP**:

```
10.2.0.2:32897
```

Always check it before doing anything else:

```bash
adbx connect 10.2.0.2:32897
adbx devices          # must show "device", not "offline"
```

If the device is not reachable, **stop and ask the user to enable
Settings › Developer options › Wireless debugging** and to give you the current
`IP:port` (the port changes every time wireless debugging is toggled).

### You MUST use `adbx`, not `adb`

`/usr/bin/adb` is client **1.0.39**; the phone/host server is **1.0.41**. Mixing
them prints `adb server version (39) doesn't match this client (41); killing...`
and leaves the device `offline`.

`adbx` (in `/usr/local/bin/adbx`) is a wrapper that points at a Termux adb
**1.0.41** install:

```bash
#!/bin/bash
P=/tmp/adbnew/prefix/data/data/com.termux/files/usr
export PATH=$P/bin:$PATH
export LD_LIBRARY_PATH=$P/lib
exec adb "$@"
```

Use `adbx` for every command: `adbx devices`, `adbx shell ...`, `adbx install ...`.

## 2. Building

Use the preinstalled `gradle` (8.14.3) from the project root. **Do not** add
`android.aapt2FromMavenOverride`; the SDK's ARM64 aapt2 is already configured.

```bash
gradle :app:assembleRelease           # signed release APK (needs network the first time)
gradle :app:assembleDebug --offline   # debug APK
```

Output: `app/build/outputs/apk/release/app-release.apk`

Notes:

- `assembleRelease` runs `lintVitalRelease`, which needs
  `com.android.tools.lint:lint-gradle`. It is **not** in the offline Maven cache,
  so release builds must run **online** (the debug build works with `--offline`).
- The phone has ~11 GB RAM. If Kotlin dies with *"Not enough memory to run
  compilation"*, `gradle.properties` already sets
  `org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=768m` and
  `kotlin.daemon.jvmargs=-Xmx1536m`. Raise those if needed.
- Android SDK: `/root/android-sdk` (build-tools 35.0.0). Use its
  `aapt2`/`apksigner` to inspect the APK:

```bash
/root/android-sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
/root/android-sdk/build-tools/35.0.0/aapt2 dump badging app/build/outputs/apk/release/app-release.apk
```

## 3. Installing the APK

```bash
adbx install -r app/build/outputs/apk/release/app-release.apk
```

### Signature rule (important)

The release APK is signed with `keystore/lumo-release.jks` (committed on
purpose so every release shares one key). Builds made before v1.1 were signed
with the throwaway debug key, so **installing a release APK over an old debug
install fails** with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. In that case:

```bash
adbx uninstall com.pocketdev.lumo_digital_assistant
adbx install app/build/outputs/apk/release/app-release.apk
```

From v1.1 on, `adb install -r` updates work in place.

> Other install paths in this container **do not work** and were already ruled
> out: `cmd package install` (and `install-create`) fails inside MIUI's
> `XSpaceManagerServiceImpl.getDefaultUserId()` with a `MANAGE_USERS`
> `SecurityException`, because every binder call from the container is
> attributed to the container app uid (10508) regardless of the local uid you
> `setuid()` to. `pm install` is not on `PATH`, `app_process` crashes in the
> linker against MIUI's `libfmt`, and `/data/app` is not mounted in the
> container. Wireless debugging via `adbx` is the supported route.

## 4. Granting the assistant role

```bash
adbx shell cmd role add-role-holder android.app.role.ASSISTANT com.pocketdev.lumo_digital_assistant
```

Check it:

```bash
adbx shell cmd role get-role-holders android.app.role.ASSISTANT
adbx shell settings get secure assistant
```

## 5. Testing the redirect

`KEYCODE_ASSIST` (219) triggers the same path as the long-press-power gesture:

```bash
adbx shell input keyevent 219
sleep 3
adbx shell "dumpsys activity activities | grep topResumedActivity"
# expected: me.proton.android.lumo/.MainActivity
```

Opening the bridge's own settings screen (it is not exported):

```bash
adbx shell monkey -p com.pocketdev.lumo_digital_assistant -c android.intent.category.LAUNCHER 1
```

Dump its UI to check strings/resources without a screenshot:

```bash
adbx shell uiautomator dump /sdcard/ui.xml && adbx shell cat /sdcard/ui.xml
```

## 6. Publishing a release

Releases are built locally and uploaded to GitHub (see `README.md` for the
public download link). Bump `versionCode`/`versionName` in `app/build.gradle.kts`
first, then:

```bash
gradle :app:assembleRelease
# create the tag + release and attach the APK via the GitHub API
```
