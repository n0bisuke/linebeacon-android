# このフォルダのドキュメント

やろうとしていること別の入口:

| やりたいこと | 見るドキュメント |
|---|---|
| **GitHub Codespacesですべて試す**(ビルド・APK・Webhook・検知) | [CODESPACES.md](CODESPACES.md) ← 1本道の手順がこの順で書いてある |
| **obnizを発信機にしてLINE Simple Beaconを試す** | [OBNIZ.md](OBNIZ.md) |
| **ローカルマシンで動かす**(Dockerなし/あり/devcontainer) | [LOCAL-CONTAINER.md](LOCAL-CONTAINER.md) |
| **linebot(obniz含む)の使い方・設定一覧** | [../linebot/README.md](../linebot/README.md) |
| **アプリ自体の仕様・ビルド方法** | [../android-app/README.md](../android-app/README.md) |
| **開発ログ/体験記事** | [../logs/](../logs/) |

## リポジトリの全体像

```
linebeacon-android/
├── android-app/        Androidアプリ(Kotlin/Compose) — スマホをLINE Simple Beacon発信機化
├── linebot/            Node.js のWebhookサーバー(server.js)+ obniz発信機(obniz-server.js)
├── docs/               この手順書群
├── logs/               開発ログ・記事
└── .devcontainer/      Codespaces設定(Android SDK導入スクリプト含む)
```

ビーコンが「発信 → 検知」まで動くまでの最短ルートは **CODESPACES.md** を参照。ローカルでやるならLOCAL-CONTAINER.md。
