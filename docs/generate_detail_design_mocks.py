from __future__ import annotations

from dataclasses import dataclass
from html import escape
from pathlib import Path
from textwrap import dedent
from typing import Iterable


OUT_DIR = Path(__file__).resolve().parent / "images" / "detail_design"
W, H = 1200, 820
PX, PY, PW, PH = 58, 38, 388, 744
SX, SY, SW, SH = PX + 18, PY + 28, PW - 36, PH - 56


@dataclass
class Callout:
    n: int
    x: int
    y: int
    title: str
    body: str


def tag(name: str, attrs: dict[str, object] | None = None, body: str | None = None) -> str:
    attrs = attrs or {}
    attr = " ".join(f'{k.replace("_", "-")}="{escape(str(v), quote=True)}"' for k, v in attrs.items())
    if attr:
        attr = " " + attr
    if body is None:
        return f"<{name}{attr}/>"
    return f"<{name}{attr}>{body}</{name}>"


def text(x: int, y: int, value: str, cls: str = "txt", **attrs: object) -> str:
    merged = {"x": x, "y": y, "class": cls, **attrs}
    return tag("text", merged, escape(value))


def rect(x: int, y: int, w: int, h: int, cls: str = "panel", **attrs: object) -> str:
    return tag("rect", {"x": x, "y": y, "width": w, "height": h, "class": cls, **attrs})


def line(x1: int, y1: int, x2: int, y2: int, cls: str = "line") -> str:
    return tag("line", {"x1": x1, "y1": y1, "x2": x2, "y2": y2, "class": cls})


def circle(x: int, y: int, r: int, cls: str = "dot") -> str:
    return tag("circle", {"cx": x, "cy": y, "r": r, "class": cls})


def number(n: int, x: int, y: int) -> str:
    return (
        circle(x, y, 12, "num")
        + text(x, y + 4, str(n), "numText", **{"text-anchor": "middle"})
    )


def chip(x: int, y: int, label: str, selected: bool = False, w: int | None = None) -> str:
    width = w or max(48, 18 + len(label) * 9)
    cls = "chip selected" if selected else "chip"
    return rect(x, y, width, 28, cls, rx=14) + text(x + 12, y + 18, label, "chipText")


def icon_button(x: int, y: int, label: str, active: bool = False) -> str:
    cls = "icon activeIcon" if active else "icon"
    return rect(x, y, 32, 32, cls, rx=16) + text(x + 16, y + 21, label, "iconText", **{"text-anchor": "middle"})


def phone_base(title: str, subtitle: str = "") -> list[str]:
    e: list[str] = [
        rect(PX, PY, PW, PH, "phone", rx=34),
        rect(SX, SY, SW, SH, "screen", rx=20),
        rect(SX, SY, SW, 56, "appbar", rx=20),
        rect(SX, SY + 36, SW, 20, "appbar"),
        circle(SX + 22, SY + 28, 2, "sensor"),
        text(SX + 44, SY + 35, title, "title"),
    ]
    if subtitle:
        e.append(text(SX + 44, SY + 51, subtitle, "tinyDim"))
    return e


def bottom_nav(active: str) -> str:
    labels = [("ホーム", "H"), ("フォルダ", "F"), ("マイリスト", "M"), ("本", "B"), ("ゴミ箱", "T")]
    y = SY + SH - 64
    item_w = SW / len(labels)
    parts = [rect(SX, y, SW, 64, "bottomBar")]
    for i, (label, short) in enumerate(labels):
        cx = int(SX + item_w * i + item_w / 2)
        selected = label == active
        parts.append(circle(cx, y + 23, 15, "navCircleOn" if selected else "navCircle"))
        parts.append(text(cx, y + 28, short, "navIcon", **{"text-anchor": "middle"}))
        parts.append(text(cx, y + 51, label, "navTextOn" if selected else "navText", **{"text-anchor": "middle"}))
    return "".join(parts)


def thumb(x: int, y: int, w: int, h: int, i: int, label: str = "") -> str:
    g = f"thumbGrad{i % 8}"
    parts = [rect(x, y, w, h, "thumb", rx=7, fill=f"url(#{g})")]
    if label:
        parts.append(rect(x + 5, y + h - 22, min(w - 10, 52), 17, "badge", rx=8))
        parts.append(text(x + 12, y + h - 9, label, "badgeText"))
    if i % 5 == 0:
        parts.append(text(x + w - 18, y + 19, "★", "star", **{"text-anchor": "middle"}))
    return "".join(parts)


def grid(x: int, y: int, cols: int, rows: int, cell_w: int, cell_h: int, gap: int = 8) -> str:
    parts: list[str] = []
    for r in range(rows):
        for c in range(cols):
            idx = r * cols + c
            label = "GIF" if idx in (2, 9) else ("00:12" if idx in (5, 11) else "")
            parts.append(thumb(x + c * (cell_w + gap), y + r * (cell_h + gap), cell_w, cell_h, idx, label))
    return "".join(parts)


def row_card(x: int, y: int, w: int, h: int, title: str, sub: str, accent: str = "") -> str:
    parts = [rect(x, y, w, h, "card", rx=8)]
    if accent:
        parts.append(rect(x, y, 4, h, accent, rx=2))
    parts.append(text(x + 16, y + 26, title, "cardTitle"))
    parts.append(text(x + 16, y + 48, sub, "smallDim"))
    parts.append(text(x + w - 28, y + 36, "⋮", "menu", **{"text-anchor": "middle"}))
    return "".join(parts)


def dialog(x: int, y: int, w: int, h: int, title: str, lines: Iterable[str], action: str) -> str:
    parts = [rect(x, y, w, h, "scrim", rx=12), rect(x + 8, y + 8, w - 16, h - 16, "dialog", rx=12)]
    parts.append(text(x + 26, y + 42, title, "dialogTitle"))
    yy = y + 72
    for line_value in lines:
        parts.append(text(x + 26, yy, line_value, "dialogText"))
        yy += 26
    parts.append(rect(x + w - 132, y + h - 48, 104, 32, "primaryBtn", rx=16))
    parts.append(text(x + w - 80, y + h - 27, action, "btnText", **{"text-anchor": "middle"}))
    return "".join(parts)


def annotation_panel(title: str, callouts: list[Callout]) -> str:
    x, y = 486, 52
    parts = [
        text(x, y, title, "docTitle"),
        text(x, y + 30, "実装画面の構成に合わせた詳細設計用モック", "docSub"),
        rect(x, y + 56, 640, 1, "separator"),
    ]
    cy = y + 94
    for item in callouts:
        parts.append(line(item.x, item.y, x + 20, cy - 6))
        parts.append(number(item.n, x + 20, cy - 6))
        parts.append(text(x + 48, cy - 12, item.title, "callTitle"))
        parts.append(text(x + 48, cy + 12, item.body, "callBody"))
        cy += 64
    return "".join(parts)


def render(title: str, body: list[str], callouts: list[Callout], filename: str) -> None:
    defs = dedent("""
    <defs>
      <linearGradient id="thumbGrad0" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#3e6bff"/><stop offset="1" stop-color="#101827"/></linearGradient>
      <linearGradient id="thumbGrad1" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#19b38b"/><stop offset="1" stop-color="#162021"/></linearGradient>
      <linearGradient id="thumbGrad2" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#ce5f4a"/><stop offset="1" stop-color="#271715"/></linearGradient>
      <linearGradient id="thumbGrad3" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#d9b94e"/><stop offset="1" stop-color="#201b12"/></linearGradient>
      <linearGradient id="thumbGrad4" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#ad7cff"/><stop offset="1" stop-color="#1a1628"/></linearGradient>
      <linearGradient id="thumbGrad5" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#e3659d"/><stop offset="1" stop-color="#27151d"/></linearGradient>
      <linearGradient id="thumbGrad6" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#4fb6d8"/><stop offset="1" stop-color="#111c22"/></linearGradient>
      <linearGradient id="thumbGrad7" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#8aa25e"/><stop offset="1" stop-color="#151b14"/></linearGradient>
    </defs>
    """).strip()
    style = dedent("""
    <style>
      svg{background:#101316;font-family:'Yu Gothic','Meiryo',system-ui,sans-serif}
      .phone{fill:#08090b;stroke:#2a2f36;stroke-width:3}
      .screen{fill:#111418}
      .appbar,.bottomBar{fill:#050607}
      .sensor{fill:#303640}
      .title{fill:#fff;font-size:17px;font-weight:700}
      .docTitle{fill:#f8fbff;font-size:26px;font-weight:800}
      .docSub{fill:#9ea7b3;font-size:14px}
      .txt{fill:#eaf0f6;font-size:13px}
      .small{fill:#d8dee6;font-size:12px}
      .tinyDim{fill:#89929d;font-size:10px}
      .smallDim{fill:#9aa3ad;font-size:11px}
      .chip{fill:#1b2027;stroke:#333a44;stroke-width:1}
      .chip.selected{fill:#063b43;stroke:#00d5ef}
      .chipText{fill:#dfe9ef;font-size:11px}
      .icon{fill:#171c22;stroke:#2e3540}
      .activeIcon{fill:#04353e;stroke:#00d5ef}
      .iconText{fill:#f2f7fb;font-size:14px;font-weight:700}
      .thumb{stroke:#222a33;stroke-width:1}
      .badge{fill:#050607cc}
      .badgeText{fill:#fff;font-size:9px;font-weight:700}
      .star{fill:#ffdb65;font-size:15px}
      .section{fill:#181d23}
      .card{fill:#1a1f26;stroke:#303844;stroke-width:1}
      .card2{fill:#131820;stroke:#333d49;stroke-width:1}
      .cardTitle{fill:#f6f8fb;font-size:13px;font-weight:700}
      .metric{fill:#ffffff;font-size:21px;font-weight:800}
      .metricLabel{fill:#9ca6b2;font-size:10px}
      .menu{fill:#b8c0cb;font-size:17px}
      .accentBlue{fill:#00c8e6}.accentGreen{fill:#25d68a}.accentYellow{fill:#ffd25d}.accentRed{fill:#ff6565}
      .scrim{fill:#00000099}
      .dialog{fill:#20252d;stroke:#48505c}
      .dialogTitle{fill:#fff;font-size:16px;font-weight:800}
      .dialogText{fill:#dce4ec;font-size:12px}
      .primaryBtn{fill:#00bcd4}
      .secondaryBtn{fill:#242b34;stroke:#46505c}
      .btnText{fill:#001216;font-size:12px;font-weight:800}
      .navCircle{fill:#1a2028}.navCircleOn{fill:#00bcd4}
      .navIcon{fill:#fff;font-size:11px;font-weight:900}
      .navText{fill:#8f99a5;font-size:9px}.navTextOn{fill:#dffaff;font-size:9px;font-weight:800}
      .scrollTrack{fill:#20252c}.scrollThumb{fill:#00bcd4}.bubble{fill:#00bcd4}
      .bubbleText{fill:#001216;font-size:11px;font-weight:800}
      .num{fill:#00bcd4}.numText{fill:#001216;font-size:12px;font-weight:900}
      .line{stroke:#00bcd4;stroke-width:1.4;stroke-opacity:.55}
      .callTitle{fill:#f4f8fc;font-size:15px;font-weight:800}
      .callBody{fill:#b5c0cc;font-size:12px}
      .separator{fill:#303844}
      .progressBg{fill:#2a313b}.progress{fill:#00bcd4}
      .input{fill:#11161c;stroke:#4c5664}
      .tabOn{fill:#00bcd4}.tabOff{fill:#222932}
      .tabTextOn{fill:#001216;font-size:11px;font-weight:800}.tabText{fill:#cbd4dd;font-size:11px}
    </style>
    """).strip()
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="title">'
        + f'<title id="title">{escape(title)}</title>'
        + defs
        + style
        + "".join(body)
        + annotation_panel(title, callouts)
        + "</svg>\n"
    )
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / filename).write_text(svg, encoding="utf-8")


def screen_media_gallery() -> None:
    e = phone_base("Gallery", "MediaStore / 日別グリッド")
    e += [icon_button(SX + SW - 112, SY + 14, "🔍"), icon_button(SX + SW - 74, SY + 14, "4"), icon_button(SX + SW - 36, SY + 14, "⋮")]
    y = SY + 70
    x = SX + 12
    for label, selected, width in [("すべて", True, 62), ("画像", False, 52), ("動画", False, 52), ("GIF", False, 46), ("日別", True, 52)]:
        e.append(chip(x, y, label, selected, width))
        x += width + 8
    e.append(text(SX + 16, y + 54, "2026年6月25日", "cardTitle"))
    e.append(text(SX + SW - 20, y + 54, "12,089 件", "smallDim", **{"text-anchor": "end"}))
    e.append(grid(SX + 14, y + 70, 4, 4, 76, 92))
    e.append(rect(SX + SW - 9, y + 80, 5, 430, "scrollTrack", rx=3))
    e.append(rect(SX + SW - 10, y + 184, 7, 86, "scrollThumb", rx=4))
    e.append(rect(SX + SW - 124, y + 192, 105, 32, "bubble", rx=16))
    e.append(text(SX + SW - 72, y + 213, "2025年11月", "bubbleText", **{"text-anchor": "middle"}))
    e.append(rect(SX, SY + SH - 118, SW, 54, "appbar"))
    e.append(text(SX + 18, SY + SH - 85, "12 件選択中", "title"))
    for i, icon in enumerate(["★", "移", "タグ", "削"]):
        e.append(icon_button(SX + SW - 152 + i * 36, SY + SH - 104, icon, i == 0))
    e.append(bottom_nav("ホーム"))
    calls = [
        Callout(1, SX + 105, SY + 28, "トップバー", "検索、列数、フィルタ/並び替えをホーム画面から直接操作します。"),
        Callout(2, SX + 90, y + 84, "日別ヘッダー", "日/月/年/ストレージ単位のグルーピング見出しをグリッド内に挿入します。"),
        Callout(3, SX + 170, y + 220, "メディアセル", "画像、GIF、動画バッジ、お気に入り状態を同じタイルで扱います。"),
        Callout(4, SX + SW - 62, y + 204, "スクロールバー位置ラベル", "バー操作中は左側に現在位置の年月を表示します。"),
        Callout(5, SX + 120, SY + SH - 90, "選択モード", "複数選択中は一括移動、タグ編集、削除などの操作を表示します。"),
    ]
    render("メディア一覧UIモック", e, calls, "01_media_gallery_ui.svg")


def screen_media_viewer() -> None:
    e = phone_base("", "")
    e.append(rect(SX, SY, SW, SH, "screen", rx=20))
    e.append(rect(SX, SY, SW, 58, "scrim"))
    e.append(text(SX + 22, SY + 36, "×", "title"))
    e.append(text(SX + SW // 2, SY + 36, "124 / 12089", "title", **{"text-anchor": "middle"}))
    for i, ic in enumerate(["★", "↻", "⋮"]):
        e.append(icon_button(SX + SW - 114 + i * 38, SY + 13, ic, i == 0))
    e.append(rect(SX + 20, SY + 96, SW - 40, 438, "card2", rx=12))
    e.append(thumb(SX + 38, SY + 116, SW - 76, 398, 4, "00:12"))
    e.append(circle(SX + SW // 2, SY + 312, 42, "scrim"))
    e.append(text(SX + SW // 2 + 3, SY + 326, "▶", "title", **{"text-anchor": "middle"}))
    e.append(rect(SX + 18, SY + 550, SW - 36, 66, "appbar", rx=12))
    e.append(text(SX + 30, SY + 574, "00:08", "small"))
    e.append(rect(SX + 78, SY + 567, 210, 5, "progressBg", rx=3))
    e.append(rect(SX + 78, SY + 567, 104, 5, "progress", rx=3))
    for i, ic in enumerate(["-1f", "▶", "+1f"]):
        e.append(chip(SX + 86 + i * 72, SY + 585, ic, i == 1, 58))
    e.append(rect(SX, SY + SH - 164, SW, 164, "section", rx=16))
    e.append(text(SX + 18, SY + SH - 132, "詳細・レコメンド", "dialogTitle"))
    e.append(chip(SX + 18, SY + SH - 110, "似た雰囲気", True, 86))
    e.append(chip(SX + 112, SY + SH - 110, "タグ編集", False, 76))
    e.append(text(SX + 20, SY + SH - 68, "danbooru: landscape / blue sky / original", "smallDim"))
    e.append(text(SX + 20, SY + SH - 42, "storage/emulated/0/Pictures/sample.mp4", "smallDim"))
    calls = [
        Callout(1, SX + SW // 2, SY + 36, "ビューアオーバーレイ", "閉じる、ページ番号、お気に入り、回転、メニューを全画面上に重ねます。"),
        Callout(2, SX + SW // 2, SY + 312, "画像/GIF/動画表示", "Coil と動画プレイヤーを切り替え、ズームやスワイプを同じ領域で扱います。"),
        Callout(3, SX + 190, SY + 590, "動画・フレーム操作", "再生、シーク、フレーム送り、フレーム保存などの動画操作をまとめます。"),
        Callout(4, SX + 120, SY + SH - 104, "詳細ボトムシート", "タグ、ファイル情報、似た画像候補、編集導線を下部に表示します。"),
    ]
    render("メディアビューアUIモック", e, calls, "02_media_viewer_ui.svg")


def screen_mylist_ai() -> None:
    e = phone_base("マイリスト", "お気に入り / AI分析")
    e += [icon_button(SX + SW - 112, SY + 14, "AI", True), icon_button(SX + SW - 74, SY + 14, "＋"), icon_button(SX + SW - 36, SY + 14, "⋮")]
    y = SY + 78
    metrics = [("お気に入り", "428"), ("未整理", "186"), ("AI未分析", "324"), ("ベクトル未分析", "91")]
    for i, (label, value) in enumerate(metrics):
        x = SX + 14 + (i % 2) * 166
        yy = y + (i // 2) * 88
        e.append(rect(x, yy, 156, 74, "card", rx=8))
        e.append(text(x + 14, yy + 26, value, "metric"))
        e.append(text(x + 14, yy + 52, label, "metricLabel"))
    e.append(text(SX + 16, y + 196, "タグカテゴリ", "cardTitle"))
    for i, label in enumerate(["人物", "背景", "ポーズ", "R-18", "未翻訳タグ"]):
        e.append(row_card(SX + 14, y + 218 + i * 58, SW - 28, 48, label, f"{(i + 2) * 41} 件", ["accentBlue", "accentGreen", "accentYellow", "accentRed", "accentBlue"][i]))
    e.append(dialog(SX + 34, SY + 394, SW - 68, 220, "AI解析の実行", ["対象画像: 324 枚", "タグ推論: Danbooru ONNX", "年齢判定: SFW / R-15 / R-18", "ベクトル生成: MediaPipe"], "開始"))
    e.append(bottom_nav("マイリスト"))
    calls = [
        Callout(1, SX + 112, y + 54, "集計カード", "お気に入り、未整理、AI未分析などの処理対象をひと目で確認します。"),
        Callout(2, SX + 100, y + 244, "カテゴリ一覧", "保存済みタグや状態別リストから該当メディアへ移動します。"),
        Callout(3, SX + 180, SY + 448, "AI解析ダイアログ", "ONNXタグ推論、年齢判定、特徴ベクトル生成をまとめて実行します。"),
        Callout(4, SX + 276, SY + 28, "AIアクション", "未分析メディアの一括解析や進捗確認に入る導線です。"),
    ]
    render("My List・AI分析UIモック", e, calls, "03_mylist_ai_ui.svg")


def screen_folder_trash() -> None:
    e = phone_base("フォルダ", "フォルダ管理 / 一括編集")
    e += [icon_button(SX + SW - 74, SY + 14, "＋", True), icon_button(SX + SW - 36, SY + 14, "⋮")]
    y = SY + 78
    for i, title in enumerate(["Camera", "Download", "X_Downloads"]):
        yy = y + i * 94
        e.append(rect(SX + 14, yy, SW - 28, 78, "card", rx=8))
        e.append(thumb(SX + 26, yy + 12, 54, 54, i))
        e.append(text(SX + 94, yy + 31, title, "cardTitle"))
        e.append(text(SX + 94, yy + 54, f"{(i + 3) * 142} 枚 / 最新 2026-06-{25 - i}", "smallDim"))
        e.append(text(SX + SW - 34, yy + 44, "⋮", "menu", **{"text-anchor": "middle"}))
    e.append(rect(SX, SY + 382, SW, 58, "appbar"))
    e.append(text(SX + 18, SY + 417, "8 件選択中", "title"))
    for i, ic in enumerate(["移", "タグ", "年齢", "削"]):
        e.append(icon_button(SX + SW - 152 + i * 36, SY + 395, ic, i == 0))
    e.append(rect(SX + 14, SY + 464, SW - 28, 124, "card2", rx=10))
    e.append(text(SX + 30, SY + 492, "ゴミ箱", "dialogTitle"))
    e.append(text(SX + 30, SY + 520, "削除済みメディア 23 件", "smallDim"))
    e.append(chip(SX + 30, SY + 540, "復元", True, 68))
    e.append(chip(SX + 108, SY + 540, "完全に削除", False, 98))
    e.append(thumb(SX + 228, SY + 486, 48, 64, 3))
    e.append(thumb(SX + 284, SY + 486, 48, 64, 5))
    e.append(bottom_nav("フォルダ"))
    calls = [
        Callout(1, SX + 260, SY + 28, "フォルダ操作", "追加、並び替え、メニューから名称変更や削除を行います。"),
        Callout(2, SX + 116, y + 34, "フォルダカード", "代表サムネイル、件数、最新日付を表示してフォルダへ遷移します。"),
        Callout(3, SX + 120, SY + 410, "一括編集バー", "選択中メディアの移動、タグ/年齢編集、ゴミ箱移動を実行します。"),
        Callout(4, SX + 116, SY + 546, "ゴミ箱操作", "アプリ内削除済みデータの復元と完全削除を分けて扱います。"),
    ]
    render("フォルダ・ゴミ箱・一括編集UIモック", e, calls, "04_folder_trash_bulk_ui.svg")


def screen_x_downloader() -> None:
    e = phone_base("Video Downloader", "X / Twitter URL")
    e.append(icon_button(SX + SW - 36, SY + 14, "↻"))
    y = SY + 80
    e.append(text(SX + 16, y, "X (Twitter) URL", "cardTitle"))
    e.append(rect(SX + 14, y + 14, SW - 28, 46, "input", rx=8))
    e.append(text(SX + 28, y + 43, "https://x.com/user/status/...", "smallDim"))
    e.append(rect(SX + 14, y + 76, SW - 28, 44, "primaryBtn", rx=22))
    e.append(text(SX + SW // 2, y + 104, "Show download options", "btnText", **{"text-anchor": "middle"}))
    e.append(text(SX + 16, y + 158, "Download history", "cardTitle"))
    for i, label in enumerate(["COMPLETED  18.4MB", "GIF SAVED  6.1MB", "FAILED  Retry available"]):
        yy = y + 178 + i * 70
        e.append(rect(SX + 14, yy, SW - 28, 58, "card", rx=8))
        e.append(thumb(SX + 24, yy + 8, 48, 42, i, "X"))
        e.append(text(SX + 84, yy + 28, label, "cardTitle"))
        e.append(text(SX + 84, yy + 48, "2026-06-25  /  Pictures/X_Downloads", "smallDim"))
    e.append(dialog(SX + 34, SY + 418, SW - 68, 210, "Download settings", ["Quality: High / Medium / Low", "Duplicate check: enabled", "GIFはGIFとして保存", "共有URLをクリップボードより優先"], "Start"))
    calls = [
        Callout(1, SX + 82, y + 42, "URL入力", "共有、VIEW、クリップボード、手入力のURLを同じ入力欄に反映します。"),
        Callout(2, SX + 190, y + 100, "オプション表示", "URL解決後に画質や保存形式を選んでダウンロードを開始します。"),
        Callout(3, SX + 132, y + 238, "履歴リスト", "成功、失敗、GIF保存、重複判定の結果を履歴として残します。"),
        Callout(4, SX + 170, SY + 472, "設定ダイアログ", "解決したメディア候補ごとに保存品質と実行/キャンセルを選びます。"),
    ]
    render("XダウンロードUIモック", e, calls, "05_x_downloader_ui.svg")


def screen_book_viewer() -> None:
    e = phone_base("", "")
    e.append(rect(SX, SY, SW, SH, "screen", rx=20))
    e.append(rect(SX, SY, SW, 58, "scrim"))
    e.append(text(SX + 20, SY + 36, "×", "title"))
    e.append(text(SX + 62, SY + 36, "sample_book.zip", "title"))
    for i, ic in enumerate(["🔖", "⋮"]):
        e.append(icon_button(SX + SW - 74 + i * 38, SY + 13, ic, i == 0))
    e.append(rect(SX + 24, SY + 104, 145, 432, "card2", rx=4))
    e.append(thumb(SX + 34, SY + 116, 125, 408, 6))
    e.append(rect(SX + 183, SY + 104, 145, 432, "card2", rx=4))
    e.append(thumb(SX + 193, SY + 116, 125, 408, 1))
    e.append(text(SX + SW // 2, SY + 570, "128 / 240", "title", **{"text-anchor": "middle"}))
    e.append(rect(SX + 54, SY + 588, SW - 108, 5, "progressBg", rx=3))
    e.append(rect(SX + 54, SY + 588, 142, 5, "progress", rx=3))
    e.append(chip(SX + 72, SY + 608, "前の本", False, 84))
    e.append(chip(SX + 196, SY + 608, "次の本", True, 84))
    e.append(rect(SX, SY + SH - 144, SW, 144, "section", rx=16))
    e.append(text(SX + 18, SY + SH - 112, "本ビュワー設定", "dialogTitle"))
    for i, label in enumerate(["自動", "見開き", "右から左", "画面内", "高画質"]):
        e.append(chip(SX + 18 + (i % 3) * 102, SY + SH - 88 + (i // 3) * 36, label, i in (0, 2), 92))
    calls = [
        Callout(1, SX + 136, SY + 36, "ビューアヘッダー", "本タイトル、しおり、検索、表示設定、スクリーンショットを扱います。"),
        Callout(2, SX + 176, SY + 300, "ページ表示", "ZIP/PDFを単ページまたは見開きで表示し、読み方向に応じてページを並べます。"),
        Callout(3, SX + 176, SY + 590, "ページ操作", "ページ数、シーク、前後の本への移動を下部にまとめます。"),
        Callout(4, SX + 130, SY + SH - 88, "表示設定", "レイアウト、読み方向、フィット、背景、画質、先読みを変更します。"),
    ]
    render("漫画ビューアUIモック", e, calls, "06_book_viewer_ui.svg")


def screen_reference_projects() -> None:
    e = phone_base("お絵描き資料", "参照プロジェクト")
    e += [icon_button(SX + SW - 74, SY + 14, "＋", True), icon_button(SX + SW - 36, SY + 14, "⋮")]
    y = SY + 78
    for i, (title, sub) in enumerate([("キャラ立ち絵 参考", "進行中 / 36 枚"), ("手とポーズ資料", "進行中 / 18 枚"), ("背景ラフ用", "完了 / 54 枚")]):
        yy = y + i * 86
        e.append(rect(SX + 14, yy, SW - 28, 72, "card", rx=8))
        for t in range(3):
            e.append(thumb(SX + 26 + t * 30, yy + 12, 26, 48, i + t))
        e.append(text(SX + 132, yy + 30, title, "cardTitle"))
        e.append(text(SX + 132, yy + 52, sub, "smallDim"))
        e.append(text(SX + SW - 34, yy + 42, "⋮", "menu", **{"text-anchor": "middle"}))
    e.append(rect(SX + 14, SY + 372, SW - 28, 170, "card2", rx=10))
    e.append(text(SX + 30, SY + 400, "プロジェクト詳細", "dialogTitle"))
    e.append(grid(SX + 30, SY + 418, 5, 2, 52, 52, 6))
    e.append(chip(SX + 30, SY + 532, "Web検索", True, 78))
    e.append(chip(SX + 118, SY + 532, "スクショ保存", False, 98))
    e.append(dialog(SX + 42, SY + 560, SW - 84, 116, "完了確認", ["一時フォルダから整理します。"], "完了"))
    calls = [
        Callout(1, SX + 264, SY + 28, "プロジェクト追加", "制作テーマごとに一時資料フォルダを作成します。"),
        Callout(2, SX + 148, y + 32, "プロジェクト一覧", "代表画像、状態、資料枚数をカードで表示します。"),
        Callout(3, SX + 162, SY + 454, "資料グリッド", "Web検索や画像長押しで追加した参考画像をまとめて参照します。"),
        Callout(4, SX + 170, SY + 610, "完了フロー", "制作終了時に一時資料を整理し、プロジェクトを完了状態にします。"),
    ]
    render("お絵描き資料UIモック", e, calls, "07_reference_projects_ui.svg")


def screen_recommendations() -> None:
    e = phone_base("おすすめ", "視聴履歴 / 類似画像")
    e.append(icon_button(SX + SW - 36, SY + 14, "↻"))
    y = SY + 78
    tabs = [("最近よく見る", True), ("似た画像", False), ("ランダム", False)]
    x = SX + 14
    for label, selected in tabs:
        e.append(rect(x, y, 100, 30, "tabOn" if selected else "tabOff", rx=15))
        e.append(text(x + 50, y + 20, label, "tabTextOn" if selected else "tabText", **{"text-anchor": "middle"}))
        x += 108
    e.append(text(SX + 16, y + 62, "閲覧履歴からの候補", "cardTitle"))
    for i in range(3):
        yy = y + 82 + i * 92
        e.append(rect(SX + 14, yy, SW - 28, 78, "card", rx=8))
        e.append(thumb(SX + 24, yy + 10, 58, 58, i + 3, "REC"))
        e.append(text(SX + 96, yy + 30, ["よく見るタグに近い画像", "閲覧時間が長い作品", "ベクトル類似候補"][i], "cardTitle"))
        e.append(text(SX + 96, yy + 54, f"score 0.{93 - i * 7} / viewed {(i + 1) * 8} 回", "smallDim"))
        e.append(rect(SX + 250, yy + 58, 70, 5, "progressBg", rx=3))
        e.append(rect(SX + 250, yy + 58, 58 - i * 12, 5, "progress", rx=3))
    e.append(rect(SX + 14, SY + 520, SW - 28, 78, "card2", rx=8))
    e.append(text(SX + 30, SY + 548, "計測モード", "dialogTitle"))
    e.append(text(SX + 30, SY + 574, "閲覧回数、滞在時間、タグ類似度を recommendation 表示へ反映", "smallDim"))
    calls = [
        Callout(1, SX + 130, y + 20, "推薦タブ", "閲覧履歴、タグ類似、ベクトル類似、ランダム候補を切り替えます。"),
        Callout(2, SX + 138, y + 116, "推薦カード", "サムネイル、推薦理由、スコア、閲覧指標を並べて提示します。"),
        Callout(3, SX + 112, SY + 548, "計測データ", "閲覧回数や滞在時間を保存し、おすすめ生成の入力にします。"),
        Callout(4, SX + 298, SY + 28, "再計算", "履歴やAI特徴量を使って候補を更新します。"),
    ]
    render("おすすめ・視聴履歴UIモック", e, calls, "08_recommendations_history_ui.svg")


def screen_creators_sites() -> None:
    e = phone_base("お気に入りクリエイター", "Creators / Sites")
    e += [icon_button(SX + SW - 112, SY + 14, "⇅"), icon_button(SX + SW - 74, SY + 14, "＋", True), icon_button(SX + SW - 36, SY + 14, "⋮")]
    y = SY + 78
    e.append(rect(SX + 14, y, 156, 30, "tabOn", rx=15))
    e.append(text(SX + 92, y + 20, "クリエイター", "tabTextOn", **{"text-anchor": "middle"}))
    e.append(rect(SX + 180, y, 156, 30, "tabOff", rx=15))
    e.append(text(SX + 258, y + 20, "お気に入りサイト", "tabText", **{"text-anchor": "middle"}))
    for i, name in enumerate(["artist_name", "illustrator_02", "manga_creator"]):
        yy = y + 54 + i * 88
        e.append(rect(SX + 14, yy, SW - 28, 74, "card", rx=8))
        e.append(circle(SX + 48, yy + 37, 24, "navCircleOn" if i == 0 else "navCircle"))
        e.append(text(SX + 92, yy + 28, name, "cardTitle"))
        e.append(text(SX + 92, yy + 52, "pixiv / X / Fantia / custom links", "smallDim"))
        e.append(chip(SX + SW - 118, yy + 22, "開く", i == 0, 58))
    e.append(rect(SX + 14, SY + 432, SW - 28, 142, "card2", rx=10))
    e.append(text(SX + 30, SY + 462, "お気に入りサイト", "dialogTitle"))
    e.append(row_card(SX + 30, SY + 480, SW - 60, 46, "Pose Reference Search", "https://example.com/search?q={query}", "accentBlue"))
    e.append(row_card(SX + 30, SY + 532, SW - 60, 46, "Texture Archive", "カスタム検索サイト / JSONバックアップ対象", "accentGreen"))
    calls = [
        Callout(1, SX + 252, SY + 28, "インポート/追加", "JSONバックアップ、追加、編集メニューをトップバーに集約します。"),
        Callout(2, SX + 144, y + 20, "タブ切替", "作家リンク管理とサイトリンク管理を同じ導線で切り替えます。"),
        Callout(3, SX + 124, y + 88, "作家カード", "pixiv、X、支援サイト、検索リンクを作家単位で保存します。"),
        Callout(4, SX + 126, SY + 500, "サイトカード", "カスタム検索URLや説明を登録し、資料探しへ素早く移動します。"),
    ]
    render("お気に入り作家・サイトUIモック", e, calls, "09_favorite_creators_sites_ui.svg")


def screen_shared_services() -> None:
    e = phone_base("Gallery", "共通基盤 / 起動タスク")
    y = SY + 76
    e.append(rect(SX + 10, y, 156, 504, "card2", rx=8))
    e.append(text(SX + 26, y + 30, "Gallery", "dialogTitle"))
    drawer_items = ["ホーム", "フォルダ", "タグ", "本", "動画DL", "お絵描き資料", "おすすめ（開発中）", "使い方ガイド"]
    for i, label in enumerate(drawer_items):
        yy = y + 58 + i * 42
        e.append(rect(SX + 22, yy, 132, 30, "tabOn" if i == 0 else "tabOff", rx=15))
        e.append(text(SX + 88, yy + 20, label, "tabTextOn" if i == 0 else "tabText", **{"text-anchor": "middle"}))
    e.append(rect(SX + 180, y, 158, 88, "card", rx=8))
    e.append(text(SX + 194, y + 28, "起動処理", "cardTitle"))
    e.append(text(SX + 194, y + 52, "MediaStore同期 / サムネ生成", "smallDim"))
    e.append(rect(SX + 194, y + 66, 118, 5, "progressBg", rx=3))
    e.append(rect(SX + 194, y + 66, 74, 5, "progress", rx=3))
    e.append(rect(SX + 180, y + 110, 158, 90, "card", rx=8))
    e.append(text(SX + 194, y + 140, "AIモデル", "cardTitle"))
    e.append(text(SX + 194, y + 164, "ONNX / MediaPipe", "smallDim"))
    e.append(rect(SX + 180, y + 224, 158, 90, "card", rx=8))
    e.append(text(SX + 194, y + 254, "Room DB", "cardTitle"))
    e.append(text(SX + 194, y + 278, "metadata / tags / history", "smallDim"))
    e.append(dialog(SX + 184, y + 342, 146, 144, "一括処理", ["サムネイル生成中", "42% 完了"], "詳細"))
    e.append(bottom_nav("ホーム"))
    calls = [
        Callout(1, SX + 90, y + 86, "ナビゲーションドロワー", "主要画面、開発中機能、ガイド、アプリ情報へ移動します。"),
        Callout(2, SX + 250, y + 66, "起動タスク", "MediaStore同期、サムネイル生成、モデル準備を共通サービスで実行します。"),
        Callout(3, SX + 246, y + 254, "Room / Repository", "メディアメタデータ、タグ、履歴、参照資料を永続化します。"),
        Callout(4, SX + 250, y + 398, "グローバル進捗", "AI解析や一括処理の進行状況をアプリ全体で共有します。"),
    ]
    render("共通基盤UIモック", e, calls, "10_shared_services_ui.svg")


def main() -> None:
    screen_media_gallery()
    screen_media_viewer()
    screen_mylist_ai()
    screen_folder_trash()
    screen_x_downloader()
    screen_book_viewer()
    screen_reference_projects()
    screen_recommendations()
    screen_creators_sites()
    screen_shared_services()


if __name__ == "__main__":
    main()
