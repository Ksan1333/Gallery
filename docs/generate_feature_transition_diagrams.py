from __future__ import annotations

from html import escape
from pathlib import Path
from textwrap import dedent


OUT_DIR = Path(__file__).resolve().parent / "images" / "detail_design"

W, H = 1420, 760
BG = "#101316"
SCREEN = "#20242B"
SCREEN_DARK = "#050607"
CARD = "#2B3038"
TEXT = "#F8FAFC"
MUTED = "#AAB2BD"
CYAN = "#00BCD4"
GREEN = "#4CAF50"
YELLOW = "#D7B95F"
RED = "#F44336"
PURPLE = "#B79CFF"
ORANGE = "#D28A5E"
BLUE = "#1DA1F2"
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


def circle(x: float, y: float, r: float, cls: str = "", **values: object) -> str:
    data = {"cx": x, "cy": y, "r": r}
    if cls:
        data["class"] = cls
    data.update(values)
    return tag("circle", data)


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


def arrow(x1: float, y1: float, x2: float, y2: float, label: str = "", color: str = LINE, curve: float = 0) -> str:
    if curve:
        mx = (x1 + x2) / 2
        body = path(f"M{x1} {y1} Q{mx} {y1 + curve} {x2} {y2}", "arrow", stroke=color)
    else:
        body = line(x1, y1, x2, y2, "arrow", stroke=color)
    if label:
        lx = (x1 + x2) / 2 - 28
        ly = (y1 + y2) / 2 - 8
        body += rect(lx - 8, ly - 17, len(label) * 9 + 16, 24, fill=BG, stroke=color, stroke_width=1, rx=12)
        body += text(lx, ly, label, "arrowLabel", fill=color)
    return body


def title(title_text: str, subtitle: str) -> list[str]:
    return [
        text(46, 52, title_text, "pageTitle"),
        text(46, 80, subtitle, "pageSub"),
        rect(46, 104, W - 92, 1, fill="#303741"),
    ]


def thumb_grid(x: float, y: float, cols: int, rows: int, cell: float, gap: float = 5) -> str:
    colors = ["#6C8BFF", "#3EB489", "#D77A61", "#D7B95F", "#8F7CFF", "#E56B9F", "#6EC6E8", "#92A76A"]
    parts: list[str] = []
    for r in range(rows):
        for c in range(cols):
            i = r * cols + c
            parts.append(rect(x + c * (cell + gap), y + r * (cell + gap), cell, cell, fill=colors[i % len(colors)], rx=3, opacity=0.9))
    return "".join(parts)


def phone(x: float, y: float, title_text: str, kind: str, route: str = "", accent: str = CYAN, w: float = 190, h: float = 270) -> str:
    parts = [rect(x, y, w, h, "phone", rx=18)]
    parts.append(rect(x + 10, y + 12, w - 20, h - 24, fill="#121212", rx=12))
    parts.append(rect(x + 10, y + 12, w - 20, 34, fill="#000000", rx=12))
    parts.append(rect(x + 10, y + 34, w - 20, 12, fill="#000000"))
    parts.append(text(x + 22, y + 35, title_text, "screenTitle"))
    if route:
        parts.append(text(x + w - 18, y + 35, route, "route", fill=accent, text_anchor="end"))
    bx, by = x + 22, y + 64
    bw = w - 44

    if kind == "grid":
        parts.append(text(bx, by - 10, "2026年6月25日", "miniMuted"))
        parts.append(thumb_grid(bx, by, 4, 4, 30, 4))
        parts.append(rect(x + w - 20, by, 4, 128, fill="#FFFFFF90", rx=2))
    elif kind == "dense":
        parts.append(text(bx, by - 10, "2026年", "miniMuted"))
        parts.append(thumb_grid(bx, by, 10, 5, 10, 2))
        parts.append(text(bx, by + 78, "28列 / 1年6段", "miniMuted"))
    elif kind == "selection":
        parts.append(thumb_grid(bx, by, 4, 3, 30, 4))
        parts.append(rect(x + 10, y + h - 68, w - 20, 38, fill="#D0BCFF", rx=4))
        parts.append(text(x + 26, y + h - 44, "8件選択中", "darkText"))
    elif kind == "viewer":
        parts.append(rect(bx, by, bw, 138, fill="#44516B", rx=8))
        parts.append(rect(x + 10, y + h - 76, w - 20, 52, fill="#000000CC", rx=8))
        parts.append(text(x + 26, y + h - 45, "削除  タグ  その他", "miniMuted"))
    elif kind == "video":
        parts.append(rect(bx, by, bw, 110, fill="#44516B", rx=8))
        parts.append(rect(bx, by + 126, bw, 4, fill="#FFFFFF30", rx=2))
        parts.append(rect(bx, by + 126, bw * 0.45, 4, fill=CYAN, rx=2))
        parts.append(text(bx + 35, by + 160, "戻る  再生  進む", "miniMuted"))
    elif kind == "category":
        for i, name in enumerate(["Favorites", "Untagged", "AI未分析", "tag"]):
            yy = by + i * 38
            parts.append(rect(bx, yy, bw, 26, fill="#FFFFFF14", rx=6))
            parts.append(text(bx + 10, yy + 18, name, "miniMuted"))
    elif kind == "folder":
        parts.append(thumb_grid(bx, by, 3, 2, 42, 6))
        parts.append(text(bx, by + 112, "Camera / Download", "miniMuted"))
    elif kind == "download":
        parts.append(rect(bx, by, bw, 38, fill="#00000000", stroke="#80868B", rx=5))
        parts.append(text(bx + 8, by + 25, "https://x.com/...", "miniMuted"))
        parts.append(rect(bx, by + 58, bw, 34, fill=BLUE, rx=17))
        parts.append(text(bx + 22, by + 80, "Show options", "buttonText"))
        parts.append(rect(bx, by + 112, bw, 54, fill="#FFFFFF12", rx=6))
    elif kind == "book":
        parts.append(rect(bx, by, 55, 120, fill="#44516B", rx=4))
        parts.append(rect(bx + 72, by, 55, 120, fill="#6B5144", rx=4))
        parts.append(rect(bx, by + 145, bw, 4, fill="#FFFFFF30", rx=2))
        parts.append(rect(bx, by + 145, bw * 0.5, 4, fill=CYAN, rx=2))
    elif kind == "reference":
        for i, name in enumerate(["キャラ資料", "手とポーズ", "背景ラフ"]):
            yy = by + i * 45
            parts.append(rect(bx, yy, bw, 32, fill="#FFFFFF12", rx=7))
            parts.append(text(bx + 10, yy + 21, name, "miniMuted"))
    elif kind == "web":
        parts.append(rect(bx, by, bw, 34, fill="#00000000", stroke="#80868B", rx=5))
        parts.append(text(bx + 10, by + 22, "search words", "miniMuted"))
        parts.append(rect(bx, by + 50, bw, 120, fill="#F4F4F4", rx=7))
        parts.append(thumb_grid(bx + 10, by + 64, 3, 2, 34, 6))
    elif kind == "creator":
        for i, name in enumerate(["artist_name", "illustrator_02", "site URL"]):
            yy = by + i * 48
            parts.append(rect(bx, yy, bw, 36, fill="#4B4038", rx=7))
            parts.append(text(bx + 10, yy + 23, name, "miniMuted"))
    elif kind == "drawer":
        for i, name in enumerate(["ホーム", "フォルダ", "動画DL", "資料"]):
            parts.append(text(bx, by + i * 30, name, "miniMuted"))
    elif kind == "progress":
        parts.append(rect(bx, by + 60, bw, 86, fill="#000000D9", rx=9))
        parts.append(text(bx + 12, by + 90, "サムネイル生成", "miniMuted"))
        parts.append(rect(bx + 12, by + 108, bw - 24, 5, fill="#FFFFFF30", rx=2))
        parts.append(rect(bx + 12, by + 108, (bw - 24) * 0.45, 5, fill=CYAN, rx=2))
    else:
        parts.append(rect(bx, by, bw, 130, fill="#FFFFFF12", rx=7))
        parts.append(text(bx + 12, by + 38, kind, "miniMuted"))
    return "".join(parts)


def dialog(x: float, y: float, title_text: str, rows: list[str], accent: str = PURPLE, w: float = 190, h: float = 138) -> str:
    parts = [rect(x, y, w, h, "dialog", rx=14)]
    parts.append(text(x + 14, y + 28, title_text, "nodeTitle", fill=accent))
    yy = y + 58
    for row in rows:
        parts.append(text(x + 16, yy, row, "dialogText"))
        yy += 24
    return "".join(parts)


def popup(x: float, y: float, title_text: str, rows: list[str], w: float = 185, h: float = 128) -> str:
    parts = [rect(x, y, w, h, "popup", rx=10)]
    parts.append(text(x + 12, y + 27, title_text, "nodeTitle", fill=YELLOW))
    yy = y + 55
    for row in rows:
        parts.append(text(x + 14, yy, row, "dialogText"))
        yy += 23
    return "".join(parts)


def sheet(x: float, y: float, title_text: str, rows: list[str], w: float = 210, h: float = 136) -> str:
    parts = [rect(x, y, w, h, "sheet", rx=15)]
    parts.append(rect(x + 68, y + 12, 74, 4, fill="#FFFFFF50", rx=2))
    parts.append(text(x + 14, y + 38, title_text, "nodeTitle", fill=GREEN))
    yy = y + 66
    for row in rows:
        parts.append(text(x + 18, yy, row, "dialogText"))
        yy += 23
    return "".join(parts)


def render(filename: str, title_text: str, subtitle: str, parts: list[str]) -> None:
    defs = dedent(
        """
        <defs>
          <marker id="arrowHead" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#73D7E8"/>
          </marker>
        </defs>
        """
    ).strip()
    style = dedent(
        """
        <style>
          svg{background:#101316;font-family:'Yu Gothic','Meiryo',system-ui,sans-serif}
          .pageTitle{fill:#F8FAFC;font-size:28px;font-weight:800}
          .pageSub{fill:#AAB2BD;font-size:14px}
          .phone{fill:#07080A;stroke:#3D4551;stroke-width:2}
          .screenTitle{fill:#F8FAFC;font-size:12px;font-weight:800}
          .route{font-size:9px;font-weight:800}
          .miniMuted{fill:#D5DCE5;font-size:10px}
          .darkText{fill:#1D192B;font-size:11px;font-weight:800}
          .buttonText{fill:#fff;font-size:11px;font-weight:800}
          .dialog{fill:#2B2633;stroke:#B79CFF;stroke-width:1.5;stroke-dasharray:6 5}
          .popup{fill:#29323D;stroke:#D7B95F;stroke-width:1.4}
          .sheet{fill:#26302A;stroke:#4CAF50;stroke-width:1.4}
          .nodeTitle{fill:#F8FAFC;font-size:13px;font-weight:800}
          .dialogText{fill:#D5DCE5;font-size:11px}
          .arrow{fill:none;stroke:#73D7E8;stroke-width:2.1;marker-end:url(#arrowHead)}
          .arrowLabel{font-size:10px;font-weight:800}
          .txt{fill:#fff;font-size:12px}
        </style>
        """
    ).strip()
    body = "".join(title(title_text, subtitle)) + "".join(parts)
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="title"><title id="title">{escape(title_text)}</title>{defs}{style}{body}</svg>\n'
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / filename).write_text(svg, encoding="utf-8")


def media_gallery() -> None:
    p = [
        phone(70, 150, "通常一覧", "grid", "home"),
        phone(330, 150, "28列表示", "dense", "home"),
        popup(590, 165, "Filter/Sort Popup", ["メディア種別", "年齢制限", "並び替え"], w=210),
        phone(70, 470, "選択モード", "selection", "selection", accent=PURPLE),
        popup(330, 492, "選択Menu", ["ゴミ箱へ", "フォルダ移動", "一括タグ編集"], w=205),
        dialog(590, 462, "一括編集Dialog", ["タグ/評価編集", "保存して戻る"], w=205, h=120),
        phone(850, 445, "フォルダ選択", "folder", "bulk_move", accent=GREEN),
        phone(1110, 445, "ゴミ箱", "grid", "trash", accent=RED),
        arrow(260, 275, 330, 275, "列数28"),
        arrow(260, 230, 590, 230, "操作バー"),
        arrow(165, 420, 165, 470, "長押し"),
        arrow(260, 575, 330, 555, "その他"),
        arrow(535, 535, 590, 520, "編集"),
        arrow(535, 590, 850, 565, "移動"),
        arrow(535, 620, 1110, 565, "削除"),
    ]
    render("01_media_gallery_transition.svg", "メディア一覧 機能内画面遷移図", "一覧、表示切替、選択モード、一括操作だけに絞った遷移", p)


def media_viewer() -> None:
    p = [
        phone(70, 250, "一覧", "grid", "home"),
        phone(330, 250, "ビューア", "viewer", "in-screen"),
        phone(590, 120, "動画操作", "video", "viewer"),
        popup(590, 430, "その他Menu", ["ascii2d検索", "フレーム保存", "タグ編集"], w=205),
        dialog(850, 380, "タグ編集Dialog", ["単体メディア", "タグ/評価更新"], w=205, h=120),
        dialog(850, 560, "ascii2d Dialog", ["Web検索", "結果確認"], accent=ORANGE, w=205, h=115),
        sheet(1110, 330, "関連Panel", ["類似候補", "候補タップで表示"], w=210, h=120),
        arrow(260, 365, 330, 365, "タップ"),
        arrow(520, 315, 590, 245, "動画/GIF"),
        arrow(520, 400, 590, 490, "その他"),
        arrow(795, 490, 850, 440, "タグ"),
        arrow(795, 520, 850, 610, "検索"),
        arrow(520, 390, 1110, 390, "関連"),
        arrow(1110, 450, 330, 390, "候補表示", curve=120),
    ]
    render("02_media_viewer_transition.svg", "メディアビューア 機能内画面遷移図", "ビューア内部の操作バー、メニュー、Dialog、関連Panelの遷移", p)


def folder_trash_bulk() -> None:
    p = [
        phone(70, 190, "フォルダ一覧", "folder", "folders"),
        dialog(70, 510, "フォルダ作成Dialog", ["フォルダ名", "作成/キャンセル"], w=210, h=115),
        popup(330, 210, "フォルダMenu", ["サムネイル設定", "順序/情報", "閉じる"], w=205),
        phone(590, 190, "フォルダ内Grid", "grid", "folders"),
        phone(850, 190, "選択モード", "selection", "selection", accent=PURPLE),
        popup(1110, 195, "一括Menu", ["編集", "フォルダ移動", "ゴミ箱へ"], w=205),
        dialog(850, 510, "一括編集Dialog", ["タグ/年齢制限", "保存"], w=205, h=115),
        phone(1110, 500, "ゴミ箱", "grid", "trash", accent=RED),
        arrow(260, 305, 590, 305, "フォルダを開く"),
        arrow(165, 460, 165, 510, "追加"),
        arrow(260, 260, 330, 260, "長押し"),
        arrow(780, 305, 850, 305, "長押し選択"),
        arrow(1040, 305, 1110, 260, "その他"),
        arrow(1210, 330, 955, 510, "編集"),
        arrow(1210, 330, 1110, 565, "削除"),
        arrow(1110, 610, 590, 360, "復元", color=GREEN, curve=120),
    ]
    render("04_folder_trash_bulk_transition.svg", "フォルダ・ゴミ箱・一括編集 機能内画面遷移図", "フォルダ一覧から一括操作、ゴミ箱、復元までの流れ", p)


def x_downloader() -> None:
    p = [
        phone(70, 220, "外部共有/URL", "download", "intent", accent=ORANGE),
        phone(330, 220, "Video Downloader", "download", "video_dl", accent=BLUE),
        dialog(590, 230, "Download settings", ["品質選択", "GIF保存", "Start/HOME"], accent=ORANGE, w=220, h=135),
        phone(850, 220, "保存履歴", "download", "history", accent=GREEN),
        phone(1110, 220, "プレビュー", "viewer", "viewer"),
        phone(590, 520, "ホーム", "grid", "home"),
        arrow(260, 335, 330, 335, "共有/貼付"),
        arrow(520, 335, 590, 300, "候補解決"),
        arrow(810, 300, 850, 335, "保存"),
        arrow(1040, 335, 1110, 335, "履歴タップ"),
        arrow(700, 365, 700, 520, "HOME", color=GREEN),
        arrow(1210, 490, 430, 395, "閉じる", curve=160),
    ]
    render("05_x_downloader_transition.svg", "X / Twitter ダウンロード 機能内画面遷移図", "URL入力、品質選択Dialog、保存履歴、プレビューの流れ", p)


def book_viewer() -> None:
    p = [
        phone(70, 220, "漫画一覧", "book", "books"),
        phone(330, 220, "BookViewer", "book", "viewer"),
        popup(590, 220, "その他Menu", ["タイトル検索", "表示設定", "スクショ/回転"], w=215),
        sheet(850, 210, "表示設定Sheet", ["単/見開き", "読む方向", "先読み"], w=215, h=135),
        phone(1110, 220, "しおり一覧", "book", "bookmarks", accent=GREEN),
        arrow(260, 335, 330, 335, "作品タップ"),
        arrow(520, 335, 590, 285, "その他"),
        arrow(805, 285, 850, 280, "設定"),
        arrow(430, 220, 1110, 335, "しおり"),
        arrow(1110, 390, 330, 380, "ジャンプ", color=GREEN, curve=150),
    ]
    render("06_book_viewer_transition.svg", "漫画ビューア 機能内画面遷移図", "漫画一覧、ビューア、その他Menu、設定Sheet、しおりジャンプ", p)


def reference_projects() -> None:
    p = [
        phone(70, 220, "資料プロジェクト", "reference", "references", accent=PURPLE),
        dialog(70, 540, "新規Project Dialog", ["プロジェクト名", "作成/キャンセル"], accent=PURPLE, w=220, h=115),
        phone(330, 220, "資料詳細", "grid", "detail", accent=PURPLE),
        dialog(590, 215, "完了確認Dialog", ["完了にする", "URL参照へ整理"], accent=PURPLE, w=220, h=120),
        phone(850, 220, "資料検索", "web", "search", accent=PURPLE),
        phone(1110, 220, "資料Viewer", "viewer", "viewer"),
        arrow(260, 335, 330, 335, "開く"),
        arrow(165, 490, 165, 540, "追加"),
        arrow(520, 290, 590, 275, "完了"),
        arrow(520, 380, 850, 335, "資料追加"),
        arrow(1040, 335, 330, 390, "画像追加", color=GREEN, curve=120),
        arrow(430, 370, 1110, 335, "資料タップ"),
    ]
    render("07_reference_projects_transition.svg", "お絵描き資料 機能内画面遷移図", "プロジェクト作成、資料詳細、Web検索、完了確認の流れ", p)


def favorite_creators_sites() -> None:
    p = [
        phone(70, 220, "Creators", "creator", "artists", accent=ORANGE),
        phone(330, 220, "編集モード", "creator", "edit", accent=ORANGE),
        dialog(590, 170, "検索Dialog", ["Google検索", "URLを反映"], accent=ORANGE, w=210, h=120),
        dialog(590, 360, "削除確認Dialog", ["削除/キャンセル"], accent=RED, w=210, h=105),
        phone(850, 220, "お気に入りサイト", "creator", "sites", accent=ORANGE),
        dialog(1110, 220, "Import/Export", ["JSON書き出し", "読み込み"], accent=GREEN, w=210, h=115),
        arrow(260, 335, 330, 335, "編集"),
        arrow(520, 290, 590, 230, "URL検索"),
        arrow(520, 400, 590, 405, "削除"),
        arrow(520, 335, 850, 335, "サイト画面"),
        arrow(1040, 335, 1110, 280, "バックアップ"),
        arrow(700, 290, 330, 390, "URL反映", color=GREEN, curve=100),
    ]
    render("09_favorite_creators_sites_transition.svg", "お気に入り作家・サイト 機能内画面遷移図", "作家/サイト編集、検索Dialog、削除確認、バックアップ操作", p)


def shared_services() -> None:
    p = [
        phone(70, 220, "現在画面", "grid", "current"),
        phone(330, 220, "Drawer", "drawer", "drawer", accent=YELLOW),
        phone(590, 120, "基本機能", "category", "home/folders"),
        phone(590, 440, "便利機能", "reference", "tools", accent=PURPLE),
        dialog(850, 160, "TutorialDialog", ["使い方ガイド", "閉じる"], w=205, h=115),
        phone(850, 440, "About", "category", "about"),
        phone(1110, 320, "ProgressOverlay", "progress", "overlay", accent=GREEN),
        arrow(260, 335, 330, 335, "メニュー"),
        arrow(520, 280, 590, 235, "基本"),
        arrow(520, 390, 590, 555, "便利"),
        arrow(520, 320, 850, 220, "ガイド"),
        arrow(520, 410, 850, 555, "情報"),
        arrow(260, 390, 1110, 435, "処理中表示", color=GREEN, curve=120),
    ]
    render("10_shared_services_transition.svg", "共通基盤 機能内画面遷移図", "Drawer、共通進捗、Tutorial、Aboutへの共通遷移", p)


def main() -> None:
    media_gallery()
    media_viewer()
    folder_trash_bulk()
    x_downloader()
    book_viewer()
    reference_projects()
    favorite_creators_sites()
    shared_services()


if __name__ == "__main__":
    main()
