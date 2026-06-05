# D06 Android SDK Design

## Goal

Create a Kotlin Android SDK that turns D06 Pro ring mouse input into stable, typed application events and exposes optional BLE metadata. The SDK should make the verified reverse-engineered behavior usable from Android apps while being explicit about Android security limits around system-wide remapping.

Primary source material:

- `D06_PRO_RE.md`
- `artifacts/hid_caps.json`
- labeled Raw Input artifacts under `artifacts/`
- Android platform APIs: `MotionEvent`, `KeyEvent`, `InputDevice`, `BluetoothGatt`, Bluetooth runtime permissions, and `AccessibilityService`

## Scope

The SDK will include all three requested areas:

1. App-level input decoding for apps that receive D06 input.
2. BLE/GATT metadata access where Android allows it.
3. A remapper/sample layer that demonstrates what is possible system-wide and clearly surfaces platform limits.

The first implementation should prioritize a reliable app-level SDK. The remapper must not promise full global HID interception because Android does not expose that to ordinary apps.

## Modules

### `d06-core`

Pure Kotlin module with no Android dependency.

Responsibilities:

- Define `D06Event` sealed types.
- Define known button, wheel, and mousepad mappings.
- Normalize raw deltas and wheel units.
- Track simple gesture state where needed, such as tap, double tap, and long press timing.
- Expose deterministic parsers that can be unit tested without Android.

Key event model:

```kotlin
sealed interface D06Event {
    data object LeftDown : D06Event
    data object LeftUp : D06Event
    data object RightDown : D06Event
    data object RightUp : D06Event
    data object MiddleDown : D06Event
    data object MiddleUp : D06Event
    data class Scroll(val direction: ScrollDirection, val units: Int) : D06Event
    data class MousepadMove(val dx: Int, val dy: Int) : D06Event
    data object MousepadTap : D06Event
    data class UnknownButton(val code: Int, val pressed: Boolean) : D06Event
}
```

Known mapping:

- Left click: down `0x0001`, up `0x0002`
- Right click: down `0x0004`, up `0x0008`
- Middle click: down `0x0010`, up `0x0020`
- Scroll up: wheel flag `0x0400`, data `+120`
- Scroll down: wheel flag `0x0400`, data `-120`
- Mousepad right: `+X`
- Mousepad left: `-X`
- Mousepad up: `-Y`
- Mousepad down: `+Y`
- Mousepad tap: left click down/up sequence with negligible movement

### `d06-input`

Android module for app-level integration.

Responsibilities:

- Detect likely D06 devices from `InputDevice` metadata when Android exposes name, vendor ID, product ID, source flags, or descriptor.
- Convert `MotionEvent` and `KeyEvent` into `D06Event`.
- Provide callbacks and Kotlin Flow adapters.
- Preserve raw Android events for diagnostics.

Public API shape:

```kotlin
class D06InputDecoder(
    private val config: D06InputConfig = D06InputConfig()
) {
    fun isD06Device(device: InputDevice): Boolean
    fun onMotionEvent(event: MotionEvent): List<D06Event>
    fun onKeyEvent(event: KeyEvent): List<D06Event>
}
```

The module should be usable from an Activity, View, Compose modifier, or custom input dispatcher. It should not require Bluetooth permissions for input decoding.

### `d06-ble`

Android BLE metadata module.

Responsibilities:

- Connect to a selected `BluetoothDevice`.
- Discover GATT services.
- Read GAP, DIS, battery, and vendor service metadata where Android permits.
- Subscribe to battery/vendor notifications where permitted.
- Avoid unsafe writes to the Telink-style vendor service by default.

Known services:

- GAP `00001800-0000-1000-8000-00805f9b34fb`
- GATT `00001801-0000-1000-8000-00805f9b34fb`
- DIS `0000180a-0000-1000-8000-00805f9b34fb`
- Battery `0000180f-0000-1000-8000-00805f9b34fb`
- HID `00001812-0000-1000-8000-00805f9b34fb`
- Vendor/Telink-like `00010203-0405-0607-0809-0a0b0c0d1912`

Android 12+ requires `BLUETOOTH_CONNECT` for connected-device GATT operations. BLE calls are asynchronous and should expose suspending functions or Flow results.

### `d06-remapper`

Optional Android library/sample layer for remapping demonstrations.

Responsibilities:

- Provide an AccessibilityService-based sample for generating gestures when user grants accessibility permission.
- Document that ordinary Android apps cannot globally intercept and replace all hardware HID events.
- Offer extension points for device-owner, rooted, Shizuku, or custom-ROM integrations without making them core dependencies.

This module is best shipped as a sample and reference implementation rather than a promise of universal system-wide remapping.

### `d06-sample`

Android sample app.

Responsibilities:

- Live event console for D06 input.
- Device info screen for BLE metadata.
- Calibration screen for unknown buttons, long press, double tap, and mode behavior.
- Export/share captured event logs as JSON.

## Data Flow

App-level flow:

1. Android receives `MotionEvent` or `KeyEvent`.
2. App passes the event to `D06InputDecoder`.
3. Decoder checks the event source and device identity.
4. Decoder maps Android button, wheel, and motion fields into `D06Event`.
5. App consumes typed events and can optionally inspect raw diagnostics.

BLE flow:

1. App requests Bluetooth permissions when needed.
2. App selects or supplies a `BluetoothDevice`.
3. `d06-ble` connects with `BluetoothGatt`.
4. Service discovery returns a typed `D06GattProfile`.
5. Reads/notifications produce `D06BleEvent` values.

Remapper flow:

1. Sample app receives app-level or accessibility-visible input.
2. Decoder maps it into `D06Event`.
3. Remapper policy decides whether to emit an Android gesture/action.
4. Accessibility dispatch is used only where Android permits it.

## Error Handling

- Device detection must be probabilistic, not brittle. If Android hides VID/PID, fall back to name/source heuristics and expose confidence.
- Unknown input should be emitted as `UnknownButton`, `UnknownMotion`, or raw diagnostics, not dropped silently.
- BLE permission failures should return typed errors with the missing permission.
- HID service access denial should be treated as expected on OS-owned HID devices.
- Vendor service writes must be opt-in and marked experimental.

## Testing

`d06-core`:

- Unit tests for all known mouse button flags.
- Unit tests for scroll `+120` and `-120`.
- Unit tests for mousepad axis sign mapping.
- Unit tests for tap detection from left down/up with low movement.
- Tests for unknown buttons 4/5 remaining unmapped until captured.

`d06-input`:

- Robolectric or Android instrumented tests for `MotionEvent` and `KeyEvent` decoding.
- Device identity heuristic tests with fake `InputDevice` metadata where possible.

`d06-ble`:

- Unit tests for UUID/profile parsing.
- Fake GATT abstraction tests for service discovery and permission errors.
- Instrumented BLE tests only when hardware is available.

`d06-sample`:

- Manual hardware validation checklist backed by exported JSON logs.

## Delivery Shape

Initial repository layout:

```text
android-sdk/
  settings.gradle.kts
  build.gradle.kts
  d06-core/
  d06-input/
  d06-ble/
  d06-remapper/
  d06-sample/
```

The first build milestone should produce:

- Kotlin/JVM unit tests for `d06-core`.
- Android library modules compiling.
- Sample app showing a live D06 event console.
- README with setup, permissions, and known Android limitations.

## Non-Goals

- Do not claim universal system-wide remapping on stock Android.
- Do not write to the vendor/Telink service until passive behavior is understood.
- Do not depend on root, Shizuku, or device-owner APIs in core modules.
- Do not assume buttons 4/5 exist physically just because the HID descriptor exposes five button usages.

## Open Validation Items

- Confirm Android exposes D06 VID/PID `0x248A/0x0101` through `InputDevice` on the target phone.
- Confirm Android button constants for middle click and wheel match the Windows Raw Input captures.
- Capture keyboard and consumer-control events if the D06 mode switch can activate them.
- Decide later whether a separate privileged remapper is needed.
