# ローカルで開発する(VirtualBoxなしでも可 / Dockerがなくても可)

GitHubに公開したリポジトリをそのままcloneして動かす手順。**Dockerがあれば追加リポジトリなしで完結**します。

## 方法0: Dockerなしでビルドする(Mac / Windowsのホスト直)

Dockerがなくてもビルドは可能。必要なのは **JDK 17とAndroid SDKだけ**(Codespacesの [.devcontainer/setup-android-sdk.sh](../.devcontainer/setup-android-sdk.sh) がやっているSDK導入は、ホストでもそのまま流用できる手順)。

### 必要なもの

| 環境 | JDK 17 | Android SDK | 備考 |
|---|---|---|---|
| **Android Studioを使う(最も簡単)** | 同梱 | 同梱 | インストールしてプロジェクトを開けばビルド可。adbも同梱 |
| macOS + Homebrew(CLI) | `brew install --cask temurin@17` | 下記コマンド | コマンドラインのみ |
| Windows(winget) | `winget install EclipseAdoptium.Temurin.17.JDK` | 下記コマンド | コマンドラインのみ |

CLIの場合のSDK導入(Android Studio不要、setup scriptと同じ手順):

```bash
# macOS例
JDKが入っている前提で:
curl -fsSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
# Windowsの方は同URLの .zip をブラウザで取得して展開

mkdir -p ~/android-sdk/cmdline-tools
unzip -q /tmp/clt.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
export ANDROID_HOME=~/android-sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

### ビルドと実機への入れるまで

```bash
git clone <このリポジトリのURL>
cd linebeacon-android/android-app
./gradlew test assembleDebug      # Windowsは gradlew.bat test assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb tcpip 5555                    # 初回のみUSB接続で実行(端末側で無線ADBポートを表示させる)
adb connect <端末のIP>:5555       # 以降は無線でOK(端末ごとにポートは異なる)
adb install app/build/outputs/apk/debug/app-debug.apk
```

- `adb` は上記SDKの `platform-tools` に含まれる。SDKなしでadbだけを入れることもできるが、**ビルドにはSDK/JDKが必須**(adbは端末操作・転送用のツール)
- Android Studioでプロジェクトを開けば `local.properties` にSDKパスが自動生成される(CLIの場合は `ANDROID_HOME` 環境変数参照)
- linebot(Webhookサーバー)はDocker不要で、Node.jsさえあり動く([../linebot/README.md](../linebot/README.md))

### APKをビルドせず入手するだけの場合

いずれか:

- **GitHub Releases**にAPKが置いてあればそのままダウンロード(リポジトリのReleasesタブを見る)
- 誰かが動かしているlinebotの `GET /apk` からダウンロード
- 上記いずれかで入手したAPKを端末に転送してインストール(提供元不明アプリの許可が必要)

## 方法1: docker compose(docs/local-dev、最も手軽)

`docs/local-dev/` にセルフコンテインドな `Dockerfile` と `compose.yaml` を置いてあります:

```bash
git clone <このリポジトリのURL>
cd linebeacon-android
docker compose -f docs/local-dev/compose.yaml run --rm android-local
# 初回はイメージビルド(SDK取得のため数分)
```

コンテナ内は `/workspace/android-app`(Gradleプロジェクトルート)にいるので、そのまま:

```bash
./gradlew test assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

- `linebot` も同じコンテナで動く(Node.js同梱): `cd ../linebot && npm start`
- **実機への接続**: コンテナはホストと同一LANにいるため、端末の**無線ADB**に接続できる

```bash
adb connect <端末のIP>:39615   # 端末の無線ADBポート(開発者向けオプションで確認)
adb install dist/app-debug.apk
```

構成: JDK 17 / Android SDK(platform-tools, android-36, build-tools 35.0.0, ライセンス承認済み)/ Node.js。GradleキャッシュはVolumeに保存されるので2回目以降は速い。

## 方法2: VS Code Dev Containers

GUI好みなら **Dev Containers** 拡張でも同じことができる(中身は `.devcontainer/`):

1. `code linebeacon-android` でVS Codeを開く(**ルート**を開くこと。`android-app/` を直接開かない)
2. 右下の **「Reopen in Container」** をクリック
3. 初回は `.devcontainer/setup-android-sdk.sh` がAndroid SDKをダウンロード(数分)。完了すれば `cd android-app && ./gradlew test assembleDebug`

Codespacesはこのdevcontainerをクラウドで使う形なので、公開手順は [CODESPACES.md](CODESPACES.md) と共通。

## linebotだけならDocker不要

Node.js(**>= 20.6**)が入っているPCならそのまま動きます:

```bash
cd linebot
cp .env.example .env    # LINE_CHANNEL_SECRET を貼る(.env必須)
npm start               # :65135
```

HTTPS公開(Codespacesポート公開/ngrok)の手順は [CODESPACES.md](CODESPACES.md) の「3. linebot」以降を参照。

## これはテンプレート作者の開発環境とどう違う?

リポジトリルートの `compose.yaml`(`android` サービス)はテンプレート作者用で、共通イメージ `android-kotlin:latest`(**別リポジトリ `shared-dev-environments`** のビルド)を参照する。公開リポジトリだけcloneした人はこの共通イメージを持っていないため、**`docs/local-dev/` の方を使う**こと。

本リポジトリの `Dockerfile` はx86_64イメージ(`debian:bookworm`)をベースにするため、OrbStackのarm64環境でも共通イメージのAAPT2問題(AAPT2 Daemon startup failed)は発生しない。
