#!/usr/bin/env python3
"""Dump D06 BLE/GATT metadata on Linux using BlueZ through bleak."""

from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path
from typing import Any


KNOWN_UUID_NAMES = {
    "00001800-0000-1000-8000-00805f9b34fb": "Generic Access",
    "00001801-0000-1000-8000-00805f9b34fb": "Generic Attribute",
    "0000180a-0000-1000-8000-00805f9b34fb": "Device Information",
    "0000180f-0000-1000-8000-00805f9b34fb": "Battery Service",
    "00001812-0000-1000-8000-00805f9b34fb": "Human Interface Device",
    "00002a00-0000-1000-8000-00805f9b34fb": "Device Name",
    "00002a01-0000-1000-8000-00805f9b34fb": "Appearance",
    "00002a04-0000-1000-8000-00805f9b34fb": "Peripheral Preferred Connection Parameters",
    "00002a05-0000-1000-8000-00805f9b34fb": "Service Changed",
    "00002a19-0000-1000-8000-00805f9b34fb": "Battery Level",
    "00002a24-0000-1000-8000-00805f9b34fb": "Model Number",
    "00002a25-0000-1000-8000-00805f9b34fb": "Serial Number",
    "00002a26-0000-1000-8000-00805f9b34fb": "Firmware Revision",
    "00002a27-0000-1000-8000-00805f9b34fb": "Hardware Revision",
    "00002a28-0000-1000-8000-00805f9b34fb": "Software Revision",
    "00002a29-0000-1000-8000-00805f9b34fb": "Manufacturer Name",
    "00002a4a-0000-1000-8000-00805f9b34fb": "HID Information",
    "00002a4b-0000-1000-8000-00805f9b34fb": "HID Report Map",
    "00002a4c-0000-1000-8000-00805f9b34fb": "HID Control Point",
    "00002a4d-0000-1000-8000-00805f9b34fb": "HID Report",
    "00002a4e-0000-1000-8000-00805f9b34fb": "HID Protocol Mode",
    "00002a4f-0000-1000-8000-00805f9b34fb": "Scan Interval Window",
    "00002a50-0000-1000-8000-00805f9b34fb": "PnP ID",
    "00002902-0000-1000-8000-00805f9b34fb": "Client Characteristic Configuration",
    "00002908-0000-1000-8000-00805f9b34fb": "Report Reference",
    "00010203-0405-0607-0809-0a0b0c0d1912": "Telink-style Vendor Service",
}

HID_REPORT_MAP_UUID = "00002a4b-0000-1000-8000-00805f9b34fb"


def uuid_name(uuid: str) -> str:
    return KNOWN_UUID_NAMES.get(uuid.lower(), "")


def bytes_to_hex(data: bytes | bytearray | memoryview) -> str:
    return " ".join(f"{byte:02X}" for byte in bytes(data))


def bytes_to_text(data: bytes | bytearray | memoryview) -> str:
    raw = bytes(data)
    if not raw:
        return ""
    if any((byte < 0x20 or byte > 0x7E) and byte not in {0x09, 0x0A, 0x0D} for byte in raw):
        return ""
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return ""


async def dump_gatt(address: str | None, name: str | None, out_dir: Path, timeout: float, read_values: bool) -> dict[str, Any]:
    BleakClient, BleakScanner = _load_bleak()
    target = address or await _find_device_address(BleakScanner, name, timeout)
    if target is None:
        raise SystemExit("no BLE target found; pass --address or pair/advertise a D06 device")

    dump: dict[str, Any] = {"address": target, "services": []}
    async with BleakClient(target, timeout=timeout) as client:
        dump["connected"] = bool(client.is_connected)
        services = await _services(client)
        for service in services:
            service_entry: dict[str, Any] = {
                "uuid": str(service.uuid),
                "name": uuid_name(str(service.uuid)),
                "handle": getattr(service, "handle", None),
                "characteristics": [],
            }
            for characteristic in service.characteristics:
                char_entry: dict[str, Any] = {
                    "uuid": str(characteristic.uuid),
                    "name": uuid_name(str(characteristic.uuid)),
                    "handle": getattr(characteristic, "handle", None),
                    "properties": list(getattr(characteristic, "properties", [])),
                    "descriptors": [],
                }
                if read_values and "read" in char_entry["properties"]:
                    try:
                        value = bytes(await client.read_gatt_char(characteristic))
                        char_entry["valueHex"] = bytes_to_hex(value)
                        text = bytes_to_text(value)
                        if text:
                            char_entry["valueText"] = text
                        if str(characteristic.uuid).lower() == HID_REPORT_MAP_UUID:
                            (out_dir / "hid_report_map.bin").write_bytes(value)
                    except Exception as exc:  # noqa: BLE errors vary by backend
                        char_entry["readError"] = str(exc)

                for descriptor in characteristic.descriptors:
                    desc_entry: dict[str, Any] = {
                        "uuid": str(descriptor.uuid),
                        "name": uuid_name(str(descriptor.uuid)),
                        "handle": getattr(descriptor, "handle", None),
                    }
                    if read_values:
                        try:
                            value = bytes(await client.read_gatt_descriptor(descriptor.handle))
                            desc_entry["valueHex"] = bytes_to_hex(value)
                            text = bytes_to_text(value)
                            if text:
                                desc_entry["valueText"] = text
                        except Exception as exc:  # noqa: BLE errors vary by backend
                            desc_entry["readError"] = str(exc)
                    char_entry["descriptors"].append(desc_entry)
                service_entry["characteristics"].append(char_entry)
            dump["services"].append(service_entry)
    return dump


async def _services(client: Any) -> Any:
    getter = getattr(client, "get_services", None)
    if getter is not None:
        try:
            return await getter()
        except Exception:
            pass
    return client.services


async def _find_device_address(scanner: Any, name: str | None, timeout: float) -> str | None:
    devices = await scanner.discover(timeout=timeout)
    terms = [name.lower()] if name else ["d06"]
    for device in devices:
        device_name = (getattr(device, "name", None) or "").lower()
        if any(term in device_name for term in terms):
            return getattr(device, "address", None)
    return None


def _load_bleak() -> tuple[Any, Any]:
    try:
        from bleak import BleakClient, BleakScanner  # type: ignore
    except ImportError as exc:
        raise SystemExit("missing dependency: install with `python3 -m pip install bleak`") from exc
    return BleakClient, BleakScanner


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--address", default=None, help="BLE address, for example AA:BB:CC:DD:EE:FF")
    parser.add_argument("--name", default="D06", help="Name substring used when --address is omitted")
    parser.add_argument("--out-dir", type=Path, default=Path("artifacts/linux/gatt"), help="Output directory")
    parser.add_argument("--timeout", type=float, default=10.0, help="BLE scan/connect timeout")
    parser.add_argument("--no-read", action="store_true", help="List services/chars without reading values")
    args = parser.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    dump = asyncio.run(
        dump_gatt(
            address=args.address,
            name=args.name,
            out_dir=args.out_dir,
            timeout=args.timeout,
            read_values=not args.no_read,
        )
    )
    out_path = args.out_dir / "gatt_dump.json"
    out_path.write_text(json.dumps(dump, indent=2, ensure_ascii=True) + "\n")
    print(f"Wrote {out_path}")
    if (args.out_dir / "hid_report_map.bin").exists():
        print(f"Wrote {args.out_dir / 'hid_report_map.bin'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
