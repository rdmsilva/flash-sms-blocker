# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Flash SMS Blocker is a root-free Android app that suppresses **Class 0 (Flash) SMS** — messages
that the GSM framework (3GPP TS 23.040, `TP-DCS` message class = 0) displays as an immediate
popup instead of storing. The app does not read, send, or store SMS; it only detects the Class 0
popup and closes it.

Plain Java (no Kotlin), SDK 34 / minSdk 24, Java 8 source level. Runtime dependencies in
`app/build.gradle` are intentionally empty — **no third-party libraries, no AndroidX in the
APK.** Keep it that way. The only dependency is `testImplementation junit:junit` (JVM unit
tests only; never ships).

## Architecture: single layer, accessibility-only

On a non-rooted phone you **cannot prevent** a Class 0 popup — the telephony framework draws it
before any app is notified. The only thing that works is to detect the popup and dismiss it
instantly. That is the entire app. (An earlier "Layer 1" that became the default SMS app to
intercept `SMS_DELIVER` was removed: it could not stop the popup and broke normal texting. Don't
reintroduce default-SMS-app behavior.)

- **`FlashSmsAccessibilityService`** — the whole product, but **device-agnostic**. Listens only for
  `TYPE_WINDOW_STATE_CHANGED`, matches the window against `FlashPopupRule.match(...)`, and dismisses
  a match by clicking the rule's discard button (never "Save", which would keep the spam), falling
  back to `GLOBAL_ACTION_BACK`. A short debounce prevents double-acting (a popup can fire both an
  `AlertDialog` and an activity event a few ms apart). It holds no device-specific data.
- **`FlashPopupRule`** — where all device knowledge lives. A small value class (package + class
  substring + text marker + dismiss labels) plus a static `KNOWN` list and `match(...)`. Adding a
  device = adding one entry to `KNOWN`; a device needing custom logic can subclass and override
  `matches(...)`.
- **`BlockStats`** — SharedPreferences-backed counter + rolling 50-entry log, plus the runtime
  **learning-mode** flag. `recordBlock` (counts) vs `recordEvent` (log only, used by learning mode).
  `clear()` wipes the counter/history but preserves learning mode. No database.
- **`MainActivity`** — status card, stats, history, "clear", and a ⋮ overflow menu (enable
  accessibility / toggle learning mode / about). No SMS logic.

### Device-specific detection — READ THIS before changing detection

Detection is pinned to the exact popup identity of each **target device**, discovered empirically,
not guessed. Matching a broad "any system dialog" heuristic (an earlier version did this) causes
the service to dismiss legitimate system dialogs — do not do that. All rules live in
`FlashPopupRule.KNOWN`.

The one verified device is the **Samsung Galaxy A73 / One UI**, where the flash popup is:
- package `com.samsung.android.messaging`
- class `com.samsung.android.messaging.ui.view.classzero.ClassZeroActivity` (with an
  `android.app.AlertDialog` whose text contains "Class 0 Message"; buttons "Cancel"/"Save").

**Learning mode** is a **runtime toggle** in the ⋮ menu (persisted via `BlockStats.isLearningMode`),
not a compile-time constant. When on, the service closes nothing — it logs every new window's
package/class/text to logcat tag `FlashBlocker/LEARN` *and* to the in-app history (works with no PC
attached), and `MainActivity` shows an amber "not blocking" status so the user isn't misled. To add
a device: toggle it on, trigger one flash SMS, read the `==== NEW WINDOW ====` block, add a
`FlashPopupRule` to `KNOWN`, toggle it off.

## Build & run

Android Studio (File > Open the project root) handles everything. From the command line the Gradle
wrapper is committed (Gradle 8.3, AGP 8.1.0, JDK 17): `./gradlew assembleDebug` →
`app/build/outputs/apk/debug/app-debug.apk`. Needs the Android SDK via `ANDROID_HOME` or a
`local.properties` with `sdk.dir=...` (gitignored). No lint config yet.

**Tests:** JUnit 4 unit tests in `app/src/test` run on the JVM (`./gradlew testDebugUnitTest`).
`./gradlew jacocoCoverageVerification` (also wired into `check`) **fails the build below 100%
line+branch coverage** of the logic classes `FlashPopupRule` and `BlockStats`; keep it at 100%
when changing them. `MainActivity` and `FlashSmsAccessibilityService` are deliberately excluded
from the coverage gate: they are Android-API glue whose real test is on-device (learning mode /
a live flash SMS). Keep logic out of those two classes so it stays testable — `BlockStats` takes
`SharedPreferences` (not `Context`) precisely for that; tests use the in-memory
`FakeSharedPreferences`, no mocking framework.

A **gradle-free manual pipeline** lives in four root scripts, one per concern (plus `common.sh`,
sourced by the others for shared paths and the SDK lookup):
- `./build.sh` — compile only (aapt → javac → d8 → aapt package → zipalign) →
  `build/app-aligned.apk`, **unsigned**, not installable.
- `./sign.sh [debug|release]` — signs it → `build/flash-sms-blocker.apk`. Debug uses a throwaway
  local keystore; release uses the durable keystore (`~/.keystores/flash-sms-blocker.jks` or
  `$RELEASE_KEYSTORE`; apksigner prompts for the password interactively — it is kept outside the
  repo and never committed, and the prompt **needs a real TTY**, so release signing runs in the
  user's own terminal, not through Claude's Bash tool).
- `./install.sh [auto|usb|wifi]` — adb-installs the signed APK (`wifi` bootstraps adb-over-Wi-Fi
  from USB or `$PHONE_IP`), then **re-enables the accessibility service** — Android disables it on
  every app update — and fails loudly if it didn't come back.
- `./deploy.sh [debug|release] [auto|usb|wifi]` — chains build → sign → install.

Gotchas the build handles that AGP does automatically:
- Legacy `aapt` needs the `package` attribute in the manifest and `<uses-sdk>` /
  `versionCode`/`versionName` — those are duplicated into the manifest (AGP reads them from
  `build.gradle`; both builds tolerate the duplication). Without them the manual APK targets SDK 0
  and modern devices refuse it.
- Gradle's `assembleRelease` also signs when `RELEASE_KEYSTORE_PASSWORD` (+ optional
  `RELEASE_KEYSTORE`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`) is set; without it the release
  APK comes out unsigned. The device only accepts updates signed with the same key as the
  installed APK — a debug build cannot update a release install.

## Device install / debugging notes (Samsung One UI)

- Samsung blocks adb sideloads: turn off **Auto Blocker** (Settings > Security and privacy) and
  **Verify apps over USB** (Developer options), or `adb install` fails with
  `INSTALL_FAILED_VERIFICATION_FAILURE`.
- USB **tethering** mode hides the device from adb — set USB to file transfer.
- Enable the service headlessly with:
  `adb shell settings put secure enabled_accessibility_services com.flashblocker/com.flashblocker.FlashSmsAccessibilityService`
  then `... accessibility_enabled 1`. Confirm with `adb shell dumpsys accessibility | grep 'Flash SMS Blocker'`.
- **Do not** set the app as default SMS app — it would break Samsung Messages for no benefit.

## Conventions

- Comments, logs, and user-facing strings are **Portuguese (Brazil)**. Match that when editing.
- Log tags are namespaced `FlashBlocker/<Component>` (`FlashBlocker/A11y`, `FlashBlocker/LEARN`).
- Package / `applicationId` / manifest namespace: `com.flashblocker`.
