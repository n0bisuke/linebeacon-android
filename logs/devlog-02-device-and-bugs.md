# 開発ログ02 — 実機接続、検証、バグフィックス (2026-11-12)

> **公開時の注意**: 実HWID・シークレット等は記載しない。数値例はダミー。

## 無線ADB接続

実機(Android 16, API 36)は **Wi-Fi無線デバッグ** で接続:

- ペアリング: `adb pair <ip>:<port> <code>` — **ペア画面を閉じるとportは失効する**(ダイアログを開き直すと新port)。ペア失敗の原因はほぼこれ
- 接続後: `adb connect <ip>:<port>`。コンテナ内からはmDNSが見えないので手動接続
- `adb shell input text` は日本語IMEを経由して**全角数字になる**ことがある → HWID入力テストは `run-as` でSharedPreferencesを直接書くか端末で手入力

## 検証結果(dumpsys)

発信開始後、`dumpsys bluetooth_manager` で実際に電波に乗っていることを確認:

```
Interval(0.625ms) : 244            (= 152.5ms、仕様推奨どおり)
TX POWER(dbm)     : -7
Legacy            : true
Connectable       : false / Scannable : true
Service Data      : [0000fe6f-..., 12]
```

12バイト = `0x02 + HWID(5B) + 0x7F + dm(5B)`。フレーム生成は仕様どおり。

単体テスト:フレーム生成(仕様例HWID `32AF519E88` + 13バイトdm)、HEX変換、異常入力拒否、空dm→`0x00` をカバー。

## 実機で見つかったバグ(2件)

### 1. 発信ボタンの権限デッドロック

`enabled = canStart && bluetoothPermissionsGranted` としていたため、**権限が無い=まさに要求したい状況でボタンだけが押せない**構造になっていた。→ ボタンは権限なしでも押せるようにして、押下で権限要求フローを起動する形に修正。

### 2. 停止直後の再発信でFGSクラッシュ

`ForegroundServiceDidNotStartInTimeException`。原因:Serviceの `onCreate` で起動したStateFlowコレクタが、**前セッションの残骸 `Stopped` を即座に拾って `startForeground()` より先に `stopSelf()`** していた。 → `status.drop(1)` を入れて「このServiceが開始後に更新された状態」だけを見るように修正。再発信→継続発信とも安定。

## 画面オフ継続

FGS(`connectedDevice`)+ 通知で**画面オフ中も15秒以上発信が継続**することを確認(経過時間が止まらず増加、復帰後もプロセス生存)。

## LINE検知・Webhook — 実機検証済み

本物のHWIDをLINE Official Account Managerで発行し、同梱のNode.js製Webhookサーバー(`linebot/`)で受信を確認:

```
━━━ 📡 ビーコン検知! ━━━
  種別   : enter
  HWID   : 0102030405     (ダミー表記)
  dm     : 0x1122aabbcc
━━━━━━━━━━━━━━━━━━━
```

- `linebot` はNode標準モジュールのみ(依存ゼロ)、`x-line-signature` のHMAC検証、トークン設定でLINEトークへの返信とオウム返し、ポート既定 **65135 (= 0xFE6F)**(`npm start`)
- dmがUTF-8テキストとして復元可能な場合はログに `(= "おはよう")` のように併記。日本語は13バイト上限のため**最大4文字**
