# D06 Linux Evdev Design

## Goal

Add Linux support by translating Linux `evdev` events into the same canonical D06 event names already proven from Windows Raw Input captures and used by the Android SDK.

## Scope

This is not a second reverse-engineering pass. The Windows captures remain the source of truth for D06 behavior. Linux support adds a platform adapter:

```text
/dev/input/event* -> Linux evdev event -> D06 event JSON
```

The first milestone is a command-line tool that can list likely D06 event nodes and stream decoded JSON events. Remapping and synthetic input injection are out of scope for this milestone.

## Mapping

| D06 event | Windows source | Linux evdev source |
| --- | --- | --- |
| `LeftDown` / `LeftUp` | `0x0001` / `0x0002` | `EV_KEY BTN_LEFT 1` / `0` |
| `RightDown` / `RightUp` | `0x0004` / `0x0008` | `EV_KEY BTN_RIGHT 1` / `0` |
| `MiddleDown` / `MiddleUp` | `0x0010` / `0x0020` | `EV_KEY BTN_MIDDLE 1` / `0` |
| `UnknownButton(4, ...)` | `0x0040` / `0x0080` | `EV_KEY BTN_SIDE 1` / `0` |
| `UnknownButton(5, ...)` | `0x0100` / `0x0200` | `EV_KEY BTN_EXTRA 1` / `0` |
| `Scroll(Up, units)` | wheel `+120` | `EV_REL REL_WHEEL +N` or `REL_WHEEL_HI_RES +120*N` |
| `Scroll(Down, units)` | wheel `-120` | `EV_REL REL_WHEEL -N` or `REL_WHEEL_HI_RES -120*N` |
| `HorizontalScroll(...)` | optional horizontal wheel | `EV_REL REL_HWHEEL` or `REL_HWHEEL_HI_RES` |
| `MousepadMove(dx, dy)` | `LastX` / `LastY` | `EV_REL REL_X` / `REL_Y` |

High-resolution wheel events can be followed by legacy `REL_WHEEL` events for the same physical movement. The adapter should prefer high-resolution wheel units when present in a sync frame and avoid double-emitting the same detent.

## Device Matching

The Linux tool should match likely D06 nodes by:

- name containing `D06`, `D06 Pro`, or `TK Wireless Receiver`;
- Bluetooth VID/PID `248a:0101` when exposed;
- USB receiver VID/PID `248a:0401` when exposed;
- explicit user-supplied `/dev/input/eventX` path.

Matching must be explainable in `--list` output. Unknown devices can still be decoded when explicitly provided.

## CLI Shape

```bash
python3 tools/linux/d06_evdev.py --list
python3 tools/linux/d06_evdev.py --node /dev/input/event12 --seconds 30
python3 tools/linux/d06_evdev.py --match "D06 Pro" --jsonl
```

Output is JSON Lines:

```json
{"event":"LeftDown","source":"/dev/input/event12","timestamp":1770000000.123456}
{"event":"Scroll","direction":"Up","units":1,"source":"/dev/input/event12","timestamp":1770000000.234567}
```

## Testing

Tests should not require D06 hardware. Use synthetic evdev events to cover:

- mouse button down/up translation;
- vertical and horizontal wheel translation;
- high-resolution wheel de-duplication;
- relative motion aggregation per `SYN_REPORT`;
- side/extra buttons remaining `UnknownButton`;
- JSON serialization shape.

## Non-Goals

- No Linux desktop remapping in this milestone.
- No `uinput`, `ydotool`, root grabbing, or event suppression yet.
- No writes to the Telink/vendor BLE service.
- No new HID report-map assumptions beyond the already captured Windows behavior.
