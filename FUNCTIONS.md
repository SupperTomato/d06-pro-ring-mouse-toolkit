# Plain-Language Function Guide

This guide explains what each user-facing part of the D06 Pro Ring Mouse Toolkit can do.

In this file, "function" means both:

- a feature a normal user can run, such as listing the D06 device on Linux
- an SDK part a developer can use inside an Android app

Private helper functions and tests are not meant to be used directly. They are only mentioned when they explain what a tool does.

## Quick Feature List

| Part | What it can do |
| --- | --- |
| Android sample app | Shows D06 button, scroll, and mousepad events on a real Android device. |
| Android SDK | Lets another Android app understand D06 input events. |
| Android remapper helper | Maps decoded D06 events to Android actions where Android allows it. |
| Android BLE helper | Reads Bluetooth service information and battery level. |
| Linux evdev tool | Lists D06 input devices and prints decoded mouse events. |
| Linux hidraw tool | Lists HID devices, saves HID descriptors, and captures raw HID reports. |
| Linux BLE/GATT tool | Saves Bluetooth service information through BlueZ. |
| Android adb tool | Captures Android input data from a connected or wireless-debug Android device. |

## D06 Event Names

These are the common names the toolkit uses after decoding the D06 Pro input.

| Event | Plain meaning |
| --- | --- |
| `LeftDown` | Left mouse button was pressed down. |
| `LeftUp` | Left mouse button was released. |
| `RightDown` | Right mouse button was pressed down. |
| `RightUp` | Right mouse button was released. |
| `MiddleDown` | Middle mouse button was pressed down. |
| `MiddleUp` | Middle mouse button was released. |
| `Scroll(Up, units)` | Wheel or scroll gesture moved upward. `units` is how many steps. |
| `Scroll(Down, units)` | Wheel or scroll gesture moved downward. |
| `HorizontalScroll(Left, units)` | Horizontal wheel moved left, if the device exposes it. |
| `HorizontalScroll(Right, units)` | Horizontal wheel moved right, if the device exposes it. |
| `MousepadMove(dx, dy)` | The D06 mousepad moved the pointer. `dx` is left/right, `dy` is up/down. |
| `MousepadTap` | A quick mousepad tap was detected. This is a timing guess because it can look like left click. |
| `Key(keyCode, scanCode, action)` | Android saw a keyboard or media key from the device. |
| `UnknownButton(code, pressed)` | A button was seen, but the toolkit does not yet know its real purpose. |

## Android SDK: Core Decoder

These parts live in `android-sdk/d06-core`.

| SDK part | What it can do |
| --- | --- |
| `D06RawMouse` | Holds raw mouse data: button flags, wheel data, X/Y movement, and time. Think "untranslated input packet." |
| `D06Mapper` | Turns raw mouse data into readable D06 events like `LeftDown`, `Scroll`, or `MousepadMove`. |
| `D06Mapper.mapRaw(...)` | Decodes one raw input packet and returns zero, one, or several D06 events. |
| `D06EventTransformConfig` | Stores user settings for movement and scroll changes, such as invert X, invert Y, sensitivity, and deadzone. |
| `D06EventTransformer` | Applies those movement and scroll settings to decoded events. |
| `D06EventTransformer.transform(event)` | Changes one event. Example: make mousepad movement faster or flip X direction. |
| `D06EventTransformer.transform(events)` | Changes a list of events. Events removed by a deadzone are dropped. |
| `ScrollDirection` | Names vertical scroll direction: `Up` or `Down`. |
| `HorizontalScrollDirection` | Names horizontal scroll direction: `Left` or `Right`. |
| `KeyAction` | Names key state: `Down` or `Up`. |

Important limit: `MousepadTap` is optional because the D06 can send the same signal for mousepad tap and physical left click. If exact left click behavior matters, leave mousepad tap detection off.

## Android SDK: App Input Decoder

These parts live in `android-sdk/d06-input`.

| SDK part | What it can do |
| --- | --- |
| `D06InputConfig` | Controls how Android input is decoded. It can enable mousepad tap detection, set tap timing, set movement transforms, and define which device names or USB/Bluetooth IDs count as D06. |
| `D06InputDeviceId` | Holds a vendor ID and product ID pair. Used to identify Bluetooth D06 or the 2.4 GHz receiver. |
| `D06InputDeviceInfo` | Holds Android's visible device name, vendor ID, and product ID. |
| `D06InputDeviceInfo.from(device)` | Copies Android device metadata into toolkit format. |
| `D06InputDeviceMatcher` | Checks whether an Android input device looks like a D06 Pro or the USB receiver. |
| `D06InputDeviceMatcher.matches(info)` | Returns true when name or hardware IDs match the accepted D06 list. |
| `D06EventListener` | Simple callback interface for Java users. It receives each decoded D06 event. |
| `D06EventListener.onEvent(event)` | Runs when the SDK decodes a D06 event. |
| `D06Input` | Main Android helper for apps. Feed it Android input events; it calls your code with D06 events. |
| `D06Input.create(listener)` | Java-friendly way to create `D06Input` with default settings. |
| `D06Input.create(config, listener)` | Java-friendly way to create `D06Input` with custom settings. |
| `D06Input.create(config, diagnostics, listener)` | Java-friendly way to create `D06Input` with custom settings and a diagnostic log. |
| `D06Input.dispatch(MotionEvent)` | Tries to decode an Android mouse/motion event. Returns true if it produced D06 events. |
| `D06Input.dispatch(KeyEvent)` | Tries to decode an Android key event. Returns true if it produced D06 events. |
| `D06Input.isD06Device(device)` | Checks whether an Android `InputDevice` appears to be the D06 or its receiver. |
| `D06Input.resetPointerTracking()` | Clears saved pointer position after focus changes, cancellation, or app state changes. |
| `D06InputDecoder` | Lower-level decoder. Use this when you want the decoded event list instead of automatic callbacks. |
| `D06InputDecoder.onMotionEvent(event)` | Converts one Android `MotionEvent` into D06 events. |
| `D06InputDecoder.onKeyEvent(event)` | Converts one Android `KeyEvent` into a D06 key event. |
| `D06InputDecoder.isD06Device(device)` | Same device check as `D06Input`, but on the lower-level decoder. |
| `D06InputDecoder.resetPointerTracking()` | Clears saved pointer position in the lower-level decoder. |
| `D06DiagnosticEvent` | One saved diagnostic record: decoded event, device info, and time. |
| `D06InputDiagnostics` | Keeps a small recent history of decoded events for logs or bug reports. |
| `D06InputDiagnostics.record(...)` | Adds one event to the diagnostic history. |
| `D06InputDiagnostics.snapshot()` | Returns the current diagnostic history. |
| `D06InputDiagnostics.clear()` | Empties the diagnostic history. |
| `D06InputDiagnostics.toJsonLines()` | Exports the diagnostic history as text where each line is one JSON event. |
| `D06_VENDOR_ID` | Known vendor ID for the D06 family: `248a`. |
| `D06_BLUETOOTH_PRODUCT_ID` | Known product ID for Bluetooth D06: `0101`. |
| `D06_USB_RECEIVER_PRODUCT_ID` | Known product ID for the 2.4 GHz receiver: `0401`. |
| `D06_PRODUCT_ID` | Old name kept for compatibility. Use the Bluetooth or receiver product ID instead. |
| `D06_DEVICE_IDS` | The built-in list of accepted D06 hardware IDs. |

## Android SDK: BLE Metadata

These parts live in `android-sdk/d06-ble`.

| SDK part | What it can do |
| --- | --- |
| `D06BleState.Idle` | BLE helper is not connected. |
| `D06BleState.Connecting` | BLE helper is trying to connect. |
| `D06BleState.Connected` | BLE connection is open and service discovery is starting. |
| `D06BleState.ServicesDiscovered(profile)` | BLE services were found and summarized. |
| `D06BleState.BatteryLevel(percent)` | Battery level was read. |
| `D06BleState.Error(message)` | BLE operation failed. |
| `D06BleClient` | Android Bluetooth helper for reading D06 service info and battery level. |
| `D06BleClient.state` | A live status stream apps can observe. |
| `D06BleClient.connect(device)` | Connects to a paired or selected Bluetooth D06 device. |
| `D06BleClient.disconnect()` | Closes the BLE connection and returns to idle state. |
| `D06BleClient.readBatteryLevel()` | Requests battery level. Returns false if battery service is not available. |
| `D06GattServiceSummary` | Short summary of one BLE service and its characteristics. |
| `D06GattProfile` | Summary of all known BLE services found on the device. |
| `D06GattProfile.fromServiceUuids(...)` | Builds a profile from a simple list of service IDs. |
| `D06GattProfile.fromBluetoothGattServices(...)` | Builds a profile from Android's Bluetooth service objects. |
| `D06GattUuids` | Stores the known Bluetooth UUID numbers for GAP, GATT, Device Information, Battery, HID, HID Report Map, and the Telink-like vendor service. |

The BLE helper reads metadata. It intentionally does not write vendor/Telink commands.

## Android SDK: Remapping

These parts live in `android-sdk/d06-remapper`.

| SDK part | What it can do |
| --- | --- |
| `D06RemapAction.NoOp` | Do nothing. |
| `D06RemapAction.Back` | Ask Android to go back. |
| `D06RemapAction.Home` | Ask Android to go home. |
| `D06RemapAction.RecentApps` | Ask Android to open recent apps. |
| `D06RemapAction.ScrollBy(dy)` | Ask Android to perform a vertical scroll gesture. |
| `D06RemapAction.SendKey(keyCode)` | Represents sending a key. The helper can choose it, but AccessibilityService cannot directly perform it. |
| `D06RemapAction.Custom(name)` | Placeholder for an app-defined action. The app must decide what it means. |
| `D06RemapConfig` | Simple remap settings for middle click, mousepad tap, unknown button 4, and unknown button 5. |
| `D06RemapConfig.toPreset(name)` | Turns simple remap settings into a named preset. |
| `D06RemapPreset` | A named set of rules that says which D06 event should become which action. |
| `D06RemapPreset.actionFor(event)` | Looks up what action a preset chooses for one D06 event. |
| `D06RemapPresetBuilder.on(event, action)` | Adds a rule: when this exact event happens, use this action. |
| `D06RemapPresetBuilder.on(event) { ... }` | Adds a rule that can choose an action dynamically for one exact event. |
| `D06RemapPresetBuilder.onScroll { ... }` | Adds a rule for scroll events. Useful for volume, page up/down, or custom scroll behavior. |
| `D06RemapPresetBuilder.onUnknownButton(code, action)` | Adds a rule for unknown button 4 or 5 after release. |
| `D06RemapPresetBuilder.fallback(action)` | Sets what to do when no rule matches. |
| `D06RemapPresets.Accessibility` | Built-in preset for common accessibility-style actions. |
| `D06RemapPresets.Presentation` | Built-in preset for slide/page control style use. |
| `D06RemapPresets.Media` | Built-in preset for play/pause, next, and volume style use. |
| `D06RemapPresets.MouseOnly` | Built-in preset that leaves mouse events alone. |
| `D06Remapper` | Applies a preset to incoming D06 events. |
| `D06Remapper.actionFor(event)` | Returns the action chosen for one event. |
| `D06Remapper.handle(event, perform)` | Finds the action, then asks your app to perform it. Returns true if something was done. |
| `D06RemapPolicy` | Small wrapper around `D06Remapper` for apps that want a policy object. |
| `D06RemapPolicy.actionFor(event)` | Returns the chosen action for one event. |
| `D06RemapValidationIssue` | Explains why a preset action cannot be performed by the built-in AccessibilityService helper. |
| `D06RemapValidator.validateForAccessibilityService(preset)` | Checks a preset and reports actions Android AccessibilityService cannot execute directly. |
| `D06AccessibilityRemapperService` | Android service helper that can perform allowed global actions after your app feeds it decoded D06 events. |
| `D06AccessibilityRemapperService.perform(action)` | Performs supported actions: back, home, recent apps, or scroll gesture. Returns false for unsupported key/custom actions. |

Important limit: Android does not let a normal app globally replace every hardware mouse event. Remapping is limited by what Android exposes to normal apps and AccessibilityService.

## Android Sample App

| Part | What it can do |
| --- | --- |
| `d06-sample` | Installs a small Android app that displays decoded D06 events live. |
| `MainActivity` | Main screen of the sample app. It receives Android input, sends it through `D06Input`, and prints the event log. |

For non-developer testing, this is the easiest Android path once someone builds the APK.

## Linux Tool: Decoded Input Events

This tool is `tools/linux/d06_evdev.py`.

| Command or function | What it can do |
| --- | --- |
| `python3 tools/linux/d06_evdev.py --list` | Lists Linux input devices and marks likely D06 devices. |
| `python3 tools/linux/d06_evdev.py --seconds 30` | Prints decoded D06 events for 30 seconds. |
| `--node /dev/input/eventX` | Reads one exact Linux input device. Useful when auto-detection is wrong. |
| `--match TERM` | Picks devices whose Linux metadata contains a word or ID you provide. |
| `--profile file.json` | Applies movement, scroll, or remap settings from a JSON profile. |
| `D06EvdevProfile.from_dict(...)` | Loads transform/remap settings from a JSON-like dictionary. |
| `D06EvdevProfile.apply(...)` | Applies profile settings to one decoded event. |
| `D06EvdevTranslator.process(...)` | Converts one Linux input event into zero or more D06 JSON events. |
| `event_to_json(...)` | Turns one decoded event into a JSON text line. |
| `load_profile(...)` | Reads a profile JSON file. |
| `parse_input_devices(...)` | Reads Linux `/proc/bus/input/devices` text and finds input devices. |
| `load_input_devices(...)` | Loads the real Linux input-device list from `/proc/bus/input/devices`. |
| `select_nodes(...)` | Chooses which `/dev/input/eventX` nodes to read. |
| `decode_event_bytes(...)` | Converts raw Linux input bytes into readable input-event objects. |
| `stream_nodes(...)` | Watches input nodes and prints decoded JSON events. |
| `print_device_list(...)` | Prints a human-readable list of input devices. |
| `main(...)` | Runs the command-line tool. |

This tool only prints events. It does not take over the mouse or stop normal pointer movement.

## Linux Tool: HID Inspection

This tool is `tools/linux/d06_hid.py`.

| Command or function | What it can do |
| --- | --- |
| `python3 tools/linux/d06_hid.py --list` | Lists Linux hidraw devices and marks likely D06 devices. |
| `python3 tools/linux/d06_hid.py --dump --out file.json` | Saves HID device metadata and report descriptor details to JSON. |
| `python3 tools/linux/d06_hid.py --capture --seconds 15` | Captures raw HID reports for a short time. |
| `--node /dev/hidrawX` | Reads one exact hidraw device. |
| `--match TERM` | Picks hidraw devices whose metadata contains a word or ID. |
| `--report-size N` | Changes how many bytes the capture reads at a time. |
| `--no-descriptor` | Dumps metadata without including descriptor bytes. |
| `list_hidraw_devices(...)` | Finds hidraw devices from Linux sysfs. |
| `dump_hidraw_devices(...)` | Builds a JSON-ready summary of HID devices. |
| `select_hidraw_nodes(...)` | Chooses which `/dev/hidrawX` nodes to read. |
| `capture_hidraw_nodes(...)` | Watches hidraw nodes and prints raw HID reports as JSON lines. |
| `summarize_report_descriptor(...)` | Turns a HID descriptor into a simpler summary. |
| `HidReportDescriptorParser.parse(...)` | Reads a HID descriptor and extracts report IDs, collections, and fields. |
| `parse_uevent(...)` | Reads Linux sysfs key/value metadata. |
| `parse_hid_id(...)` | Splits a HID hardware ID into bus, vendor, and product IDs. |
| `d06_match_reasons(...)` | Explains why a device looks like D06: name match or hardware ID match. |
| `bytes_to_hex(...)` | Formats bytes as readable hex text. |
| `print_device_list(...)` | Prints a human-readable hidraw device list. |
| `main(...)` | Runs the command-line tool. |

Use this when you need lower-level evidence than normal mouse events.

## Linux Tool: BLE/GATT Dump

This tool is `tools/linux/dump_d06_gatt.py`.

| Command or function | What it can do |
| --- | --- |
| `python3 tools/linux/dump_d06_gatt.py --address AA:BB:CC:DD:EE:FF` | Connects to a BLE device and saves service/characteristic metadata. |
| `--name D06` | Scans by device name when no address is provided. |
| `--out-dir DIR` | Chooses where to save `gatt_dump.json` and any HID report-map file. |
| `--timeout 10` | Sets BLE scan/connect timeout. |
| `--no-read` | Lists BLE services without trying to read values. |
| `uuid_name(...)` | Converts known UUID numbers into friendly names. |
| `bytes_to_hex(...)` | Formats bytes as readable hex text. |
| `bytes_to_text(...)` | Shows readable text when a BLE value is plain text. |
| `dump_gatt(...)` | Connects through BlueZ/bleak and builds the full GATT dump. |
| `main(...)` | Runs the command-line tool. |

This tool reads Bluetooth metadata. It does not write vendor commands.

## Android Host Tool: adb Input Capture

This tool is `tools/android/d06_android_input.sh`.

| Command or function | What it can do |
| --- | --- |
| `tools/android/d06_android_input.sh list` | Shows Android input devices and D06/TK receiver hints from `getevent` and `dumpsys input`. |
| `tools/android/d06_android_input.sh capture --seconds 10` | Captures Android input events for a short time while you press or move the D06. |
| `--device /dev/input/eventX` | Captures one exact Android input node. |
| `--serial SERIAL` | Chooses one Android phone/tablet when more than one is connected. |
| `--out file.txt` | Saves capture output to a file while also showing it on screen. |
| `tools/android/d06_android_input.sh dump-input` | Saves Android's full input-device report. |
| `usage()` | Prints help text. |
| `run_adb()` | Runs adb, optionally against the selected serial number. |
| `parse_common()` | Reads shared command options such as `--serial`. |

Use this when testing a real Android device over USB debugging or wireless debugging.

## Config And Service Files

| File | What it can do |
| --- | --- |
| `tools/linux/d06-profile.example.json` | Example Linux profile for movement sensitivity, scroll sensitivity, axis inversion, deadzone, and event remapping. |
| `tools/linux/99-d06-pro.rules` | udev rule template that can make D06 input devices easier to access without running as root. |
| `tools/linux/d06-evdev.service` | systemd user service template for running the Linux evdev decoder in the background. |

## What This Toolkit Does Not Do

- It does not flash or modify D06 firmware.
- It does not write to the vendor/Telink service.
- It does not make a normal Android app able to globally replace all mouse input.
- It does not guarantee `MousepadTap` is physically different from left click.
- It does not hide the normal mouse pointer movement on Linux or Android.
