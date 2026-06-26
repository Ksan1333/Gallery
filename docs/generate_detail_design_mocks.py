from __future__ import annotations

from dataclasses import dataclass
from html import escape
from pathlib import Path
from textwrap import dedent


OUT_DIR = Path(__file__).resolve().parent / "images" / "detail_design"

W, H = 920, 980
PX, PY, PW, PH = 54, 38, 430, 900
SX, SY, SW, SH = PX + 18, PY + 28, PW - 36, PH - 56
LEGX = 532

BLACK = "#000000"
APP_BG = "#444444"
CONTROL_BG = "#444444"
CARD = "#1E1E1E"
TEXT = "#FFFFFF"
MUTED = "#9E9E9E"
CYAN = "#00BCD4"
X_BLUE = "#1DA1F2"
CREATOR_BG = "#11100F"
CREATOR_CARD = "#1D1A18"
CREATOR_FIELD = "#28231F"
CREATOR_INK = "#F4EFE8"
CREATOR_MUTED = "#B8ADA2"
CREATOR_ACCENT = "#D28A5E"


@dataclass(frozen=True)
class Legend:
    n: int
    title: str
    body: str


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


def line(x1: float, y1: float, x2: float, y2: float, cls: str = "line") -> str:
    return tag("line", {"x1": x1, "y1": y1, "x2": x2, "y2": y2, "class": cls})


def marker(n: int, x: float, y: float) -> str:
    return circle(x, y, 11, "marker") + text(x, y + 4, str(n), "markerText", text_anchor="middle")


def icon_text(x: float, y: float, label: str, cls: str = "iconLabel") -> str:
    return text(x, y, label, cls, text_anchor="middle")


def phone(screen_bg: str = APP_BG) -> list[str]:
    return [
        rect(PX, PY, PW, PH, "device", rx=34),
        rect(SX, SY, SW, SH, "screen", rx=18, fill=screen_bg),
        circle(SX + SW / 2, SY - 10, 4, "camera"),
    ]


def app_bar(title: str, left: str = "≡", actions: list[str] | None = None, centered: bool = False, bg: str = BLACK, fg: str = TEXT) -> str:
    actions = actions or []
    parts = [rect(SX, SY, SW, 56, fill=bg)]
    if left:
        parts.append(text(SX + 28, SY + 35, left, "barIcon", fill=fg, text_anchor="middle"))
    tx = SX + SW / 2 if centered else SX + 58
    parts.append(text(tx, SY + 36, title, "barTitle", fill=fg, text_anchor="middle" if centered else "start"))
    for i, label in enumerate(reversed(actions)):
        x = SX + SW - 28 - i * 38
        parts.append(text(x, SY + 35, label, "barIconSmall", fill=fg, text_anchor="middle"))
    return "".join(parts)


def gallery_control_bar(disabled_filter: bool = False) -> str:
    y = SY + 56
    parts = [rect(SX, y, SW, 56, fill=CONTROL_BG, opacity=0.95)]
    icons = [("F", MUTED if disabled_filter else TEXT), ("P", "#4CAF50"), ("S", MUTED if disabled_filter else TEXT)]
    for i, (label, fill) in enumerate(icons):
        x = SX + SW - 28 - (len(icons) - 1 - i) * 42
        parts.append(circle(x, y + 28, 18, "iconCircle"))
        parts.append(text(x, y + 34, label, "iconLabel", fill=fill, text_anchor="middle"))
    return "".join(parts)


def bottom_nav(active: str) -> str:
    labels = [("すべて", "H"), ("フォルダ", "L"), ("マイリスト", "♥"), ("本", "B"), ("ゴミ箱", "D")]
    y = SY + SH - 80
    x = SX + 8
    w = SW - 16
    item = w / len(labels)
    parts = [rect(x, y, w, 64, "bottomPill", rx=32)]
    for i, (label, glyph) in enumerate(labels):
        cx = x + item * i + item / 2
        selected = label == active
        parts.append(text(cx, y + 25, glyph, "navGlyphOn" if selected else "navGlyph", text_anchor="middle"))
        parts.append(text(cx, y + 48, label, "navTextOn" if selected else "navText", text_anchor="middle"))
    return "".join(parts)


def legend(title: str, items: list[Legend]) -> str:
    parts = [
        text(LEGX, 68, title, "docTitle"),
        text(LEGX, 96, "実装画面に近い詳細設計モック", "docSub"),
        rect(LEGX, 118, 320, 1, fill="#30343A"),
    ]
    y = 154
    for item in items:
        parts.append(circle(LEGX + 12, y - 6, 11, "marker"))
        parts.append(text(LEGX + 12, y - 2, str(item.n), "markerText", text_anchor="middle"))
        parts.append(text(LEGX + 34, y - 10, item.title, "legendTitle"))
        parts.append(text(LEGX + 34, y + 14, item.body, "legendBody"))
        y += 74
    return "".join(parts)


def thumb(x: float, y: float, w: float, h: float, i: int, label: str = "", square: bool = False) -> str:
    rx = 1 if square else 8
    parts = [rect(x, y, w, h, "thumb", rx=rx, fill=f"url(#thumb{i % 8})")]
    if label:
        parts.append(rect(x + w - 42, y + h - 18, 38, 15, "badge", rx=2))
        parts.append(text(x + w - 23, y + h - 7, label, "badgeText", text_anchor="middle"))
    if i % 6 == 0:
        parts.append(text(x + 10, y + h - 6, "♥", "fav"))
    return "".join(parts)


def media_grid(x: float, y: float, cols: int, rows: int, cell: float, gap: float = 1.0) -> str:
    parts: list[str] = []
    for r in range(rows):
        for c in range(cols):
            i = r * cols + c
            label = "GIF" if i in (2, 13) else ("0:12" if i in (5, 11) else "")
            parts.append(thumb(x + c * (cell + gap), y + r * (cell + gap), cell, cell, i, label, square=True))
    return "".join(parts)


def category_tile(x: float, y: float, w: float, title: str, count: str, i: int, show_thumb: bool = True) -> str:
    parts: list[str] = [rect(x, y, w, 134 if show_thumb else 62, fill="transparent")]
    if show_thumb:
        parts.append(thumb(x, y, w, w, i))
        ty = y + w + 17
    else:
        ty = y + 22
    parts.append(text(x + 4, ty, title, "catTitle"))
    parts.append(text(x + 4, ty + 18, count, "catSub"))
    return "".join(parts)


def card_row(x: float, y: float, w: float, h: float, title: str, sub: str, icon: str = "") -> str:
    parts = [rect(x, y, w, h, "m3Card", rx=8)]
    if icon:
        parts.append(circle(x + 30, y + h / 2, 18, "iconCircle"))
        parts.append(text(x + 30, y + h / 2 + 6, icon, "iconLabel", text_anchor="middle"))
        tx = x + 62
    else:
        tx = x + 16
    parts.append(text(tx, y + 30, title, "cardTitle"))
    parts.append(text(tx, y + 52, sub, "cardSub"))
    return "".join(parts)


def button(x: float, y: float, w: float, label: str, color: str = CYAN, text_color: str = "#001216") -> str:
    return rect(x, y, w, 42, fill=color, rx=21) + text(x + w / 2, y + 27, label, "buttonText", fill=text_color, text_anchor="middle")


def dialog_box(x: float, y: float, w: float, h: float, title: str, rows: list[str], action: str) -> str:
    parts = [rect(SX, SY, SW, SH, fill="#000000", opacity=0.55), rect(x, y, w, h, "dialog", rx=18)]
    parts.append(text(x + 22, y + 38, title, "dialogTitle"))
    yy = y + 70
    for row in rows:
        parts.append(text(x + 22, yy, row, "dialogText"))
        yy += 30
    parts.append(button(x + w - 132, y + h - 52, 104, action))
    return "".join(parts)


def render(title: str, body: list[str], legends: list[Legend], filename: str) -> None:
    defs = dedent(
        """
        <defs>
          <linearGradient id="thumb0" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#6C8BFF"/><stop offset="1" stop-color="#15203D"/></linearGradient>
          <linearGradient id="thumb1" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#3EB489"/><stop offset="1" stop-color="#14251D"/></linearGradient>
          <linearGradient id="thumb2" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#D77A61"/><stop offset="1" stop-color="#301915"/></linearGradient>
          <linearGradient id="thumb3" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#D7B95F"/><stop offset="1" stop-color="#2C2412"/></linearGradient>
          <linearGradient id="thumb4" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#8F7CFF"/><stop offset="1" stop-color="#21193A"/></linearGradient>
          <linearGradient id="thumb5" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#E56B9F"/><stop offset="1" stop-color="#351826"/></linearGradient>
          <linearGradient id="thumb6" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#6EC6E8"/><stop offset="1" stop-color="#132836"/></linearGradient>
          <linearGradient id="thumb7" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#92A76A"/><stop offset="1" stop-color="#1A2214"/></linearGradient>
        </defs>
        """
    ).strip()
    style = dedent(
        """
        <style>
          svg{background:#101316;font-family:'Yu Gothic','Meiryo',system-ui,sans-serif}
          .device{fill:#07080A;stroke:#2B3038;stroke-width:3}
          .screen{stroke:#171A1F;stroke-width:1}
          .camera{fill:#1E242C}
          .barTitle{font-size:20px;font-weight:500}
          .barIcon{font-size:25px;font-weight:600}
          .barIconSmall{font-size:18px;font-weight:600}
          .iconCircle{fill:#2D2D2D}
          .iconLabel{font-size:14px;font-weight:700;fill:#FFFFFF}
          .thumb{stroke:#222;stroke-width:.6}
          .badge{fill:#000000AA}
          .badgeText{fill:#fff;font-size:9px;font-weight:700}
          .fav{fill:#F44336;font-size:13px;font-weight:700}
          .txt{fill:#fff;font-size:13px}
          .headerText{fill:#fff;font-size:20px;font-weight:500}
          .small{fill:#E8EAED;font-size:12px}
          .muted{fill:#9E9E9E;font-size:12px}
          .bottomPill{fill:#000000D9}
          .navGlyph,.navText{fill:#8F8F8F}
          .navGlyphOn,.navTextOn{fill:#FFFFFF;font-weight:700}
          .navGlyph,.navGlyphOn{font-size:19px}
          .navText,.navTextOn{font-size:9px}
          .scrollThumb{fill:#FFFFFFCC}
          .scrollLabel{fill:#000000C7}
          .scrollLabelText{fill:#fff;font-size:13px}
          .marker{fill:#00BCD4}
          .markerText{fill:#001216;font-size:12px;font-weight:900}
          .docTitle{fill:#F8FAFC;font-size:25px;font-weight:800}
          .docSub{fill:#AAB2BD;font-size:13px}
          .legendTitle{fill:#F5F7FA;font-size:15px;font-weight:800}
          .legendBody{fill:#B9C2CE;font-size:12px}
          .catTitle{fill:#FFFFFF;font-size:13px}
          .catSub{fill:#A0A0A0;font-size:11px}
          .m3Card{fill:#1E1E1E}
          .cardTitle{fill:#FFFFFF;font-size:15px;font-weight:700}
          .cardSub{fill:#AAAAAA;font-size:11px}
          .buttonText{font-size:14px;font-weight:700}
          .inputBox{fill:#00000000;stroke:#80868B;stroke-width:1.2}
          .inputLabel{fill:#BDBDBD;font-size:12px}
          .inputText{fill:#FFFFFF;font-size:13px}
          .dialog{fill:#1A1A1A;stroke:#2C2C2C}
          .dialogTitle{fill:#fff;font-size:18px;font-weight:700}
          .dialogText{fill:#D5D5D5;font-size:13px}
          .brownCard{fill:#1D1A18;stroke:#4B4038}
          .brownField{fill:#28231F}
          .brownTitle{fill:#F4EFE8;font-size:18px;font-weight:700}
          .brownText{fill:#B8ADA2;font-size:13px}
          .brownAccent{fill:#D28A5E;font-size:13px;font-weight:700}
          .progressTrack{fill:#FFFFFF26}
          .progress{fill:#00BCD4}
          .line{stroke:#00BCD4;stroke-width:1.2;opacity:.65}
        </style>
        """
    ).strip()
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="title">'
        f'<title id="title">{escape(title)}</title>'
        + defs
        + style
        + "".join(body)
        + legend(title, legends)
        + "</svg>\n"
    )
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / filename).write_text(svg, encoding="utf-8")


def screen_media_gallery() -> None:
    e = phone(APP_BG)
    e.append(app_bar("すべて", left="≡"))
    e.append(gallery_control_bar())
    y = SY + 126
    e.append(text(SX + 16, y, "2026年6月25日", "headerText"))
    e.append(media_grid(SX + 10, y + 16, 4, 6, (SW - 44) / 4))
    e.append(rect(SX + SW - 10, SY + 138, 4, 520, "scrollThumb", rx=2))
    e.append(rect(SX + SW - 124, SY + 350, 106, 30, "scrollLabel", rx=6))
    e.append(text(SX + SW - 71, SY + 370, "2025年11月", "scrollLabelText", text_anchor="middle"))
    e.append(bottom_nav("すべて"))
    e += [marker(1, SX + 150, SY + 28), marker(2, SX + SW - 73, SY + 84), marker(3, SX + 92, y + 18), marker(4, SX + SW - 58, SY + 366), marker(5, SX + SW / 2, SY + SH - 48)]
    render(
        "メディア一覧UIモック",
        e,
        [
            Legend(1, "黒ヘッダー", "実装の title は「すべて」。左にドロワーメニューを置きます。"),
            Legend(2, "操作バー", "フィルタ、年齢制限、並び替えアイコンだけを右寄せ表示します。"),
            Legend(3, "LazyVerticalGrid", "日付ヘッダーと正方形サムネイルを同じグリッドに表示します。"),
            Legend(4, "スクロールバー", "ドラッグ中のみ年月日ラベルをバー左に表示します。"),
            Legend(5, "ボトムナビ", "実装文言は「すべて / フォルダ / マイリスト / 本 / ゴミ箱」です。"),
        ],
        "01_media_gallery_ui.svg",
    )


def screen_media_viewer() -> None:
    e = phone(BLACK)
    e.append(rect(SX, SY, SW, SH, fill=BLACK))
    e.append(thumb(SX + 18, SY + 72, SW - 36, 520, 4))
    overlay_y = SY + SH - 184
    e.append(rect(SX, overlay_y, SW, 184, fill="#000000D9"))
    e.append(text(SX + 16, overlay_y + 28, "0:08 / 1:20", "small"))
    e.append(rect(SX + 96, overlay_y + 19, SW - 156, 4, "progressTrack", rx=2))
    e.append(rect(SX + 96, overlay_y + 19, 112, 4, "progress", rx=2))
    e.append(text(SX + SW - 28, overlay_y + 30, "V", "barIconSmall", text_anchor="middle"))
    tx = SX + 10
    for i in range(8):
        e.append(thumb(tx + i * 47, overlay_y + 46, 42, 42, i, square=False))
    for i, label in enumerate(["D", "R", "♥", "⋮"]):
        x = SX + 54 + i * 88
        e.append(circle(x, overlay_y + 132, 18, "iconCircle"))
        e.append(text(x, overlay_y + 138, label, "iconLabel", fill="#F44336" if label == "♥" else TEXT, text_anchor="middle"))
    e += [marker(1, SX + SW / 2, SY + 320), marker(2, SX + 182, overlay_y + 22), marker(3, SX + 88, overlay_y + 67), marker(4, SX + 230, overlay_y + 132)]
    render(
        "メディアビューアUIモック",
        e,
        [
            Legend(1, "表示領域", "黒背景に画像/GIF/動画を Fit 表示し、スワイプやズームを受けます。"),
            Legend(2, "動画シーク", "動画時だけ時間表示、Slider、ミュートボタンを表示します。"),
            Legend(3, "サムネイル列", "下部 LazyRow で前後メディアまたはGIFフレームを表示します。"),
            Legend(4, "操作ボタン", "削除、回転、お気に入り、その他メニューを丸ボタンで並べます。"),
        ],
        "02_media_viewer_ui.svg",
    )


def screen_mylist_ai() -> None:
    e = phone(APP_BG)
    e.append(app_bar("マイリスト", left="≡", actions=["＋", "AI"]))
    e.append(gallery_control_bar(disabled_filter=True))
    start_y = SY + 138
    labels = [("Favorites", "428 枚"), ("Untagged", "186 枚"), ("landscape", "94 枚"), ("portrait", "76 枚"), ("blue_sky", "51 枚"), ("AI未分析", "324 枚")]
    cell_w = (SW - 48) / 3
    for i, (title, count) in enumerate(labels):
        x = SX + 16 + (i % 3) * (cell_w + 8)
        y = start_y + (i // 3) * 80
        e.append(category_tile(x, y, cell_w, title, count, i, show_thumb=False))
    e.append(dialog_box(SX + 32, SY + 364, SW - 64, 274, "AI解析の実行", ["解析対象の期間を選択してください:", "○ 直近7日間", "● 直近30日間", "○ すべての期間", "対象画像: 324 枚", "予想時間: 約 16 分"], "解析開始"))
    e.append(bottom_nav("マイリスト"))
    e += [marker(1, SX + 118, SY + 28), marker(2, SX + SW - 66, SY + 28), marker(3, SX + 68, start_y + 22), marker(4, SX + SW / 2, SY + 404)]
    render(
        "My List・AI分析UIモック",
        e,
        [
            Legend(1, "CategoryScreen", "My List はカテゴリ一覧画面として表示されます。"),
            Legend(2, "AI/タグ追加", "右上に AI解析 と タグ作成 の IconButton を追加します。"),
            Legend(3, "3列カテゴリ", "実装では初期3列、サムネイルなしでカテゴリ名と件数を並べます。"),
            Legend(4, "AI解析ダイアログ", "期間選択、対象枚数、予想時間、解析開始を表示します。"),
        ],
        "03_mylist_ai_ui.svg",
    )


def screen_folder_trash_bulk() -> None:
    e = phone(APP_BG)
    e.append(app_bar("フォルダ", left="≡", actions=["＋"]))
    e.append(gallery_control_bar(disabled_filter=True))
    start_y = SY + 138
    folders = [("Camera", "142 枚"), ("Download", "283 枚"), ("X_Downloads", "71 枚"), ("References", "36 枚"), ("Screenshots", "204 枚"), ("Wallpapers", "58 枚"), ("Manga", "19 枚"), ("AI", "93 枚")]
    cell_w = (SW - 56) / 4
    for i, (title, count) in enumerate(folders):
        x = SX + 12 + (i % 4) * (cell_w + 10)
        y = start_y + (i // 4) * 128
        e.append(category_tile(x, y, cell_w, title, count, i, show_thumb=True))
    sel_y = SY + 410
    e.append(rect(SX, sel_y, SW, 56, fill="#D0BCFF"))
    e.append(text(SX + 28, sel_y + 35, "×", "barIcon", fill="#1D192B", text_anchor="middle"))
    e.append(text(SX + 62, sel_y + 34, "8 件選択中", "cardTitle", fill="#1D192B"))
    e.append(text(SX + SW - 70, sel_y + 34, "♥  ⋮", "barIconSmall", fill="#1D192B", text_anchor="middle"))
    e.append(rect(SX + 28, SY + 500, SW - 56, 116, "m3Card", rx=8))
    e.append(text(SX + 48, SY + 532, "ゴミ箱", "cardTitle"))
    e.append(text(SX + 48, SY + 556, "復元 / 完全に削除は TrashScreen の上部ボタンで実行", "cardSub"))
    e.append(button(SX + 48, SY + 572, 86, "復元"))
    e.append(button(SX + 146, SY + 572, 118, "完全に削除", "#B3261E", "#FFFFFF"))
    e.append(bottom_nav("フォルダ"))
    e += [marker(1, SX + 116, SY + 28), marker(2, SX + SW - 54, SY + 28), marker(3, SX + 64, start_y + 52), marker(4, SX + 145, sel_y + 28), marker(5, SX + 68, SY + 532)]
    render(
        "フォルダ・ゴミ箱・一括編集UIモック",
        e,
        [
            Legend(1, "フォルダ画面", "CategoryScreen を使い、黒ヘッダーとDarkGray操作バーを表示します。"),
            Legend(2, "作成ボタン", "フォルダ一覧では右上にフォルダ作成 IconButton を表示します。"),
            Legend(3, "4列カテゴリ", "フォルダは代表サムネイル、名称、件数を4列グリッドで表示します。"),
            Legend(4, "選択モード", "長押し後は選択バーに件数、お気に入り、その他メニューを表示します。"),
            Legend(5, "TrashScreen", "ゴミ箱は別画面で復元と完全削除ボタンを持ちます。"),
        ],
        "04_folder_trash_bulk_ui.svg",
    )


def screen_x_downloader() -> None:
    e = phone(BLACK)
    e.append(app_bar("Video Downloader", left="≡"))
    y = SY + 88
    e.append(rect(SX + 16, y, SW - 32, 58, "inputBox", rx=4))
    e.append(text(SX + 30, y - 4, "X (Twitter) URL", "inputLabel"))
    e.append(text(SX + 48, y + 36, "https://x.com/user/status/...", "inputText"))
    e.append(text(SX + 30, y + 36, "🔗", "inputText"))
    e.append(button(SX + 16, y + 78, SW - 32, "Show download options", X_BLUE, "#FFFFFF"))
    hist_y = y + 170
    e.append(text(SX + 48, hist_y, "Download history", "cardTitle"))
    e.append(text(SX + SW - 60, hist_y, "Clear history", "muted", text_anchor="middle"))
    for i, status in enumerate(["COMPLETED", "COMPLETED", "FAILED"]):
        yy = hist_y + 22 + i * 86
        e.append(rect(SX + 16, yy, SW - 32, 74, "m3Card", rx=8))
        e.append(thumb(SX + 28, yy + 10, 96, 54, i, square=False))
        e.append(text(SX + 140, yy + 28, f"X download #{i + 1}", "cardTitle"))
        e.append(text(SX + 140, yy + 48, "https://x.com/...", "cardSub"))
        e.append(text(SX + SW - 50, yy + 64, status, "cardSub", fill="#4CAF50" if status == "COMPLETED" else "#F44336", text_anchor="middle"))
    e.append(dialog_box(SX + 42, SY + 470, SW - 84, 248, "Download settings", ["Select quality:", "● High (1080p)", "○ Medium (720p)", "○ Low (480p)", "HOME     Cancel"], "Start download"))
    e += [marker(1, SX + 110, SY + 28), marker(2, SX + 92, y + 30), marker(3, SX + SW / 2, y + 100), marker(4, SX + 120, hist_y + 58), marker(5, SX + SW / 2, SY + 512)]
    render(
        "XダウンロードUIモック",
        e,
        [
            Legend(1, "TopAppBar", "実装タイトルは Video Downloader。左にメニューを表示します。"),
            Legend(2, "OutlinedTextField", "ラベルは X (Twitter) URL、Link アイコン付きです。"),
            Legend(3, "実行ボタン", "青いボタンで Download settings ダイアログを開きます。"),
            Legend(4, "履歴カード", "黒背景に Card、96x64 のプレビュー、URL、日時、状態を表示します。"),
            Legend(5, "設定ダイアログ", "画質選択、GIF検出、HOME/Cancel、Start download を表示します。"),
        ],
        "05_x_downloader_ui.svg",
    )


def screen_book_viewer() -> None:
    e = phone(BLACK)
    e.append(rect(SX, SY, SW, SH, fill=BLACK))
    e.append(rect(SX, SY, SW, 56, fill="#00000099"))
    e.append(text(SX + 28, SY + 36, "×", "barIcon", text_anchor="middle"))
    e.append(text(SX + 64, SY + 35, "sample_book.zip", "barTitle"))
    e.append(text(SX + SW - 70, SY + 35, "🔖", "barIconSmall", text_anchor="middle"))
    e.append(text(SX + SW - 28, SY + 35, "⋮", "barIconSmall", text_anchor="middle"))
    e.append(rect(SX + 38, SY + 110, 145, 440, fill="#141414"))
    e.append(thumb(SX + 48, SY + 124, 125, 412, 1))
    e.append(rect(SX + 202, SY + 110, 145, 440, fill="#141414"))
    e.append(thumb(SX + 212, SY + 124, 125, 412, 2))
    e.append(rect(SX, SY + SH - 134, SW, 134, fill="#00000099"))
    e.append(text(SX + SW / 2, SY + SH - 102, "128 / 240", "small", text_anchor="middle"))
    e.append(rect(SX + 74, SY + SH - 82, SW - 148, 5, "progressTrack", rx=3))
    e.append(rect(SX + 74, SY + SH - 82, 126, 5, "progress", rx=3))
    e.append(text(SX + 120, SY + SH - 42, "前の本", "muted", text_anchor="middle"))
    e.append(text(SX + SW - 120, SY + SH - 42, "次の本", "muted", text_anchor="middle"))
    e += [marker(1, SX + 145, SY + 28), marker(2, SX + SW / 2, SY + 320), marker(3, SX + SW / 2, SY + SH - 98), marker(4, SX + SW - 46, SY + 28)]
    render(
        "漫画ビューアUIモック",
        e,
        [
            Legend(1, "ヘッダー", "閉じる、タイトル、しおり、その他メニューを黒半透明で重ねます。"),
            Legend(2, "ページ表示", "ZIP/PDF を単ページまたは見開きで、黒背景に表示します。"),
            Legend(3, "下部操作", "ページ数、シークバー、前の本/次の本を下部に表示します。"),
            Legend(4, "その他メニュー", "タイトル検索、表示設定、スクリーンショット、画面回転を開きます。"),
        ],
        "06_book_viewer_ui.svg",
    )


def screen_reference_projects() -> None:
    e = phone(BLACK)
    e.append(app_bar("お絵描き資料", left="≡", centered=True))
    y = SY + 78
    projects = [("キャラ立ち絵 参考", "進行中"), ("手とポーズ資料", "進行中"), ("背景ラフ用", "完了")]
    for i, (title, status) in enumerate(projects):
        yy = y + i * 86
        e.append(rect(SX + 16, yy, SW - 32, 70, "brownCard", rx=8))
        e.append(circle(SX + 48, yy + 35, 18, "iconCircle", fill="#263238"))
        e.append(text(SX + 48, yy + 41, "B" if status == "進行中" else "✓", "iconLabel", fill=CYAN if status == "進行中" else MUTED, text_anchor="middle"))
        e.append(text(SX + 80, yy + 30, title, "cardTitle"))
        e.append(text(SX + 80, yy + 52, status, "cardSub", fill="#4CAF50" if status == "進行中" else MUTED))
        e.append(text(SX + SW - 36, yy + 42, "D", "barIconSmall", fill=MUTED, text_anchor="middle"))
    e.append(circle(SX + SW - 50, SY + SH - 128, 28, fill=CYAN))
    e.append(text(SX + SW - 50, SY + SH - 119, "＋", "barIcon", fill="#001216", text_anchor="middle"))
    e.append(dialog_box(SX + 42, SY + 384, SW - 84, 196, "新規プロジェクト", ["プロジェクト名 (例: エルフの描き方)", "資料は Gallery/References に保存"], "作成"))
    e += [marker(1, SX + SW / 2, SY + 28), marker(2, SX + 120, y + 35), marker(3, SX + SW - 50, SY + SH - 128), marker(4, SX + SW / 2, SY + 424)]
    render(
        "お絵描き資料UIモック",
        e,
        [
            Legend(1, "CenterAlignedTopAppBar", "黒背景でタイトル中央、左にメニューを配置します。"),
            Legend(2, "プロジェクトカード", "Brush/Check アイコン、タイトル、進行中/完了、削除ボタンを持ちます。"),
            Legend(3, "FAB", "右下の FloatingActionButton から新規プロジェクトを作成します。"),
            Legend(4, "作成ダイアログ", "プロジェクト名入力と作成/キャンセルを表示します。"),
        ],
        "07_reference_projects_ui.svg",
    )


def screen_recommendations() -> None:
    e = phone(BLACK)
    e.append(app_bar("おすすめ", left="≡"))
    e.append(text(SX + SW / 2, SY + SH / 2 - 20, "おすすめのデータがまだありません。", "muted", text_anchor="middle"))
    e.append(text(SX + SW / 2, SY + SH / 2 + 4, "計測モードで画像を鑑賞してください。", "muted", text_anchor="middle"))
    e += [marker(1, SX + 112, SY + 28), marker(2, SX + 76, SY + SH / 2 - 8)]
    render(
        "おすすめ・視聴履歴UIモック",
        e,
        [
            Legend(1, "おすすめ画面", "実装は黒い TopAppBar と空状態メッセージのシンプルな構成です。"),
            Legend(2, "空状態", "計測モードで閲覧データが貯まるまで案内文を中央表示します。"),
        ],
        "08_recommendations_history_ui.svg",
    )


def screen_creators_sites() -> None:
    e = phone(CREATOR_BG)
    e.append(app_bar("Creators", left="≡", actions=["✎", "UP", "DL"], centered=True, bg=CREATOR_BG, fg=CREATOR_INK))
    y = SY + 78
    creators = [("artist_name", "pixiv / X / Support"), ("illustrator_02", "User sites / custom"), ("Untitled creator", "URL未設定")]
    for i, (name, sub) in enumerate(creators):
        yy = y + i * 118
        e.append(rect(SX + 16, yy, SW - 32, 100, "brownCard", rx=8))
        e.append(text(SX + 32, yy + 31, name, "brownTitle"))
        e.append(rect(SX + 32, yy + 48, SW - 64, 34, "brownField", rx=8))
        e.append(text(SX + 46, yy + 70, sub, "brownText"))
        e.append(text(SX + SW - 42, yy + 70, "↗", "brownAccent", text_anchor="middle"))
    sites_y = SY + 474
    e.append(rect(SX + 16, sites_y, SW - 32, 128, "brownCard", rx=8))
    e.append(text(SX + 32, sites_y + 30, "お気に入りサイト", "brownTitle"))
    e.append(text(SX + 32, sites_y + 58, "別画面: CenterAlignedTopAppBar「お気に入りサイト」", "brownText"))
    e.append(rect(SX + 32, sites_y + 74, SW - 64, 34, "brownField", rx=6))
    e.append(text(SX + 44, sites_y + 96, "https://example.com/search?q={query}", "brownAccent"))
    e += [marker(1, SX + SW / 2, SY + 28), marker(2, SX + 108, y + 32), marker(3, SX + SW - 64, SY + 28), marker(4, SX + 118, sites_y + 30)]
    render(
        "お気に入り作家・サイトUIモック",
        e,
        [
            Legend(1, "Creators画面", "実装は茶系テーマで CenterAlignedTopAppBar を使います。"),
            Legend(2, "作家カード", "作家名と登録済みリンクを Card / Surface で表示します。"),
            Legend(3, "DL/UP/編集", "バックアップ書き出し、読み込み、表示/編集切替を右上に置きます。"),
            Legend(4, "お気に入りサイト", "別画面も同じ茶系テーマでサイト名、説明、URLカードを表示します。"),
        ],
        "09_favorite_creators_sites_ui.svg",
    )


def screen_shared_services() -> None:
    e = phone(APP_BG)
    e.append(app_bar("すべて", left="≡"))
    e.append(gallery_control_bar())
    e.append(rect(SX, SY, SW * 0.76, SH, fill="#121212"))
    e.append(text(SX + 20, SY + 44, "Gallery", "barTitle"))
    e.append(text(SX + 20, SY + 72, "高機能ギャラリー", "muted"))
    drawer = ["ホーム", "フォルダ", "タグ", "本", "ゴミ箱", "動画DL", "お気に入りクリエイター", "お気に入りサイト", "お絵描き資料", "おすすめ（開発中）", "使い方ガイド", "このアプリについて"]
    y = SY + 108
    for i, label in enumerate(drawer[:10]):
        yy = y + i * 38
        if i == 0:
            e.append(rect(SX + 12, yy - 20, SW * 0.69, 34, fill="#2A2A2A", rx=17))
        e.append(text(SX + 32, yy, label, "txt"))
    op_y = SY + SH - 158
    e.append(rect(SX + 16, op_y, SW - 32, 84, fill="#000000D9", stroke="#FFFFFF22", rx=12))
    e.append(text(SX + 32, op_y + 28, "サムネイル生成", "cardTitle"))
    e.append(text(SX + SW - 52, op_y + 28, "42%", "cardTitle", fill=CYAN, text_anchor="middle"))
    e.append(rect(SX + 32, op_y + 48, SW - 64, 4, "progressTrack", rx=2))
    e.append(rect(SX + 32, op_y + 48, 132, 4, "progress", rx=2))
    e.append(text(SX + 32, op_y + 68, "MediaStore同期 / AIモデル準備 / Room更新", "muted"))
    e += [marker(1, SX + 52, SY + 28), marker(2, SX + 84, SY + 132), marker(3, SX + 130, op_y + 28), marker(4, SX + 242, SY + 278)]
    render(
        "共通基盤UIモック",
        e,
        [
            Legend(1, "ModalNavigationDrawer", "左スワイプ/メニューで主要機能と設定系へ移動します。"),
            Legend(2, "ドロワー項目", "MainActivity の実装ラベルをそのまま並べています。"),
            Legend(3, "GlobalProgressOverlay", "起動タスクや一括処理の進捗を黒いカードで重ねます。"),
            Legend(4, "共通状態", "GalleryState、Room、Repository、サービス処理が各画面を支えます。"),
        ],
        "10_shared_services_ui.svg",
    )


def main() -> None:
    screen_media_gallery()
    screen_media_viewer()
    screen_mylist_ai()
    screen_folder_trash_bulk()
    screen_x_downloader()
    screen_book_viewer()
    screen_reference_projects()
    screen_recommendations()
    screen_creators_sites()
    screen_shared_services()


if __name__ == "__main__":
    main()
