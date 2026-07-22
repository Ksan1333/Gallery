# Gallery

Gallery は、端末内の画像・GIF・動画・ZIP/PDF の本・X 由来メディアを、閲覧、整理、AI 分析、制作資料収集までまとめて扱う Android アプリです。

## できること

| 機能 | 概要 | 詳細設計 |
| --- | --- | --- |
| メディア一覧 | MediaStore の画像・GIF・動画を 2 / 3 / 4 / 7 / 28 列で表示。検索、フィルタ、並び替え、範囲選択に対応。 | [一覧](docs/detail_design/01_media_gallery.md) |
| メディアビューア | 画像・GIF・動画の全画面表示、ズーム、再生、フレーム保存、壁紙、タグ、削除を提供。 | [ビューア](docs/detail_design/02_media_viewer.md) |
| AI 分析 | タグ、年齢区分、特徴ベクトルをバックグラウンド分析し、進捗とキャンセルを表示。 | [AI 分析](docs/detail_design/03_ai_analysis.md) |
| 整理 | フォルダ、フォルダグループ、ゴミ箱、一括編集、一括移動を提供。 | [整理](docs/detail_design/04_folder_trash_bulk.md) |
| X ダウンロード | 共有・URL から画像、動画、GIF を候補解決し、MediaStore と履歴へ保存。 | [X ダウンロード](docs/detail_design/05_x_downloader.md) |
| 本／資料 | ZIP/PDF ビューアと、お絵描き資料プロジェクトを提供。 | [本](docs/detail_design/06_book_viewer.md) / [資料](docs/detail_design/07_reference_projects.md) |
| 関連メディア | ビューア内で画像の視覚類似候補とランダム候補を表示。 | [関連メディア](docs/detail_design/08_related_media.md) |
| お気に入り・設定 | 作家・サイト、テーマ、操作設定、JSON バックアップ、アプリ更新確認を管理。 | [お気に入り](docs/detail_design/09_favorite_creators_sites.md) / [設定](docs/detail_design/11_settings_backup_update.md) |

## 設計・試験ドキュメント

- [更新履歴](CHANGELOG.md)
- [基本設計書](基本設計書.md)
- [画面遷移図](docs/画面遷移図.md)
- [共通 UI 仕様](docs/common_ui_spec.md)
- [リグレッションテスト計画](docs/test/regression_test_plan.md)
- [リグレッションテスト項目 TSV](docs/test/regression_test_cases.tsv)
- [未実装の自動テスト一覧](docs/test/automated_test_backlog.md)

## 主なデータ

Room DB は `gallery_database` version `19` です。主なテーブルは `media_metadata`、`media_tags`、`managed_folders`、`folder_order`、`video_downloads`、`reference_projects`、`reference_items` です。

## 開発メモ

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```
