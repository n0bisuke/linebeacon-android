# linebot — LINE Simple Beacon確認用Webhookサーバー

Androidビーコン発信側の検証用に、Messaging APIのWebhookを受信してビーコンイベントを表示する簡易サーバー。**依存ライブラリなし**(Node.js組み込みモジュールのみ)。

できること:

- `type: "beacon"` イベント(enter / broadcast / exit)を受信して**見やすいログで表示**(HWID / dm / 検知元)
- Channel secretによる**署名検証**(`x-line-signature`)
- `LINE_CHANNEL_ACCESS_TOKEN` を設定すると、**検知をLINEのトークに返信**(`📡 ビーコン検知!` メッセージ)

## セットアップ

1. [LINE Developers Console](https://developers.line.biz/) でMessaging APIチャネルを作成
2. 「チャネル基本設定」から **Channel secret** をコピー
3. 「Messaging API設定」→「Webhook URL」に `https://<このサーバーの公開URL>/webhook` を設定し、「Webhookの利用」を**オン**

```bash
cd linebot
cp .env.example .env          # LINE_CHANNEL_SECRET を貼る(.envはコミットしない)
node server.js                # 既定: http://localhost:65135/webhook (npm startでも可)
```

## HTTPS公開について

LINEのWebhookは**公開されたHTTPS URL**が必須のため、ローカル実行のままでは届きません。開発中の確認はトンネルを使うのが手軽:

```bash
npx ngrok http 65135          # 例: https://xxxx.ngrok-free.app
```

発行されたURLの末尾に `/webhook` を付けたものをWebhook URLに設定。設定後、Consoleの「検証」ボタンで `200` が返ることを確認し、ビーコン発信側スマホを受信側端末の近くに置くと、このサーバーに検知ログが出ます。

## 受信できるイベントの見方

ビーコン検知時のログ例:

```
━━━ 📡 ビーコン検知! ━━━
  時刻   : 2026/11/12 15:30:00
  種別   : enter
  HWID   : 0102030405
  dm     : 0x1122aabbcc
  検知元 : userId=U4af49d00…
━━━━━━━━━━━━━━━━━━━
```

- `HWID` は端末に設定した10桁HEXと一致すること(公式アカウントに登録したHWIDと一致しないとイベント自体が来ない)
- `dm` は端末のDevice MessageのHEX(空欄時 `00`)
- 返信を有効にした場合、検知元のユーザーのトークに返信される(グループ・トークへの配信などは対象外)

## 注意

- **Channel secret / Channel access token はコードに書かず .env(未コミット)または環境変数で**渡すこと。公開リポジトリに載せない
- 「Webhookの利用」をオンにしていても、**LINE Beacon機能は日本・台湾・タイでのみ動作**
- Beaconイベントが来ない場合: 受信側端末の「LINE Beacon」設定が有効か、公式アカウントが**友だち追加**されているか、検知側ネットワークでBLEが動いているかを確認
