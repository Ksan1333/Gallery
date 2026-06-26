# 漫画ビューア 詳細設計

## 1. 概要

端末ストレージ上の ZIP / PDF 漫画ファイルをスキャンし、ページ単位で閲覧する。しおり、見開き、表示品質、ページスクリーンショット保存に対応する。

## 2. 利用者向け機能説明

ZIP や PDF の漫画をアプリ内で読めます。横画面では見開き、縦画面では 1 ページなど、読みやすい表示に切り替えられます。途中まで読んだ作品はしおりから戻れます。

## 3. 開発者向け技術説明

`BookRepository` が外部ストレージを再帰走査し、ZIP は `ZipFile`、PDF は `PdfRenderer` でページ数とサムネイルを取得する。`BookViewerScreen` は SharedPreferences に表示設定としおりを保存し、ページ Bitmap は必要時にロードする。

## 4. 画面設計

### 4.1. 画面の説明

漫画画面は、端末ストレージ内の ZIP / PDF を作品一覧として表示する画面である。作品カードにはタイトル、ページ数、サムネイルを表示し、ユーザーは読みたい作品を選ぶ。キャッシュ済み情報を先に出すことで、スキャン中でも一覧を確認しやすくする。

漫画ビューアは、読書中の画面占有を優先したページ表示画面である。縦画面では単ページ、横画面では見開きなど、端末の向きや設定に応じて表示を切り替える。しおり、ページスライダー、表示設定、スクリーンショット保存を備え、長い作品でも途中から再開しやすい。

### 4.2. 画面要素

| 画面 | 内容 |
| --- | --- |
| `BookScreen` | ZIP / PDF 一覧、スキャン、サムネイル表示 |
| `BookViewerScreen` | ページ表示、ページ移動、見開き、設定、しおり |
| `BookBookmarksScreen` | しおり一覧、該当作品・ページへのジャンプ |

| 設定 | 値 |
| --- | --- |
| ページレイアウト | AUTO / SINGLE / DOUBLE |
| 読み方向 | RIGHT_TO_LEFT / LEFT_TO_RIGHT |
| フィット | SCREEN / WIDTH / HEIGHT |
| 背景 | BLACK / GRAY / WHITE |
| 描画品質 | STANDARD / HIGH |
| その他 | ページ間隔、プリロード、タップナビゲーション、画面常時点灯 |

### 4.3. UIモック

#### 漫画一覧

![漫画一覧UIモック](../images/detail_design/06_book_list_ui.svg)

#### 漫画ビューア

![漫画ビューアUIモック](../images/detail_design/06_book_viewer_ui.svg)

#### 表示設定パネル

![漫画表示設定UIモック](../images/detail_design/06_book_settings_ui.svg)

| 番号 | UI部品 | 機能 |
| --- | --- | --- |
| 1 | 漫画一覧 | 表紙、ファイル名、ページ数、最終閲覧位置、読書進捗を表示する。 |
| 2 | 漫画ビューア | ZIP/PDFを単ページまたは見開きで黒背景に表示する。 |
| 3 | ビューア操作 | 閉じる、しおり、その他メニュー、ページシーク、前後本移動を行う。 |
| 4 | 表示設定パネル | 表示形式、読む方向、背景、先読みなどをビューア上で変更する。 |

### 4.4. ユースケース図

```mermaid
flowchart LR
    User["利用者"] --> UC1(["漫画ファイルをスキャンする"])
    User --> UC2(["作品一覧を見る"])
    User --> UC3(["ページを読む"])
    User --> UC4(["表示設定を変える"])
    User --> UC5(["しおりを付ける"])
    User --> UC6(["ページをスクショ保存する"])
```

### 4.5. 画面/操作フロー

```mermaid
flowchart TD
    Open["漫画画面を開く"] --> Cache["キャッシュ済み作品を表示"]
    Cache --> Scan["ZIP/PDF をスキャン"]
    Scan --> List["作品一覧更新"]
    List --> Select["作品選択"]
    Select --> Viewer["BookViewerScreen"]
    Viewer --> Load["ZIP/PDF ページを Bitmap 化"]
    Load --> Read["ページ表示"]
    Read --> Action{"読書操作"}
    Action -->|ページ移動| Load
    Action -->|設定変更| Settings["表示設定保存"]
    Action -->|しおり| Bookmark["book_bookmarks 保存"]
    Action -->|スクショ| Screenshot["MediaStore に保存"]
```

## 5. 関連 DB

Room DB は使わない。以下をファイル・SharedPreferences に保存する。

| 保存先 | 用途 |
| --- | --- |
| BookRepository cache index | スキャン済み漫画、ページ数、サムネイル、更新日時、サイズ |
| `book_bookmarks` | しおり |
| `BOOK_VIEWER_PREFS` | ビューア設定 |
| MediaStore | ページスクリーンショット保存 |

## 6. ER 図

```mermaid
erDiagram
    BOOK_CACHE {
        string id PK
        string path
        string title
        string type
        int pageCount
        string thumbnailPath
        long lastModified
        long fileSize
    }
    BOOK_BOOKMARK {
        string bookId PK
        int page
        long savedAt
    }
    BOOK_VIEWER_SETTINGS {
        string key PK
        string value
    }
    BOOK_CACHE ||--o| BOOK_BOOKMARK : "bookId"
```

## 7. DAO / Repository

| 種別 | 実装 | 役割 |
| --- | --- | --- |
| Repository | `BookRepository.scanBooks()` | ZIP/PDF スキャン、キャッシュ更新 |
| Repository | `loadCachedBooks()` | 起動直後の高速表示 |
| Repository | `getZipPage()` | ZIP 内画像を Bitmap 化 |
| Repository | `getPdfPage()` | PDF ページを Bitmap 化 |
| UI | `BookViewerScreen` | ページロード、設定、しおり、スクショ |
| Storage | SharedPreferences | しおり・設定 |

## 8. シーケンス図

```mermaid
sequenceDiagram
    participant User as User
    participant Book as BookScreen
    participant Repo as BookRepository
    participant Viewer as BookViewerScreen
    participant FS as Storage
    participant Pref as SharedPreferences
    participant Store as MediaStore

    User->>Book: 漫画画面を開く
    Book->>Repo: loadCachedBooks()
    Book->>Repo: scanBooks()
    Repo->>FS: ZIP/PDF 再帰スキャン
    Repo->>FS: サムネイル生成/キャッシュ保存
    Repo-->>Book: BookData 一覧
    User->>Book: 作品選択
    Book->>Viewer: BookData
    Viewer->>Pref: 設定読み込み
    Viewer->>Repo: getZipPage/getPdfPage
    Repo-->>Viewer: Bitmap
    User->>Viewer: しおり/設定/スクショ
    Viewer->>Pref: しおり・設定保存
    Viewer->>Store: スクショ保存
```

## 9. 補足

- 全ファイルスキャンには `MANAGE_EXTERNAL_STORAGE` が必要になる。
- キャッシュはファイル更新日時とサイズで再利用可否を判断する。
- 高画質設定は読み込みコストが高いため、標準品質を既定にする。

## 10. 利用 API・外部連携

| API / ライブラリ | 用途 |
| --- | --- |
| Android `PdfRenderer` | PDF ページのレンダリング |
| `java.util.zip.ZipFile` | ZIP 内画像ページの読み取り |
| Android `MediaStore` | ページスクリーンショット保存 |
| Android `MANAGE_EXTERNAL_STORAGE` | ZIP / PDF の端末ストレージスキャン |
| SharedPreferences | しおり、ビューア設定 |
