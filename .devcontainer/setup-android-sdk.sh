#!/usr/bin/env bash
# GitHub Codespaces向け: Android SDK と adb をセットアップする。
# Codespace作成時に一度だけ実行される(onCreateCommand)。
#
# 入れるもの:
#   - Android SDK cmdline-tools + platform-tools + platforms;android-36
#     (Gradle/AGPが不足コンポーネントを自動取得できるようライセンス承認まで行う)
#   - unzip等の最低限のユーティリティ
set -euo pipefail

SDK_DIR="$HOME/android-sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

# --- 必要ユーティリティ(無ければsudoで入れる。vscodeユーザーはsudo可) ---
command -v unzip >/dev/null || sudo apt-get update -y && sudo apt-get install -y unzip curl

# --- cmdline-tools の入手 ---
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[setup] downloading Android cmdline-tools..."
  curl -fsSL -o /tmp/commandlinetools.zip "$CMDLINE_TOOLS_URL"
  mkdir -p "$SDK_DIR/cmdline-tools"
  unzip -q /tmp/commandlinetools.zip -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -f /tmp/commandlinetools.zip
fi
export ANDROID_HOME="$SDK_DIR"

# --- ライセンス承認(無いとGradleの自動コンポーネント取得に失敗する) ---
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null

# --- 基本コンポーネント ---
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-36" "build-tools;35.0.0" >/dev/null

"$SDK_DIR/platform-tools/adb" version | head -1

echo "[setup] done. ANDROID_HOME=$SDK_DIR"
