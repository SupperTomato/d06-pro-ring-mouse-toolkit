# D06 Pro Ring Mouse

Language: English | [简体中文](README.zh-CN.md)

Reverse-engineering notes, capture tools, and a Kotlin Android SDK for the D06 Pro Bluetooth ring mouse.

The D06 Pro presents itself as a Bluetooth HID device with mouse, keyboard, and consumer-control collections. This repository documents the observed HID/BLE behavior and packages the confirmed mappings into Android SDK modules that can decode D06 events inside an Android app.

## What This Project Does

- Documents the D06 Pro Bluetooth identity, GATT services, HID collections, and observed runtime behavior.
- Maps verified controls: left click, right click, middle click, scroll up/down, mousepad motion, mousepad tap, double tap, and long press behavior observed so far.
- Provides Windows PowerShell capture tools for BLE/GATT, HID caps, and Raw Input events.
- Provides an Android SDK with a pure Kotlin mapper, Android `MotionEvent` / `KeyEvent` adapter, BLE metadata client, constrained remapper helper, and sample app.
- Provides a Linux `evdev` translator that maps `/dev/input/event*` mouse events into the same canonical D06 event names.
- Keeps capture artifacts so future mappings can be checked against the original data.

This project does not flash firmware, modify the mouse, or write to the vendor/Telink service. Vendor writes are intentionally avoided until that protocol is known.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `D06_PRO_RE.md` | Detailed reverse-engineering report and feature audit |
| `artifacts/` | Labeled Raw Input, HID, and GATT capture data |
| `tools/` | Windows PowerShell capture/enumeration scripts and Linux `evdev` translator |
| `android-sdk/` | Multi-module Kotlin/Android SDK and sample app |
| `docs/superpowers/specs/` | SDK design notes |
| `docs/superpowers/plans/` | Implementation plan used to build the SDK |

## Verified D06 Mapping

| D06 action | Observed host event | Android SDK event |
| --- | --- | --- |
| Left click | Mouse left down/up: `0x0001`, `0x0002` | `LeftDown`, `LeftUp` |
| Right click | Mouse right down/up: `0x0004`, `0x0008` | `RightDown`, `RightUp` |
| Middle click | Mouse middle down/up: `0x0010`, `0x0020` | `MiddleDown`, `MiddleUp` |
| Scroll up | Wheel flag `0x0400`, data `+120` | `Scroll(Up, units)` |
| Scroll down | Wheel flag `0x0400`, data `-120` | `Scroll(Down, units)` |
| Mousepad right | Relative `+X` | `MousepadMove(+dx, 0)` |
| Mousepad left | Relative `-X` | `MousepadMove(-dx, 0)` |
| Mousepad up | Relative `-Y` | `MousepadMove(0, -dy)` |
| Mousepad down | Relative `+Y` | `MousepadMove(0, +dy)` |
| Mousepad tap | Same HID pattern as left click | `MousepadTap` when tap detection is enabled |
| Mousepad double tap | Repeated left click pairs | Repeated tap/click events |
| Left/right long press | Normal held mouse button in captured Windows mode | Normal down/up events |

Mousepad tap and physical left click can be indistinguishable at HID level because both may arrive as the same left-button down/up pair. The SDK tap mode is a timing/no-movement heuristic, not a separate hardware signal.

## Android SDK

The SDK lives in `android-sdk/` and has five modules:

| Module | What it does |
| --- | --- |
| `d06-core` | Pure Kotlin event model and D06 raw mouse mapper |
| `d06-input` | Android `MotionEvent` / `KeyEvent` decoder and D06 device matcher |
| `d06-ble` | BLE/GATT UUID profile and battery/service discovery client |
| `d06-remapper` | AccessibilityService-based remapper helper for Android-supported actions |
| `d06-sample` | Live event console app for hardware validation |

Minimum SDK:

- `d06-core`, `d06-input`, `d06-ble`: minSdk 23
- `d06-remapper`, `d06-sample`: minSdk 24 because Android gesture dispatch is API 24+

### Build The SDK

```bash
cd android-sdk
./gradlew test assembleDebug
```

The sample debug APK is produced at:

```text
android-sdk/d06-sample/build/outputs/apk/debug/d06-sample-debug.apk
```

### Install The Sample App

Connect an Android device with USB debugging enabled:

```bash
cd android-sdk
./gradlew :d06-sample:installDebug
```

Then pair the D06 Pro over Bluetooth, open **D06 SDK Sample**, and interact with the screen. The sample logs decoded D06 events.

### Use The SDK In An Android App

The SDK is currently source-based, not published to Maven. Add the modules to your app's `settings.gradle.kts`:

```kotlin
include(":d06-core")
project(":d06-core").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-core")

include(":d06-input")
project(":d06-input").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-input")

include(":d06-ble")
project(":d06-ble").projectDir = file("../D06-Pro-Ring-Mouse/android-sdk/d06-ble")
```

Then add dependencies in your app module:

```kotlin
dependencies {
    implementation(project(":d06-core"))
    implementation(project(":d06-input"))
    implementation(project(":d06-ble"))
}
```

Decode D06 input in an `Activity`:

```kotlin
import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import com.d06.sdk.input.D06Input
import com.d06.sdk.input.D06InputConfig

class MainActivity : Activity() {
    private val d06 = D06Input(D06InputConfig(detectMousepadTap = true)) { event ->
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

Use the matcher when you need to check the Android input device:

```kotlin
val isD06 = motionEvent.device?.let { d06.isD06Device(it) } == true
```

`D06Input` only consumes events from recognized D06 device metadata, or from events where Android does not expose device metadata. If your device appears under a different name/VID/PID, add it to `D06InputConfig`.

For advanced integrations that need the decoded list instead of a callback, use `D06InputDecoder` directly.

The matcher recognizes both known paths:

- Bluetooth HID: VID/PID `248a:0101`, names containing `D06` or `D06 Pro`
- 2.4 GHz USB receiver through USB-OTG: VID/PID `248a:0401`, names containing `TK Wireless Receiver`

The USB receiver path still arrives as normal Android `MotionEvent` / `KeyEvent` input. It does not require Bluetooth permissions, but it does require the Android device to support USB host/OTG and accept the receiver as a HID mouse/keyboard.

### BLE Usage

Android 12 and newer require `BLUETOOTH_CONNECT`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

After your app has runtime permission and a `BluetoothDevice`:

```kotlin
import com.d06.sdk.ble.D06BleClient

val client = D06BleClient(context)
client.connect(bluetoothDevice)

// Observe client.state for:
// Idle, Connecting, Connected, ServicesDiscovered, BatteryLevel, Error
```

The BLE client is for metadata and battery/service discovery. It does not replace Android's HID stack and does not write vendor configuration commands.

### Remapper Limits

`d06-remapper` can help trigger Android-supported accessibility actions, such as back/home or dispatchable gestures, when your app has an allowed path to decoded `D06Event` values.

Stock Android does not expose a normal app API for globally intercepting and replacing every hardware HID mouse event system-wide. For full global HID interception, you would need platform privileges, a custom input service, root-level integration, or a different OS/device policy surface.

## Linux Evdev Translator

Linux support translates the already verified Windows Raw Input mapping into Linux `evdev` events. It does not redo the reverse engineering; it uses Linux's standard HID-to-evdev mapping:

| D06 action | Linux event | JSON event |
| --- | --- | --- |
| Left click | `EV_KEY BTN_LEFT 1/0` | `LeftDown`, `LeftUp` |
| Right click | `EV_KEY BTN_RIGHT 1/0` | `RightDown`, `RightUp` |
| Middle click | `EV_KEY BTN_MIDDLE 1/0` | `MiddleDown`, `MiddleUp` |
| Scroll up/down | `EV_REL REL_WHEEL +/-N` or `REL_WHEEL_HI_RES +/-120*N` | `Scroll(Up/Down, units)` |
| Mousepad motion | `EV_REL REL_X/REL_Y` | `MousepadMove(dx, dy)` |
| Button 4/5 if exposed | `EV_KEY BTN_SIDE/BTN_EXTRA` | `UnknownButton(4/5, pressed)` |

List Linux input nodes and D06 match reasons:

```bash
python3 tools/linux/d06_evdev.py --list
```

Stream decoded JSON Lines from a matched D06 node:

```bash
python3 tools/linux/d06_evdev.py --seconds 30
```

Or pass a node explicitly:

```bash
python3 tools/linux/d06_evdev.py --node /dev/input/event12 --seconds 30
```

Reading `/dev/input/event*` usually requires root, an `input` group membership, or a udev rule. The first Linux milestone only decodes and prints events; it does not grab the device, suppress normal mouse behavior, or emit replacement input.

## Reverse Engineering Tools

Run these from Windows PowerShell while the D06 Pro is paired:

```powershell
.\tools\dump_d06_gatt.ps1
.\tools\dump_hid_caps.ps1
.\tools\list_raw_input_devices.ps1
.\tools\capture_raw_input.ps1 -Seconds 10
```

The captures are useful for:

- finding whether a control emits mouse, keyboard, or consumer-control reports;
- checking button flags and wheel data;
- confirming mousepad axis signs;
- discovering whether hidden buttons 4/5 exist on a specific unit or mode.

## Features Still Worth Testing

- Mouse button 4/5 behavior if the physical unit exposes side/back/forward controls.
- Keyboard-mode outputs from HID collection `Col01`.
- Consumer/media-control outputs from HID collection `Col02`.
- Mode-toggle behavior before and after repeating known controls.
- Battery notifications and raw HID report map on a host that allows HID-over-GATT access.
- Vendor/Telink service notifications. Avoid writes until the protocol is known.

## Current Validation State

Completed:

- Windows BLE/GATT enumeration
- Windows HID collection/caps parsing
- Windows Raw Input captures for known controls
- Android SDK build and unit tests
- Android sample app debug build

Pending:

- Live Android device validation with the sample app
- Android metadata capture for how the D06 appears on specific phones/tablets
- Any controls not physically visible or not activated during the capture session

## Safety Notes

- The D06 vendor service resembles a Telink OTA/config service. Do not write to it unless you have a known-good protocol and recovery plan.
- The capture artifacts may include your specific Bluetooth address and device IDs. Keep that in mind if publishing forks or issue reports.
- The Android remapper module is intentionally conservative so it does not imply system-wide input interception that stock Android cannot provide.
