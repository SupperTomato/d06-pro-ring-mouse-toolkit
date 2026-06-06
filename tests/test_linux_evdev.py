from __future__ import annotations

import json
import unittest

from tools.linux.d06_evdev import (
    BTN_EXTRA,
    BTN_LEFT,
    BTN_MIDDLE,
    BTN_RIGHT,
    BTN_SIDE,
    EV_KEY,
    EV_REL,
    EV_SYN,
    REL_HWHEEL,
    REL_HWHEEL_HI_RES,
    REL_WHEEL,
    REL_WHEEL_HI_RES,
    REL_X,
    REL_Y,
    SYN_REPORT,
    D06EvdevProfile,
    D06EvdevTranslator,
    EvdevEvent,
    event_to_json,
    load_profile,
    parse_input_devices,
)


def event(event_type: int, code: int, value: int, timestamp: float = 1770000000.25) -> EvdevEvent:
    return EvdevEvent(timestamp=timestamp, event_type=event_type, code=code, value=value)


def collect(translator: D06EvdevTranslator, events: list[EvdevEvent]) -> list[dict[str, object]]:
    decoded: list[dict[str, object]] = []
    for item in events:
        decoded.extend(translator.process(item, source="/dev/input/event12"))
    return decoded


class D06LinuxEvdevTest(unittest.TestCase):
    def test_maps_primary_mouse_buttons_to_d06_events(self) -> None:
        translator = D06EvdevTranslator()

        decoded = collect(
            translator,
            [
                event(EV_KEY, BTN_LEFT, 1),
                event(EV_KEY, BTN_LEFT, 0),
                event(EV_KEY, BTN_RIGHT, 1),
                event(EV_KEY, BTN_RIGHT, 0),
                event(EV_KEY, BTN_MIDDLE, 1),
                event(EV_KEY, BTN_MIDDLE, 0),
            ],
        )

        self.assertEqual(
            [item["event"] for item in decoded],
            ["LeftDown", "LeftUp", "RightDown", "RightUp", "MiddleDown", "MiddleUp"],
        )
        self.assertEqual({item["source"] for item in decoded}, {"/dev/input/event12"})

    def test_maps_side_and_extra_buttons_as_unknown_buttons(self) -> None:
        translator = D06EvdevTranslator()

        decoded = collect(
            translator,
            [
                event(EV_KEY, BTN_SIDE, 1),
                event(EV_KEY, BTN_SIDE, 0),
                event(EV_KEY, BTN_EXTRA, 1),
                event(EV_KEY, BTN_EXTRA, 0),
            ],
        )

        self.assertEqual(
            decoded,
            [
                {
                    "event": "UnknownButton",
                    "code": 4,
                    "pressed": True,
                    "source": "/dev/input/event12",
                    "timestamp": 1770000000.25,
                },
                {
                    "event": "UnknownButton",
                    "code": 4,
                    "pressed": False,
                    "source": "/dev/input/event12",
                    "timestamp": 1770000000.25,
                },
                {
                    "event": "UnknownButton",
                    "code": 5,
                    "pressed": True,
                    "source": "/dev/input/event12",
                    "timestamp": 1770000000.25,
                },
                {
                    "event": "UnknownButton",
                    "code": 5,
                    "pressed": False,
                    "source": "/dev/input/event12",
                    "timestamp": 1770000000.25,
                },
            ],
        )

    def test_aggregates_relative_motion_until_sync_report(self) -> None:
        translator = D06EvdevTranslator()

        decoded = collect(
            translator,
            [
                event(EV_REL, REL_X, 12),
                event(EV_REL, REL_X, 3),
                event(EV_REL, REL_Y, -4),
                event(EV_SYN, SYN_REPORT, 0),
            ],
        )

        self.assertEqual(
            decoded,
            [{"event": "MousepadMove", "dx": 15, "dy": -4, "source": "/dev/input/event12", "timestamp": 1770000000.25}],
        )

    def test_maps_legacy_wheel_events_to_scroll_units(self) -> None:
        translator = D06EvdevTranslator()

        decoded = collect(
            translator,
            [
                event(EV_REL, REL_WHEEL, 2),
                event(EV_REL, REL_HWHEEL, -1),
                event(EV_SYN, SYN_REPORT, 0),
            ],
        )

        self.assertEqual(
            decoded,
            [
                {"event": "Scroll", "direction": "Up", "units": 2, "source": "/dev/input/event12", "timestamp": 1770000000.25},
                {
                    "event": "HorizontalScroll",
                    "direction": "Left",
                    "units": 1,
                    "source": "/dev/input/event12",
                    "timestamp": 1770000000.25,
                },
            ],
        )

    def test_prefers_hi_res_wheel_and_does_not_double_emit_legacy_wheel(self) -> None:
        translator = D06EvdevTranslator()

        decoded = collect(
            translator,
            [
                event(EV_REL, REL_WHEEL_HI_RES, 120),
                event(EV_REL, REL_WHEEL, 1),
                event(EV_REL, REL_HWHEEL_HI_RES, -240),
                event(EV_REL, REL_HWHEEL, -2),
                event(EV_SYN, SYN_REPORT, 0),
            ],
        )

        self.assertEqual(
            decoded,
            [
                {"event": "Scroll", "direction": "Up", "units": 1, "source": "/dev/input/event12", "timestamp": 1770000000.25},
                {
                    "event": "HorizontalScroll",
                    "direction": "Left",
                    "units": 2,
                    "source": "/dev/input/event12",
                    "timestamp": 1770000000.25,
                },
            ],
        )

    def test_serializes_event_as_json_line(self) -> None:
        payload = {
            "event": "Scroll",
            "direction": "Down",
            "units": 1,
            "source": "/dev/input/event12",
            "timestamp": 1770000000.25,
        }

        self.assertEqual(json.loads(event_to_json(payload)), payload)
        self.assertTrue(event_to_json(payload).endswith("\n"))

    def test_parse_input_devices_matches_name_and_vid_pid(self) -> None:
        proc_text = """
I: Bus=0005 Vendor=248a Product=0101 Version=0001
N: Name="D06 Pro"
P: Phys=aa:bb:cc
H: Handlers=event7 mouse0

I: Bus=0003 Vendor=248a Product=0401 Version=0110
N: Name="TK Wireless Receiver Mouse"
P: Phys=usb-0000:00:14.0-1/input0
H: Handlers=mouse1 event12

I: Bus=0003 Vendor=1234 Product=5678 Version=0001
N: Name="Other Mouse"
H: Handlers=event20 mouse2
""".strip()

        devices = parse_input_devices(proc_text)

        self.assertEqual(
            [(device.event_node, device.name, device.vendor_id, device.product_id) for device in devices],
            [
                ("/dev/input/event7", "D06 Pro", 0x248A, 0x0101),
                ("/dev/input/event12", "TK Wireless Receiver Mouse", 0x248A, 0x0401),
                ("/dev/input/event20", "Other Mouse", 0x1234, 0x5678),
            ],
        )
        self.assertEqual([device.is_likely_d06 for device in devices], [True, True, False])

    def test_profile_transforms_motion_and_scroll(self) -> None:
        profile = D06EvdevProfile.from_dict(
            {
                "transform": {
                    "invert_x": True,
                    "movement_sensitivity": 2,
                    "movement_deadzone": 3,
                    "scroll_sensitivity": 2,
                }
            }
        )
        translator = D06EvdevTranslator(profile=profile)

        decoded = collect(
            translator,
            [
                event(EV_REL, REL_X, 2),
                event(EV_REL, REL_Y, 4),
                event(EV_REL, REL_WHEEL, -1),
                event(EV_SYN, SYN_REPORT, 0),
            ],
        )

        self.assertEqual(
            decoded,
            [
                {"event": "MousepadMove", "dx": 0, "dy": 8, "source": "/dev/input/event12", "timestamp": 1770000000.25},
                {"event": "Scroll", "direction": "Down", "units": 2, "source": "/dev/input/event12", "timestamp": 1770000000.25},
            ],
        )

    def test_profile_remaps_named_events(self) -> None:
        profile = D06EvdevProfile.from_dict(
            {
                "remap": {
                    "LeftUp": {"event": "Custom", "name": "select"},
                    "Scroll.Down": {"event": "Custom", "name": "volume_down"},
                }
            }
        )
        translator = D06EvdevTranslator(profile=profile)

        decoded = collect(
            translator,
            [
                event(EV_KEY, BTN_LEFT, 0),
                event(EV_REL, REL_WHEEL, -1),
                event(EV_SYN, SYN_REPORT, 0),
            ],
        )

        self.assertEqual(
            decoded,
            [
                {"event": "Custom", "name": "select", "source": "/dev/input/event12", "timestamp": 1770000000.25},
                {"event": "Custom", "name": "volume_down", "source": "/dev/input/event12", "timestamp": 1770000000.25},
            ],
        )

    def test_load_profile_from_json_file(self) -> None:
        import tempfile
        from pathlib import Path

        with tempfile.TemporaryDirectory() as tmpdir:
            path = Path(tmpdir) / "profile.json"
            path.write_text('{"transform":{"invert_y":true},"remap":{"RightUp":{"event":"Custom","name":"back"}}}')

            profile = load_profile(path)

        self.assertEqual({"event": "MousepadMove", "dx": 1, "dy": -2}, profile.apply({"event": "MousepadMove", "dx": 1, "dy": 2}))
        self.assertEqual({"event": "Custom", "name": "back"}, profile.apply({"event": "RightUp"}))


if __name__ == "__main__":
    unittest.main()
