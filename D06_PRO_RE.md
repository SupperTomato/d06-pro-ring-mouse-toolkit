# D06 Pro Ring Mouse Reverse Engineering

Date: 2026-06-04

## Host Access Notes

- The Linux shell is WSL2 and does not expose the host Bluetooth stack, BlueZ, `/dev/input`, or `/dev/hidraw`.
- Windows sees the device and was used for enumeration/capture via PowerShell, WinRT, `hid.dll`, and Raw Input.

## Device Identity

- Advertised/Windows name: `D06 Pro`
- BLE address: `D1:0B:CB:55:CA:78`
- Windows BLE instance: `BTHLE\DEV_D10BCB55CA78\7&9B5A8DB&0&D10BCB55CA78`
- VID source: `0x02` (Bluetooth SIG-assigned Device ID vendor ID)
- VID: `0x248A`
- PID: `0x0101`
- Product version: `0x0001`
- Bluetooth manufacturer/company ID from registry: `0x0211`, assigned by Bluetooth SIG to Telink Semiconductor Co. Ltd.
- GAP appearance from registry: `0x03C2` (`962` decimal)

References:

- Bluetooth SIG Assigned Numbers lists company ID `0x0211` as Telink Semiconductor Co. Ltd: https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Assigned_Numbers/out/en/index-en.html
- Telink SDK docs show the OTA characteristic UUID pattern found on this device: https://doc.telink-semi.cn/doc/en/software/res/sdk/ble/b91_ble_single_connection_en/b91_ble_single_connection_en/

## GATT Services

Windows blocks app-level enumeration of the HID-over-GATT service contents because the OS HID driver owns it, but the service list and non-HID service metadata are visible.

| Handle | UUID | Meaning | Notes |
| --- | --- | --- | --- |
| `0x0001` | `00001800-0000-1000-8000-00805f9b34fb` | Generic Access | Device name, appearance, preferred connection params |
| `0x0008` | `00001801-0000-1000-8000-00805f9b34fb` | Generic Attribute | No chars returned |
| `0x0009` | `0000180a-0000-1000-8000-00805f9b34fb` | Device Information | PnP ID |
| `0x000c` | `0000180f-0000-1000-8000-00805f9b34fb` | Battery Service | Battery level characteristic |
| `0x0010` | `00010203-0405-0607-0809-0a0b0c0d1912` | Telink-style vendor/OTA service | See chars below |
| `0x0019` | `00001812-0000-1000-8000-00805f9b34fb` | HID-over-GATT | Access denied through WinRT GATT |

Vendor service characteristics:

| Handle | UUID | Properties | Notes |
| --- | --- | --- | --- |
| `0x0011` | `00010203-0405-0607-0809-0a0b0c0d2b12` | Read, WriteWithoutResponse | Matches Telink OTA data UUID pattern |
| `0x0013` | `f51c527c-79bb-70bb-8b42-d234b6818e38` | Read, WriteWithoutResponse, Notify | Unknown vendor function |
| `0x0016` | `0000feff-0000-1000-8000-00805f9b34fb` | Read, WriteWithoutResponse, Notify | Unknown/Telink-adjacent vendor function |

## HID Collections

Windows exposes three HID top-level collections under the BLE HID service:

| Collection | Windows class | Usage page/usage | Report ID | Input length | Output length | Purpose |
| --- | --- | --- | --- | --- | --- | --- |
| `Col01` | Keyboard | `0x0001 / 0x0006` | `0x01` | 9 bytes | 2 bytes | Keyboard report and LED output |
| `Col02` | HIDClass | `0x000c / 0x0001` | `0x02` | 3 bytes | 0 | Consumer control report |
| `Col03` | Mouse | `0x0001 / 0x0002` | `0x03` | 7 bytes | 0 | Mouse buttons, motion, wheel |

### Keyboard Report

Parsed caps:

- Report ID `0x01`
- Input button usages on page `0x07`
- Modifier range: `0xE0..0xE7`
- Key usage range: `0x00..0xF1`
- Output LED usages on page `0x08`, range `0x01..0x05`

Likely standard keyboard layout:

```text
01 mm rr k1 k2 k3 k4 k5 k6
```

where `mm` is modifier bits, `rr` is reserved, and `k1..k6` are key usage slots.

### Consumer Control Report

Parsed caps:

- Report ID `0x02`
- Usage page `0x0C`
- Consumer usage range `0x0001..0x028C`
- Input report length 3 bytes

Likely layout:

```text
02 uu uu
```

where `uu uu` is a 16-bit consumer-control usage.

### Mouse Report

Parsed caps:

- Report ID `0x03`
- Button usage page `0x09`, usages `0x01..0x05`
- Value usages returned by Windows: `Y (0x31)`, `X (0x30)`, `Wheel (0x38)`
- `X` and `Y`: 16-bit signed, logical range `-32767..32767`
- Wheel: 8-bit signed, logical range `-127..127`
- Input report length 7 bytes

Likely layout from parsed caps:

```text
03 bb yy yy xx xx ww
```

`bb` contains up to five mouse button bits plus padding. The `Y, X, wheel` order is what Windows returned from `HidP_GetValueCaps`; confirm raw byte order on a host that can read the HID report map or by BLE sniffing.

## Captured Runtime Behavior

Capture artifacts:

- `artifacts/raw_input_events.json`: mouse movement plus left/right click sample
- `artifacts/raw_input_events_controls.json`: wheel/scroll sample

Confirmed from Raw Input:

- Mouse motion comes from `Col03`.
- Left click emits Raw Input button flags:
  - `0x0001`: left down
  - `0x0002`: left up
- Right click emits:
  - `0x0004`: right down
  - `0x0008`: right up
- Wheel/scroll emits:
  - `0x0400`: vertical wheel event
  - data `+120`: one wheel step one direction
  - data `-120`: one wheel step the opposite direction (`65416` as unsigned 16-bit in JSON)

No keyboard or consumer-control events were captured in the two manual capture windows.

## Labeled Button Captures

| Physical control | Artifact | Source collection | Observed event |
| --- | --- | --- | --- |
| Left click | `artifacts/button_left_click.json` | Mouse `Col03` | `0x0001` down, `0x0002` up |
| Right click | `artifacts/button_right_click.json` | Mouse `Col03` | `0x0004` down, `0x0008` up |
| Middle button click | `artifacts/button_middle_click.json` | Mouse `Col03` | `0x0010` down, `0x0020` up |
| Middle button click repeat | `artifacts/button_middle_click_repeat.json` | Mouse `Col03` | Repeated as `0x0010/0x0020` |
| Middle button click repeat 2 | `artifacts/button_middle_click_repeat2.json` | Mouse `Col03` | Repeated as `0x0010/0x0020` |
| Left then right repeat | `artifacts/button_left_right_repeat.json` | Mouse `Col03` | Left repeated as `0x0001/0x0002`; right repeated as `0x0004/0x0008` |
| Scroll up/down | `artifacts/button_scroll_up_down.json` | Mouse `Col03` | Wheel flag `0x0400`; up emitted `+120`, down emitted `-120` |
| Scroll up/down repeat | `artifacts/button_scroll_up_down_repeat.json` | Mouse `Col03` | Repeated as wheel flag `0x0400`; up `+120`, down `-120` |
| Scroll up loose | `artifacts/gesture_scroll_up_loose.json` | Mouse `Col03` | Wheel flag `0x0400`; user-labeled scroll up emitted only `+120` |
| Scroll down loose | `artifacts/gesture_scroll_down_loose.json` | Mouse `Col03` | Wheel flag `0x0400`; user-labeled scroll down emitted only `-120` |
| Mousepad mixed motion | `artifacts/mousepad_motion_axes_repeat.json` | Mouse `Col03` | Mixed-direction capture; useful for confirming relative motion, not for final direction labels |
| Mousepad right | `artifacts/gesture_mousepad_right_loose.json` | Mouse `Col03` | Dominant `+X` motion |
| Mousepad left | `artifacts/gesture_mousepad_left_loose.json` | Mouse `Col03` | Dominant `-X` motion |
| Mousepad up | `artifacts/gesture_mousepad_up_loose.json` | Mouse `Col03` | Dominant `-Y` motion |
| Mousepad down | `artifacts/gesture_mousepad_down_loose.json` | Mouse `Col03` | Dominant `+Y` motion |
| Mousepad tap | `artifacts/gesture_mousepad_tap.json` | Mouse `Col03` | Emits left click: `0x0001` down, `0x0002` up; minor incidental motion possible |
| Mousepad double tap | `artifacts/gesture_touchpad_double_tap.json` | Mouse `Col03` | Emits repeated left clicks: `0x0001` down, `0x0002` up; no distinct double-tap HID event observed |
| Mousepad tap-hold-drag | `artifacts/gesture_touchpad_tap_hold_drag.json` | Mouse `Col03` | Emits pointer motion only in this capture; no held left-button state observed |
| Left long press | `artifacts/button_left_long_press.json` | Mouse `Col03` | Emits normal held left button: `0x0001` down, `0x0002` up; no keyboard/consumer event observed |
| Right long press | `artifacts/button_right_long_press.json` | Mouse `Col03` | Emits normal held right button: `0x0004` down, `0x0008` up; no keyboard/consumer event observed |

Corrected mousepad direction mapping:

| User gesture | Mouse delta |
| --- | --- |
| Right swipe | `+X` |
| Left swipe | `-X` |
| Up swipe | `-Y` |
| Down swipe | `+Y` |
| Tap | Left click: `0x0001/0x0002` |
| Double tap | Repeated left clicks: `0x0001/0x0002` pairs |
| Tap-hold-drag | Pointer motion only in captured Windows Raw Input; no drag-button hold observed |

Corrected scroll mapping:

| User gesture | Raw Input event |
| --- | --- |
| Scroll up | Wheel flag `0x0400`, data `+120` |
| Scroll down | Wheel flag `0x0400`, data `-120` (`65416` if read unsigned) |

## Feature Audit

Features already reverse engineered:

| Area | Status |
| --- | --- |
| Mouse buttons 1-3 | Mapped: left, right, middle |
| Mousepad tap | Mapped to left click |
| Mousepad relative motion | Mapped: right `+X`, left `-X`, up `-Y`, down `+Y` |
| Wheel/scroll | Mapped: up `+120`, down `-120` |
| HID collections | Enumerated: keyboard, consumer control, mouse |
| GATT services | Enumerated except OS-owned HID service contents |
| Device identity | Mapped: BLE address, VID/PID/version, Telink company ID |

Features still available to reverse engineer:

| Feature | Evidence | How to test |
| --- | --- | --- |
| Mouse button 4 | Mouse HID caps expose usages `0x01..0x05`; only buttons 1-3 have been observed | Capture any side/back/forward physical control and look for Raw Input flags `0x0040` down, `0x0080` up |
| Mouse button 5 | Same as above | Capture remaining side/back/forward control and look for `0x0100` down, `0x0200` up |
| Keyboard-mode outputs | `Col01` is a keyboard collection with report ID `0x01` and key usages `0x00..0xF1` | Put device in any keyboard/presentation mode and capture Raw Input keyboard events |
| Consumer/media controls | `Col02` is a consumer-control collection with report ID `0x02` and usages `0x0001..0x028C` | Press media/presenter controls and capture Raw Input HID/consumer events |
| Longer long-press thresholds | Left/right long press emitted only held mouse buttons in tested Windows captures; phone/app manuals mention alternate behavior | Retest with precise 2-3 second and phone/camera app context if needed |
| Additional touchpad gestures | Double tap and tap-hold-drag were captured; no distinct HID gesture beyond repeated left clicks / motion was observed | Test any other physical gesture pattern separately if present |
| Mode toggle behavior | A mode button may emit no event but change what later controls emit | Capture before/after state: press mode control, then repeat left/right/middle/scroll/mousepad |
| Battery level / notifications | Battery service `0x180F`, char `0x2A19`, read/notify | Improve WinRT buffer decoding or use another BLE client to read/subscribe |
| Vendor/Telink private service | Service `00010203-0405-0607-0809-0a0b0c0d1912` with writable/notify chars | Passive notify capture first; avoid writes until protocol is known because it may be OTA/config |
| HID report map bytes | HID service `0x1812` exists but Windows blocks app-level access | Pair to Linux/BlueZ with `hidraw`, or sniff BLE, to dump the raw report descriptor |

All runtime Raw Input captures so far produced only mouse collection events. No keyboard or consumer-control event has been observed yet.

## Likely Activation Map

The physical unit appears to expose fewer controls than the HID descriptor advertises. Based on the observed captures and the D06 Pro manuals, treat the main controls as:

| Physical action | Expected host behavior | Verification status |
| --- | --- | --- |
| Left click button | Mouse left click | Confirmed |
| Right click button | Mouse right click | Confirmed |
| Wheel / middle button click | Mouse middle click | Confirmed |
| Wheel scroll up | Wheel `+120` | Confirmed |
| Wheel scroll down | Wheel `-120` | Confirmed |
| Touchpad move | Relative mouse movement | Confirmed |
| Touchpad tap | Mouse left click | Confirmed |
| Touchpad double tap | Repeated left clicks; apps may interpret this as "Like" or open behavior | Confirmed |
| Touchpad tap-hold-drag | Pointer motion only in Windows capture; no drag-button hold observed | Confirmed for this host mode |
| Left button long press | Normal held left click in Windows capture; manuals describe app-specific phone behavior | Confirmed for this host mode |
| Right button long press | Normal held right click in Windows capture; manuals describe app-specific phone behavior | Confirmed for this host mode |
| Wheel long press ~1 second | Power on or mode switch between Bluetooth and 2.4 GHz | Risky to capture; may disconnect |
| Wheel long press ~5 seconds | Power off | Known from manual; avoid during normal capture |
| 2.4 GHz receiver mode | May expose a different USB HID device than Bluetooth mode | Not tested |

Important distinction: the manuals describe several phone/video/PPT behaviors as application outcomes. On Windows Raw Input, those may still be ordinary mouse clicks, wheel events, or keyboard/consumer events. They only become "next video", "previous page", "like", "volume", or "camera" inside the target app.

## Tools Added

- `tools/dump_d06_gatt.ps1`: WinRT BLE service/characteristic enumerator.
- `tools/dump_hid_caps.ps1`: `hid.dll` parser for HID top-level collection caps.
- `tools/capture_hid_reports.ps1`: direct HID report reader. It confirms Windows denies direct read access to D06 keyboard/mouse collections.
- `tools/list_raw_input_devices.ps1`: Raw Input device source lister.
- `tools/capture_raw_input.ps1`: event capture by source device path.

Useful commands from WSL:

```bash
rtk powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/dump_hid_caps.ps1 -Filter d10bcb55ca78 -OutFile artifacts/hid_caps.json
rtk powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/list_raw_input_devices.ps1 -Filter 'd10bcb55ca78|02248a'
rtk powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/capture_raw_input.ps1 -Filter d10bcb55ca78 -OutFile artifacts/raw_input_events.json -Seconds 12
```

## Remaining Gaps

- Raw HID report bytes for keyboard/mouse are blocked by Windows (`Access is denied`) while the system owns those collections.
- WinRT GATT returns `AccessDenied` for the HID service, so the HID Report Map characteristic could not be read from Windows.
- To fully confirm report byte offsets and descriptor bytes, pair the device to a Linux host with BlueZ/hidraw access, use a BLE sniffer, or temporarily inspect from a host where the HID service is not claimed by the OS HID driver.
