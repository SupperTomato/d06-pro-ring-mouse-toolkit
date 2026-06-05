#!/usr/bin/env python3
"""Translate Linux evdev mouse events into canonical D06 events."""

from __future__ import annotations

import argparse
import json
import os
import re
import select
import struct
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


EV_SYN = 0x00
EV_KEY = 0x01
EV_REL = 0x02

SYN_REPORT = 0x00

REL_X = 0x00
REL_Y = 0x01
REL_HWHEEL = 0x06
REL_WHEEL = 0x08
REL_WHEEL_HI_RES = 0x0B
REL_HWHEEL_HI_RES = 0x0C

BTN_LEFT = 0x110
BTN_RIGHT = 0x111
BTN_MIDDLE = 0x112
BTN_SIDE = 0x113
BTN_EXTRA = 0x114

D06_BLUETOOTH_ID = (0x248A, 0x0101)
D06_USB_RECEIVER_ID = (0x248A, 0x0401)
D06_IDS = {D06_BLUETOOTH_ID, D06_USB_RECEIVER_ID}
D06_NAME_TERMS = ("d06", "d06 pro", "tk wireless receiver")

INPUT_EVENT = struct.Struct("llHHi")


@dataclass(frozen=True)
class EvdevEvent:
    timestamp: float
    event_type: int
    code: int
    value: int


@dataclass(frozen=True)
class LinuxInputDevice:
    event_node: str
    name: str | None
    bus_id: int | None
    vendor_id: int | None
    product_id: int | None
    version: int | None
    handlers: tuple[str, ...]
    is_likely_d06: bool
    match_reasons: tuple[str, ...]


class D06EvdevTranslator:
    """Stateful translator from Linux evdev frames to D06 event dictionaries."""

    def __init__(self, hi_res_detent: int = 120) -> None:
        self.hi_res_detent = hi_res_detent
        self._dx = 0
        self._dy = 0
        self._wheel = 0
        self._hwheel = 0
        self._wheel_hi_res = 0
        self._hwheel_hi_res = 0
        self._frame_timestamp: float | None = None

    def process(self, event: EvdevEvent, source: str | None = None) -> list[dict[str, Any]]:
        if event.event_type == EV_KEY:
            decoded = self._key_event(event)
            return [self._with_common(decoded, source, event.timestamp)] if decoded else []
        if event.event_type == EV_REL:
            self._relative_event(event)
            return []
        if event.event_type == EV_SYN and event.code == SYN_REPORT:
            return self._flush_frame(source, event.timestamp)
        return []

    def _key_event(self, event: EvdevEvent) -> dict[str, Any] | None:
        if event.value not in {0, 1}:
            return None
        button_map = {
            BTN_LEFT: ("LeftDown", "LeftUp"),
            BTN_RIGHT: ("RightDown", "RightUp"),
            BTN_MIDDLE: ("MiddleDown", "MiddleUp"),
        }
        if event.code in button_map:
            down, up = button_map[event.code]
            return {"event": down if event.value else up}
        unknown_map = {
            BTN_SIDE: 4,
            BTN_EXTRA: 5,
        }
        if event.code in unknown_map:
            return {"event": "UnknownButton", "code": unknown_map[event.code], "pressed": bool(event.value)}
        return None

    def _relative_event(self, event: EvdevEvent) -> None:
        self._frame_timestamp = event.timestamp
        if event.code == REL_X:
            self._dx += event.value
        elif event.code == REL_Y:
            self._dy += event.value
        elif event.code == REL_WHEEL:
            self._wheel += event.value
        elif event.code == REL_HWHEEL:
            self._hwheel += event.value
        elif event.code == REL_WHEEL_HI_RES:
            self._wheel_hi_res += event.value
        elif event.code == REL_HWHEEL_HI_RES:
            self._hwheel_hi_res += event.value

    def _flush_frame(self, source: str | None, timestamp: float) -> list[dict[str, Any]]:
        event_timestamp = self._frame_timestamp if self._frame_timestamp is not None else timestamp
        decoded: list[dict[str, Any]] = []
        if self._dx or self._dy:
            decoded.append({"event": "MousepadMove", "dx": self._dx, "dy": self._dy})
        decoded.extend(self._wheel_events())
        self._reset_frame()
        return [self._with_common(item, source, event_timestamp) for item in decoded]

    def _wheel_events(self) -> list[dict[str, Any]]:
        decoded: list[dict[str, Any]] = []
        vertical = self._wheel_units(self._wheel_hi_res, self._wheel)
        horizontal = self._wheel_units(self._hwheel_hi_res, self._hwheel)
        if vertical:
            direction = "Up" if vertical > 0 else "Down"
            decoded.append({"event": "Scroll", "direction": direction, "units": abs(vertical)})
        if horizontal:
            direction = "Right" if horizontal > 0 else "Left"
            decoded.append({"event": "HorizontalScroll", "direction": direction, "units": abs(horizontal)})
        return decoded

    def _wheel_units(self, hi_res_value: int, legacy_value: int) -> int | float:
        if hi_res_value:
            units = hi_res_value / self.hi_res_detent
            return int(units) if units.is_integer() else units
        return legacy_value

    def _reset_frame(self) -> None:
        self._dx = 0
        self._dy = 0
        self._wheel = 0
        self._hwheel = 0
        self._wheel_hi_res = 0
        self._hwheel_hi_res = 0
        self._frame_timestamp = None
        self._frame_has_click_button_event = False

    @staticmethod
    def _with_common(payload: dict[str, Any], source: str | None, timestamp: float) -> dict[str, Any]:
        result = dict(payload)
        if source is not None:
            result["source"] = source
        result["timestamp"] = timestamp
        return result


def event_to_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, separators=(",", ":"), ensure_ascii=True) + "\n"


def parse_input_devices(proc_text: str) -> list[LinuxInputDevice]:
    devices: list[LinuxInputDevice] = []
    for block in re.split(r"\n\s*\n", proc_text.strip()):
        if not block.strip():
            continue
        name = _match_text(r'N:\s+Name="(.*)"', block)
        handlers = tuple((_match_text(r"H:\s+Handlers=(.*)", block) or "").split())
        bus_id = _match_hex(r"Bus=([0-9a-fA-F]+)", block)
        vendor_id = _match_hex(r"Vendor=([0-9a-fA-F]+)", block)
        product_id = _match_hex(r"Product=([0-9a-fA-F]+)", block)
        version = _match_hex(r"Version=([0-9a-fA-F]+)", block)
        for handler in handlers:
            if not handler.startswith("event"):
                continue
            reasons = _d06_match_reasons(name, vendor_id, product_id)
            devices.append(
                LinuxInputDevice(
                    event_node=f"/dev/input/{handler}",
                    name=name,
                    bus_id=bus_id,
                    vendor_id=vendor_id,
                    product_id=product_id,
                    version=version,
                    handlers=handlers,
                    is_likely_d06=bool(reasons),
                    match_reasons=tuple(reasons),
                )
            )
    return devices


def load_input_devices(proc_path: Path = Path("/proc/bus/input/devices")) -> list[LinuxInputDevice]:
    if not proc_path.exists():
        return []
    return parse_input_devices(proc_path.read_text())


def select_nodes(devices: list[LinuxInputDevice], match_terms: list[str]) -> list[str]:
    if match_terms:
        terms = [term.lower() for term in match_terms]
        selected = [
            device.event_node
            for device in devices
            if all(term in _device_haystack(device) for term in terms)
        ]
    else:
        selected = [device.event_node for device in devices if device.is_likely_d06]
    return sorted(set(selected), key=_event_node_sort_key)


def decode_event_bytes(data: bytes) -> list[EvdevEvent]:
    events: list[EvdevEvent] = []
    usable = len(data) // INPUT_EVENT.size * INPUT_EVENT.size
    for offset in range(0, usable, INPUT_EVENT.size):
        sec, usec, event_type, code, value = INPUT_EVENT.unpack(data[offset : offset + INPUT_EVENT.size])
        events.append(EvdevEvent(timestamp=sec + usec / 1_000_000, event_type=event_type, code=code, value=value))
    return events


def stream_nodes(nodes: list[str], seconds: float | None) -> int:
    translators = {node: D06EvdevTranslator() for node in nodes}
    fds: dict[int, str] = {}
    for node in nodes:
        fds[os.open(node, os.O_RDONLY | os.O_NONBLOCK)] = node
    deadline = None if seconds is None else time.time() + seconds
    try:
        while deadline is None or time.time() < deadline:
            ready, _, _ = select.select(list(fds), [], [], 0.25)
            for fd in ready:
                node = fds[fd]
                try:
                    data = os.read(fd, INPUT_EVENT.size * 64)
                except BlockingIOError:
                    continue
                for evdev_event in decode_event_bytes(data):
                    for decoded in translators[node].process(evdev_event, source=node):
                        print(event_to_json(decoded), end="", flush=True)
    finally:
        for fd in fds:
            os.close(fd)
    return 0


def print_device_list(devices: list[LinuxInputDevice]) -> None:
    for device in devices:
        vid = _hex4(device.vendor_id)
        pid = _hex4(device.product_id)
        likely = "yes" if device.is_likely_d06 else "no"
        reasons = ",".join(device.match_reasons) if device.match_reasons else "-"
        print(f"{device.event_node}\tlikely_d06={likely}\tvid_pid={vid}:{pid}\tname={device.name or '-'}\treasons={reasons}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true", help="List Linux input event nodes and D06 match reasons")
    parser.add_argument("--node", action="append", default=[], help="Read an explicit /dev/input/eventX node")
    parser.add_argument("--match", action="append", default=[], help="Select nodes whose /proc input block contains this term")
    parser.add_argument("--seconds", type=float, default=None, help="Stop after this many seconds")
    parser.add_argument("--jsonl", action="store_true", help="Emit JSON Lines; currently the default stream format")
    args = parser.parse_args(argv)

    devices = load_input_devices()
    if args.list:
        print_device_list(devices)
        return 0

    nodes = args.node or select_nodes(devices, args.match)
    if not nodes:
        raise SystemExit("no D06 input event nodes matched; use --list or pass --node /dev/input/eventX")
    return stream_nodes(nodes, args.seconds)


def _match_text(pattern: str, text: str) -> str | None:
    match = re.search(pattern, text)
    return match.group(1) if match else None


def _match_hex(pattern: str, text: str) -> int | None:
    value = _match_text(pattern, text)
    return int(value, 16) if value is not None else None


def _d06_match_reasons(name: str | None, vendor_id: int | None, product_id: int | None) -> list[str]:
    reasons: list[str] = []
    lowered_name = (name or "").lower()
    if any(term in lowered_name for term in D06_NAME_TERMS):
        reasons.append("name")
    if vendor_id is not None and product_id is not None and (vendor_id, product_id) in D06_IDS:
        reasons.append("vid_pid")
    return reasons


def _device_haystack(device: LinuxInputDevice) -> str:
    return " ".join(
        [
            device.event_node,
            device.name or "",
            _hex4(device.vendor_id),
            _hex4(device.product_id),
            " ".join(device.handlers),
        ]
    ).lower()


def _event_node_sort_key(node: str) -> int:
    try:
        return int(Path(node).name.removeprefix("event"))
    except ValueError:
        return 0


def _hex4(value: int | None) -> str:
    return "----" if value is None else f"{value:04x}"


if __name__ == "__main__":
    raise SystemExit(main())
