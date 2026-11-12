# 検証ガイド

実際にビーコンが正しく発信・検知されているかを確認する手順。パケット確認(LINEに頼らず自力で確認)→ LINE検知 → Webhook配信の3段階で進める。

## ステップ1: BLEパケットの確認(nRF Connect)— HWID登録不要

ダミーHWID(例: `0102030405`)でも確認可能。別のスマートフォン1台(iPhone / Androidどちらでも)を用意する。

1. [nRF Connect for Mobile](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp) をインストール
2. SCANNER を開き、Service Dataフィルタ `0xFE6F` を設定(または一覧から該当端末の広告を探す)
3. 該当アドバタイズを展開して以下が見えればフレーム生成は仕様どおり:
   - **Service UUID: `0xFE6F`**
   - **Service Data: `6FFE 02 <HWID 10桁> 7F <Device Message>`**
     - 例: `6FFE 02 0102030405 7F 1122AABBCC`(ダミー値)
   - 間隔 ≒152.5ms / Flags AD (`0x06`) はAndroid OSが自動付与するため、パケット全体が公式仕様のオクテット構成と一致する

## ステップ2: LINEアプリによる検知(本物のHWID+公式アカウントが必要)

1. LINE公式アカウントがなければ [LINE Developers Console](https://developers.line.biz/) で作成(個人でも無料。要Eメール登録)
2. [LINE Official Account Manager](https://manager.line.biz/) →「ビーコン」→「LINE Simple Beacon」で **HWIDを発行**し、アプリに入力して再発信
3. 受信側端末(発信側と**別端末**を推奨):
   - Bluetoothをオン
   - 設定 → プライバシー管理 → **LINE Beacon を有効化**
   - 対象の公式アカウントを**友だち追加**
4. 発信側端末を数m以内に置く → 受信側のLINEで通知/アクションが発生すれば検知成功

## ステップ3: Webhookでの受信確認(Messaging API)

1. LINE Developers Consoleで Messaging APIチャネルを作成し、Webhook URLを設定して「Webhookの利用」をオン
2. 受信サーバーを用意しない場合、手軽な確認手段は [webhook.site](https://webhook.site) — 発行URLをWebhook URLに設定すれば、届くJSONをブラウザでそのまま確認できる
3. 再度受信側端末を近づけ、`type: "beacon"`, `beacon.type: "enter"`, `beacon.hwid`, `beacon.dm` を含むイベントが届くことを確認

## 注意

- LINE Beacon機能は**日本・台湾・タイでのみ動作**
- 検知側と発信側は別端末にする(同一端末の自発信検知は挙動を保証しない)
- `beacon.dm` は空欄時 `00` として届く(仕様: 未使用時は `0x00`)
- **Webhook受信までの成功は本プロジェクトでは未検証**(要: 本物のHWID登録・受信端末・公式アカウント)
