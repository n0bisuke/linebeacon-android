# ローカルのDockerコンテナで開発する

GitHubに公開したリポジトリをそのままcloneして動かす手順。**Dockerがあれば追加リポジトリなしで完結**します。

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

Node.jsが入っているPCならそのまま動きます(依存ライブラリなし):

```bash
cd linebot
cp .env.example .env    # LINE_CHANNEL_SECRET を貼る
npm start               # :65135
```

HTTPS公開(Codespacesポート公開/ngrok)の手順は [CODESPACES.md](CODESPACES.md) の「3. linebot」以降を参照。

## これはテンプレート作者の開発環境とどう違う?

リポジトリルートの `compose.yaml`(`android` サービス)はテンプレート作者用で、共通イメージ `android-kotlin:latest`(**別リポジトリ `shared-dev-environments`** のビルド)を参照する。公開リポジトリだけcloneした人はこの共通イメージを持っていないため、**`docs/local-dev/` の方を使う**こと。

本リポジトリの `Dockerfile` はx86_64イメージ(`debian:bookworm`)をベースにするため、OrbStackのarm64環境でも共通イメージのAAPT2問題(AAPT2 Daemon startup failed)は発生しない。
