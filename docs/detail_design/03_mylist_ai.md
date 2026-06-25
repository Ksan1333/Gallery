# My List・AI分析 詳細設計

## 1. 概要

お気に入り、未整理、AI 未分析、ベクトル未分析、タグカテゴリを My List として表示し、AI 分析やカテゴリ別閲覧へ接続する。

## 2. お客さん目線の説明

自分が整理したいものだけをまとめて見られる画面です。まだタグがない画像、AI で分析していない画像、お気に入り、タグごとのまとまりをすぐ開けます。AI 分析は期間を選んで実行でき、進捗も確認できます。

## 3. エンジニア目線の説明

`MyListScreen` は `MediaRepository` の Flow と metadata map からカテゴリを組み立てる。分析開始時は Navigation で `analysis/{type}/{periodDays}` へ遷移し、`AnalysisService` が foreground service として対象メディアを順次処理する。

## 4. 画面設計

| 領域 | 内容 |
| --- | --- |
| My List 一覧 | Favorites、Untagged、Unanalyzed AI、Unanalyzed Vector、Tag categories |
| カテゴリ詳細 | `CategoryScreen` で対象メディアをグリッド表示 |
| 分析起動 | 分析種別と対象期間を選択 |
| 分析進捗 | `AnalysisProgressScreen` で進捗、キャンセル、完了後 My List 復帰 |

## 5. 関連 DB

| テーブル | 用途 |
| --- | --- |
| `media_metadata` | `isFavorite`, `isAiAnalyzed`, `ageRating`, `featureVector` |
| `media_tags` | タグカテゴリ、AI タグ結果 |
| `tag_translations` | AI タグの表示名変換 |

## 6. ER 図

```mermaid
erDiagram
    MEDIA_METADATA {
        string uri PK
        boolean isFavorite
        string ageRating
        boolean isAiAnalyzed
        blob featureVector
    }
    MEDIA_TAGS {
        string uri PK
        string tag PK
        float confidence
    }
    TAG_TRANSLATIONS {
        string original PK
        string translated
    }
    MEDIA_METADATA ||--o{ MEDIA_TAGS : "logical uri"
```

## 7. DAO / Repository / Service

| 種別 | 実装 | 役割 |
| --- | --- | --- |
| DAO | `getFavoriteUris()` | お気に入り URI |
| DAO | `getAllTagsWithCounts()` | タグカテゴリ件数 |
| DAO | `saveAiAnalysisResult()` | タグ、年齢制限、分析済み状態の保存 |
| DAO | `updateFeatureVector()` | ベクトル保存 |
| Repository | `getUntaggedMedia()` | 未整理メディア |
| Repository | `getUnanalyzedAiCount()` | AI 未分析数 |
| Service | `AnalysisService` | 分析全体の foreground 実行 |
| Service | `AiTaggingService` | ONNX タグ推論 |
| Service | `VectorSearchService` | MediaPipe ベクトル生成 |

## 8. シーケンス図

```mermaid
sequenceDiagram
    participant User as User
    participant MyList as MyListScreen
    participant Progress as AnalysisProgressScreen
    participant Service as AnalysisService
    participant Model as ModelDownloader
    participant AI as AiTaggingService/VectorSearchService
    participant Repo as MediaRepository
    participant DB as Room

    MyList->>Repo: カテゴリ用 Flow 購読
    Repo->>DB: metadata / tags 取得
    DB-->>MyList: カテゴリ表示データ
    User->>MyList: 分析開始
    MyList->>Progress: analysis route へ遷移
    Progress->>Service: start(type, periodDays)
    Service->>Model: モデル確認・不足時ダウンロード
    Service->>Repo: getAllMedia(forceRefresh=true)
    Service->>DB: metadata summary 取得
    Service->>Service: 対象抽出
    loop 対象画像
        Service->>AI: analyzeSingle(media)
        AI->>DB: タグ/年齢制限/ベクトル保存
    end
    Service->>Repo: refresh
    Progress-->>MyList: 完了/キャンセルで戻る
```

## 9. 補足

- 動画は AI タグ・ベクトル分析対象から除外する。
- 端末温度が高い場合は `AnalysisService` がクールダウンまたは一時停止する。
- `AUTO_RATING` は設計上の分析種別として扱われるが、現状コードでは主にタグ分析とベクトル分析が実処理の中心。
