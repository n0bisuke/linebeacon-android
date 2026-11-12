# 外部clone者向けのローカル開発イメージ(docs/local-dev/ に置いてあるもの)。
# 共通リポジトリ shared-dev-environments がなくても、このリポジトリだけでビルドが完結する。
#
#   リポジトリルートから:
#   docker compose -f docs/local-dev/compose.yaml run --rm android-local
#
# 構成は .devcontainer/ (Codespaces) と揃えている:
#   JDK 17 / Android SDK (platform-tools, android-36, build-tools 35.0.0) / Node.js
FROM debian:bookworm-slim

ENV ANDROID_HOME=/opt/android-sdk

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
       openjdk-17-jdk-headless git curl unzip ca-certificates nodejs npm \
  && rm -rf /var/lib/apt/lists/*

# Android SDK cmdline-tools + 基本コンポーネント(ライセンス承認済み)
RUN curl -fsSL -o /tmp/commandlinetools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
  && mkdir -p "$ANDROID_HOME/cmdline-tools" \
  && unzip -q /tmp/commandlinetools.zip -d "$ANDROID_HOME/cmdline-tools" \
  && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
  && rm -f /tmp/commandlinetools.zip \
  && yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null \
  && "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
       "platform-tools" "platforms;android-36" "build-tools;35.0.0"

ENV PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

WORKDIR /workspace/android-app
