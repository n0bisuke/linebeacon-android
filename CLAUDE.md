# linebeacon-android

Android (Kotlin / Gradle) アプリ。Claude Code は Docker コンテナ内で動いている。

- Gradle プロジェクトルートは `android-app/`(コンテナ内 `/workspace/android-app`)。ビルドは `./gradlew` を使う。
- コンテナの環境(入っているツール・SDK・できないこと)は下記を参照。環境定義はこのリポジトリには無く、共通リポジトリ `shared-dev-environments/android/` で管理している。

@/opt/dev-env/ENVIRONMENT.md
