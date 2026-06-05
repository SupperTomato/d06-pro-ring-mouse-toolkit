# D06 Linux Evdev Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tested Linux evdev translator and CLI that converts `/dev/input/event*` mouse events into canonical D06 JSON events.

**Architecture:** Keep canonical behavior in a small Python translator that accepts normalized evdev event triples. Keep Linux filesystem/device discovery in the CLI wrapper so translation can be unit-tested without hardware.

**Tech Stack:** Python 3 standard library, Linux evdev event codes, `unittest` for tests.

---

## File Structure

- Create `tools/linux/d06_evdev.py`: Linux event code constants, translation state machine, device discovery, JSONL CLI.
- Create `tests/test_linux_evdev.py`: synthetic `unittest` tests for translator and JSON serialization.
- Modify `README.md`: add Linux support section and commands.
- Modify `README.zh-CN.md`: add matching Chinese Linux support section.

## Tasks

- [x] Write failing tests for button, wheel, motion, unknown-button, high-resolution wheel, and JSON output behavior.
- [x] Implement minimal translator and JSON serializer.
- [x] Implement device discovery from `/proc/bus/input/devices` and explicit node matching.
- [x] Implement CLI options: `--list`, `--node`, `--match`, `--seconds`, `--jsonl`.
- [x] Update READMEs with Linux scope and permission notes.
- [x] Run `python3 -m unittest tests.test_linux_evdev -v`.
- [x] Run `cd android-sdk && ./gradlew test` to ensure Android stays green.
