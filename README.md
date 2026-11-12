# linebeacon-android

Android (Kotlin / Gradle) 開発プロジェクト。Dockerイメージは共通リポジトリ `shared-dev-environments` で管理しています。

## 前提
- `shared-dev-environments` を `../../shared-dev-environments` に配置する(別の場所なら `SHARED_DEV_ENV_DIR` に絶対パスを指定)。
- 共通イメージ `android-kotlin:latest` をビルド済みにする(`docker image ls android-kotlin` で確認。`pull access denied` と出たらこれが未実施):
  `cd shared-dev-environments && npm run build:android`
- 使うDockerのコンテキストでビルドすること(`docker context ls` の `*` 付きが現在のもの。OrbStack/Docker Desktopでイメージは別)

## 使い方
```bash
# shared-dev-environments から移動する場合
cd ../5_android-workspaces/linebeacon-android
docker compose run --rm android      # コンテナ内では最初から /workspace/android-app にいる
```

```text
linebeacon-android/                ← docker compose はここ(compose.yaml がある場所)で実行
├── compose.yaml
├── .env / .env.sample
└── android-app/       ← 生成時に作成(名前固定)。Gradle/Android Studio のプロジェクトルート
                          コンテナ内の /workspace/android-app にマウントされる
```
Android Studio でプロジェクトを新規作成するときは、保存先を `android-app/` にする。
Claude Code は初回に `claude` でログイン(Claudeを選んだ場合。全プロジェクトで共有)。

## Volume
- 共通: `android-gradle-cache`, `android-claude-config`
- 専用: `linebeacon-android_project-gradle`, `linebeacon-android_root-build`, `linebeacon-android_app-build`
