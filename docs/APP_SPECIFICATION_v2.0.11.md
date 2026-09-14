# Gallery アプリ仕様書

対象バージョン: 2.0.11  
文書バージョン: 2.0.11  
最終更新: 2026-09-14

## 2.0.11で確認した仕様

- 選択モード中は通常グリッドのサムネイル左下にビューアーボタンを表示する。
- ビューアーボタンからメディアを開いても、ギャラリー側の選択状態は変更しない。
- AVIF/HEIFはCoil 2互換のデコーダーで読み込み、API 24以降を対象とする。端末やファイルの破損状態によっては表示できない場合がある。
- グリッド項目の再配置アニメーションを無効化し、列数変更や再読込時の意図しない位置移動を抑制する。

## 変更対象ファイル

- `ui/component/GalleryGridView.kt`: 選択中ビューアーボタン、AVIFデコーダー用グリッドローダー、再配置アニメーション抑制。
- `GalleryApplication.kt`: アプリ共通ImageLoaderへのAVIF/HEIFデコーダー追加。
- `gradle/libs.versions.toml`, `settings.gradle.kts`: Coil 2互換AVIFライブラリとJitPackリポジトリ。
- `app/build.gradle.kts`: versionCode/versionName更新、compileSdk 36。
