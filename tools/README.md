# Tools

| Path | Purpose |
| --- | --- |
| `android/d06_android_input.sh` | Lists Android input devices, captures `adb getevent`, and dumps `dumpsys input`. |
| `linux/d06_evdev.py` | Lists Linux input nodes and translates D06 `evdev` events to canonical JSON Lines. |
| `linux/d06_hid.py` | Lists Linux `hidraw` nodes, dumps HID report descriptors, and captures raw HID reports. |
| `linux/dump_d06_gatt.py` | Dumps BLE/GATT metadata through BlueZ using `bleak`. |

Most Linux input capture commands need root, `input` group access, or a udev rule. Android commands need `adb` plus USB or wireless debugging.
