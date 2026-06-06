from __future__ import annotations

import unittest

from tools.linux.d06_hid import (
    bytes_to_hex,
    d06_match_reasons,
    parse_hid_id,
    parse_uevent,
    summarize_report_descriptor,
)
from tools.linux.dump_d06_gatt import bytes_to_text, uuid_name


class D06LinuxHidTest(unittest.TestCase):
    def test_parse_hid_id_extracts_bus_vid_pid(self) -> None:
        self.assertEqual(parse_hid_id("0005:0000248A:00000101"), (0x0005, 0x248A, 0x0101))
        self.assertEqual(parse_hid_id("bad"), (None, None, None))

    def test_parse_uevent_and_match_d06_identity(self) -> None:
        uevent = parse_uevent(
            "\n".join(
                [
                    "HID_ID=0005:0000248A:00000101",
                    "HID_NAME=D06 Pro",
                    "HID_PHYS=aa:bb/input0",
                ]
            )
        )
        bus, vendor, product = parse_hid_id(uevent["HID_ID"])

        self.assertEqual((bus, vendor, product), (0x0005, 0x248A, 0x0101))
        self.assertEqual(d06_match_reasons(uevent["HID_NAME"], vendor, product), ["name", "vid_pid"])

    def test_report_descriptor_summary_finds_report_fields(self) -> None:
        mouse_descriptor = bytes(
            [
                0x05,
                0x01,
                0x09,
                0x02,
                0xA1,
                0x01,
                0x85,
                0x03,
                0x05,
                0x09,
                0x19,
                0x01,
                0x29,
                0x05,
                0x15,
                0x00,
                0x25,
                0x01,
                0x95,
                0x05,
                0x75,
                0x01,
                0x81,
                0x02,
                0x05,
                0x01,
                0x09,
                0x30,
                0x09,
                0x31,
                0x16,
                0x01,
                0x80,
                0x26,
                0xFF,
                0x7F,
                0x75,
                0x10,
                0x95,
                0x02,
                0x81,
                0x06,
                0xC0,
            ]
        )

        summary = summarize_report_descriptor(mouse_descriptor)

        self.assertEqual(summary["reportIds"], [3])
        self.assertEqual(summary["collections"][0]["usagePage"], 1)
        self.assertEqual(summary["collections"][0]["usage"], 2)
        self.assertEqual(summary["fields"][0]["kind"], "Input")
        self.assertEqual(summary["fields"][0]["usagePage"], 9)
        self.assertEqual(summary["fields"][0]["usageMinimum"], 1)
        self.assertEqual(summary["fields"][0]["usageMaximum"], 5)
        self.assertEqual(summary["fields"][1]["reportSize"], 16)
        self.assertEqual(summary["fields"][1]["reportCount"], 2)

    def test_gatt_helpers_are_dependency_free(self) -> None:
        self.assertEqual(uuid_name("00002A19-0000-1000-8000-00805F9B34FB"), "Battery Level")
        self.assertEqual(bytes_to_hex(b"\x01\xaf"), "01 AF")
        self.assertEqual(bytes_to_text(b"D06 Pro"), "D06 Pro")
        self.assertEqual(bytes_to_text(b"\x00\xff"), "")


if __name__ == "__main__":
    unittest.main()
