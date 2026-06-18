# Gallery アプリ基本設計書

## 1. システム概要

本アプリは、Android端末内の画像、GIF、動画、漫画ファイルを取得し、メタデータ、タグ、AI分析結果、視聴計測、削除状態をローカルDBに保存しながら高速に閲覧・管理するギャラリーアプリである。

UI は Jetpack Compose、状態管理は `GalleryState`、データアクセスは `MediaRepository` と Room、メディア取得は ContentResolver / MediaStore、画像表示は Coil、動画再生は Media3 ExoPlayer、AI分析は ONNX Runtime と MediaPipe Vision を使用する。

## 2. 全体構成

| レイヤ | 主な責務 | 主な構成要素 |
| --- | --- | --- |
| Presentation | 画面表示、ユーザー操作、ナビゲーション | `MainActivity`, 各 `Screen`, 各 `Component` |
| State | UI状態、フィルタ、選択、ビューア戻り位置の保持 | `GalleryState` |
| Repository | MediaStore取得、Room操作、ファイル操作、推薦検索 | `MediaRepository`, `BookRepository` |
| Local DB | メタデータ、タグ、フォルダ、履歴、計測情報の永続化 | Room, `MediaDao`, Entity |
| Service | 長時間処理、進捗、バックグラウンド処理 | `AnalysisService`, `ThumbnailGenerationService`, `GlobalOperationService` |
| AI | タグ推定、年齢制限推定、画像ベクトル生成 | `AiTaggingService`, `VectorSearchService`, `ModelDownloader` |
| Utility | サムネイル、タグ翻訳、モデル取得 | `ThumbnailUtils`, `TagTranslationService` |

## 3. 画面構成

### 3.1. メインアクティビティ

`MainActivity` はアプリ全体のナビゲーション、権限要求、ドロワー、ボトムナビ、グローバル進捗を管理する。

主なルートは以下の通り。

| ルート | 画面 |
| --- | --- |
| `home` | 全メディア一覧 |
| `folders` | フォルダ一覧 |
| `folders_select` | フォルダ選択 |
| `mylist` | マイリスト |
| `mylist/{tagName}` | タグまたはカテゴリ別一覧 |
| `analysis/{type}/{periodDays}` | AI分析進捗 |
| `mass_edit` | 一括編集 |
| `books` | 漫画一覧・漫画ビューア |
| `trash` | ゴミ箱 |
| `video_downloader` | X / Twitter ダウンロード |
| `recommendations` | おすすめ一覧 |

共有インテントとVIEWインテントで X / Twitter URL を受け取り、動画ダウンロード画面へ誘導する。

### 3.2. ホームギャラリー

全メディアを対象としたメイン一覧画面である。

- 起動時にメディアスキャンを行う
- Paging Flow からグリッドデータを取得する
- フィルタ、ソート、グルーピング、ズーム状態を `GalleryState` で管理する
- ビューアを開くときの対象リストと開始位置を保持する
- ビューアから戻ったときに最後に表示したメディア位置へ復帰する
- レコメンド起点の場合は対象メディアを中央寄せで表示する
- 一括編集、フォルダ移動、削除、タグ編集へ接続する

### 3.3. GalleryGridView

メディア一覧の中核コンポーネントである。

- `LazyVerticalGrid` で表示する
- `LazyPagingItems<GridItem>` と通常リストの両方に対応する
- 表示列は 1、3、4、7、28
- 28列は年単位表示として扱い、各年最大6件に制限する
- 28列では `AsyncImage` を生成せず、メディア種別に応じた軽量セルを表示する
- 7列では月単位、1/3/4列では日単位を基本にする
- メディア種別、年齢制限、端末向けアスペクト比で絞り込む
- 日付、サイズ、名前でソートする
- メタデータ Flow は debounce して過剰な再計算を抑える
- フィルタ、ソート、グリッドアイテム生成は `Dispatchers.Default` で行う
- 高速スクロールバーを提供する
- スクロール時にトップバーを隠す
- 長押しドラッグによる範囲選択と範囲解除を扱う
- 選択モードではお気に入り、削除、一括編集、フォルダサムネイル設定を表示する

### 3.4. メディアビューア

画像、GIF、動画を共通のビューアで扱う。

- `HorizontalPager` で前後メディア移動を行う
- タップでUI表示を切り替える
- ダブルタップでズームを切り替える
- ピンチでズーム、拡大中はパンする
- 下スワイプで閉じる
- 上スワイプでレコメンドタブを開く
- タグ、AI判定、ファイル情報を表示する
- お気に入り、タグ編集、削除、復元、完全削除を実行する
- 現在ページを戻り位置として `GalleryState.lastViewedUri` に反映する
- 画像やフレームを壁紙に設定する
- 動画・GIFの現在フレームをスクリーンショット保存する
- フォルダサムネイル設定に対応する

### 3.5. 動画ビューア

動画は Media3 ExoPlayer で再生する。

- 再生、一時停止、ミュート
- 送り戻し間隔の選択
- 戻し・送りボタン
- シークバー
- フレーム単位パッド
- 下部操作領域の左右スワイプによるコマ送り
- 再生位置からフレーム抽出

### 3.6. GIFビューア

GIF は Coil とフレーム抽出処理を組み合わせて扱う。

- 通常GIF再生
- フレームステップUIの表示切り替え
- MediaMetadataRetriever または `Movie` によるフレーム抽出
- 現在フレーム保存
- 現在フレームの壁紙設定

### 3.7. マイリスト

メディアを管理観点別に分類する画面である。

- お気に入り
- 未整理
- AI未分析
- ベクトル未分析
- タグ別カテゴリ
- カテゴリ件数と代表サムネイル
- カテゴリ並び替え
- カテゴリ選択モード
- AI分析期間選択
- AI分析見積もり時間表示

カテゴリ選択後は `CategoryScreen` を使用して対象メディアのグリッド表示、選択、ビューア連携を行う。

### 3.8. フォルダ画面

フォルダ単位の整理画面である。

- 端末内フォルダのスキャン
- 管理対象フォルダの表示
- フォルダ追加
- DCIM 配下への新規フォルダ作成
- フォルダ並び替え
- カスタムサムネイル設定
- サムネイルリセット
- フォルダ内メディア表示
- フォルダ選択画面から一括移動先指定

### 3.9. ゴミ箱

アプリ内削除状態を管理する画面である。

- `isDeleted` が true のメディアを表示する
- 削除日時順に並べる
- 復元する
- 完全削除する
- 複数選択による復元・完全削除を行う
- ゴミ箱ビューアでは通常削除操作を復元・完全削除操作に置き換える

### 3.10. 漫画画面

ZIP / PDF の漫画を扱う。

- 端末ストレージから `.zip`, `.pdf` をスキャンする
- 隠しフォルダや Android 配下を除外する
- フォルダ別に漫画を一覧する
- ZIP 内の画像枚数をページ数として扱う
- PDF のページ数を取得する
- 先頭ページをサムネイルとして生成する
- 漫画ビューアで単ページ、見開き表示を切り替える
- ページスライダー、プレビュー、スクリーンショット保存に対応する
- Android 11 以降では全ファイルアクセス権限を要求する

### 3.11. X / Twitter ダウンロード画面

X / Twitter のメディア保存を行う。

- 共有URLを初期入力として受け取る
- クリップボードURLを検出する
- 手入力URLを受け付ける
- status URL から `api.fxtwitter.com` 経由でメディア候補を取得する
- mp4, gif, webp, jpg, jpeg, png の直接URLを扱う
- m3u8 を候補から除外する
- High / Medium / Low で候補URLを選択する
- MediaStore に保存する
- ダウンロード履歴を Room に保存する
- 履歴の失敗・完了状態を表示する
- 履歴削除とギャラリー更新を行う

### 3.12. レコメンド画面

視聴計測と類似度を使ったおすすめ一覧画面である。

- 計測モードで蓄積した閲覧回数・閲覧時間を参照する
- 上位視聴メディア、タグ類似、ベクトル類似、ランダム候補を組み合わせる
- おすすめ候補をグリッド表示する
- 選択したメディアをビューアで開く

## 4. データ設計

### 4.1. Room Database

DB名は `gallery_database`、バージョンは 16。

| Entity | テーブル | 内容 |
| --- | --- | --- |
| `MediaMetadataEntity` | `media_metadata` | メディアURI、日時、サイズ、解像度、動画/GIF判定、お気に入り、AI判定、削除状態、特徴ベクトル、フォルダ名、サムネイル有無 |
| `TagEntity` | `media_tags` | メディアURI、タグ名、信頼度 |
| `FolderOrderEntity` | `folder_order` | フォルダ表示順 |
| `ManagedFolderEntity` | `managed_folders` | 管理対象フォルダ、カスタムサムネイル |
| `TagTranslationEntity` | `tag_translations` | タグ翻訳 |
| `VideoDownloadEntity` | `video_downloads` | ダウンロードURL、保存先、状態、日時 |
| `MeasureStatsEntity` | `measure_stats` | 閲覧回数、閲覧時間 |

`FloatArray` は TypeConverter で `ByteArray` に変換し、画像ベクトルとして保存する。

### 4.2. 主な列挙値

| 種別 | 値 |
| --- | --- |
| `GalleryViewMode` | `FOLDER`, `MYLIST`, `TRASH` |
| `GroupingMode` | `NONE`, `DAY`, `MONTH`, `YEAR`, `STORAGE` |
| `MediaTypeFilter` | `ALL`, `IMAGE`, `VIDEO`, `GIF` |
| `AgeRatingFilter` | `ALL`, `SFW`, `R15`, `R18` |
| `DeviceFilter` | `ALL`, `SMARTPHONE`, `PC` |
| `SortMode` | `DATE_ADDED`, `SIZE`, `NAME` |

### 4.3. DAO の主な操作

- メタデータ取得、登録、削除、一覧取得
- メタデータサマリ取得
- タグ取得、登録、削除、タグ件数取得
- お気に入り更新
- 年齢制限更新
- フォルダ名更新
- AI分析済み状態更新
- 特徴ベクトル更新
- サムネイル有無更新
- ゴミ箱状態更新
- 視聴計測取得・更新
- フォルダ順、管理対象フォルダ、カスタムサムネイル更新
- ダウンロード履歴登録・取得・削除

## 5. Repository 設計

### 5.1. MediaRepository

メディア管理の中心であり、以下を担当する。

- MediaStore から画像・動画を取得する
- MediaData へ変換する
- 既存メタデータと突き合わせて Room へ保存する
- 端末から消えたメディアのメタデータ、タグを削除する
- Paging 用のグリッドデータを生成する
- グルーピングヘッダーを生成する
- フィルタ・ソートの前提となるメタデータを提供する
- お気に入り、年齢制限、タグを更新する
- メディアをゴミ箱へ移動、復元、完全削除する
- MediaStore の `RELATIVE_PATH` またはファイル移動によりフォルダ移動する
- DCIM 配下にフォルダを作成する
- タグ別、未整理、未分析、ベクトル未分析、ランダムメディアを提供する
- タグ類似度とベクトル類似度で関連メディアを検索する
- 視聴計測を保存し、推薦候補を作る
- フォルダ表示順、管理対象フォルダ、カスタムサムネイルを管理する

### 5.2. BookRepository

漫画ファイル管理を担当する。

- 外部ストレージから ZIP / PDF を再帰的にスキャンする
- 不要なディレクトリを除外する
- ZIP 内の画像ページを取得する
- PDF ページを取得する
- ZIP / PDF の先頭ページからサムネイルを生成する

## 6. サービス設計

### 6.1. AnalysisService

AI分析を foreground service として実行する。

- 分析種別は `AI_TAGGING`, `COLOR_VECTOR`, `AUTO_RATING` を想定する
- 期間指定により対象メディアを絞り込む
- モデル不足時は `ModelDownloader.downloadAllModels` を実行する
- タグ分析時は `AiTaggingService` を初期化する
- ベクトル分析時は `VectorSearchService` を初期化する
- 未分析メディアのみを対象にする
- 1件ずつ順次処理し、進捗と通知を更新する
- `GlobalOperationService` のキャンセル状態を確認する
- 完了後に `GalleryState.refresh()` を呼ぶ

### 6.2. GlobalOperationService

アプリ全体の長時間処理状態を管理する。

- 操作IDを発行する
- タイトル、進捗、詳細テキスト、処理タグ、期間、キャンセル可否を保持する
- キャンセル要求を保持する
- 処理中状態を Flow で提供する
- グローバル進捗オーバーレイから参照される

### 6.3. ThumbnailGenerationService

バックグラウンドでサムネイル生成とサムネイル状態更新を行う。

- アプリ起動後に開始する
- サムネイル未生成メディアを対象にする
- 画像サムネイルをキャッシュへ保存する
- 動画は代表フレームを取得する
- DB の `hasThumbnail` を更新する
- 進捗表示と停止に対応する

### 6.4. ModelDownloader

AIモデルとタグファイルを管理する。

- ベクトルモデルファイルを取得する
- Danbooru / WD Tagger モデルを取得する
- タグCSVを取得する
- ファイルサイズで有効性を確認する
- 複数ファイルのダウンロード進捗を重み付きで通知する

### 6.5. TagTranslationService

AIタグの表示名変換を行う。

- 手動オーバーライドを読み込む
- DB 由来または内蔵辞書でタグを翻訳する
- ビューア、タグ一覧、一括編集で表示名として使用する

## 7. AI設計

### 7.1. AIタグ分析

`AiTaggingService` は ONNX Runtime で Smiling Wolf / WD Tagger 系モデルを実行する。

- 入力画像を 448 x 448 にリサイズする
- 画像読み込み時に `inSampleSize` で過剰デコードを避ける
- 入力テンソル形状を NHWC / NCHW のどちらにも対応させる
- 推論結果のスコアがしきい値以上のタグを保存する
- rating 系タグとキーワードで SFW / R15 / R18 を判定する
- タグと信頼度を `TagEntity` として保存する
- `MediaMetadataEntity` に年齢制限とAI分析済み状態を保存する
- `Mutex` で同時推論を避ける
- XNNPACK、NNAPI FP16 を試行し、利用できない場合はCPUで動作する

### 7.2. 画像ベクトル分析

`VectorSearchService` は MediaPipe Image Embedder で特徴ベクトルを生成する。

- 入力画像を 224 x 224 付近まで縮小してデコードする
- L2 normalize 済みの埋め込みを取得する
- GPU delegate を試行し、失敗時は CPU delegate に切り替える
- 取得した `FloatArray` を DB に保存する
- コサイン類似度で類似画像を検索する

### 7.3. 推薦

推薦は複数の候補源を組み合わせる。

- 視聴時間と閲覧回数が多いメディア
- 現在メディアとタグが重なるメディア
- 現在メディアと画像ベクトルが近いメディア
- 年齢制限が近いランダムメディア

## 8. 権限・インテント設計

### 8.1. 権限

| 権限 | 用途 |
| --- | --- |
| `READ_MEDIA_IMAGES` | Android 13 以降の画像読み取り |
| `READ_MEDIA_VIDEO` | Android 13 以降の動画読み取り |
| `READ_EXTERNAL_STORAGE` | Android 12 以前の読み取り |
| `WRITE_EXTERNAL_STORAGE` | Android 9 以前の書き込み |
| `MANAGE_EXTERNAL_STORAGE` | 漫画ファイルスキャンなど全ファイルアクセス |
| `INTERNET` | AIモデル取得、X / Twitter API取得、ダウンロード |
| `ACCESS_NETWORK_STATE` | 通信状態確認 |
| `SET_WALLPAPER` | 壁紙設定 |
| `FOREGROUND_SERVICE` | AI分析などの長時間処理 |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14 以降のデータ同期型 foreground service |
| `POST_NOTIFICATIONS` | Android 13 以降の通知 |

### 8.2. インテント

- `ACTION_SEND` の `text/plain` から共有URLを受け取る
- `ACTION_VIEW` で `x.com` / `twitter.com` の status URL を受け取る
- `launchMode="singleTop"` により既存画面へURLを渡す

## 9. 非機能設計

### 9.1. 性能

- UIスレッドでAI分析、サムネイル生成、重いソートを実行しない
- 一覧は Paging と LazyVerticalGrid を使い、画面外アイテムのコストを抑える
- 28列表示はサムネイルを読まず、セル描画のみで高速化する
- 年単位表示は各年6件までに制限する
- メタデータ更新は debounce してスクロール中の再計算を減らす
- 画像読み込みは RGB_565、INEXACT サイズ、キャッシュを活用する
- 動画サムネイルは列数が少ない場合に限定して取得する
- AI分析は未分析対象のみを順次処理する

### 9.2. 保守性

- 画面状態を `GalleryState` に集約する
- DB操作は DAO と Repository に閉じ込める
- 長時間処理は Service と `GlobalOperationService` に分離する
- AI処理は `AiTaggingService` と `VectorSearchService` に分離する
- 画面共通の一覧表示は `GalleryGridView` と `CategoryScreen` に集約する

### 9.3. 拡張性

- モデル取得は `ModelDownloader` に分離し、モデル差し替えを容易にする
- フィルタ条件は enum と `GalleryState` に集約する
- 推薦処理はタグ類似、ベクトル類似、視聴計測を組み合わせられる構成にする
- フォルダ管理、タグ管理、視聴計測は Room に保存し、将来の検索条件追加に備える

### 9.4. 安全性

- ゴミ箱移動と完全削除を明確に分ける
- ゴミ箱内ビューアでは操作内容を復元・完全削除に限定する
- MediaStore 書き込み時は Android バージョンに応じて `IS_PENDING` や `RELATIVE_PATH` を使用する
- モデルダウンロード失敗時は処理を中断し、ユーザーに通知する

## 10. 依存関係

| 分類 | ライブラリ |
| --- | --- |
| UI | Compose BOM, Activity Compose, Material3, Material Icons, Foundation |
| Navigation | Navigation Compose |
| 画像・動画 | Coil Compose, Coil GIF, Coil Video, Media3 ExoPlayer, Media3 UI, Palette |
| DB | Room Runtime, Room KTX, Room Paging, KSP Room Compiler |
| AI | MediaPipe Tasks Vision, ONNX Runtime Android |
| 通信 | OkHttp, OkHttp Logging |
| セキュリティ | Conscrypt |
| Paging | Paging Runtime, Paging Compose |

## 11. 運用上の注意

- 初回AI分析ではモデルファイルのダウンロードが必要になる
- AI分析は画像枚数に比例して時間がかかるため、期間指定または未分析のみの実行を前提とする
- 28列表示は俯瞰専用であり、詳細確認は1、3、4、7列またはビューアで行う
- 漫画ファイルの全ストレージスキャンには全ファイルアクセス権限が必要になる
- X / Twitter ダウンロードは外部API応答形式の変更に影響を受ける
- 完全削除は端末上のメディアを復元できない状態にするため、通常の整理ではゴミ箱移動を優先する
