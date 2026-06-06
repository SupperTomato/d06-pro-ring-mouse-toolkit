#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  tools/android/d06_android_input.sh list [--serial SERIAL]
  tools/android/d06_android_input.sh capture [--serial SERIAL] [--device /dev/input/eventX] [--seconds 15] [--out artifacts/android/getevent/android_getevent.txt]
  tools/android/d06_android_input.sh dump-input [--serial SERIAL] [--out artifacts/android/dumpsys/input.txt]

Environment:
  ADB=/path/to/adb        Override adb binary.
  ADB_SERIAL=SERIAL      Default adb serial.
EOF
}

adb_bin="${ADB:-adb}"
serial="${ADB_SERIAL:-}"

run_adb() {
  if [[ -n "$serial" ]]; then
    "$adb_bin" -s "$serial" "$@"
  else
    "$adb_bin" "$@"
  fi
}

parse_common() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --serial)
        serial="$2"
        shift 2
        ;;
      *)
        echo "unknown option: $1" >&2
        return 2
        ;;
    esac
  done
}

cmd="${1:-help}"
if [[ $# -gt 0 ]]; then
  shift
fi

case "$cmd" in
  list)
    parse_common "$@"
    echo "# getevent devices"
    run_adb shell getevent -lp
    echo
    echo "# dumpsys input D06/TK hints"
    run_adb shell dumpsys input | grep -Ei "D06|TK Wireless|248a|Bluetooth|Input Device" || true
    ;;

  capture)
    seconds=15
    out=""
    device=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --serial)
          serial="$2"
          shift 2
          ;;
        --seconds)
          seconds="$2"
          shift 2
          ;;
        --out)
          out="$2"
          shift 2
          ;;
        --device)
          device="$2"
          shift 2
          ;;
        *)
          echo "unknown option: $1" >&2
          exit 2
          ;;
      esac
    done

    adb_args=()
    if [[ -n "$serial" ]]; then
      adb_args=(-s "$serial")
    fi
    capture_cmd=("$adb_bin" "${adb_args[@]}" shell getevent -lt)
    if [[ -n "$device" ]]; then
      capture_cmd+=("$device")
    fi

    echo "Capturing Android getevent for ${seconds}s. Press/move the D06 now." >&2
    set +e
    if [[ -n "$out" ]]; then
      mkdir -p "$(dirname "$out")"
      timeout "${seconds}s" "${capture_cmd[@]}" | tee "$out"
      status=${PIPESTATUS[0]}
    else
      timeout "${seconds}s" "${capture_cmd[@]}"
      status=$?
    fi
    set -e
    if [[ "$status" -ne 0 && "$status" -ne 124 ]]; then
      exit "$status"
    fi
    ;;

  dump-input)
    out="artifacts/android/dumpsys/input.txt"
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --serial)
          serial="$2"
          shift 2
          ;;
        --out)
          out="$2"
          shift 2
          ;;
        *)
          echo "unknown option: $1" >&2
          exit 2
          ;;
      esac
    done
    mkdir -p "$(dirname "$out")"
    run_adb shell dumpsys input > "$out"
    echo "Wrote $out"
    ;;

  help|-h|--help)
    usage
    ;;

  *)
    usage >&2
    exit 2
    ;;
esac
