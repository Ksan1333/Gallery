# おすすめ・視聴履歴 詳細設計

## 1. 概要

閲覧回数・閲覧時間、タグ類似、画像ベクトル類似、ランダム候補を組み合わせておすすめメディアを表示する。

## 2. 利用者向け機能説明

よく見ている画像や、それに似たタグ・雰囲気の画像をおすすめとして表示します。大量の画像から、見返したいものや近い雰囲気のものを探しやすくします。

## 3. 開発者向け技術説明

ビューアが閲覧時間を `measure_stats` に保存し、`MediaRepository.getRecommendations()` が上位履歴を起点にタグ類似とベクトル類似を展開する。足りない分はランダム候補で補う。

## 4. 画面設計

### 4.1. 画面の説明

おすすめ画面は、ユーザーがよく見ているメディアや、それに近いメディアをまとめて表示する発見用の画面である。通常のギャラリーと同じグリッドを使うため、気になった候補はそのままビューアで開ける。

ビューア内の関連表示は、今見ている 1 枚を起点に似たタグや似た見た目の候補を提示する。おすすめ画面が広い入口だとすると、ビューア内の関連表示は「この画像に近いものを続けて見たい」という流れを支える画面である。

### 4.2. 画面要素

| 画面/部品 | 内容 |
| --- | --- |
| `RecommendationScreen` | おすすめ 100 件を読み込み、`GalleryGridView` で表示 |
| `MediaViewerScreen` | 上スワイプで関連タブ、タグ類似・ベクトル類似表示 |
| `GalleryGridView` | おすすめから開いたメディアも通常ビューアに接続 |

### 4.3. ユースケース図

```mermaid
flowchart LR
    User["利用者"] --> UC1(["メディアを閲覧する"])
    User --> UC2(["おすすめを見る"])
    User --> UC3(["関連メディアを見る"])
    User --> UC4(["おすすめからビューアを開く"])
    System["推薦ロジック"] --> UC2
    UC1 --> Stats(["閲覧履歴を蓄積する"])
```

### 4.4. 画面/操作フロー

```mermaid
flowchart TD
    View["ビューアで閲覧"] --> SaveStats["measure_stats 保存"]
    SaveStats --> RecoOpen["おすすめ画面を開く"]
    RecoOpen --> Rank["閲覧履歴上位を抽出"]
    Rank --> Similar["タグ類似/ベクトル類似を追加"]
    Similar --> Fill["不足分をランダム補完"]
    Fill --> Grid["おすすめグリッド表示"]
    Grid --> Viewer["選択したメディアをビューア表示"]
```

## 5. 関連 DB

| テーブル | 用途 |
| --- | --- |
| `measure_stats` | 閲覧回数、合計閲覧時間、最終閲覧日時 |
| `media_tags` | タグ類似計算 |
| `media_metadata` | 特徴ベクトル、年齢制限、削除状態 |

## 6. ER 図

```mermaid
erDiagram
    MEDIA_METADATA {
        string uri PK
        string ageRating
        blob featureVector
        boolean isDeleted
    }
    MEDIA_TAGS {
        string uri PK
        string tag PK
        float confidence
    }
    MEASURE_STATS {
        string uri PK
        int viewCount
        long totalDurationSeconds
        long lastViewedTimestamp
    }
    MEDIA_METADATA ||--o{ MEDIA_TAGS : "logical uri"
    MEDIA_METADATA ||--o| MEASURE_STATS : "logical uri"
```

## 7. DAO / Repository

| 種別 | 実装 | 役割 |
| --- | --- | --- |
| DAO | `getMeasureStats()` / `insertMeasureStats()` | 閲覧統計の取得・保存 |
| DAO | `getAllMeasureStatsFlow()` | 推薦起点の取得 |
| DAO | `getAllTagsWithUris()` | タグ類似用 |
| DAO | `getVectorsByRating()` / `getAllVectors()` | ベクトル類似用 |
| Repository | `updateMeasureStats()` | 閲覧統計加算 |
| Repository | `getRecommendations()` | おすすめ候補生成 |
| Repository | `findMediaByTagSimilarity()` | タグ一致率で類似候補 |
| Repository | `findSimilarVisualMedia()` | コサイン類似度で画像類似候補 |

## 8. シーケンス図

```mermaid
sequenceDiagram
    participant Viewer as MediaViewerScreen
    participant Repo as MediaRepository
    participant DB as Room
    participant Reco as RecommendationScreen
    participant Grid as GalleryGridView

    Viewer->>Repo: updateMeasureStats(uri, duration)
    Repo->>DB: measure_stats upsert
    Reco->>Repo: getRecommendations(100)
    Repo->>DB: measure_stats / tags / vectors 取得
    Repo->>Repo: 上位履歴、タグ類似、ベクトル類似、ランダムを結合
    Repo-->>Reco: MediaData list
    Reco->>Grid: おすすめ表示
```

## 9. 補足

- 視聴履歴が少ない場合はランダム候補が中心になる。
- ベクトル類似は `featureVector` が保存済みの画像だけが対象になる。
- 削除済みメディアを推薦に混ぜないよう metadata 側の削除状態に注意する。

## 10. 利用 API・外部連携

| API / ライブラリ | 用途 |
| --- | --- |
| Room | 閲覧履歴、タグ、特徴ベクトル取得 |
| Kotlin Coroutines / Flow | 推薦候補生成と画面更新 |
| MediaPipe 生成済みベクトル | ベクトル類似検索 |
| `GalleryGridView` | おすすめ結果の一覧表示 |
