# Changelog

## Unreleased

- Organize capture artifacts by platform and capture surface.
- Add `artifacts/README.md` and `tools/README.md` indexes.
- Replace Windows-only capture scripts with Linux `evdev`/`hidraw`/BlueZ helpers and Android `adb getevent` tooling.
- Remove all Windows-only shell script files and documentation references.

## 0.2.0 - 2026-06-05

- Add custom Android remap presets and built-in preset profiles.
- Add event transforms for movement/scroll sensitivity, axis inversion, deadzone, and tap window config.
- Add diagnostics JSON Lines export for decoded Android events.
- Add Linux JSON profile support plus udev/systemd templates.
- Add Gradle `maven-publish` setup for library modules.
- Add Kotlin, Java, Compose, and Linux profile examples.

## 0.1.0 - 2026-06-05

- Add D06 core mapper, Android input decoder, BLE helper, remapper skeleton, sample app, and Linux evdev decoder.
