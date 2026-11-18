# obnizでLINE Simple Beaconを試す

スマートフォンのAndroidアプリの代わりに、**obnizボードをLINE Simple Beaconの発信機**にして試すための手順。
Webhookの受信(server.jsと同じ機能)が `obniz-server.js` 1本に同居しています。

```
obniz(BLE発信) ─→ 受信側スマホのLINE ─→ LINEサーバー ─→ Webhook → obniz-server.js
                                                     └→ 検知返信(botトークに「検知しました!」)
```

## 必要なもの

| 項目 | 備考 |
|---|---|
| **obniz本体**(OS 2.0以降) | Wi-Fiに接続できること。ディスプレイ表示があるモデルだと進行状況が見やすい |
| **LINE Developers ConsoleのMessaging APIチャネル** | Channel secret(**必須**)・Channel access token(**必須**) |
| **登録済みHWID** | [LINE Official Account Manager](https://manager.line.biz/beacon/register)で「LINE Simple Beacon」のハードウェアIDを発行済みのもの |
| **Node.js >= 20.6** | `fetch` / `AbortSignal.timeout` / `--env-file` を使用 |
| **受信側のスマホ** | LINEアプリで「LINE Beacon」有効、公式アカウントと友だち済み |

## 1. セットアップ

```bash
git clone <このリポジトリのURL>
cd linebeacon-android/linebot
npm install                       # obnizパッケージ(依存はこれのみ)
cp .env.example .env
```

`.env` を編集:

```
LINE_CHANNEL_SECRET=<Channel secret>            # 必須(署名検証)
LINE_CHANNEL_ACCESS_TOKEN=<チャネルアクセストークン>  # 必須(検知返信・apk返信・オウム返しで必要)
OBNIZ_ID=<obnizのID 例: 1234-5678>              # obniz本体画面/obnizアプリで確認
LINE_BEACON_HWID=<登録済みHWID 10桁HEX>          # 例: 0102030405(未登録HWIDだとLINEは検知しない)
# MASK_HWID=1                                   # 既定ON(ログ・返信のHWIDを先頭2/末尾2にマスク)
# PORT=65135                                    # server.jsと同時起動するなら変更
```

## 2. 起動

```bash
npm run start:obniz
```

- 起動するとobnizクラウドに接続し本体ディスプレイが次のように遷移します: **Connected → BLE Ready → Beacon ON**
- ターミナルに `[obniz] Beacon送信開始` と出れば発信中。ディスプレイには **Beacon ON** が表示されたまま
- `Ctrl+C` で終了すると `Beacon OFF` 表示・送信停止

## 3. Webhook URLの設定

server.jsと手順は同じ(詳細は[CODESPACES.md](CODESPACES.md)の「3」参照):

1. サーバーを公開HTTPSに(CodespacesのPort 65135を**Public**、または `npx ngrok http 65135`)
2. LINE Developers Console → Messaging API設定 → **Webhook URL** に `https://<公開URL>/webhook`
3. 「Webhookの利用」**オン**、「検証」で **200**

## 4. 発信→検知の確認

1. **受信側のスマホがobnizのWi-Fi半径(数十cm〜数m)に来るように置く。**
   スマホ側LINEのBeacon設定で該当公式アカウントの検知がON
2. 検知するとターミナルに:

   ```
   ===== Beacon検知 =====
   イベント: enter
   HWID: 01••••••05
   dm: (なし)
   ユーザー: U4af49d0...
   ======================
   ```

   対応するトークにbotから **Beaconを検知しました！** が届く

## 発信内容(フレーム構造)

LINE Simple Beaconの仕様どおり(dmは固定の `0x00`):

```
0x02 0x01 0x06            Flags
0x03 0x03 0x6F 0xFE       16-bit Service UUID 0xFE6F
0x0B 0x16 0x6F 0xFE 0x02 <HWID 5B> 0x7F 0x00   Service Data (0x16)
                          ↑UUID↑  ↑FrameType↑↑HWID↑  ↑ TxPower ↑dm
```

dmには日本語テキスト等は送れません(変更は`obniz-server.js`の`advData`を編集)。

## トラブルシューティング

| 症状 | 対処 |
|---|---|
| 起動時に `環境変数を確認してください` | `.env` の `OBNIZ_ID` / `LINE_BEACON_HWID`(10桁HEX)/ `LINE_CHANNEL_SECRET` / `LINE_CHANNEL_ACCESS_TOKEN` のいずれかが未設定 |
| `Connected` のまま進まない | obnizがオフライン・obnizクラウドに接続失敗。obnizのWi-Fi/電源を確認 |
| ディスプレイは `ERROR` | obnizのBLE init失敗。ターミナルのエラー文を確認しobniz再起動 |
| Webhook「検証」が失敗 | ポートがPrivateになっている、サーバーが停止中。サーバーを起動したままPublicで検証 |
| 検知が来ない | (1) HWIDがLINEに未登録 or 入力ミス (2) 検知側スマホのLINE BeaconがOFF・友だち追加なし・距離が遠い (3) **LINE Beaconの機能は日本・台湾・タイでのみ動作** (4) Webhookの利用がONか、検証が200か |
| server.jsと同時起動でポートがかぶる | `.env` の `PORT` で分ける(例: server.js=65135、obniz側=65136) |

## Androidアプリとの違い

| | Androidアプリ | obniz-server.js |
|---|---|---|
| 発信 | スマホのBLE(実行中dm差し替え可) | obnizのBLE(dm固定0x00) |
| dm | HEX / テキスト(UTF-8) / バッテリー自動 | なし(固定) |
| 返信(オウム返し等) | — | 同じWebhook受信に同居 |
| 必要なもの | LINEビーコン対応端末+HWID | obnizボード+HWID |

Androidアプリとの並行比較をしたければ、スマホアプリで発信 → obniz側のWebhook受信、にも同じserver/obniz-serverがそのまま使えます。
