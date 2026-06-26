from __future__ import annotations

from html import escape
from pathlib import Path
from textwrap import dedent


OUT_DIR = Path(__file__).resolve().parent / "images" / "screen_transition"
W, H = 1600, 1050

BG = "#101316"
PANEL = "#151A20"
SCREEN = "#20242B"
SCREEN_2 = "#2A2F38"
DIALOG = "#2B2633"
POPUP = "#29323D"
SHEET = "#26302A"
TEXT = "#F8FAFC"
MUTED = "#AAB2BD"
CYAN = "#00BCD4"
GREEN = "#4CAF50"
YELLOW = "#D7B95F"
RED = "#F44336"
PURPLE = "#B79CFF"
ORANGE = "#D28A5E"
LINE = "#73D7E8"


def attrs(values: dict[str, object]) -> str:
    return " ".join(f'{k.replace("_", "-")}="{escape(str(v), quote=True)}"' for k, v in values.items())


def tag(name: str, values: dict[str, object], body: str | None = None) -> str:
    a = attrs(values)
    if body is None:
        return f"<{name} {a}/>"
    return f"<{name} {a}>{body}</{name}>"


def rect(x: float, y: float, w: float, h: float, cls: str = "", **values: object) -> str:
    data = {"x": x, "y": y, "width": w, "height": h}
    if cls:
        data["class"] = cls
    data.update(values)
    return tag("rect", data)


def text(x: float, y: float, value: str, cls: str = "txt", **values: object) -> str:
    data = {"x": x, "y": y, "class": cls}
    data.update(values)
    return tag("text", data, escape(value))


def line(x1: float, y1: float, x2: float, y2: float, cls: str = "line", **values: object) -> str:
    data = {"x1": x1, "y1": y1, "x2": x2, "y2": y2, "class": cls}
    data.update(values)
    return tag("line", data)


def path(d: str, cls: str = "line", **values: object) -> str:
    data = {"d": d, "class": cls}
    data.update(values)
    return tag("path", data)


def circle(x: float, y: float, r: float, cls: str = "", **values: object) -> str:
    data = {"cx": x, "cy": y, "r": r}
    if cls:
        data["class"] = cls
    data.update(values)
    return tag("circle", data)


def title_block(title: str, subtitle: str) -> list[str]:
    return [
        text(56, 60, title, "pageTitle"),
        text(56, 88, subtitle, "pageSub"),
        rect(56, 110, W - 112, 1, fill="#303741"),
    ]


def screen_card(x: float, y: float, title: str, route: str, rows: list[str], w: float = 210, h: float = 230, accent: str = CYAN) -> str:
    parts = [rect(x, y, w, h, "screenCard", rx=14)]
    parts.append(rect(x, y, w, 42, fill="#050607", rx=14))
    parts.append(rect(x, y + 28, w, 14, fill="#050607"))
    parts.append(text(x + 14, y + 27, title, "cardTitle"))
    if route:
        parts.append(text(x + w - 12, y + 27, route, "route", fill=accent, text_anchor="end"))
    yy = y + 66
    for row in rows:
        parts.append(rect(x + 14, yy - 18, w - 28, 24, fill="#FFFFFF10", rx=6))
        parts.append(text(x + 24, yy, row, "cardText"))
        yy += 34
    parts.append(rect(x + 12, y + h - 36, w - 24, 24, fill="#00000099", rx=12))
    return "".join(parts)


def dialog_card(x: float, y: float, title: str, rows: list[str], w: float = 210, h: float = 150, accent: str = PURPLE) -> str:
    parts = [rect(x, y, w, h, "dialogCard", rx=14)]
    parts.append(text(x + 14, y + 30, title, "cardTitle", fill=accent))
    yy = y + 60
    for row in rows:
        parts.append(text(x + 18, yy, row, "dialogText"))
        yy += 27
    return "".join(parts)


def popup_card(x: float, y: float, title: str, rows: list[str], w: float = 190, h: float = 135, accent: str = YELLOW) -> str:
    parts = [rect(x, y, w, h, "popupCard", rx=10)]
    parts.append(text(x + 12, y + 27, title, "cardTitle", fill=accent))
    yy = y + 55
    for row in rows:
        parts.append(text(x + 16, yy, row, "dialogText"))
        yy += 25
    return "".join(parts)


def sheet_card(x: float, y: float, title: str, rows: list[str], w: float = 230, h: float = 155, accent: str = GREEN) -> str:
    parts = [rect(x, y, w, h, "sheetCard", rx=16)]
    parts.append(rect(x + 78, y + 12, 74, 4, fill="#FFFFFF50", rx=2))
    parts.append(text(x + 16, y + 42, title, "cardTitle", fill=accent))
    yy = y + 72
    for row in rows:
        parts.append(text(x + 20, yy, row, "dialogText"))
        yy += 26
    return "".join(parts)


def label(x: float, y: float, value: str, color: str = CYAN) -> str:
    return rect(x - 8, y - 18, len(value) * 8 + 16, 25, fill="#101316", stroke=color, stroke_width=1, rx=12) + text(x, y, value, "arrowLabel", fill=color)


def arrow(x1: float, y1: float, x2: float, y2: float, caption: str = "", color: str = LINE, bend: float = 0) -> str:
    if bend:
        mx = (x1 + x2) / 2
        d = f"M{x1} {y1} Q{mx} {y1 + bend} {x2} {y2}"
        body = path(d, "arrow", stroke=color)
    else:
        body = line(x1, y1, x2, y2, "arrow", stroke=color)
    if caption:
        body += label((x1 + x2) / 2 - 16, (y1 + y2) / 2 - 8, caption, color)
    return body


def legend(items: list[tuple[str, str]]) -> str:
    x, y = 1220, 820
    parts = [rect(x, y, 310, 150, fill="#0B0E12", stroke="#303741", rx=12), text(x + 18, y + 30, "凡例", "cardTitle")]
    yy = y + 62
    for color, name in items:
        parts.append(rect(x + 18, yy - 16, 34, 18, fill=color, rx=5))
        parts.append(text(x + 64, yy, name, "dialogText"))
        yy += 30
    return "".join(parts)


def svg(title: str, subtitle: str, body: list[str]) -> str:
    defs = dedent(
        """
        <defs>
          <marker id="arrowHead" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#73D7E8"/>
          </marker>
        </defs>
        """
    ).strip()
    style = dedent(
        """
        <style>
          svg{background:#101316;font-family:'Yu Gothic','Meiryo',system-ui,sans-serif}
          .pageTitle{fill:#F8FAFC;font-size:30px;font-weight:800}
          .pageSub{fill:#AAB2BD;font-size:14px}
          .screenCard{fill:#20242B;stroke:#4A5362;stroke-width:1.3}
          .dialogCard{fill:#2B2633;stroke:#B79CFF;stroke-width:1.5;stroke-dasharray:7 5}
          .popupCard{fill:#29323D;stroke:#D7B95F;stroke-width:1.4}
          .sheetCard{fill:#26302A;stroke:#4CAF50;stroke-width:1.4}
          .cardTitle{fill:#F8FAFC;font-size:15px;font-weight:800}
          .route{font-size:10px;font-weight:700}
          .cardText{fill:#E8EAED;font-size:12px}
          .dialogText{fill:#D5DCE5;font-size:12px}
          .arrow{fill:none;stroke:#73D7E8;stroke-width:2.2;marker-end:url(#arrowHead)}
          .arrowLabel{font-size:11px;font-weight:800}
          .txt{fill:#fff;font-size:12px}
        </style>
        """
    ).strip()
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="title">'
        f'<title id="title">{escape(title)}</title>'
        + defs
        + style
        + "".join(title_block(title, subtitle))
        + "".join(body)
        + "</svg>\n"
    )


def write(name: str, title: str, subtitle: str, body: list[str]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / name).write_text(svg(title, subtitle, body), encoding="utf-8")


def diagram_global_navigation() -> None:
    b: list[str] = []
    b.append(screen_card(70, 170, "アプリ起動", "MainActivity", ["権限確認", "MediaStore同期", "起動タスク開始"], accent=GREEN))
    b.append(screen_card(365, 170, "ホーム", "home", ["ギャラリー一覧", "フィルタ/ソート", "スクロール位置保持"]))
    b.append(screen_card(650, 150, "ドロワー", "drawer", ["ホーム", "便利機能", "情報/ガイド"], accent=YELLOW))
    b.append(screen_card(930, 120, "フォルダ", "folders", ["フォルダ一覧", "フォルダ内グリッド", "一括操作"]))
    b.append(screen_card(1210, 120, "My List", "mylist", ["お気に入り", "未整理", "AI未分析"]))
    b.append(screen_card(930, 390, "本", "books", ["漫画一覧", "ビューア", "しおり"]))
    b.append(screen_card(1210, 390, "ゴミ箱", "trash", ["削除済み一覧", "復元", "完全削除"]))
    b.append(screen_card(70, 520, "X共有/VIEW", "intent", ["ACTION_SEND", "ACTION_VIEW", "initialUrl"], accent=ORANGE))
    b.append(screen_card(365, 520, "動画DL", "video_downloader", ["URL入力", "品質選択", "履歴プレビュー"], accent=ORANGE))
    b.append(screen_card(650, 520, "制作補助", "references", ["プロジェクト", "資料詳細", "Web検索"], accent=PURPLE))
    b.append(screen_card(930, 670, "作家/サイト", "favorite_*", ["作家リンク", "サイトリンク", "検索Dialog"], accent=ORANGE))
    b.append(screen_card(1210, 670, "おすすめ/情報", "recommend/about", ["おすすめ", "使い方Dialog", "このアプリについて"], accent=PURPLE))
    b.append(dialog_card(640, 790, "TutorialDialog", ["使い方ガイド", "ドロワーから表示"], w=230, h=120))
    b.append(popup_card(365, 790, "GlobalProgressOverlay", ["サムネイル生成", "AI分析進捗"], w=230, h=120, accent=GREEN))

    b.append(arrow(280, 285, 365, 285, "起動後"))
    b.append(arrow(575, 230, 650, 230, "メニュー"))
    for x, y, label_text in [(930, 235, "基本"), (1210, 235, "基本"), (930, 505, "基本"), (1210, 505, "基本"), (365, 620, "共有"), (650, 620, "便利"), (930, 785, "便利"), (1210, 785, "情報")]:
        b.append(arrow(860, 265, x, y, label_text, bend=70 if y > 500 else -40))
    b.append(arrow(280, 635, 365, 635, "外部URL"))
    b.append(arrow(650, 752, 755, 790, "ガイド"))
    b.append(arrow(470, 400, 470, 790, "進捗表示", color=GREEN))
    b.append(legend([(SCREEN, "画面"), (DIALOG, "Dialog"), (POPUP, "Popup/Overlay"), (LINE, "遷移")]))
    write("01_global_navigation.svg", "全体画面遷移図", "MainActivity の Drawer / BottomBar / Intent から主要画面へ遷移する流れ", b)


def diagram_gallery_viewer() -> None:
    b: list[str] = []
    b.append(screen_card(70, 170, "ホームギャラリー", "home", ["日/月/年グリッド", "28列表示", "スクロールバー"]))
    b.append(popup_card(80, 450, "Filter/Sort Menu", ["メディア種別", "年齢制限", "並び替え"], w=210, h=145))
    b.append(popup_card(310, 450, "年月日ラベル", ["スクロールバー左", "ドラッグ中のみ表示"], w=190, h=120))
    b.append(screen_card(390, 170, "選択モード", "selection", ["複数選択", "選択バー", "その他メニュー"], accent=PURPLE))
    b.append(popup_card(435, 450, "選択Menu", ["ゴミ箱へ", "フォルダ移動", "一括タグ編集"], w=210, h=145))
    b.append(dialog_card(690, 150, "一括編集Dialog", ["mass_edit", "UnifiedMediaEditDialog", "タグ/年齢制限"], w=220, h=160))
    b.append(screen_card(690, 390, "フォルダ選択", "bulk_move_selection", ["移動先を選択", "新規フォルダDialog", "移動実行"], accent=GREEN))
    b.append(screen_card(690, 650, "ゴミ箱", "trash", ["論理削除後", "復元/完全削除", "TrashScreen"], accent=RED))
    b.append(screen_card(1000, 170, "メディアビューア", "in-screen", ["全画面表示", "動画/GIF再生", "左右スワイプ"]))
    b.append(popup_card(1250, 120, "Viewer More", ["ascii2d検索", "フレーム保存", "壁紙/タグ編集"], w=230, h=150))
    b.append(dialog_card(1250, 330, "タグ編集Dialog", ["単体メディア編集", "タグ追加/評価更新"], w=230, h=130))
    b.append(dialog_card(1250, 510, "ascii2d Dialog", ["Web検索表示", "結果を確認"], w=230, h=130, accent=ORANGE))
    b.append(sheet_card(1000, 650, "関連メディアPanel", ["タグ類似", "ベクトル類似", "候補から再表示"], w=240, h=150))

    b.append(arrow(280, 285, 390, 285, "長押し"))
    b.append(arrow(180, 400, 185, 450, "アイコン"))
    b.append(arrow(230, 312, 1000, 285, "タップ"))
    b.append(arrow(495, 400, 520, 450, "その他"))
    b.append(arrow(645, 520, 690, 230, "編集"))
    b.append(arrow(645, 550, 690, 505, "移動"))
    b.append(arrow(645, 580, 690, 735, "削除"))
    b.append(arrow(1210, 285, 1250, 195, "その他"))
    b.append(arrow(1365, 270, 1365, 330, "タグ"))
    b.append(arrow(1365, 270, 1365, 510, "検索"))
    b.append(arrow(1115, 400, 1115, 650, "関連"))
    b.append(arrow(1120, 650, 1000, 400, "候補タップ"))
    b.append(legend([(SCREEN, "画面/内部画面"), (DIALOG, "Dialog"), (POPUP, "Dropdown/Popup"), (SHEET, "Panel/Sheet")]))
    write("02_gallery_viewer_flow.svg", "ギャラリー・ビューア遷移図", "一覧操作、選択モード、ビューア内ポップアップ/ダイアログの流れ", b)


def diagram_management_ai() -> None:
    b: list[str] = []
    b.append(screen_card(70, 170, "My List", "mylist", ["整理カテゴリ", "タグカテゴリ", "AI分析ボタン"]))
    b.append(screen_card(360, 140, "カテゴリ一覧", "mylist/{tag}", ["条件付きグリッド", "タップでビューア", "選択操作"]))
    b.append(dialog_card(360, 430, "AI分析Dialog", ["分析種別", "期間選択", "開始/キャンセル"], w=230, h=150))
    b.append(dialog_card(640, 430, "DatePickerDialog", ["開始日/終了日", "期間を確定"], w=220, h=130, accent=ORANGE))
    b.append(screen_card(910, 390, "分析進捗", "analysis/{type}/{days}", ["ForegroundService", "進捗/キャンセル", "完了でMy List"], accent=GREEN))
    b.append(screen_card(70, 610, "フォルダ", "folders", ["フォルダ一覧", "フォルダ内表示", "分析開始"]))
    b.append(dialog_card(360, 650, "フォルダ作成Dialog", ["DCIM直下", "フォルダ名入力", "作成/キャンセル"], w=230, h=150))
    b.append(dialog_card(640, 650, "フォルダMenu", ["順序/サムネイル", "フォルダ情報", "閉じる"], w=220, h=130))
    b.append(screen_card(910, 650, "FolderPicker", "folders_select", ["移動先選択", "新規作成", "戻る"], accent=GREEN))
    b.append(screen_card(1210, 390, "ゴミ箱", "trash", ["削除済み一覧", "復元", "完全削除"], accent=RED))
    b.append(screen_card(1210, 650, "MediaViewer", "trash viewer", ["削除済み表示", "復元導線", "閲覧のみ"], accent=RED))

    b.append(arrow(280, 250, 360, 250, "カテゴリ"))
    b.append(arrow(180, 400, 360, 500, "AI"))
    b.append(arrow(590, 500, 640, 500, "任意期間"))
    b.append(arrow(590, 505, 910, 505, "開始"))
    b.append(arrow(1020, 390, 180, 240, "完了/中断", color=GREEN, bend=-180))
    b.append(arrow(280, 690, 360, 720, "追加"))
    b.append(arrow(280, 740, 640, 715, "長押し"))
    b.append(arrow(505, 290, 910, 715, "フォルダ移動"))
    b.append(arrow(1120, 735, 1210, 470, "削除"))
    b.append(arrow(1320, 620, 1320, 650, "タップ"))
    b.append(arrow(1210, 520, 360, 250, "復元後戻る", color=GREEN, bend=150))
    b.append(legend([(SCREEN, "画面"), (DIALOG, "Dialog"), (GREEN, "完了/復帰"), (RED, "削除系")]))
    write("03_management_ai_flow.svg", "整理・AI分析遷移図", "My List、分析、フォルダ管理、ゴミ箱のダイアログを含む流れ", b)


def diagram_external_book_reference() -> None:
    b: list[str] = []
    b.append(screen_card(70, 150, "X共有/URL", "intent/clipboard", ["共有URL", "VIEW URL", "クリップボード"], accent=ORANGE))
    b.append(screen_card(340, 150, "動画DL", "video_downloader", ["URL入力", "候補解決", "履歴"], accent=ORANGE))
    b.append(dialog_card(610, 140, "Download settings", ["品質選択", "GIF保存", "HOME/Cancel"], w=230, h=150, accent=ORANGE))
    b.append(screen_card(900, 150, "保存履歴", "history", ["保存済みカード", "状態表示", "プレビュー"]))
    b.append(screen_card(1180, 150, "MediaViewer", "preview", ["保存済み再生", "閉じる", "ホームへ戻る"]))

    b.append(screen_card(70, 430, "漫画一覧", "books", ["ZIP/PDFスキャン", "作品カード", "しおり導線"]))
    b.append(screen_card(340, 430, "漫画ビューア", "BookViewer", ["ページ表示", "しおり", "ページシーク"]))
    b.append(popup_card(610, 430, "Book More", ["タイトル検索", "表示設定", "スクショ/回転"], w=220, h=145))
    b.append(sheet_card(890, 430, "表示設定Sheet", ["単/見開き", "読む方向", "先読み"], w=230, h=150))
    b.append(screen_card(1180, 430, "しおり一覧", "book_bookmarks", ["しおりカード", "本へジャンプ", "解除"]))

    b.append(screen_card(70, 710, "資料プロジェクト", "references", ["一覧", "新規作成", "進行/完了"], accent=PURPLE))
    b.append(dialog_card(70, 910, "新規Project Dialog", ["プロジェクト名", "作成/キャンセル"], w=230, h=115))
    b.append(screen_card(340, 710, "資料詳細", "reference_detail", ["資料カード", "追加", "完了切替"], accent=PURPLE))
    b.append(dialog_card(610, 710, "完了確認Dialog", ["完了にする", "URL参照へ整理"], w=230, h=130, accent=PURPLE))
    b.append(screen_card(890, 710, "資料検索", "reference_search", ["WebView検索", "画像追加", "スクショ保存"], accent=PURPLE))
    b.append(dialog_card(1180, 710, "作家/サイトDialog", ["検索WebView", "削除確認", "Import/Export"], w=250, h=150, accent=ORANGE))

    b.append(arrow(280, 260, 340, 260, "URL"))
    b.append(arrow(550, 260, 610, 215, "候補"))
    b.append(arrow(840, 215, 900, 260, "保存"))
    b.append(arrow(1110, 260, 1180, 260, "履歴タップ"))
    b.append(arrow(1390, 360, 450, 270, "閉じる/HOME", bend=150))

    b.append(arrow(280, 540, 340, 540, "作品タップ"))
    b.append(arrow(550, 540, 610, 500, "その他"))
    b.append(arrow(830, 500, 890, 505, "設定"))
    b.append(arrow(450, 430, 1180, 540, "しおり"))
    b.append(arrow(1290, 430, 340, 540, "ジャンプ", bend=-140))

    b.append(arrow(280, 820, 340, 820, "プロジェクト"))
    b.append(arrow(180, 900, 180, 910, "追加"))
    b.append(arrow(550, 820, 610, 770, "完了"))
    b.append(arrow(550, 860, 890, 820, "資料追加"))
    b.append(arrow(1110, 820, 1180, 785, "外部/検索補助"))
    b.append(legend([(SCREEN, "画面"), (DIALOG, "Dialog"), (POPUP, "Dropdown"), (SHEET, "BottomSheet")]))
    write("04_external_book_reference_flow.svg", "外部連携・漫画・制作補助遷移図", "X保存、漫画ビューア、お絵描き資料、作家/サイト管理の流れ", b)


def main() -> None:
    diagram_global_navigation()
    diagram_gallery_viewer()
    diagram_management_ai()
    diagram_external_book_reference()


if __name__ == "__main__":
    main()
