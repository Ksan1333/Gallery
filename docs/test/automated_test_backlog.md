# 未実装の自動テスト一覧

対象バージョン: 1.4.0
この表は、現在の `app/src/test` / `app/src/androidTest` にまだ追加されていない試験を、実装順に管理する。今回 `GalleryScrollbarGeometryTest` に追加したレール座標の境界計算は対象外とする。

| ID | 優先度 | 種別 | 対象 | 検証内容 |
| --- | --- | --- | --- | --- |
| AUT-GRID-002 | P0 | Compose instrumented | `GalleryGridView` | 高さの異なる複数レーンでバーを最下端へドラッグし、`canScrollForward=false` とレール下端／コンテンツ下端の一致を確認する。 |
| AUT-GRID-003 | P1 | Compose instrumented | `GalleryGridView` | 2 / 3 / 4 / 7 列、各 system inset でレールの上端・下端が content padding と一致することを確認する。 |
| AUT-XDL-001 | P0 | JUnit | X JSON 候補抽出 | 同じ `mediaKey` に動画と静止画サムネイルがある fixture から、動画品質だけが選択候補になることを確認する。 |
| AUT-XDL-002 | P0 | instrumented | MediaStore 保存 | 画像、MP4、直接 GIF、変換 GIF の `DATE_TAKEN`、`DATE_ADDED`、`DATE_MODIFIED` がダウンロード開始時刻に設定されることを確認する。 |
| AUT-XDL-003 | P1 | JUnit | URL 解決フォールバック | fxtwitter / vxtwitter / fixupx の JSON 形状差、失敗、m3u8 除外を fixture で確認する。 |
| AUT-VIEW-001 | P1 | Compose instrumented | 動画／メディアビューアの 3 点メニュー | MP4 では GIF 変換が enabled、静止画と GIF では disabled、デフォルトのボトムバーには存在しないことを semantics で確認する。 |
| AUT-RELATED-001 | P1 | JUnit | `findSimilarVisualMedia()` | ベクトルなし、閾値境界、自己 URI 除外、最大 25 件、スコア順を fixture DB で確認する。 |
| AUT-BACKUP-001 | P1 | JUnit | `GalleryBackupManager` | 全設定と作家・サイトの JSON を export/import し、Boolean/Integer/Float/String と重複回避を確認する。 |
| AUT-UPDATE-001 | P1 | JUnit | `AppUpdateManager` | tag の正規化、現在版との比較、通知済み版の抑止、APK asset 不在時の扱いを fixture JSON で確認する。 |
| AUT-REF-001 | P1 | instrumented | 参照資料完了 | 完了処理が一時資料だけを整理し、ギャラリー本体の URI を削除しないことを確認する。 |
| AUT-BOOK-001 | P2 | instrumented | ZIP/PDF 本 | ページ取得、しおり保存・復元、見開き／方向設定を小さな fixture で確認する。 |
| AUT-TRASH-001 | P0 | instrumented | ゴミ箱 | ゴミ箱移動、復元、完全削除で MediaStore と Room の状態が一貫することを確認する。 |
| AUT-PERM-001 | P2 | instrumented | 権限分岐 | API 28、29–32、33 以降の画像／動画権限と通知権限の要求分岐を確認する。 |

## 導入順

1. `AUT-GRID-002`、`AUT-XDL-001`、`AUT-XDL-002`、`AUT-TRASH-001`
2. `AUT-VIEW-001`、`AUT-BACKUP-001`、`AUT-UPDATE-001`
3. 残りの端末依存・WebView・本ビューア試験
