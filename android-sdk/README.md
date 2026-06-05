# D06 Android SDK

Language: English | [简体中文](README.zh-CN.md)

Kotlin Android SDK for the D06 Pro ring mouse.

This SDK decodes D06 Pro events in Android apps that receive the device's input stream. It also exposes BLE/GATT metadata helpers and a constrained AccessibilityService remapper helper. It does not replace Android's system HID driver.

## Modules

- `d06-core`: pure Kotlin event model and verified D06 mapping logic.
- `d06-input`: Android `MotionEvent` and `KeyEvent` decoder for apps that receive D06 input.
- `d06-ble`: BLE/GATT metadata client for service discovery and battery reads.
- `d06-remapper`: constrained AccessibilityService remapper helper.
- `d06-sample`: live event console and validation app.

`d06-core`, `d06-input`, and `d06-ble` support minSdk 23. `d06-remapper` and `d06-sample` require minSdk 24 because Android gesture dispatch is API 24+.

## Verified Mapping

| D06 action | SDK event |
| --- | --- |
| Left click | `LeftDown`, `LeftUp` |
| Right click | `RightDown`, `RightUp` |
| Middle click | `MiddleDown`, `MiddleUp` |
| Scroll up | `Scroll(Up, units)` |
| Scroll down | `Scroll(Down, units)` |
| Mousepad right | `MousepadMove(+dx, 0)` |
| Mousepad left | `MousepadMove(-dx, 0)` |
| Mousepad up | `MousepadMove(0, -dy)` |
| Mousepad down | `MousepadMove(0, +dy)` |
| Mousepad tap | `MousepadTap` when tap detection is enabled |
| Extra mouse buttons 4/5 | `UnknownButton(4/5, pressed)` until hardware behavior is observed |
| Keyboard/media controls | `Key(keyCode, scanCode, action)` when Android exposes them as key events |

## Important Limits

Mousepad tap and physical left click can be indistinguishable because both can arrive as the same HID left-button down/up pair. `D06Mapper(detectMousepadTap = true)` uses a timing and no-movement heuristic. Leave it disabled when you need exact left-button preservation.

The SDK can decode D06 input inside apps that receive the input events. A stock Android app cannot universally intercept and replace every hardware HID event system-wide. The remapper module is a constrained AccessibilityService helper for actions Android allows.

## BLE Permissions

Android 12 and newer require:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

Request the permission at runtime before calling BLE connect/discover/read APIs.

## Build

```bash
./gradlew test assembleDebug
```

## Publish Locally Or Use JitPack

Library modules are configured with Gradle `maven-publish`:

```bash
./gradlew publishToMavenLocal
```

For JitPack, create a GitHub release or tag, then depend on the modules from `https://jitpack.io` using the tag or commit hash.

## Add The SDK To An App

The modules are source-based. Add the modules to your app's `settings.gradle.kts`:

```kotlin
include(":d06-core")
project(":d06-core").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-core")

include(":d06-input")
project(":d06-input").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-input")

include(":d06-ble")
project(":d06-ble").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-ble")
```

Then depend on the modules you need:

```kotlin
dependencies {
    implementation(project(":d06-core"))
    implementation(project(":d06-input"))
    implementation(project(":d06-ble"))
}
```

## Decode Input

```kotlin
import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.core.D06EventTransformConfig
import com.d06.sdk.input.D06Input
import com.d06.sdk.input.D06InputConfig
import com.d06.sdk.input.D06InputDiagnostics

class MainActivity : Activity() {
    private val diagnostics = D06InputDiagnostics()
    private val d06 = D06Input(
        D06InputConfig(
            detectMousepadTap = true,
            eventTransform = D06EventTransformConfig(
                movementSensitivity = 1.25f,
                movementDeadzone = 2
            )
        ),
        diagnostics
    ) { event ->
        // Handle LeftDown, Scroll, MousepadMove, MousepadTap, etc.
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return d06.dispatch(ev) || super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return d06.dispatch(event) || super.dispatchKeyEvent(event)
    }
}
```

Use `d06.isD06Device(inputDevice)` to check whether Android metadata matches the known D06 name or VID/PID.

`D06Input` only consumes events from recognized D06 device metadata, or from events where Android does not expose device metadata. If your device appears under a different name/VID/PID, add it to `D06InputConfig`.

For advanced integrations that need the decoded list instead of a callback, use `D06InputDecoder` directly.

## Remap Presets

```kotlin
import com.d06.sdk.core.D06Event
import com.d06.sdk.remapper.D06RemapAction
import com.d06.sdk.remapper.D06RemapPreset
import com.d06.sdk.remapper.D06Remapper

val preset = D06RemapPreset("my-profile") {
    on(D06Event.MousepadTap, D06RemapAction.Home)
    on(D06Event.MiddleUp, D06RemapAction.Back)
}

val remapper = D06Remapper(preset)
val action = remapper.actionFor(D06Event.MousepadTap)
```

Built-in presets: `D06RemapPresets.Accessibility`, `Presentation`, `Media`, `MouseOnly`.

Use `D06RemapValidator.validateForAccessibilityService(preset)` before sending actions to `D06AccessibilityRemapperService`; app-handled key actions cannot be executed directly by AccessibilityService.

## Diagnostics

```kotlin
val jsonl = diagnostics.toJsonLines()
```

The diagnostics helper keeps a bounded decoded-event history with input-device metadata.

The matcher recognizes both known D06 paths:

- Bluetooth HID: VID/PID `248a:0101`, names containing `D06` or `D06 Pro`
- 2.4 GHz USB receiver through USB-OTG: VID/PID `248a:0401`, names containing `TK Wireless Receiver`

The USB receiver path is decoded from normal Android `MotionEvent` / `KeyEvent` input. It does not use the BLE module and does not need Bluetooth permissions.

## Read BLE Metadata

```kotlin
import com.d06.sdk.ble.D06BleClient

val client = D06BleClient(context)
client.connect(bluetoothDevice)

// Observe client.state for service profile and battery-level updates.
```

The BLE module is for service discovery and battery reads. It intentionally avoids vendor-service writes.

## Manual Validation

1. Pair the D06 Pro to the Android device.
2. Launch `d06-sample`.
3. Tap the mousepad and confirm `MousepadTap`.
4. Swipe right/left/up/down and confirm axis signs.
5. Scroll up/down and confirm scroll direction.
6. Press left/right/middle buttons and confirm events.
7. Try long press, double tap, mode controls, and any hidden buttons; export logs for unmapped behavior.

## Hardware Validation

- Device: D06 Pro
- Android device: not attached to this environment
- Android version: not verified
- Input device metadata exposed: not verified on Android
- Confirmed mappings: build-time mapper tests pass; live Android sample validation pending
- Unmapped behavior: physical Android validation pending
