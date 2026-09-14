# Gallery アプリ仕様書

対象バージョン: 2.0.10  
文書バージョン: 2.0.10  
最終更新: 2026-09-14

## 2.0.10で確認した仕様

- ReleaseにはUniversal APKとABI別APKを含める。端末のABIに合うAPKを優先し、Universal APKを全端末共通の手動更新用として利用できる。
- アプリ内インストーラーはAPK URIと`application/vnd.android.package-archive`を指定して起動する。
- ギャラリーの並び順と日付グループは、MediaStoreの`DATE_MODIFIED`を同期して保持した同一の日時値を使用する。撮影日時は使用しない。

## 変更対象ファイル

- `util/AppUpdateManager.kt`: APKインストーラーのMIME type指定。
- `app/build.gradle.kts`: versionCode/versionName更新、Universal APK生成。
- `data/repository/MediaRepository.kt`, `ui/component/GalleryGridView.kt`: 変更日時による同期・表示・グループ化。
