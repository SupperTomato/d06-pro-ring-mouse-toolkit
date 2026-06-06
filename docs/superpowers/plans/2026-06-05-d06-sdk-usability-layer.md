# D06 SDK Usability Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add practical user-facing SDK features: custom remap presets, event transforms, diagnostics export, Linux profile/template support, publishing config, and examples.

**Architecture:** Keep hardware decoding in `d06-core`/`d06-input`; add transforms as a pure event post-processor; keep remap presets in `d06-remapper` as policy only, with Accessibility execution explicitly limited. Linux remains a decoder/remap CLI with JSON profiles, not a privileged virtual input driver.

**Tech Stack:** Kotlin/JVM tests, Android library modules, Python stdlib CLI/tests, Gradle `maven-publish`.

---

### Task 1: Event Transforms

**Files:**
- Create: `android-sdk/d06-core/src/main/kotlin/com/d06/sdk/core/D06EventTransformer.kt`
- Create: `android-sdk/d06-core/src/test/kotlin/com/d06/sdk/core/D06EventTransformerTest.kt`
- Modify: `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputConfig.kt`
- Modify: `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputDecoder.kt`

- [ ] Write failing tests for inversion, sensitivity, deadzone, scroll scaling.
- [ ] Implement `D06EventTransformConfig` and `D06EventTransformer`.
- [ ] Wire config into `D06InputDecoder`.
- [ ] Run `./gradlew :d06-core:test :d06-input:testDebugUnitTest`.

### Task 2: Custom Remap Presets

**Files:**
- Modify: `android-sdk/d06-remapper/src/main/kotlin/com/d06/sdk/remapper/D06RemapPolicy.kt`
- Create: `android-sdk/d06-remapper/src/test/kotlin/com/d06/sdk/remapper/D06RemapPresetTest.kt`

- [ ] Write failing tests for custom exact mappings, scroll mappings, built-in presets, and accessibility validation.
- [ ] Add `D06RemapPreset`, `D06RemapPresetBuilder`, `D06Remapper`, `D06RemapPresets`, and validator.
- [ ] Preserve `D06RemapConfig` compatibility.
- [ ] Run `./gradlew :d06-remapper:testDebugUnitTest`.

### Task 3: Diagnostics

**Files:**
- Create: `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06InputDiagnostics.kt`
- Modify: `android-sdk/d06-input/src/main/kotlin/com/d06/sdk/input/D06Input.kt`
- Create: `android-sdk/d06-input/src/test/kotlin/com/d06/sdk/input/D06InputDiagnosticsTest.kt`

- [ ] Write failing tests for bounded log, JSON Lines export, and `D06Input` recording.
- [ ] Implement diagnostics without adding JSON dependencies.
- [ ] Run `./gradlew :d06-input:testDebugUnitTest`.

### Task 4: Linux Profiles And Templates

**Files:**
- Modify: `tools/linux/d06_evdev.py`
- Modify: `tests/test_linux_evdev.py`
- Create: `tools/linux/d06-profile.example.json`
- Create: `tools/linux/99-d06-pro.rules`
- Create: `tools/linux/d06-evdev.service`

- [ ] Write failing tests for profile transforms and remap output.
- [ ] Add `--profile` JSON support with stdlib parsing.
- [ ] Add install templates for udev and systemd user service.
- [ ] Run `python3 -m unittest discover -v`.

### Task 5: Publishing And Examples

**Files:**
- Modify: Android Gradle module build files.
- Create: `android-sdk/examples/kotlin-activity/MainActivity.kt`
- Create: `android-sdk/examples/java-activity/MainActivity.java`
- Create: `android-sdk/examples/compose/D06ComposeExample.kt`
- Create: `docs/research/sdk-feature-research.md`
- Modify: `README.md`, `README.zh-CN.md`, `android-sdk/README.md`, `android-sdk/README.zh-CN.md`
- Create: `CHANGELOG.md`

- [ ] Add `maven-publish` to library modules and verify `publishToMavenLocal`.
- [ ] Add examples and docs with current Android/Linux limits.
- [ ] Record research-backed future feature backlog.
- [ ] Run full verification: `./gradlew test assembleDebug publishToMavenLocal`, `python3 -m unittest discover -v`, `git diff --check`.
