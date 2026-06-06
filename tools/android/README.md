# D06 Android Capture Tools

These helpers run from a Linux host with `adb` installed and an Android device connected over USB or wireless debugging.

List Android input devices:

```bash
tools/android/d06_android_input.sh list
```

Capture raw Android input events:

```bash
tools/android/d06_android_input.sh capture --seconds 15 --out artifacts/android_getevent.txt
```

Capture one Android input node after finding it with `list`:

```bash
tools/android/d06_android_input.sh capture --device /dev/input/event7 --seconds 15
```

Dump Android input metadata:

```bash
tools/android/d06_android_input.sh dump-input --out artifacts/android_input.txt
```

For decoded events inside an app, use the SDK sample or `D06InputDiagnostics`.
