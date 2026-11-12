# 開発ログ03 — 動的dm(テキスト/バッテリー)、将来構想 (2026-11-12)

> **公開時の注意**: 実HWID等は書かない。

## dmの3モード化

Device Message入力を3モードに拡張(FilterChip + 設定永続化):

| モード | 入力 | dm |
|---|---|---|
| HEX | `1122AABBCC` | そのまま |
| テキスト | 日本語/ASCII | UTF-8 HEXに自動変換(「おはよう」→ `E3818A…`、上限13バイト=日本語4文字) |
| バッテリー自動 | (なし) | 例 `🔋85%`(UTF-8 7バイト) |

発信に使う実効dmは `SettingsStore.effectiveDeviceMessage()` に一本化し、UIとFGSのどちらから開始しても同一パスを通るようにした。

### テキストモード発開時のバグ

初回の実装では「保存済みHEX」を発信パスが読むため、**テキストモード保存値ではなく前回のHEXが電波に出た**(UIプレビューと実フレームが食い違う)。`dumpsys` で発見。発信パスを実効dm経由に統一して修正。

## バッテリーdm動的更新

`🔋85%` 形式(dm)を **発信中に60秒ごとに再読み込み**、値が変わったら:

- `AdvertisingSet.setAdvertisingData()` で稼働中の広告を差し替え — **セッション再起動なし**
- ログとUIのフレーム表示を更新

設計ポイント:

- `BluetoothLeAdvertiser.setAdvertisingData(AdvertisingSet, AdvertiseData)` の第1引数は `onAdvertisingSetStarted` で受け取る `AdvertisingSet` インスタンスのため、advertiser側で保持しておく
- 差し替えは変化があった時だけ(無駄なAPIコール回避)
- フレームのバイト数上限は元々のlegacy31バイトチェックから余裕(Bのdmは4バイト最大)

## これからの構想( ESP32にできない体験 )

- **自己検知モード**: `BluetoothLeScanner` で `0xFE6F` をスキャン(BLUTOOTH_SCAN / neverForLocation)、ビーコンを拾ったらバイブ+ログ。**別端末なしで発信の生存確認**が可能に
- **相互検知**: 2台で互いのビーコンを拾うと両方バイブ(LINEサーバー不要の近接シグナル)
- RSSI履歴・検知回数カウンタなど近接の可視化
- 時間帯/ジオフェンスでの発信トリガー

## GitHub Codespaces対応

`.devcontainer/` を追加:

- onCreateでAndroid SDK(platforms;android-36 等)+ライセンス承認、Nodeはfeatureで
- linebotのポート65135はport forwarding、**LINEのWebhookにはport visibilityを「Public」に**する必要あり(認証付きURLはLINEサーバーが取れない)
- 実機への `adb install` はLAN外から不可 → APKをReleases等で渡す

## 追記: HWIDマスク表示(2026-11-12)

発信HWIDは仕様上そのままBLEに乗るため、**画面共有・スクショ・サーバーログ公開時の安全策**としてマスク表示を実装:

- Android側: アプリ内の「HWIDをマスク表示」スイッチ(既定ON)。設定カードのフレームプレビュー・発信状態・ログのHWIDを先頭2桁+末尾2桁のみ表示(`01••••••05`)。**発信フレーム自体は常に本物のHWID**
- linebot側: `MASK_HWID` 環境変数(既定ON)。検知ログと返信メッセージのHWIDを同フォーマットでマスク。0で解除
- Nodeの古いバージョン(12)でも動くよう `??` を使わず `||` で書いた(コンテナ/nodeの差異で詰まないため)
