# GitHub Codespacesで動かす手順(検証済み)

ローカルにDocker/Android SDK環境がなくても、Codespace上で**Androidアプリのビルド**と**linebot(Webhookサーバー)** の両方が完結します。
リポジトリルートの `.devcontainer/` 設定により、Codespace作成時にJDK 17・Android SDK(ライセンス承認済み)・Node.js(LTS)が自動セットアップされます。

実際に確認できたフロー(2026-11 検証):

```
Codespace作成 → ./gradlew test assembleDebug  → APK完成(dist/)
     → linebot起動 → ポートを「Public」に → Webhook URL設定
     → 実機にAPKインストール → 発信開始 → 📡 検知ログ
```

全体の所要目安: Codespace作成&SDK導入 約5分 / ビルド 約5分 / linebot起動 1分

---

## 0. 事前準備

1. [LINE Developers Console](https://developers.line.biz/) でMessaging APIチャネルを作成
2. 「チャネル基本設定」から **Channel secret** をコピー(手順3で使う)
3. 返信・オウム返しを試すなら「Messaging API設定」→ **Channel access token(長期)** も発行してコピー
4. [LINE Official Account Manager](https://manager.line.biz/beacon/register) で**ビーコン端末(HWID)の登録**を済ませておく(動作確認で発信元の端末に登録済みHWIDを設定するため)

---

## 1. Codespaceを作る

1. GitHubでこのリポジトリを開く(Forkした場合はそのリポジトリ)
2. **Code → Codespaces タブ → Create codespace on main**
3. 初回は起動ターミナルに以下のようなログが流れ、終わると自動でSDKが入っています:

   ```
   Running the onCreateCommand from devcontainer.json...
   bash .devcontainer/setup-android-sdk.sh
   [setup] downloading Android cmdline-tools...
   [setup] done. ANDROID_HOME=/home/vscode/android-sdk     ← これが出れば成功
   ```

   完了後の確認:

   ```bash
   cat /etc/os-release | head -2    # "Debian GNU/Linux 12" であること(alpineなら後述のトラブルシューティングへ)
   echo $ANDROID_HOME               # /home/vscode/android-sdk
   node -v                          # v20.6.0 以上
   ```

作成・導入に数分。

---

## 2. Androidアプリをビルドする

```bash
cd /workspaces/linebeacon-android/android-app
./gradlew test assembleDebug      # 初回は依存の取得もあり4〜5分
```

成功:

```
BUILD SUCCESSFUL
APK: app/build/outputs/apk/debug/app-debug.apk
```

linebotの `/apk` で配信するため共有用にコピー:

```bash
mkdir -p dist && cp app/build/outputs/apk/debug/app-debug.apk dist/app-debug.apk
```

> `compileSdk = 36` に関するWARNING(AGP 8.7.3の検証範囲の話)は無視してOK。消したい場合は `android-app/gradle.properties` に `android.suppressUnsupportedCompileSdk=36` を追加

---

## 3. linebotを起動してURLを公開する

### 3-1. .env を用意して起動

```bash
cd /workspaces/linebeacon-android/linebot
cp .env.example .env              # 中身を編集して LINE_CHANNEL_SECRET(と必要なら ACCESS_TOKEN)を貼る
npm install                       # obnizパッケージ(初回のみ)
npm start                         # :65135 で待受(0xFE6F)
```

起動ログ:

```
LINE Beacon webhook server listening on :65135/webhook
署名検証: 有効 (LINE_CHANNEL_SECRET を使用)
```

※ `npm start` は `--env-file=.env` で `.env` を読むため、**`.env` の作成が必須**です。

### 3-2. ポートを「Public」に ← 検知させるなら必須

1. VS Code下部の**Portsパネル**に `linebot webhook server` (65135) が出る
2. その行を右クリック(または鎖アイコン横)→ **Visibility: Public**
3. URL(コピー)は `https://<codespace名>-65135.app.github.dev` の形

> 認証付き(Private)URLはLINEサーバーがアクセスできず、Webhook検証・検知が両方失敗します。ハマりやすいポイントなのでここを先に直すこと。

### 3-3. LINE Developers Consoleへ設定

1. [LINE Developers Console](https://developers.line.biz/) → 対象チャネル → **Messaging API設定**
2. **Webhook URL** に `https://<codespace名>-65135.app.github.dev/webhook` を入力
3. 「Webhookの利用」を**オン**、「検証」ボタンで **200** が返ることを確認(ブラウザで `https://...-65135.app.github.dev/` を開いて `LINE Beacon webhook server` と出ることも確認)

---

## 4. 実機にAPKを入れて発信する

APKの配布は起動中のlinebotから行えます:

- **スマホのブラウザ**: `https://<codespace名>-65135.app.github.dev/apk` を開く → ダウンロード → タップでインストール(「提供元不明アプリ」の許可が必要)
- **LINEで配布**: 実機のbotトークで **`apk` と送信** → botがダウンロードURLを返信するのでタップ(Publicポート必須)
- 代替: VS Codeエクスプローラーで `app-debug.apk` を右クリック → **Download...** して手動転送

実機アプリで:

1. HWID欄に**登録済みHWID(10桁HEX)** を入力(dmは空欄でもHEXでもテキストでもバッテリーでもOK)
2. **発信開始** → Bluetooth権限を許可 → ステータスが「発信中」になる
3. LINEの入った受信側端末を近くに置く(LINE BeaconのON・公式アカウントの友だち追加も要)

---

## 5. 検知を確認する

linebotを起動したターミナルに:

```
━━━ 📡 ビーコン検知! ━━━
  時刻   : 2026/11/18 ...
  種別   : enter
  HWID   : 01••••••05    (マスク表示。.env の MASK_HWID=0 で実値)
  dm     : 0x383525(= "85%")
  検知元 : userId=U4af...
━━━━━━━━━━━━━━━━━━━
```

- アクセストークンを設定していれば、検知元ユーザーのトークに `📡 ビーコン検知!` が返信され、テキストメッセージはオウム返しされます
- ここまで来たら全ルート完成

---

## 6. トラブルシューティング

| 症状 | 原因と対処 |
|---|---|
| Codespaceが **Alpine Linux**(`cat /etc/os-release` でalpine)で、Java/adbが無い | devcontainer作成が失敗した際の**リカバリコンテナ**。作り直しが確実: 旧Codespaceを削除 → 最新mainで再作成 |
| `JAVA_HOME is set to an invalid directory: /usr/lib/jvm/msopenjdk-current` | 同上のリカバリコンテナ(GitHub既定イメージの名残)。Rebuildではなく**削除→作り直し**推奨 |
| 起動ログに `onCreateCommand from devcontainer.json failed with exit code 141` | 一時期の **setup-android-sdk.sh のSIGPIPEバグ**(最新mainでは修正済み)。`git pull` してから「Codespaces: Rebuild Container... without cache」の実行 |
| 起動時に `SDK未インストール: onCreateが失敗していないか確認してください` | 古い `.devcontainer/devcontainer.json`(PATH不足)でのみ出る誤検知。SDK自体は入っている。mainをpullしてRebuildすれば出ない。現環境の確認は `ls $HOME/android-sdk/platform-tools/adb` |
| Webhook「検証」がタイムアウト/失敗する | ポートがPrivateのまま(→3-2を参照)、または**Codespaceがスリープ中**。PortsパネルでPublicにしたうえでサーバーを起動したまま検証する |
| 「検証」は200なのに検知が来ない | 発信側&受信側の近接/LINE Beacon設定/友だち追加を確認(手順「4.」「5.」)。**Japan/Taiwan/ThailandのいずれかでLINE Beacon機能が有効な地域であること** |
| Codespaceを放置して動かない | Codespaceは一定時間で**自動停止**。Webhookは動作中のみ届く。テスト中は起動し続ける(課金/無料枠の対象) |

## 7. そのほか

- **APKの別の持ち出し方**： GitHub Releasesへ `dist/app-debug.apk` をドラッグ&ドロップ。Releases URLからビルド不要で入手できるため、ハンズオン等人に渡す場合の最終成果物はReleases推奨
- **Claude Codeで追加開発する場合**： 受講生各自が [ollama.com](https://ollama.com) のアカウント→APIキーを発行し、Repositories → Settings → Secrets → Codespaces に `ANTHROPIC_BASE_URL=https://ollama.com`、`ANTHROPIC_AUTH_TOKEN=<自分のキー>`、`ANTHROPIC_MODEL=<使うモデル>` を入れておけばキーはコミット不要のままCodespaceで `claude` が使える(Ollama Cloudは従量課金)
- Codespaceでの編集はgit commit & pushで本リポジトリへ。**pushしていない編集はCodespace削除で消えます**
