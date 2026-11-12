# ローカルのDockerコンテナで開発する

GitHubに公開したリポジトリをそのままcloneして動かす手順。**VS Code + Docker があれば追加リポジトリなしで完結**します。

## 前提

- Docker Desktop / OrbStack / Docker Engine どれでも可
- [Visual Studio Code](https://code.visualstudio.com/) + **Dev Containers** 拡張

## 手順(devcontainer: おすすめ・完全セルフコンテインド)

1. リポジトリをclone:

   ```bash
   git clone <このリポジトリのURL>
   code linebeacon-android   # cloneしたフォルダ名
   ```

2. VS Code右下の通知 **「Reopen in Container」** をクリック(出ない場合はコマンドパレット → `Dev Containers: Reopen in Container`。リポジトリ自体を開くこと。`android-app/` だけで開かない)
3. 初回は `.devcontainer/setup-android-sdk.sh` が走りAndroid SDKをダウンロード(数分)。完了すればビルド可能:

   ```bash
   cd android-app
   ./gradlew test assembleDebug
   ```

4. APKは `app/build/outputs/apk/debug/app-debug.apk`

**実機への接続**: コンテナはホストと同一LANにいるため、端末の**無線ADB**に接続できる:

```bash
adb tcpip 5555          # 最初にUSB接続したホスト側で実行(または端末側でポートを確認)
adb connect <端末のIP>:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

CodespacesのようにAPKを手元へ運ぶ必要がないので、デバッグ実行・ログ確認(`adb logcat`)もコンテナから直接可能。

## docker compose を使う場合(内部開発者向け)

リポジトリルートの `compose.yaml` は共通イメージ `android-kotlin:latest` を参照しており、**別リポジトリ `shared-dev-environments` のビルドが必要**です(ルートREADME参照)。公開リポジトリだけをcloneした人はこの方法では起動できず、上のdevcontainerを使ってください。

```bash
# shared-dev-environments が ../../shared-dev-environments にある前提
docker compose run --rm android   # コンテナ内は /workspace/android-app
```

OrbStackのarm64環境ではAAPT2(x86_64バイナリ)のためビルドが失敗することがある。対処は[android-app/README.mdの注意書き](../android-app/README.md)を参照(devcontainerイメージはx86_64のためこの問題は出ない)。

## linebotだけならDocker不要

Node.jsが入っているPCならそのまま動きます(依存ライブラリなし):

```bash
cd linebot
cp .env.example .env    # LINE_CHANNEL_SECRET を貼る
npm start               # :65135
```

HTTPS公開(Codespacesポート公開/ngrok)の手順は [CODESPACES.md](CODESPACES.md) の「3. linebot」以降を参照。
