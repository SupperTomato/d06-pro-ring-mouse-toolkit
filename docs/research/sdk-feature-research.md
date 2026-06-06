# D06 SDK Feature Research

Date: 2026-06-05

## Platform Findings

- Android HID input is translated by the system input pipeline: kernel input driver, `EventHub`, `InputReader`, then `InputDispatcher` to the focused window. A normal SDK can decode events delivered to its app, but cannot globally replace the system HID pipeline.
- Android `AccessibilityService.dispatchGesture` can dispatch touch gestures from API 24 when the service declares gesture capability. It can run global actions such as back/home/recents, but it is not a general HID event injector.
- Linux user-space remapping that needs true replacement normally reads evdev, grabs the physical device, then emits a virtual device through `uinput`. That is useful, but it is a higher-permission phase with security and event-forwarding risk.
- JitPack can publish Android libraries from GitHub when Gradle `maven-publish` is configured and `publishToMavenLocal` works.

## Features Added In SDK v0.2 Work

- Custom Android remap presets via `D06RemapPreset("name") { ... }`.
- Built-in preset objects: `Accessibility`, `Presentation`, `Media`, `MouseOnly`.
- Remap validator for actions an AccessibilityService cannot execute directly.
- Event transform config: invert axes, sensitivity, deadzone, scroll scaling, tap window.
- Diagnostics ring buffer with JSON Lines export.
- Linux JSON profile support for transforms and named remaps.
- Linux udev and systemd user-service templates.
- Gradle publishing setup for local Maven/JitPack readiness.
- Kotlin, Java, Compose, and Linux profile examples.

## Future Backlog

- Android sample UI for editing presets on-device.
- Profile import/export in the sample app.
- Event recorder/replay command for reproducing user reports in tests.
- Optional Linux `uinput` virtual-device remapper after permission model is explicit.
- Maven Central release automation after artifact coordinates and signing identity are stable.
- CI workflow for Android tests, Python tests, and Maven-local publishing.

## Sources

- Android input pipeline: https://source.android.com/docs/core/interaction/input
- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Linux uinput: https://cdn.kernel.org/doc/html/latest/input/uinput.html
- Python evdev: https://pypi.org/project/evdev/
- Gradle Maven Publish: https://docs.gradle.org/current/userguide/publishing_maven.html
- JitPack Android publishing: https://docs.jitpack.io/android/
