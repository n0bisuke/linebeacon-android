# GitHub Codespacesで動かす手順

ローカルにDocker/Android SDK環境がなくても、Codespace上でビルドとlinebot(WEBHOOKサーバー)の両方が動きます。
リポジトリルートの `.devcontainer/` により、Codespace作成時にAndroid SDK(ライセンス承認済み)とNode.jsが自動セットアップされます。

## 1. Codespaceを作る

1. GitHubでこのリポジトリを開く(Forkした場合はそのリポジトリ)
2. **Code → Codespaces タブ → Create codespace on main**
3. 初回は `.devcontainer/setup-android-sdk.sh` が走り、SDKのダウンロードに数分かかる(以降は再起動してもキャッシュで速い)
4. 「SDK未インストール…」と出た場合はCodespaceを一度Reload/再起動すると解消することがある

## 2. Androidアプリをビルドする

```bash
cd android-app
./gradlew test assembleDebug     # ANDROID_HOMEはdevcontainerで設定済み
```

APKは `app/build/outputs/apk/debug/app-debug.apk` にできる。linebotで公開するため共有用にコピーしておく:

```bash
mkdir -p dist && cp app/build/outputs/apk/debug/app-debug.apk dist/app-debug.apk
```

**実機へのINSTALL**は、後で起動するlinebotの公開URLから直接できる(下の「3.」とセット):

- スマホのブラウザで **`https://<codespace名>-65135.app.github.dev/apk`** を開くとAPKがダウンロードされ、タップでインストールできる(「提供元不明アプリ」の許可が必要)
- **LINEで配布**も可能: 実機のトークでbotに **`apk` と送信**すると、botがダウンロードURLを返信する。そのURLをタップ→ダウンロード→インストール。URLは自分の他の端末や友だちに転送してもOK(Publicポート必須)
- うまくいかない場合の代替: VS Codeのエクスプローラーで `app-debug.apk` を右クリック → **Download...** でローカル保存し、端末へ手動転送
- HWIDやdmの設定はすべて端末側のアプリ画面で行う(Codespace側の設定は不要)
- アプリ自体はブラウザ上では動かない(実機インストールが必須)

## 3. linebot(Webhookサーバー)を起動する

### トークン類の用意(2択)

- **Codespaces Secrets**: リポジトリ設定 → Codespaces → Repository secrets に `LINE_CHANNEL_SECRET`(必須)と `LINE_CHANNEL_ACCESS_TOKEN`(返信・オウム返ししたい場合)を登録。サーバー起動時に読まれる
- **.env ファイル**: Codespace内の `linebot/.env` に作成(`cp .env.example .env`して編集)。`.env` はgitignore済みなのでコミットされない

### 起動とポート公開

```bash
cd linebot
npm start                        # :65135 で待受(0xFE6F)
```

1. VS Code下部の**Portsパネル**に `linebot webhook server` (65135) が出る
2. その行の **Visibility を「Public」に変更** ← 認証付き(Private)URLはLINEサーバーが取得できないためここが必須
3. 発行されたURLをコピー(URL欄のアイコンから)。`https://<codespace名>-65135.app.github.dev/webhook` の形

### LINE Developers Consoleへ設定

1. [LINE Developers Console](https://developers.line.biz/) → Messaging API設定
2. **Webhook URL** に `https://<codespace名>-65135.app.github.dev/webhook` を入力・「Webhookの利用」を**オン**
3. 「検証」ボタンで `200` が返ることを確認(Codespaceがスリープしていると失敗するので起動中に確認)

## 4. 動作確認

1. 実機アプリで「発信開始」し、LINEが入っている受信側端末を近くに置く(同一HWIDが公式アカウントに登録済みのこと)
2. linebotのターミナルに `📡 ビーコン検知!` のログが出る(HWIDは既定でマスク表示、`MASK_HWID=0` で解除)
3. アクセストークンを設定していれば、検知元ユーザーのトークに返信が届く
4. トークで `apk` と送ると、APKダウンロードURL(`https://<codespace名>-65135.app.github.dev/apk`)が返ってくる

## 制限・注意

- **Codespaceは放置で自動停止**する。停止中はWebhookが届かない。テスト中は定期的に動かしておく
- Codespaceは**課金・無料枠**の対象なので、使わないときは削除推奨
- ビルド成果物(APK)の持ち出しは上記のDownloadのほか、GitHub Releasesへドラッグ&ドロップでも可
- 実機へのadb接続(無線ADB)はLAN外から不可。デバッグ実行はローカルのDocker環境(compose.yaml)側で行う
