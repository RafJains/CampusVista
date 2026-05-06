#!/bin/zsh
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$REPO_DIR/python-backend"
ANDROID_DIR="$REPO_DIR/android-app"
BACKEND_LOG="$REPO_DIR/.campusvista-backend.log"
BACKEND_PID_FILE="$REPO_DIR/.campusvista-backend.pid"

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

if ! python3 - <<'PY'
import fastapi
import numpy
import open_clip
import PIL
import torch
import uvicorn
PY
then
  print_step "Installing missing Python dependencies"
  python3 -m pip install --user \
    -r "$BACKEND_DIR/requirements.txt" \
    -r "$REPO_DIR/python-tools/requirements.txt" \
    || fail "Python dependency installation failed."
fi

start_backend() {
  if [[ -f "$BACKEND_PID_FILE" ]]; then
    local existing_pid
    existing_pid="$(cat "$BACKEND_PID_FILE")"
    if kill -0 "$existing_pid" 2>/dev/null; then
      print_step "Backend already running on port 8000"
      return
    fi
  fi

  if lsof -ti tcp:8000 >/dev/null 2>&1; then
    print_step "Something is already using port 8000; assuming backend is running"
    return
  fi

  print_step "Starting OpenCLIP backend on http://127.0.0.1:8000"
  (
    cd "$BACKEND_DIR"
    PYTHONPATH="$REPO_DIR/python-common:$BACKEND_DIR" \
      python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000
  ) >"$BACKEND_LOG" 2>&1 &
  echo "$!" > "$BACKEND_PID_FILE"

  for _ in {1..60}; do
    if curl -sS -o /dev/null -D - "http://127.0.0.1:8000/health" >/dev/null 2>&1; then
      print_step "Backend is ready"
      return
    fi
    sleep 1
  done

  tail -80 "$BACKEND_LOG" || true
  fail "Backend did not become ready. See $BACKEND_LOG"
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
  if ! "$adb" devices | awk 'NR > 1 && $2 == "device" { found=1 } END { exit found ? 0 : 1 }'; then
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
  "$adb" shell am start -n com.example.campusvista/.ui.splash.SplashActivity >/dev/null
}

start_backend
install_and_launch_if_device_ready

cat <<EOF

CampusVista is running.

Backend:
  http://127.0.0.1:8000

Emulator backend URL:
  http://10.0.2.2:8000/

Backend log:
  $BACKEND_LOG

Keep this window open while using backend/OpenCLIP recognition.
Press Ctrl+C to stop this launcher window. If the backend was started by this launcher,
you can stop it with:
  kill \$(cat "$BACKEND_PID_FILE")

EOF

while true; do
  sleep 3600
done
