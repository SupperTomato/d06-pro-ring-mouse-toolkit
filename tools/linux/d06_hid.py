#!/usr/bin/env python3
"""Inspect and capture D06 HID data on Linux via hidraw/sysfs."""

from __future__ import annotations

import argparse
import json
import os
import select
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


D06_BLUETOOTH_ID = (0x248A, 0x0101)
D06_USB_RECEIVER_ID = (0x248A, 0x0401)
D06_IDS = {D06_BLUETOOTH_ID, D06_USB_RECEIVER_ID}
D06_NAME_TERMS = ("d06", "d06 pro", "tk wireless receiver")


@dataclass(frozen=True)
class HidrawDevice:
    hidraw: str
    hidraw_node: str
    sysfs_path: str
    name: str | None
    bus_id: int | None
    vendor_id: int | None
    product_id: int | None
    hid_id: str | None
    descriptor_size: int | None
    is_likely_d06: bool
    match_reasons: tuple[str, ...]


def list_hidraw_devices(
    sysfs_root: Path = Path("/sys/class/hidraw"),
    dev_root: Path = Path("/dev"),
) -> list[HidrawDevice]:
    devices: list[HidrawDevice] = []
    if not sysfs_root.exists():
        return devices

    for hidraw_path in sorted(sysfs_root.glob("hidraw*"), key=lambda path: _hidraw_sort_key(path.name)):
        device_path = hidraw_path / "device"
        uevent = parse_uevent(_read_text(device_path / "uevent") or "")
        hid_id = uevent.get("HID_ID")
        bus_id, vendor_id, product_id = parse_hid_id(hid_id)
        name = uevent.get("HID_NAME") or _read_text(device_path / "name")
        descriptor = _read_bytes(device_path / "report_descriptor")
        reasons = d06_match_reasons(name, vendor_id, product_id)
        devices.append(
            HidrawDevice(
                hidraw=hidraw_path.name,
                hidraw_node=str(dev_root / hidraw_path.name),
                sysfs_path=str(device_path),
                name=name.strip() if name else None,
                bus_id=bus_id,
                vendor_id=vendor_id,
                product_id=product_id,
                hid_id=hid_id,
                descriptor_size=len(descriptor) if descriptor is not None else None,
                is_likely_d06=bool(reasons),
                match_reasons=tuple(reasons),
            )
        )
    return devices


def dump_hidraw_devices(devices: list[HidrawDevice], include_descriptor: bool = True) -> list[dict[str, Any]]:
    dumps: list[dict[str, Any]] = []
    for device in devices:
        entry: dict[str, Any] = {
            "hidraw": device.hidraw,
            "hidrawNode": device.hidraw_node,
            "sysfsPath": device.sysfs_path,
            "name": device.name,
            "busId": device.bus_id,
            "vendorId": device.vendor_id,
            "productId": device.product_id,
            "hidId": device.hid_id,
            "descriptorSize": device.descriptor_size,
            "isLikelyD06": device.is_likely_d06,
            "matchReasons": list(device.match_reasons),
        }
        if include_descriptor:
            descriptor = _read_bytes(Path(device.sysfs_path) / "report_descriptor")
            if descriptor is not None:
                entry["reportDescriptorHex"] = bytes_to_hex(descriptor)
                entry["reportDescriptorSummary"] = summarize_report_descriptor(descriptor)
        dumps.append(entry)
    return dumps


def select_hidraw_nodes(devices: list[HidrawDevice], match_terms: list[str]) -> list[str]:
    if match_terms:
        terms = [term.lower() for term in match_terms]
        selected = [
            device.hidraw_node
            for device in devices
            if all(term in _device_haystack(device) for term in terms)
        ]
    else:
        selected = [device.hidraw_node for device in devices if device.is_likely_d06]
    return sorted(set(selected), key=lambda node: _hidraw_sort_key(Path(node).name))


def capture_hidraw_nodes(nodes: list[str], seconds: float | None, report_size: int) -> int:
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
                    data = os.read(fd, report_size)
                except BlockingIOError:
                    continue
                if not data:
                    continue
                print(
                    json.dumps(
                        {
                            "event": "HidReport",
                            "source": node,
                            "timestamp": time.time(),
                            "length": len(data),
                            "hex": bytes_to_hex(data),
                        },
                        separators=(",", ":"),
                    ),
                    flush=True,
                )
    finally:
        for fd in fds:
            os.close(fd)
    return 0


def summarize_report_descriptor(data: bytes) -> dict[str, Any]:
    parser = HidReportDescriptorParser()
    return parser.parse(data)


class HidReportDescriptorParser:
    def __init__(self) -> None:
        self.usage_page: int | None = None
        self.report_size: int | None = None
        self.report_count: int | None = None
        self.report_id = 0
        self.usages: list[int] = []
        self.usage_minimum: int | None = None
        self.usage_maximum: int | None = None
        self.collections: list[dict[str, Any]] = []
        self.fields: list[dict[str, Any]] = []
        self.report_ids: set[int] = set()

    def parse(self, data: bytes) -> dict[str, Any]:
        offset = 0
        while offset < len(data):
            prefix = data[offset]
            offset += 1
            if prefix == 0xFE:
                if offset + 2 > len(data):
                    break
                size = data[offset]
                offset += 2 + size
                continue

            size = _hid_item_size(prefix)
            item_type = (prefix >> 2) & 0x03
            tag = (prefix >> 4) & 0x0F
            raw = data[offset : offset + size]
            offset += size
            value = int.from_bytes(raw, "little", signed=False) if raw else 0

            if item_type == 0:
                self._main(tag, value)
            elif item_type == 1:
                self._global(tag, value)
            elif item_type == 2:
                self._local(tag, value)

        return {
            "reportIds": sorted(self.report_ids),
            "collections": self.collections,
            "fields": self.fields,
        }

    def _main(self, tag: int, value: int) -> None:
        if tag == 0x0A:
            self.collections.append(
                {
                    "usagePage": self.usage_page,
                    "usage": self.usages[-1] if self.usages else None,
                    "type": value,
                }
            )
            self._clear_local()
        elif tag in {0x08, 0x09, 0x0B}:
            kind = {0x08: "Input", 0x09: "Output", 0x0B: "Feature"}[tag]
            self.fields.append(
                {
                    "kind": kind,
                    "reportId": self.report_id,
                    "usagePage": self.usage_page,
                    "usages": list(self.usages),
                    "usageMinimum": self.usage_minimum,
                    "usageMaximum": self.usage_maximum,
                    "reportSize": self.report_size,
                    "reportCount": self.report_count,
                    "bits": (self.report_size or 0) * (self.report_count or 0),
                    "flags": value,
                }
            )
            self._clear_local()
        elif tag == 0x0C:
            self._clear_local()

    def _global(self, tag: int, value: int) -> None:
        if tag == 0x00:
            self.usage_page = value
        elif tag == 0x07:
            self.report_size = value
        elif tag == 0x08:
            self.report_id = value
            self.report_ids.add(value)
        elif tag == 0x09:
            self.report_count = value

    def _local(self, tag: int, value: int) -> None:
        if tag == 0x00:
            self.usages.append(value)
        elif tag == 0x01:
            self.usage_minimum = value
        elif tag == 0x02:
            self.usage_maximum = value

    def _clear_local(self) -> None:
        self.usages.clear()
        self.usage_minimum = None
        self.usage_maximum = None


def parse_uevent(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key] = value
    return result


def parse_hid_id(value: str | None) -> tuple[int | None, int | None, int | None]:
    if not value:
        return None, None, None
    parts = value.split(":")
    if len(parts) != 3:
        return None, None, None
    try:
        return int(parts[0], 16), int(parts[1], 16), int(parts[2], 16)
    except ValueError:
        return None, None, None


def d06_match_reasons(name: str | None, vendor_id: int | None, product_id: int | None) -> list[str]:
    reasons: list[str] = []
    lowered_name = (name or "").lower()
    if any(term in lowered_name for term in D06_NAME_TERMS):
        reasons.append("name")
    if vendor_id is not None and product_id is not None and (vendor_id, product_id) in D06_IDS:
        reasons.append("vid_pid")
    return reasons


def bytes_to_hex(data: bytes) -> str:
    return " ".join(f"{byte:02X}" for byte in data)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true", help="List Linux hidraw devices and D06 match reasons")
    parser.add_argument("--dump", action="store_true", help="Dump matching hidraw metadata/report descriptors as JSON")
    parser.add_argument("--capture", action="store_true", help="Capture raw hidraw reports as JSON Lines")
    parser.add_argument("--node", action="append", default=[], help="Explicit /dev/hidrawX node")
    parser.add_argument("--match", action="append", default=[], help="Select devices whose metadata contains this term")
    parser.add_argument("--seconds", type=float, default=15.0, help="Capture duration")
    parser.add_argument("--report-size", type=int, default=64, help="Maximum report bytes per read")
    parser.add_argument("--out", type=Path, default=None, help="Write --dump JSON to this path")
    parser.add_argument("--no-descriptor", action="store_true", help="Do not include report descriptor bytes in --dump")
    args = parser.parse_args(argv)

    devices = list_hidraw_devices()
    if args.list:
        print_device_list(devices)
        return 0

    nodes = args.node or select_hidraw_nodes(devices, args.match)
    selected = [device for device in devices if device.hidraw_node in nodes]

    if args.dump:
        payload = dump_hidraw_devices(selected, include_descriptor=not args.no_descriptor)
        text = json.dumps(payload, indent=2, ensure_ascii=True) + "\n"
        if args.out is None:
            print(text, end="")
        else:
            args.out.parent.mkdir(parents=True, exist_ok=True)
            args.out.write_text(text)
            print(f"Wrote {args.out}")
        return 0

    if args.capture:
        if not nodes:
            raise SystemExit("no hidraw nodes matched; use --list or pass --node /dev/hidrawX")
        return capture_hidraw_nodes(nodes, args.seconds, args.report_size)

    print_device_list(devices)
    return 0


def print_device_list(devices: list[HidrawDevice]) -> None:
    for device in devices:
        vid = _hex4(device.vendor_id)
        pid = _hex4(device.product_id)
        likely = "yes" if device.is_likely_d06 else "no"
        reasons = ",".join(device.match_reasons) if device.match_reasons else "-"
        print(
            f"{device.hidraw_node}\tlikely_d06={likely}\tvid_pid={vid}:{pid}"
            f"\tname={device.name or '-'}\tdescriptor_size={device.descriptor_size or '-'}"
            f"\treasons={reasons}"
        )


def _read_text(path: Path) -> str | None:
    try:
        return path.read_text().strip()
    except OSError:
        return None


def _read_bytes(path: Path) -> bytes | None:
    try:
        return path.read_bytes()
    except OSError:
        return None


def _device_haystack(device: HidrawDevice) -> str:
    return " ".join(
        [
            device.hidraw_node,
            device.name or "",
            _hex4(device.vendor_id),
            _hex4(device.product_id),
            device.hid_id or "",
        ]
    ).lower()


def _hidraw_sort_key(name: str) -> int:
    try:
        return int(name.removeprefix("hidraw"))
    except ValueError:
        return 0


def _hid_item_size(prefix: int) -> int:
    size_code = prefix & 0x03
    return 4 if size_code == 3 else size_code


def _hex4(value: int | None) -> str:
    return "----" if value is None else f"{value:04x}"


if __name__ == "__main__":
    raise SystemExit(main())
