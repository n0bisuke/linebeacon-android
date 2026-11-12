# LINE Beacon Transmitter

Androidスマートフォン単体でLINE Simple BeaconのBLE広告を発信するアプリ。ESP32 / M5Stack等の専用ハードウェアなしで、発信したビーコンを別のスマートフォンのLINEアプリが検知し、LINE Messaging APIのWebhookにBeaconイベントを届けることを目的とする。

## アプリ概要

- Kotlin + Jetpack Compose のネイティブアプリ
- LINE Simple Beacon仕様のBLE広告を発信:
  - Service UUID **0xFE6F**(リトルエンディアンで `0x6F 0xFE`)
  - Service Data AD (type `0x16`): `0x6F 0xFE` + フレーム `[FrameType 0x02][HWID 5バイト][Measured TxPower 0x7F][Device Message 1..13バイト]`
  - Device Message未使用時は仕様どおり `0x00` を1バイト送信
  - 広告間隔: 152.5ms(LINEのビーコン端末仕様で強く推奨される間隔。`AdvertisingSetParameters` の 244 × 0.625ms)
  - レガシー広告(Bluetooth 4.0互換)のみを使用。iBeacon等の別形式には置き換えない
- 画面を閉じても発信を続けるため Foreground Service(`connectedDevice`)で広告を維持
- HWID / Device Message を端末内(SharedPreferences)に保存。外部送信は一切しない
- **HWIDマスク表示**(既定ON): 画面・ログのHWIDを `01••••••05` のように先頭2桁と末尾2桁だけ表示。発信されるフレーム自体には常に本物のHWIDが入る(LINEの仕様上不可欠)。スクショ公開時の安全策

対応仕様・参考: [line/line-simple-beacon](https://github.com/line/line-simple-beacon)、[ビーコン端末仕様](https://developers.line.biz/ja/docs/messaging-api/beacon-device-spec/)、[LINEでビーコンを使う](https://developers.line.biz/ja/docs/messaging-api/using-beacons/)

> Secure message(MAC/マスクタイムスタンプ/バッテリー)を持つ正式LINE Beacon端末の完全再現は対象外。Simple Beacon仕様のみ実装している(端末なりすましは本仕様では防げない点も公式どおり)。

## 対応Androidバージョン

- Android 8.0(API 26)〜 / targetSdk 34
- Android 12(API 31)以降は `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` の実行時権限が必要
- Android 13(API 33)以降は通知権限 `POST_NOTIFICATIONS` を要求(未許可でも発信は継続可)

## 必要な権限

| 権限 | 用途 |
|---|---|
| `BLUETOOTH_ADVERTISE` (API 31+) | BLE広告の発信 |
| `BLUETOOTH_CONNECT` (API 31+) | アダプタ状態取得 / FGSタイプ `connectedDevice` の開始要件(Android 14+) |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (〜API 30) | 上記の旧権限 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE` | 画面オフ中の発信継続 |
| `POST_NOTIFICATIONS` (API 33+) | 発信中の通知表示 |

HWIDの取得は不要な権限なし。`ACCESS_FINE_LOCATION` はスキャン時にのみ必要なため要求していない(広告単独では不要)。

## HWIDの取得方法

1. [LINE Official Account Manager](https://manager.line.biz/) でLINE公式アカウントを開く
2. 「ビーコン」登録ページ(<https://manager.line.biz/beacon/register>)で「LINE Simple Beacon」のハードウェアIDを発行する
3. 発行された10桁のHEX(例: `32AF519E88` ※ダミー値)をアプリのHWID入力欄へ入力する

## ビルド方法

このワークスペースは既存のDocker開発環境(`compose.yaml`、イメージ `android-kotlin:latest`)上でビルドする。

```bash
# /workspace (linebeacon-android) で実行
docker compose run --rm android   # コンテナ内は /workspace/android-app にいる

cd /workspace/android-app
./gradlew assembleDebug           # Debug APK生成
./gradlew test                    # 単体テスト(フレーム生成・HEX変換・入力検証)
```

### GitHub Codespacesでビルドする場合

リポジトリルートの `.devcontainer/` により、Codespaceを開くとAndroid SDK(licenses承認済み)とNode.jsがセットアップ済みになります:

```bash
cd android-app                    # gradleプロジェクトルート
./gradlew assembleDebug           # ANDROID_HOMEはdevcontainerで設定済み
./gradlew test
```

- **ビルド**: 上記のとおりOK。本家イメージはx86_64のためOrbStack arm64特有のAAPT2問題もなし
- **実機 `adb install`**: Codespaceはクラウド上にありLAN外のため、端末の無線ADB(プライベートIP)には**直接届かない**。APKをファイル転送(Releases等)して端末側で直接インストールするか、LAN側マシンをTCPトンネルで中継させる
- **linebot**: むしろCodespaces向き。Portパネルで **65135 を「Public」に変更**(認証付きURLはLINEのWebhook取得に失敗する)し、`https://<codespace名>-65135.app.github.dev/webhook` をWebhook URLに設定。Channel secret等はCodespacesのSecrets(`LINE_CHANNEL_SECRET` / `LINE_CHANNEL_ACCESS_TOKEN`)に登録すれば `cd linebot && npm start` で.env不要

APK出力先: `app/build/outputs/apk/debug/app-debug.apk`(共有用コピーとして `dist/app-debug.apk` にも配置)

> **OrbStack + arm64 環境の注意**: 共通イメージ `android-kotlin:latest` はarm64コンテナだが、AGPが同梱するAAPT2はx86_64バイナリのため、`AAPT2 ... Daemon startup failed` でビルドが失敗することがある。対処はOrbStackの[マルチアーチ手順](https://orb.cx/multiarch)に従い、実行するコンテナ内でx86_64向けglibc/ローダーを入れる(Ubuntuの場合):
>
> ```bash
> apt-get update && apt-get install -y libc6-amd64-cross libgcc-s1-amd64-cross
> mkdir -p /lib64 /lib/x86_64-linux-gnu /usr/lib/x86_64-linux-gnu
> ln -sf /usr/x86_64-linux-gnu/lib/ld-linux-x86-64.so.2 /lib64/ld-linux-x86-64.so.2
> for lib in /usr/x86_64-linux-gnu/lib/*.so*; do ln -sf "$lib" /lib/x86_64-linux-gnu/; done
> ```
>
> ※コンテナは `docker compose run` ごとに再作成されるため、この対処は毎回必要(恒久対応にはイメージ側への追加が推奨)。

## APKのインストール方法

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# ホストから接続している場合(コンテナのadbに実機が見えないとき)はホストで実行
```

またはAPKを端末へ転送してファイルマネージャからインストール(提供元不明アプリの許可が必要)。

## 操作方法

1. アプリ起動後、HWID(10桁HEX)とDevice Message(最大26桁HEX、空欄= `0x00`)を入力。入力は自動保存される
2. 「対応状況」カードで Bluetoothオン / BLE Advertising対応 / 権限 を確認
3. 「発信開始」で権限要求→Foreground service通知とともに広告開始
4. 「発信停止」または通知上の「停止」で終了
5. 「発信状態」カードに間隔・TxPower・送出中フレームHEX、「ログ」に操作履歴とエラーを表示

## 試す手順(クイックスタート)

インストール後、次の3段階で動作を確認できる。詳細な手順は **[docs/TESTING.md](docs/TESTING.md)** を参照。

1. **BLEパケット確認(nRF Connect)** — HWID登録不要。ダミーHWIDでもOK。Service Data `6FFE 02 <HWID> 7F <dm>` が見えればフレーム生成は仕様どおり
2. **LINEアプリでの検知** — 本物のHWID(LINE Official Account Managerで発行)を入力して発信し、受信側端末で「LINE Beacon有効化+友だち追加」して近づける
3. **Webhook配信確認(Messaging API)** — Webhook URLを設定し、`type: "beacon"` イベントの受信を確認([webhook.site](https://webhook.site)を使うと手軽)

※LINE Beaconは日本・台湾・タイでのみ動作。検知側は発信側と別端末を使用すること

## 動作上の制約

- **Simple Beaconの仕様上、なりすまし防止機構がない**(公式仕様どおり)。第三者が同一HWIDを発信できるため、Bot側でHWIDを過信した処理をしないこと
- AndroidのBLE広告は**OSの電力制約・Bluetoothスタック実装に依存**する。スクリーンオフ後の発信継続はForeground Service + `connectedDevice` で担保するが、一部メーカーのバッテリー最適化(省電力アプリ)で広告が止まる場合がある。動作しない端末での継続性は**未検証**
- この端末自身でLINEを同時起動している場合、自端末発信の検知挙動は保証しない(別端末での検知を想定)
- `isMultipleAdvertisementSupported() == false` の端末でも単一広告は多くの場合発信可能。対応可否は「対応状況」カードとエラーログで確認すること
- Android 14以降はFGS起動時に `BLUETOOTH_CONNECT` 権限が必要(アプリは起動前に権限を要求する)
- Device MessageはWebhookの `dm` フィールドにHEX文字列として届く。空欄時は `0x00` を送出(仕様: 使用しない場合は `0x00` を設定)

## 開発者向けメモ

- BLE制御は `com.example.linebeacon.beacon`(`BeaconAdvertiser` / `SimpleBeaconFrame` / `Hex`)に分離し、UI (`MainActivity`) から触らない
- Device Messageを将来センサー値等から動的に変えたい場合は、`BeaconConfig.frame` の生成差し替え(または `BeaconAdvertiser.start(frame)` に直接 ByteArray を渡す)で対応可能
- テスト: `./gradlew test` — 仕様例(HWID `32AF519E88` + 13バイトdm)を含むフレーム生成、HEX変換、異常入力拒否をカバー

## License

[MIT](../LICENSE)
