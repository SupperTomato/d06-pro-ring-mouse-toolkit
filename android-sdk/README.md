# D06 Android SDK

Kotlin Android SDK for the D06 Pro ring mouse.

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
