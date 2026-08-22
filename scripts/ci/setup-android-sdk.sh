#!/bin/sh
# setup-android-sdk.sh — idempotent Android SDK setup with persistent cache (/opt/android-sdk)
set -e
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[sdk] downloading commandline-tools (cold)"
  curl -fsSL -o /tmp/cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  cd /tmp && rm -rf cmdtools && unzip -q cmdtools.zip -d cmdtools
  mv cmdtools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  echo "[sdk] commandline-tools installed"
else
  echo "[sdk] commandline-tools cached (warm)"
fi
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null 2>&1 || true
echo "[sdk] ready:"
ls "$ANDROID_HOME/platforms" 2>/dev/null
ls "$ANDROID_HOME/build-tools" 2>/dev/null

