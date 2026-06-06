# Capture Artifacts

Capture data is grouped by host platform and capture surface.

| Path | Contents |
| --- | --- |
| `android/getevent/` | Android `adb shell getevent` captures from paired D06 Bluetooth input nodes. |
| `linux/evdev/` | Linux `evdev` JSON Lines captures from the USB receiver path. |
| `windows/gatt/` | Historical BLE/GATT service dumps kept as reference data. |
| `windows/hid/` | Historical HID collection/capability dumps kept as reference data. |
| `windows/raw-input/` | Historical labeled raw input captures used to confirm button, wheel, and mousepad mappings. |

Generated Linux HID/GATT and Android dumps should use matching platform folders, for example:

```bash
python3 tools/linux/d06_hid.py --dump --out artifacts/linux/hid/hid_caps.json
python3 tools/linux/dump_d06_gatt.py --out-dir artifacts/linux/gatt
tools/android/d06_android_input.sh capture --out artifacts/android/getevent/android_getevent.txt
```
