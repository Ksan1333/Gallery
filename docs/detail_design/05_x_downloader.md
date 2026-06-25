# X / Twitter ダウンロード 詳細設計

## 1. 概要

X / Twitter の共有 URL、VIEW URL、クリップボード URL、手入力 URL、直接メディア URL から動画・画像・GIF を保存する。

## 2. 利用者向け機能説明

X の投稿を共有したり、URL を貼ったりするだけで、動画や画像を端末の Gallery フォルダに保存できます。画質を選べて、GIF は GIF として保存できるように扱います。保存履歴からすぐ開き直すこともできます。

## 3. 開発者向け技術説明

`MainActivity` が `ACTION_SEND` と `ACTION_VIEW` を受け、`VideoDownloadScreen` に `initialUrl` を渡す。status URL は fxtwitter / vxtwitter / fixupx API 候補で JSON 解決し、`MediaUrlCandidate` に正規化する。保存は MediaStore の pending entry に stream copy し、履歴を `video_downloads` に保存する。

## 4. 画面設計

### 4.1. 画面の説明

X / Twitter ダウンロード画面は、外部アプリから共有された投稿 URL や、ユーザーが貼り付けた URL を受け取り、保存可能なメディア候補に変換する画面である。URL がすでに分かっている場合は、入力よりも候補解決と保存操作にすぐ進めることを優先する。

候補解決後は、動画の画質や GIF として保存するかを選ぶ。保存中は進捗を表示し、完了後は履歴に追加する。履歴行からは保存済みメディアをプレビューできるため、ダウンロード作業と保存確認を同じ画面で完結できる。

### 4.2. 画面要素

| 領域 | 内容 |
| --- | --- |
| URL 入力 | 共有 URL、クリップボード、手入力 |
| 候補解決 | status URL / 直接 URL を判定 |
| 品質選択 | High / Medium / Low、GIF 専用選択 |
| 履歴 | 保存済み URL、タイトル、保存先、状態 |
| プレビュー | 保存済みメディアを `MediaViewerScreen` で開く |
| HOME | ダイアログ表示中でもホームへ戻れる導線 |

### 4.3. ユースケース図

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

### 4.4. 画面/操作フロー

```mermaid
flowchart TD
    Entry["共有/VIEW/貼り付け/手入力"] --> Detect{"URL種別"}
    Detect -->|status URL| Resolve["fxtwitter/vxtwitter/fixupx で候補解決"]
    Detect -->|直接URL| Candidate["直接メディア候補化"]
    Resolve --> Options["品質選択ダイアログ"]
    Candidate --> Options
    Options --> Download["MediaStore へ保存"]
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
    Screen-->>External: 品質選択
    External->>Screen: 保存開始
    Screen->>DB: DOWNLOADING/Pending 履歴登録
    Screen->>Store: pending entry 作成
    Screen->>Store: ダウンロード bytes 書き込み
    alt GIF 由来 mp4
        Screen->>Screen: フレーム抽出・GIF エンコード
    end
    Screen->>Store: IS_PENDING=0
    Screen->>DB: COMPLETED/FAILED 更新
    Screen->>Repo: refresh
```

## 9. 補足

- `isGifSource` を保存候補に持たせ、GIF が mp4 として誤保存されることを避ける。
- 外部 API の JSON 形状変更に備え、複数ホストと複数キーを探索する。
- m3u8 は直接保存対象から除外する。

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
