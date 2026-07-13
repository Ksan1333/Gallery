# X / Twitter ダウンロード 詳細設計

## 1. 概要

X / Twitter の共有 URL、VIEW URL、クリップボード URL、手入力 URL、直接メディア URL から動画・画像・GIF を保存する。

## 2. 利用者向け機能説明

X の投稿を共有したり、URL を貼ったりするだけで、動画や画像を端末の Gallery フォルダに保存できます。画質を選べて、GIF は GIF として保存できるように扱います。保存履歴からすぐ開き直すこともできます。

## 3. 開発者向け技術説明

`MainActivity` が `ACTION_SEND` と `ACTION_VIEW` を受け、`VideoDownloadScreen` に `initialUrl` を渡す。status URL は syndication / fxtwitter / vxtwitter / fixupx API候補でJSON解決し、`MediaUrlCandidate`へ正規化する。候補は`mediaKey`単位に束ね、同じ動画の画質違いと別メディアを区別する。保存はMediaStoreのpending entryへstream copyし、1メディア1履歴として`video_downloads`へ保存する。

## 4. 画面設計

### 4.1. 画面の説明

X / Twitter ダウンロード画面は、外部アプリから共有された投稿 URL や、ユーザーが貼り付けた URL を受け取り、保存可能なメディア候補に変換する画面である。URL がすでに分かっている場合は、入力よりも候補解決と保存操作にすぐ進めることを優先する。

候補解決後は、動画の画質や GIF として保存するかを選ぶ。保存中は進捗を表示し、完了後は履歴に追加する。履歴行からは保存済みメディアをプレビューできるため、ダウンロード作業と保存確認を同じ画面で完結できる。

### 4.2. 画面要素

| 領域 | 内容 |
| --- | --- |
| URL 入力 | 共有 URL、クリップボード、手入力 |
| 候補解決 | status URL / 直接 URL を判定 |
| 品質選択 | メディアごとの品質候補、GIF / MP4選択、複数件一括保存 |
| 履歴 | 保存済み URL、タイトル、保存先、状態 |
| プレビュー | 保存済みメディアを `MediaViewerScreen` で開く |
| HOME | ダイアログ表示中でもホームへ戻れる導線 |

### 4.3. UIモック

#### URL入力・履歴概要

![XダウンロードUIモック](../images/detail_design/05_x_downloader_ui.svg)

#### 保存候補・品質選択

![Xダウンロード設定UIモック](../images/detail_design/05_x_downloader_options_ui.svg)

#### ダウンロード履歴

![Xダウンロード履歴UIモック](../images/detail_design/05_x_downloader_history_ui.svg)

| 番号 | UI部品 | 機能 |
| --- | --- | --- |
| 1 | URL入力欄 | 共有URL、VIEW URL、クリップボードURL、手入力URLを表示・編集する。 |
| 2 | 候補解決ボタン | URLを解析し、保存可能なメディア候補を取得する。 |
| 3 | 品質選択ダイアログ | High/Medium/Low、GIF保存、開始、HOME、キャンセルを選ぶ。 |
| 4 | 履歴カード | 保存済みメディア、URL、保存先、状態、プレビュー導線を表示する。 |
| 5 | 再確認導線 | 履歴からアプリ内ビューアで保存済みメディアを確認する。 |

### 4.4. 機能内画面遷移図

外部共有URL、Video Downloader、保存設定ダイアログ、保存履歴、プレビュー、HOME復帰の流れを、画面タイトル付きのミニUIモックと矢印で示す。

![X / Twitter ダウンロード 機能内画面遷移図](../images/detail_design/05_x_downloader_transition.svg)

### 4.5. ユースケース図

```mermaid
flowchart LR
    User["利用者"] --> UC1(["URLを共有する"])
    User --> UC2(["URLを貼り付ける"])
    User --> UC3(["保存候補を解決する"])
    User --> UC4(["画質/GIFを選ぶ"])
    User --> UC5(["メディアを保存する"])
    User --> UC6(["履歴から開く"])
    External["外部アプリ"] --> UC1
```

### 4.6. 画面/操作フロー

```mermaid
flowchart TD
    Entry["共有/VIEW/貼り付け/手入力"] --> Detect{"URL種別"}
    Detect -->|status URL| Resolve["fxtwitter/vxtwitter/fixupx で候補解決"]
    Detect -->|直接URL| Candidate["直接メディア候補化"]
    Resolve --> Options["品質選択ダイアログ"]
    Candidate --> Options
    Options --> Group["mediaKeyごとに候補を集約"]
    Group --> Download["全メディアを個別タスクでMediaStoreへ保存"]
    Download --> Gif{"GIF由来?"}
    Gif -->|はい| Transcode["GIFとして変換保存"]
    Gif -->|いいえ| Complete["保存完了"]
    Transcode --> Complete
    Complete --> History["video_downloads 履歴更新"]
```

## 5. 関連 DB

| テーブル | 用途 |
| --- | --- |
| `video_downloads` | URL、タイトル、保存先、日時、状態 |
| `media_metadata` | 保存後のギャラリー再スキャンで反映 |

## 6. ER 図

```mermaid
erDiagram
    VIDEO_DOWNLOADS {
        string url PK
        string title
        string savePath
        long downloadDate
        string status
    }
    MEDIA_METADATA {
        string uri PK
        string mimeType
        string fileName
        long dateAdded
    }
```

## 7. DAO / Repository

| 種別 | 実装 | 役割 |
| --- | --- | --- |
| DAO | `insertVideoDownload()` | 履歴登録・状態更新 |
| DAO | `getAllVideoDownloads()` | 履歴表示 |
| DAO | `isVideoDownloaded()` | 重複判定 |
| DAO | `clearVideoDownloadHistory()` | 履歴削除 |
| UI/Logic | `resolveXVideoUrls()` | API から候補 URL 解決 |
| UI/Logic | `startDownloadTask()` | MediaStore 保存、履歴更新 |
| UI/Logic | `transcodeMp4ToGif()` | GIF 由来 mp4 の GIF 保存 |
| UI/Logic | `groupDownloadCandidates()` | 画質違いをメディア単位へ集約 |
| UI/Logic | `buildMediaDownloadSelections()` | 各メディアの保存候補と変換要否を決定 |

## 8. シーケンス図

```mermaid
sequenceDiagram
    participant External as 外部アプリ/User
    participant Main as MainActivity
    participant Screen as VideoDownloadScreen
    participant API as fxtwitter/vxtwitter/fixupx
    participant Store as MediaStore
    participant DB as Room
    participant Repo as MediaRepository

    External->>Main: ACTION_SEND / ACTION_VIEW / URL入力
    Main->>Screen: initialUrl
    Screen->>Screen: URL 種別判定
    alt status URL
        Screen->>API: API 候補へ問い合わせ
        API-->>Screen: JSON
        Screen->>Screen: MediaUrlCandidate 抽出
    else 直接メディア URL
        Screen->>Screen: content-type / 拡張子判定
    end
    Screen->>Screen: mediaKey別に候補を集約
    Screen-->>External: メディア件数・品質・GIF/MP4選択
    External->>Screen: 全件保存開始
    loop メディアごと
        Screen->>DB: 一意な履歴IDでDOWNLOADING登録
        Screen->>Store: 個別pending entry作成
        Screen->>Store: ダウンロードbytes書き込み
        alt GIF由来mp4かつGIF選択
            Screen->>Screen: フレーム抽出・GIFエンコード
        end
        Screen->>Store: IS_PENDING=0
        Screen->>DB: COMPLETED/FAILED更新
    end
    Screen->>Repo: refresh
```

## 9. 補足

- `isGifSource` を保存候補に持たせ、GIF が mp4 として誤保存されることを避ける。
- 外部 API の JSON 形状変更に備え、複数ホストと複数キーを探索する。
- m3u8 は直接保存対象から除外する。
- 通常動画とGIFが混在する投稿でGIF保存を選んでも、通常動画はMP4のまま保存する。
- バッチ保存時はファイル名へ連番を付け、同一投稿内の上書きを防ぐ。

## 10. 利用 API・外部連携

| API / ライブラリ | 用途 |
| --- | --- |
| `api.fxtwitter.com` | X / Twitter 投稿のメディア候補解決 |
| `api.vxtwitter.com` | X / Twitter 投稿のメディア候補解決フォールバック |
| `api.fixupx.com` | X / Twitter 投稿のメディア候補解決フォールバック |
| OkHttp | API 呼び出し、メディアダウンロード |
| Android `MediaStore` | ダウンロードメディア保存 |
| Android `Intent.ACTION_SEND` / `ACTION_VIEW` | 共有 URL / X 投稿 URL の受信 |
| gifencoder / MediaCodec / MediaMetadataRetriever | GIF 由来動画の GIF 変換補助 |
