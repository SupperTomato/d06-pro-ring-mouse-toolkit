# SDK Performance Guide

Language: English | [简体中文](PERFORMANCE.zh-CN.md)

This guide lists the practical optimizations for getting the best performance from the D06 Pro Ring Mouse Toolkit.

## Best Defaults

| Optimization | Why it helps |
| --- | --- |
| Use only the modules you need. | Smaller app, fewer dependencies, less startup work. |
| Reuse one `D06Input` or `D06InputDecoder` instance. | Avoids rebuilding mapper, matcher, and pointer-tracking state for every event. |
| Keep `detectMousepadTap = false` unless you need tap detection. | Avoids delayed left-click decisions and keeps physical left click exact. |
| Keep event callbacks fast. | Android input events arrive on the app input path; slow work can make the UI feel delayed. |
| Disable diagnostics in release builds unless debugging. | Avoids keeping/logging extra event history. |
| Do not export JSON logs on every input event. | JSON export is for bug reports, not the hot input path. |
| Reuse remap presets/remappers. | Build rules once, then only look up actions during input. |
| Connect BLE only when reading metadata or battery. | HID mouse input does not need the BLE helper to stay connected. |
| Use release builds with R8/minify for real testing. | Debug builds are slower and include more checks/logging. |

## Android App Optimizations

| What to do | Plain-language explanation |
| --- | --- |
| Create `D06Input` once per Activity/View/input surface. | Treat it like one controller for that screen, not something to recreate for every click. |
| Feed it only real input events. | Call `dispatchGenericMotionEvent` and `dispatchKeyEvent`; do not route unrelated app events through it. |
| Let the built-in device matcher filter devices. | `D06Input` skips events from devices that do not look like D06 or the receiver. |
| Keep `acceptedNames` and `acceptedDeviceIds` small. | Matching stays simple and avoids accidentally decoding the wrong mouse. |
| Use `isD06Device(device)` before doing expensive app work. | Cheap check first, expensive logic only for D06 devices. |
| Call `resetPointerTracking()` after focus loss, cancellation, or screen changes. | Prevents one stale pointer position from causing a bad movement jump. |
| Do not block inside the event callback. | No file writes, network calls, sleeps, large database work, or heavy UI rebuilds directly inside the callback. |
| Queue heavy work to another coroutine/thread. | Input stays responsive while logs, analytics, or app actions happen separately. |
| Coalesce high-rate `MousepadMove` events when updating UI. | Many tiny move events can be merged before redrawing a custom UI. |
| Throttle visible debug logs. | Showing every event on screen is useful for testing but can slow UI if the list grows forever. |

## Tap Detection Optimizations

| Setting | Performance effect |
| --- | --- |
| `detectMousepadTap = false` | Fastest and most exact for normal left click. This is the safest default. |
| `detectMousepadTap = true` | Allows `MousepadTap`, but left click may be delayed until the tap window resolves. |
| Smaller `tapWindowMillis` | Less waiting before deciding whether a quick left-button pattern is a tap. |
| Larger `tapWindowMillis` | More likely to detect taps, but can make left-click behavior feel less immediate. |

The device can make mousepad tap and physical left click look identical. Performance and correctness are both best when tap detection is only enabled for apps that truly need it.

## Movement And Scroll Optimizations

| Option | How to use it |
| --- | --- |
| `movementDeadzone` | Drop tiny accidental motion before your app sees it. Useful for jitter. |
| `movementSensitivity` | Adjust pointer/mousepad movement once in the SDK instead of doing custom scaling everywhere. |
| `scrollSensitivity` | Adjust scroll steps once in the SDK. |
| `invertX` / `invertY` | Flip direction once in the SDK instead of fixing it in every screen. |
| Event coalescing in your app | Merge several `MousepadMove` events before a visual redraw when building custom UI. |

Avoid very high sensitivity values unless you need them. They can make small noisy movement look large.

## Diagnostics And Logging

| What to do | Why |
| --- | --- |
| Use `D06InputDiagnostics` only while testing or when collecting bug reports. | It stores extra event records. |
| Keep diagnostic `capacity` small. | Large histories use more memory. |
| Call `snapshot()` only when needed. | It copies the saved history. |
| Call `toJsonLines()` only when exporting. | It builds text and should not run for every event. |
| Clear old logs with `clear()`. | Keeps memory predictable during long sessions. |

## Remapper Optimizations

| What to do | Why |
| --- | --- |
| Create `D06RemapPreset` once. | Rule building is setup work, not per-event work. |
| Reuse `D06Remapper`. | Per-event action lookup stays small. |
| Run `D06RemapValidator.validateForAccessibilityService(...)` during setup. | Validation should not happen on every event. |
| Use remapping mostly for button/tap/scroll actions. | Accessibility gestures are system operations and are not meant for high-rate pointer movement. |
| Avoid `SendKey` with `D06AccessibilityRemapperService`. | The service cannot directly perform `SendKey`; unsupported actions waste time if retried. |
| Keep `Custom` actions app-local and fast. | The SDK only names the custom action; your app does the real work. |

## BLE Optimizations

| What to do | Why |
| --- | --- |
| Do not use BLE for normal mouse input. | Android receives mouse input through HID; BLE helper is for metadata and battery. |
| Connect only when needed. | Saves battery and reduces Bluetooth work. |
| Disconnect after reading battery/service info. | Leaves the HID path alone and avoids stale connections. |
| Avoid reconnect loops. | Failed BLE connections can be expensive and noisy. |
| Observe `D06BleClient.state` once from your app lifecycle. | Avoid duplicate collectors doing the same work. |

## Linux Tool Optimizations

| Tool | Optimization |
| --- | --- |
| `d06_evdev.py` | Use `--node /dev/input/eventX` after you know the device. It skips auto-selection. |
| `d06_evdev.py` | Use a profile JSON for transforms/remaps instead of post-processing output in another script. |
| `d06_evdev.py` | Use `--seconds N` for captures so long-running tests do not grow logs forever. |
| `d06_hid.py` | Use `--dump --no-descriptor` when you only need device metadata. |
| `d06_hid.py` | Use `--capture` only when raw HID reports are needed; it is lower-level and noisier than evdev. |
| `dump_d06_gatt.py` | Use `--no-read` when service layout is enough. Reading every value can be slower. |
| Android adb script | Use `--device /dev/input/eventX` after identifying the right Android input node. |
| Android adb script | Use `--seconds N` and `--out file.txt` for bounded, reproducible captures. |

## App Build Optimizations

| What to do | Why |
| --- | --- |
| Depend on `d06-core` and `d06-input` only if you just need decoded input. | BLE/remapper modules are optional. |
| Add `d06-ble` only when reading battery or service metadata. | Keeps Bluetooth code out of apps that do not need it. |
| Add `d06-remapper` only when using Android remap helpers. | Keeps accessibility/remap code out of decoder-only apps. |
| Test performance with a release build. | Debug builds do not show real speed. |
| Enable R8/minify in the final app. | Removes unused SDK/app code. |
| Keep sample/debug event console out of production screens. | Live event lists are useful for testing, not for fastest runtime. |

## Future SDK Optimizations Worth Adding

These are not required for normal use, but they are useful improvements if the SDK grows.

| Possible SDK improvement | Benefit |
| --- | --- |
| No-allocation callback decoder | Avoid creating lists for very high-rate input paths. |
| Device-match cache by Android device ID | Avoid repeated name/VID/PID matching for the same device. |
| Built-in movement coalescer | Merge rapid `MousepadMove` events before app UI redraws. |
| Built-in throttled diagnostics exporter | Safer long-running debug logs. |
| Benchmark module | Measures decoder cost and catches performance regressions. |
| Optional Kotlin Flow/Channel adapter | Gives apps backpressure-friendly event streams. |
| Preset validation cache | Avoids repeating identical validation work. |
| Release APK for the sample app | Lets non-developers test without building from source. |
| Published Maven/JitPack release | Lets app developers depend on compiled artifacts instead of local source modules. |

## Fastest Recommended Android Setup

For an app that only needs to understand D06 input:

1. Depend only on `d06-core` and `d06-input`.
2. Create one `D06Input` instance.
3. Keep `detectMousepadTap = false` unless needed.
4. Keep callback work tiny.
5. Use no diagnostics in release.
6. Use `D06InputDiagnostics` only when collecting a bug report.
7. Test real performance on a release build and a real device.
