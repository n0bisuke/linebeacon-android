# Claude Code 作業ルール(LINE Beacon Transmitter)

## 目的と優先順位

このリポジトリは、AndroidスマートフォンをLINE Simple Beaconの発信機として使うネイティブアプリ「LINE Beacon Transmitter」を開発します。まず実機で確実にBLE広告を発信できる小さなMVPを完成させてください。

作業開始時に `compose.yaml`(親ディレクトリ)、Gradle設定、既存コードを読んでください。既存の構成・命名・UIを尊重し、確認せずにプロジェクトを作り直したり、ビルド環境をホストへ移したりしないでください。依頼された変更に必要な範囲だけ編集します。

## MVPの範囲

- Kotlin + Jetpack Compose。minSdk 26 / Android 8.0以降(targetSdk 34)。
- HWID(10桁HEX)とDevice Message(最大13バイトHEX、空欄可)の入力と検証。
- BLE Advertisingの開始・停止、対応状況・Bluetooth ON/OFF・発信状態・エラーの表示。
- 入力した設定の端末内保存(SharedPreferences、外部送信しない)。
- 画面を閉じた後も発信を続けるためのForeground Service(connectedDevice)。バックグラウンドのOS制約で保証できない動作は未確認として明記する。
- Secure message(MAC/タイムスタンプ/バッテリー)を持つ正式LINE Beacon端末の完全再現は対象外。Simple Beacon仕様のみ実装する。iBeaconなど別形式への置き換えはしない。

## LINE Simple Beacon仕様とBLEパケットの扱い

- 実装は公式資料を正とする: https://github.com/line/line-simple-beacon 、 https://developers.line.biz/ja/docs/messaging-api/beacon-device-spec/ 。推測でプロトコルを実装しない。
- Service Data AD(type 0x16)は UUID 0xFE6F をリトルエンディアン(0x6F 0xFE)で置き、フレームは `[FrameType 0x02][HWID 5バイト][MeasuredTxPower 0x7F][Device Message 1..13バイト]`。Device Message未使用時は 0x00 を1バイト送る。マルチバイトはビッグエンディアン。
- レガシー広告(Bluetooth 4.0互換)を使い、広告間隔は端末仕様の推奨152.5ms(AdvertisingSetParametersで244×0.625ms)を目安にする。広告サイズ上限(31バイト)超過は起動前に検証して拒否する。
- BLE Advertising非対応・Bluetooth無効・権限不足・開始失敗はクラッシュではなく状態表示とログで扱う。

## 公開Gitリポジトリの確認

- このリポジトリは公開GitHubリポジトリとして扱う。変更の報告やコミット前には `git status` と `git ls-files`(該当リポジトリがgit管理下にある場合)を確認する。
- HWIDはLINE公式アカウントに発行・紐付けられる識別子である。実際のHWIDをコード・README・ログにハードコードしてコミットしない。ドキュメントの例はダミー値(`0000000000`等)を使う。
- `.env`、チャネルアクセストークン、署名鍵をコード・ドキュメント・会話ログへ書かない。チャネルアクセストークン等のMessaging API秘密情報(Androidアプリに埋め込む)はそもそも実装しない。
- ADBログ、bugreport、端末スクリーンショット、nRF Connectの実測キャプチャには端末固有情報が含まれ得るため、原則コミットしない。作業用ファイルを置く場合は `.gitignore` で隔離し、追跡状態を `git ls-files` で別途確認する。

## 開発環境の制約

- Android CLI、Android SDK、Gradle、Claude Codeは既存のDocker/OrbStackコンテナ内で実行する(親ディレクトリの `compose.yaml`、サービス名 `android`、共通イメージ `android-kotlin:latest`)。
- 新しいDocker環境を作らない。SDK・Gradleキャッシュをホストへbind mountしない(既存のnamed volume `android-gradle-cache` 等を使う)。
- Android Studioの導入やホスト側Gradle/SDKによるビルドへ切り替えない。ADBはホスト(またはコンテナのadb)から実機へ接続してよい。
- ビルド成果物のAPKは `android-app/dist/app-debug.apk` に配置して報告する(実体は `app/build/outputs/apk/debug/`。`dist` に置くのは共有用のコピーであり、破壊的な移動をしない)。

## 変更と検証

1. 変更前に関連する既存ファイルと権限設定(AndroidManifestの権限、FGS種別)を確認する。
2. 小さい単位で実装し、既存のCompose/Gradle/パッケージ構成に合わせる。BLE制御(`beacon`パッケージ)とUI(`MainActivity`等)を分離し、Device Messageを将来センサー値等から動的に変えられる設計を保つ。
3. Android SDK/Gradleがあるコンテナ内でビルドし、`app/src/test` のユニットテスト(フレームバイト列生成、HEX変換、異常入力拒否)を実行する。
4. BLE広告が正しいバイト列になるかは実機+ nRF Connect等でService UUID/Dataを確認して初めて確認済みとする。ビルド成功とLINE検知・Webhook受信成功を混同しない。
5. LINEアプリ検知やWebhook受信など実機検証できない項目は、未確認と明記して報告する。APKの場所と確認手順を示す。
6. 作業後は変更ファイル、ビルド/検証結果、既知のAndroid API制約(Android 14のFGS権限、Android 12のBLUETOOTH_ADVERTISE実行時権限など)を簡潔に報告する。

ユーザーが依頼していない依存追加、ネットワーク送信、データ削除、Docker volumeの削除は行わない。既存ファイルを消したり移動したりする前に、その必要性を説明してユーザーに確認する。
