# [Qiita記事案] Androidスマホを「LINE Simple Beacon発信機」にする — ESP32不要のビーコンアプリを Kotlin/Compose で作った

**対象読者**: LINE Beacon / Messaging API を触ってみたい人、ESP32でビーコンを作ったことがある人、BLE広告 (AdvertisingSet API) の実装事情が知りたい人

> 本記事は開発中リポジトリの `logs/blog.md` に置いてあるドラフトです。数値・HWIDはすべてダミーです(`0102030405` 等)。

## はじめに

LINE Beaconを発信するにはESP32/M5Stackにファームを焼くのが定番でしたが、「スマホがあるならスマホでよくない?」と思ってKotlin + Jetpack Composeでネイティブアプリを作りました。

最終的に実現したのは:

- LINE Simple Beacon (UUID 0xFE6F / FrameType 0x02) を**Bluetooth 4.0レガシー広告**で 152.5ms間隔 発信
- 画面オフ中もForeground Serviceで発信継続
- Device Messageを **HEX / テキスト(日本語OK) / バッテリー残量の自動更新** の3モードで変えられる
- 受信側LINEアプリの検知 → **WebhookでBeaconイベント受信まで実機で確認**

## LINE Simple Beaconの仕様(公式)

[formula](https://github.com/line/line-simple-beacon)のフレーム形式:

```
+-----------+-----------+------------------+--------------+
| FrameType | HWID      | Measured TxPower | Device Msg   |
| 1 byte    | 5 bytes   | 1 byte           | 1..13 bytes  |
| 0x02      | (発行)    | 0x7F             | 任意         |
+-----------+-----------+------------------+--------------+
```

- BLE Advertisingの **Service Data AD(type 0x16)** に、UUID 0xFE6F(リトルエンディアンで `6F FE`)を付けた構造
- dm未使用時は仕様どおり `0x00` を1バイト送る
- HWIDは [LINE Official Account Manager](https://manager.line.biz/beacon/register) で発行(古い記事の「LINE Things開発者トライアル」は終了済みなので注意)

## 実装のポイント

### 1. 広告APIは AdvertisingSet 一択

旧 `BluetoothLeAdvertiser.startAdvertising()` は非推奨なので、モダンな `startAdvertisingSet()` を使いました。間隔の単位が 0.625ms で **244 × 0.625 = ちょうど152.5ms**(LINEのビーコン端末仕様が強く推奨する間隔)に指定できます。

```kotlin
val params = AdvertisingSetParameters.Builder()
    .setLegacyMode(true)    // Bluetooth 4.0互換広告
    .setConnectable(false)  // broadcasterのみ
    .setScannable(true)     // legacy非接続型は必須
    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
    .setInterval(244)       // 244 * 0.625ms = 152.5ms
    .build()
```

※AOSPを読んだ限り、legacy広告で「接続不可+スキャン不可」の組み合わせだけがIllegal引数で、上記はOKです。

### 2. 画面オフ中も発信:Foreground Service(connectedDevice)

Android 12+ のランタイム権限 BLUETOOTH_ADVERTISE / BLUETOOTH_CONNECT、Android 13+ POST_NOTIFICATIONS、Android 14+ はFGS開始時にBLUETOOTH_CONNECT が必要。UIで権限要求→`startForegroundService` の流れで安全に開始します。

### 3. 動的dmは `AdvertisingSet.setAdvertisingData()`

バッテリーdmモードでは、発信中に60秒ごとに残量を読み直し、値が変わったら稼働中の `AdvertisingSet` に対して `setAdvertisingData()` を呼ぶだけで**セッションを張り直さずにcmを差し替えられます**。

```kotlin
// onAdvertisingSetStartedで受け取ったAdvertisingSetを保持しておく
advertisingSet.setAdvertisingData(
    AdvertiseData.Builder()
        .addServiceUuid(ParcelUuid(LINE_UUID))
        .addServiceData(ParcelUuid(LINE_UUID), newFrame)
        .build()
)
```

dmは `🔋85%` のようなUTF-8テキストでもOK(絵文字4バイト+数字+%、最大8バイト)。Webhookの `dm` はHEX(例 `F09F948B383525`)として届き、受信サーバーで復元表示できます(日本語は1文字3バイト、上限13バイトで最大4文字)。

## ハマったところ(実装系)

### arm64 Docker環境でAAPT2が死ぬ

OrbStackのarm64コンテナに対し、AGP同梱のAAPT2はx86_64バイナリなので `Daemon startup failed`。x86_64 glibcとローダーをコンテナに入れれば解消(リポジトリREADMEに手順)。

### 無線ADBで実機とペアリングできない

`adb pair <ip>:<port> <code>` の **portはダイアログを閉じると失効**します。失敗したら画面を開き直す。コンテナ内からはmNSSが見えないのでportは手動入力です。

### `input text` が全角になる問題

日本語IMEの影響で `adb shell input text 0102...` は端末によって全角数字入力されます。テスト用に `run-as` で端末内のSharedPreferencesへ直接書くのが楽:

```bash
adb shell run-as com.example.linebeacon sh -c \
  'cat /data/data/com.example.linebeacon/shared_prefs/beacon_settings.xml'
```

### (致命傷) 停止→再発信でFGSクラッシュ

`ForegroundServiceDidNotStartInTimeException`。Serviceが **開始時に前セッションの残骸 `Stopped` 状態を拾って即座に `stopSelf()`** していました。KotlinのFlowで `drop(1)` を入れて「自分のセッション開始後に更新された状態」だけを見るように修正。

### テキストモードで電波に出るdmが古い

UIプレビューは新dmだが実フレームが前値のまま(発信パスとUIパスで別の保存値を見ていた)。`SettingsStore.effectiveDeviceMessage()` に実効dmを一本化し、UI/FGSどちらから開始しても同一パスに。

## 検証結果(dumpsys)

実際に電波に乗っているかは `adb shell dumpsys bluetooth_manager` で確認できます:

```
Interval(0.625ms) : 244            ← 152.5ms(仕様推奨)
TX POWER(dbm)     : -7
Legacy            : true
Connectable       : false / Scannable : true
Service Data      : [0000fe6f-..., 12]
```

受信側はLINEアプリの検知/友だち追加が要りますが、**パケットの見え方だけなら nRF Connect** で `Service Data: 6FFE 02 <HWID> 7F <dm>` を確認できます。LINE検知もWebHook受信も同梱のNode.jsサーバー(`linebot/`、依存ゼロ・署名検証・オウム返し返信)で確認しました。

## これから

- 自己検知(スマホが自分のビーコンを拾ったら振動)→ 別端末なしの生存確認
- 2台の相互検知(LINEサーバー不要の近接シグナル)
- 時間帯・ジオフェンスでの発信トリガー

## まとめ

- スマホはビーコン発信機として完全に実用(152.5ms・画面オフ継続もOK)
- dmは実行中に何でも変えられる — ここがESP32との大きな差分
- LINEの検知・Webhookは**公式アカウントでのみ確認可能**、ビーコン機能は日本・台湾・タイ限定

ソース: [GitHub(公開準備中)](https://github.com/...) / 公式仕様:
- [line-simple-beacon](https://github.com/line/line-simple-beacon)
- [LINEビーコン端末仕様](https://developers.line.biz/ja/docs/messaging-api/beacon-device-spec/)
- [LINEでビリコンを使う](https://developers.line.biz/ja/docs/messaging-api/using-beacons/)
