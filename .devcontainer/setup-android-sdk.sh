#!/usr/bin/env bash
# GitHub Codespaces向け: Android SDK と adb をセットアップする。
# Codespace作成時に onCreateCommand として一度だけ実行される。
#
# 入れるもの:
#   - Android SDK cmdline-tools + platform-tools + platforms;android-36
#     (Gradle/AGPが不足コンポーネントを自動取得できるようライセンス承認まで行う)
#   - unzip等の最低限のユーティリティ
#
# NOTE: pipefail を使わない。`yes | sdkmanager` や `| head` のような
# 上流が先に終わるパイプはSIGPIPE(exit 141)になり、pipefailがあると
# CodespacesのonCreateCommandが失敗扱いになりリカバリコンテナ(Alpine)に
# されてしまう事故があった(実際に発生)。
set -eu

SDK_DIR="$HOME/android-sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

# --- 必要ユーティリティ(無いときだけsudoで入れる。vscodeユーザーはsudo可) ---
if ! command -v unzip >/dev/null 2>&1; then
  sudo apt-get update -y
  sudo apt-get install -y unzip curl
fi

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
# yes は sdkmanager が終わるとSIGPIPEで死ぬが、pipefailが無ければ問題ない
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null

# --- 基本コンポーネント ---
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-36" "build-tools;35.0.0" >/dev/null

# 動作確認(パイプ+headは避ける)
"$SDK_DIR/platform-tools/adb" version > /dev/null

echo "[setup] done. ANDROID_HOME=$SDK_DIR"
