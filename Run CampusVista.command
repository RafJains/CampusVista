#!/bin/zsh
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$REPO_DIR/android-app"

cd "$REPO_DIR"

print_step() {
  printf "\n==> %s\n" "$1"
}

fail() {
  printf "\nERROR: %s\n" "$1" >&2
  printf "Press Enter to close this window.\n"
  read -r _
  exit 1
}

android_sdk_dir() {
  if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
    printf "%s\n" "$ANDROID_HOME"
    return
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
    printf "%s\n" "$ANDROID_SDK_ROOT"
    return
  fi
  if [[ -f "$ANDROID_DIR/local.properties" ]]; then
    local sdk_dir
    sdk_dir="$(sed -n 's/^sdk.dir=//p' "$ANDROID_DIR/local.properties" | head -1)"
    sdk_dir="${sdk_dir//\\:/\:}"
    if [[ -n "$sdk_dir" && -x "$sdk_dir/platform-tools/adb" ]]; then
      printf "%s\n" "$sdk_dir"
      return
    fi
  fi
}

install_and_launch_if_device_ready() {
  local sdk_dir
  sdk_dir="$(android_sdk_dir || true)"
  if [[ -z "$sdk_dir" ]]; then
    print_step "Android SDK not found in env/local.properties; opening Android Studio"
    open -a "Android Studio" "$ANDROID_DIR" >/dev/null 2>&1 || true
    return
  fi

  local adb="$sdk_dir/platform-tools/adb"
  local connected_devices
  connected_devices=("${(@f)$("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }')}")
  if (( ${#connected_devices[@]} == 0 )); then
    print_step "No emulator/device is connected yet; opening Android Studio"
    open -a "Android Studio" "$ANDROID_DIR" >/dev/null 2>&1 || true
    printf "Start an emulator in Android Studio, then run this file again to auto-install and launch.\n"
    return
  fi

  print_step "Installing Android app on connected emulator/device"
  (
    cd "$ANDROID_DIR"
    ./gradlew :app:installDebug
  )

  print_step "Launching CampusVista"
  local device
  for device in "${connected_devices[@]}"; do
    "$adb" -s "$device" shell am start -n com.campusvista.app/com.example.campusvista.ui.splash.SplashActivity >/dev/null
  done
}

install_and_launch_if_device_ready

cat <<EOF

CampusVista is running.

The Android app now uses packaged offline campus data and on-device recognition.
No Mac backend, Wi-Fi, or USB tunnel is required after installation.

EOF
