# D06 Pro Ring Mouse Toolkit

Language: English | [简体中文](README.zh-CN.md)

This project helps people understand and use the D06 Pro ring mouse on Android and Linux.

Plain English: the D06 Pro behaves like a small Bluetooth or USB mouse. This repo records what each button and gesture sends, provides an Android SDK/sample app, and includes Linux tools for checking what the ring mouse is doing.

For a plain-language list of what each SDK part, command, and event can do, see [FUNCTIONS.md](FUNCTIONS.md) or [简体中文](FUNCTIONS.zh-CN.md).

For SDK performance advice, see [PERFORMANCE.md](PERFORMANCE.md) or [简体中文](PERFORMANCE.zh-CN.md).

## Who This Is For

- D06 Pro owners who want to know what the buttons actually do.
- Android users testing the ring mouse with a phone or tablet.
- Android app developers who want to decode D06 input inside an app.
- Linux users who want to inspect or translate D06 input events.
- Reverse-engineering users who want the captured HID/BLE evidence.

## Current Status

Working:

- left, right, and middle click decoding
- scroll up/down decoding
- mousepad movement direction mapping
- mousepad tap/double-tap observation
- Android input decoder SDK
- Android sample app
- Linux `evdev` decoder
- Linux `hidraw` and BLE/GATT inspection tools
- USB receiver detection on Linux

Still limited:

- stock Android apps cannot globally replace every hardware mouse event
- mousepad tap and physical left click can look identical at HID level
- hidden button 4/5, keyboard mode, and media mode still need more real-device captures
- vendor/Telink writes are intentionally not supported

## Simple Android Use

For normal testing:

1. Pair the D06 Pro with your Android device over Bluetooth, or connect the 2.4 GHz receiver through USB-OTG.
2. Build and install the sample app:

```bash
cd android-sdk
./gradlew :d06-sample:installDebug
```

3. Open **D06 SDK Sample**.
4. Press buttons and move the mousepad. The app shows decoded D06 events.

At the moment, there is no packaged app release in this repo. You still need Gradle/Android Studio, or someone to build the sample APK for you.

## Simple Linux Use

List possible D06 input devices:

```bash
python3 tools/linux/d06_evdev.py --list
```

Print decoded events:

```bash
python3 tools/linux/d06_evdev.py --seconds 30
```

If Linux blocks access to `/dev/input/event*`, run with `sudo`, join the `input` group, or install the udev rule template in `tools/linux/99-d06-pro.rules`.

The Linux tool prints events. It does not take over the mouse or stop normal pointer movement.

## Button And Gesture Map

| D06 action | What the host sees | SDK event |
| --- | --- | --- |
| Left click | Mouse left down/up | `LeftDown`, `LeftUp` |
| Right click | Mouse right down/up | `RightDown`, `RightUp` |
| Middle click | Mouse middle down/up | `MiddleDown`, `MiddleUp` |
| Scroll up | Mouse wheel positive step | `Scroll(Up, units)` |
| Scroll down | Mouse wheel negative step | `Scroll(Down, units)` |
| Mousepad right | Relative `+X` movement | `MousepadMove(+dx, 0)` |
| Mousepad left | Relative `-X` movement | `MousepadMove(-dx, 0)` |
| Mousepad up | Relative `-Y` movement | `MousepadMove(0, -dy)` |
| Mousepad down | Relative `+Y` movement | `MousepadMove(0, +dy)` |
| Mousepad tap | Same pattern as left click | `MousepadTap` only when tap detection is enabled |
| Mousepad double tap | repeated left click pairs | repeated tap/click events |

The manuals may describe app actions such as "next video", "like", or "camera". At the device level, those are usually normal mouse, keyboard, or media-control events. The app decides what they mean.

## Android SDK For Developers

The SDK lives in `android-sdk/`.

| Module | Purpose |
| --- | --- |
| `d06-core` | pure Kotlin D06 event model and mapper |
| `d06-input` | Android `MotionEvent` / `KeyEvent` decoder |
| `d06-ble` | BLE/GATT metadata and battery helper |
| `d06-remapper` | limited AccessibilityService remapping helper |
| `d06-sample` | sample app for real-device testing |

Build everything:

```bash
cd android-sdk
./gradlew test assembleDebug
```

Publish to Maven local:

```bash
cd android-sdk
./gradlew publishToMavenLocal
```

For local source development, include the modules from a nearby clone:

```kotlin
include(":d06-core")
project(":d06-core").projectDir = file("../d06-pro-ring-mouse-toolkit/android-sdk/d06-core")

include(":d06-input")
project(":d06-input").projectDir = file("../d06-pro-ring-mouse-toolkit/android-sdk/d06-input")

include(":d06-ble")
project(":d06-ble").projectDir = file("../d06-pro-ring-mouse-toolkit/android-sdk/d06-ble")
```

Basic decoder usage:

```kotlin
class MainActivity : Activity() {
    private val d06 = D06Input(
        D06InputConfig(detectMousepadTap = true)
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

See [android-sdk/README.md](android-sdk/README.md) for full SDK usage, remap presets, diagnostics, and BLE permissions.

## Linux And Android Capture Tools

Linux:

```bash
python3 tools/linux/d06_evdev.py --list
python3 tools/linux/d06_evdev.py --seconds 10
python3 tools/linux/d06_hid.py --list
sudo python3 tools/linux/d06_hid.py --dump --out artifacts/linux/hid/hid_caps.json
python3 tools/linux/dump_d06_gatt.py --address AA:BB:CC:DD:EE:FF --out-dir artifacts/linux/gatt
```

Android from a Linux host with USB or wireless debugging:

```bash
tools/android/d06_android_input.sh list
tools/android/d06_android_input.sh capture --seconds 10 --out artifacts/android/getevent/android_getevent.txt
tools/android/d06_android_input.sh dump-input --out artifacts/android/dumpsys/input.txt
```

## Repository Map

| Path | What it contains |
| --- | --- |
| `android-sdk/` | Android SDK modules and sample app |
| `tools/` | Linux and Android capture tools |
| `artifacts/` | grouped capture data from Android, Linux, and historical host captures |
| `FUNCTIONS.md` / `FUNCTIONS.zh-CN.md` | plain-language guide to toolkit functions and features |
| `PERFORMANCE.md` / `PERFORMANCE.zh-CN.md` | SDK performance optimization guide |
| `D06_PRO_RE.md` | detailed reverse-engineering notes |
| `docs/research/` | SDK feature research |
| `docs/superpowers/` | implementation specs and plans |

## Safety And Privacy

- This project does not flash firmware.
- This project does not write to the vendor/Telink service.
- Capture files may contain device names, hardware IDs, or Bluetooth addresses.
- Android remapping is intentionally conservative because stock Android does not allow normal apps to replace all hardware mouse input system-wide.
