# linebot — LINE Simple Beacon確認用サーバー / obniz発信機

Messaging APIのWebhookを受信してビーコンイベントを表示するサーバーと、obnizをLINE Simple Beacon発信機にするサーバー。**server.jsは外部依存なし**(Node.js組み込みモジュールのみ)。obniz-server.jsのみ `obniz` パッケージを使用。

**Node.js >= 20.6** が必要(`--env-file` / `fetch` / `AbortSignal.timeout` を使用):

```bash
node -v    # v20.6.0 以降
npm install    # obnizパッケージ取得(server.jsのみなら事実上不要だが実行しておく)
```

## 2つのサーバー

| 起動コマンド | ファイル | 内容 |
|---|---|---|
| `npm start` | `server.js` | **Webhook受信のみ**。発信はAndroidアプリが担当 |
| `npm run start:obniz` | `obniz-server.js` | **obnizをBeacon発信機にしつつ**Webhookも受信。AndroidアプリなしでESP32的な構成 |

どちらも既定ポート 65135(=0xFE6F)。**同時に動かすには `.env` で `PORT` を分ける**(例: server.jsは65135、obniz側は65136)。

## できること(共通)

- `type: "beacon"` イベント(enter / broadcast / exit)を受信して**見やすいログで表示**(HWID / dm / 検知元)
- Channel secretによる**署名検証**(`x-line-signature`)
- `LINE_CHANNEL_ACCESS_TOKEN` を設定すると、**検知をLINEのトークに返信**(`📡 ビーコン検知!` メッセージ。obniz-server.jsでは必須)
- トークン設定時に受け取った**テキストメッセージにはオウム返し**で返信(botの疎通確認用)
- server.jsのみ: **APK配信**(Androidアプリ用、下記参照)

**HWIDのマスク表示**: 既定でON。ログ・返信に表示されるHWIDが `0102030405 → 01••••••05` のように先頭2桁と末尾2桁だけになります(画面共有やスクショを公開する際の安全策)。実HWIDを見たい場合は `.env` に `MASK_HWID=0` を書いて再起動。

## server.js(Webhook受信のみ)のセットアップ

1. [LINE Developers Console](https://developers.line.biz/) でMessaging APIチャネルを作成
2. 「チャネル基本設定」から **Channel secret** をコピー
3. 「Messaging API設定」→「Webhook URL」に `https://<このサーバーの公開URL>/webhook` を設定し、「Webhookの利用」を**オン**

```bash
cd linebot
cp .env.example .env          # LINE_CHANNEL_SECRET を貼る(.envはコミットしない)
npm start                     # 既定: http://localhost:65135/webhook
```

**APK配信(Androidアプリ用)**: `GET /apk` で `android-app/dist/app-debug.apk` をダウンロード配信。トークで **`apk` と送るとダウンロードURLを返信**(実機インストール用。`APK_FILE` / `APK_PATH` 環境変数で変更可)

## obnizを発信機にする(npm run start:obniz)

obnizのBLEをLINE Simple Beacon発信機として動かし、Webhook受信も同じプロセスで行う構成。**LINE Official Account Managerでビーコン端末として登録済みのHWID**を使うこと(未登録のHWIDではLINEが検知しない):

```
0x02 0x01 0x06              # Flags
0x03 0x03 0x6F 0xFE         # 16-bit Service UUID 0xFE6F
0x0B 0x16 0x6F 0xFE ...     # Service Data (0x16): UUID + [FrameType 0x02][HWID 5B][TxPower 0x7F][dm 0x00]
```

`.env` に追加:

```
OBNIZ_ID=1234-5678                  # obniz本体画面・obnizアプリ配布時に発行されるID
LINE_BEACON_HWID=0102030405         # 登録済みHWID(10桁HEX)
LINE_CHANNEL_ACCESS_TOKEN=...       # 必須(返信用)
```

```bash
npm run start:obniz
```

起動するとobniz本体のディスプレイに `Connected → BLE Ready → Beacon ON` と表示され、発信を開始(dmは固定の0x00)。Webhook URLの設定はserver.jsと同じ。発信中はobnizをネットワークにつないだままにする。

**注意**: obniz-server.jsはビーコン検知の返信にトークンを必ず使うため、`LINE_CHANNEL_ACCESS_TOKEN` 未設定だと起動時エラーになる。

## HTTPS公開について

LINEのWebhookは**公開されたHTTPS URL**が必須のため、ローカル実行のままでは届きません。**GitHub Codespacesで公開する手順は [docs/CODESPACES.md](../docs/CODESPACES.md)** を参照(ポート公開の「Public」変更がポイント)。ローカル実行時はトンネルを使うのが手軽:

```bash
npx ngrok http 65135          # 例: https://xxxx.ngrok-free.app
```

発行されたURLの末尾に `/webhook` を付けたものをWebhook URLに設定。設定後、Consoleの「検証」ボタンで `200` が返ることを確認し、ビーコン発信側スマホ(or obniz)を受信側端末の近くに置くと、このサーバーに検知ログが出ます。

## 受信できるイベントの見方

ビーコン検知時のログ例(server.js):

```
━━━ 📡 ビーコン検知! ━━━
  時刻   : 2026/11/12 15:30:00
  種別   : enter
  HWID   : 01••••••05   (マスク表示。MASK_HWID=0 で実値表示)
  dm     : 0x1122aabbcc
  検知元 : userId=U4af49d00…
━━━━━━━━━━━━━━━━━━━
```

obniz-server.jsは `===== Beacon検知 =====` 形式のシンプルなログになります(`dm` はHEXそのまま、テキスト併記なし)。

- `HWID` は端末(obnizまたはAndroidアプリ)に設定した10桁HEXと一致すること(公式アカウントに登録したHWIDと一致しないとイベント自体が来ない)
- `dm` は端末のDevice MessageのHEX(空欄時 `00`)。中身がUTF-8テキストとして復元できる場合のみ `(= "テキスト")` と併記表示される。日本語を送るには発信側アプリでUTF-8のHEXにエンコードして入力する(日本語1文字=3バイト、dm上限13バイトのため**最大4文字**)
- 返信を有効にした場合、検知元のユーザーのトークに返信される(グループ・トークへの配信などは対象外)

## 注意

- **Node.js >= 20.6**(`--env-file`、`fetch`、`AbortSignal.timeout` を使用)。`.env` ファイルが無いと `npm start` が起動しないので先に `cp .env.example .env`
- **Channel secret / Channel access token はコードに書かず .env(未コミット)または環境変数で**渡すこと。公開リポジトリに載せない
- 「Webhookの利用」をオンにしていても、**LINE Beacon機能は日本・台湾・タイでのみ動作**
- Beaconイベントが来ない場合: 受信側端末の「LINE Beacon」設定が有効か、公式アカウントが**友だち追加**されているか、検知側ネットワークでBLEが動いているかを確認
