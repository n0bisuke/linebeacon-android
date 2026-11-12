# 開発ログ01 — セットアップと仕様確認 (2026-11-12)

> **公開時の注意**: 本ログのHWID・IPアドレス等はすべてダミー値。実機のHWID類は書かない。

## ゴール

Androidスマホ単体で LINE Simple Beacon のBLE広告を発信し、ESP32/M5Stackビーコンビーコン端末を置き換える。最終的に別のスマホのLINEアプリが検知し、Messaging APIのWebhookにイベントが届くところまで。

## 方針

- 既存のDocker開発環境(`compose.yaml`、イメージ `android-kotlin:latest`)を尊重 — 新しい環境を作らない
- 仕様を**公式ドキュメントで確認**してから実装する(推測でプロトコルを作らない)
- LINEのトークン類はアプリに**埋め込まない**
- BLEロジックとUI分離、dmは後から動的に変えられる設計にする

## 仕様の確認結果(公式)

[official spec](https://github.com/line/line-simple-beacon) より:

| 項目 | 値 |
|---|---|
| Service UUID | 0xFE6F(AD構造内はリトルエンディアン `6F FE`) |
| Service Data AD type | 0x16 |
| フレーム | `FrameType(0x02) + HWID(5B) + MeasuredTxPower(0x7F) + Device Message(1..13B)` |
| フレーム長 | 8〜20バイト |
| dm未使用時 | `0x00` を1バイト |
| 推奨広告間隔 | 152.5ms(ビーコン端末仕様) |

HWIDの発行は `manager.line.biz/beacon/register`(LINE Official Account Manager、公式READMEに記載)。古い記事にある「LINE Things開発者トライアル」は終了済みサービスなので注意。

## Docker環境(OrbStack arm64)でのビルド問題

AGPが同梱する **AAPT2はx86_64バイナリ**で、arm64コンテナだと `Daemon startup failed` で死ぬ。OrbStackのマルチアーチ手順に従ってx86_64 glibc/ローダーを入れた:

```bash
apt-get update && apt-get install -y libc6-amd64-cross libgcc-s1-amd64-cross
mkdir -p /lib64 /lib/x86_64-linux-gnu /usr/lib/x86_64-linux-gnu
ln -sf /usr/x86_64-linux-gnu/lib/ld-linux-x86-64.so.2 /lib64/ld-linux-x86-64.so.2
for lib in /usr/x86_64-linux-gnu/lib/*.so*; do ln -sf "$lib" /lib/x86_64-linux-gnu/; done
```

※ `docker compose run` ごとのエフェメラルなのでコンテナを再作成するたびに再適用が必要。READMEに記載済み。恒久対応はイメージ側へ。

## 技術選定メモ

- 広告APIは **`BluetoothLeAdvertiser.startAdvertisingSet`**(API 26+)。旧 `startAdvertising` は非推奨。AOSP検証の結果、legacy広告の違法値は `connectable && !scannable` の組み合わせのみで、`connectable=false, scannable=true` は合法
- 間隔は `AdvertisingSetParameters` の単位0.625ms × **244 = ちょうど152.5ms**
- FGSタイプは `connectedDevice`(Android 14+では開始時にBLUETOOTH_CONNECTが要る)。locationは不要(`neverForLocation`相当の広告単独用途)
