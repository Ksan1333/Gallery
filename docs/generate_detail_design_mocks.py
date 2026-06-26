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
PURPLE_BAR = "#D0BCFF"
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
    data = {"x": round(x, 2), "y": round(y, 2), "width": round(w, 2), "height": round(h, 2)}
    if cls:
        data["class"] = cls
    data.update(values)
    return tag("rect", data)


def circle(x: float, y: float, r: float, cls: str = "", **values: object) -> str:
    data = {"cx": round(x, 2), "cy": round(y, 2), "r": round(r, 2)}
    if cls:
        data["class"] = cls
    data.update(values)
    return tag("circle", data)


def text(x: float, y: float, value: str, cls: str = "txt", **values: object) -> str:
    data = {"x": round(x, 2), "y": round(y, 2), "class": cls}
    data.update(values)
    return tag("text", data, escape(value))


def line(x1: float, y1: float, x2: float, y2: float, cls: str = "line", **values: object) -> str:
    data = {"x1": round(x1, 2), "y1": round(y1, 2), "x2": round(x2, 2), "y2": round(y2, 2)}
    if cls:
        data["class"] = cls
    data.update(values)
    return tag("line", data)


def path(d: str, **values: object) -> str:
    data = {"d": d}
    data.update(values)
    return tag("path", data)


def polyline(points: str, **values: object) -> str:
    data = {"points": points}
    data.update(values)
    return tag("polyline", data)


def polygon(points: str, **values: object) -> str:
    data = {"points": points}
    data.update(values)
    return tag("polygon", data)


def group(body: str, **values: object) -> str:
    return tag("g", values, body)


def marker(n: int, x: float, y: float) -> str:
    return circle(x, y, 11, "marker") + text(x, y + 4, str(n), "markerText", text_anchor="middle")


def icon_line(x1: float, y1: float, x2: float, y2: float, color: str) -> str:
    return line(x1, y1, x2, y2, "", stroke=color, stroke_width=2, stroke_linecap="round")


def stroke_icon(name: str, cx: float, cy: float, size: float = 24, color: str = TEXT) -> str:
    common = {
        "fill": "none",
        "stroke": color,
        "stroke_width": 2,
        "stroke_linecap": "round",
        "stroke_linejoin": "round",
    }
    filled = {"fill": color, "stroke": "none"}
    body: list[str] = []

    if name == "menu":
        body += [icon_line(4, 6, 20, 6, color), icon_line(4, 12, 20, 12, color), icon_line(4, 18, 20, 18, color)]
    elif name == "back":
        body += [polyline("15,5 8,12 15,19", **common), icon_line(8, 12, 21, 12, color)]
    elif name == "forward":
        body += [polyline("9,5 16,12 9,19", **common), icon_line(3, 12, 16, 12, color)]
    elif name == "add":
        body += [icon_line(12, 5, 12, 19, color), icon_line(5, 12, 19, 12, color)]
    elif name == "close":
        body += [icon_line(6, 6, 18, 18, color), icon_line(18, 6, 6, 18, color)]
    elif name == "more":
        body += [circle(12, 5, 1.8, fill=color), circle(12, 12, 1.8, fill=color), circle(12, 19, 1.8, fill=color)]
    elif name == "filter":
        body.append(path("M4 5h16l-6.5 7v5.5L10.5 19v-7L4 5z", **common))
    elif name == "shield":
        body.append(path("M12 3l7 3v5c0 5-3 8.5-7 10-4-1.5-7-5-7-10V6l7-3z", **common))
    elif name == "sort":
        body += [path("M7 5v14M7 5l-3 3M7 5l3 3", **common), path("M17 19V5M17 19l-3-3M17 19l3-3", **common)]
    elif name == "home":
        body.append(path("M4 11l8-7 8 7v9h-5v-6H9v6H4v-9z", **common))
    elif name == "list":
        body += [
            icon_line(8, 6, 20, 6, color),
            icon_line(8, 12, 20, 12, color),
            icon_line(8, 18, 20, 18, color),
            circle(4, 6, 1.2, fill=color),
            circle(4, 12, 1.2, fill=color),
            circle(4, 18, 1.2, fill=color),
        ]
    elif name == "heart":
        body.append(path("M12 20s-7-4.5-9-9c-1.5-3.5 2.5-6.5 5.5-4.5 1.6 1 2.2 2.3 3.5 3.8 1.3-1.5 1.9-2.8 3.5-3.8 3-2 7 1 5.5 4.5-2 4.5-9 9-9 9z", **filled))
    elif name == "book":
        body += [path("M5 4h6a4 4 0 0 1 4 4v12H9a4 4 0 0 0-4 4V4z", **common), path("M15 4h4v16h-4", **common)]
    elif name == "trash":
        body += [path("M5 7h14M10 7V5h4v2M7 7l1 14h8l1-14", **common), icon_line(10, 11, 10, 18, color), icon_line(14, 11, 14, 18, color)]
    elif name == "delete_sweep":
        body += [path("M5 7h9M8 7V5h4v2M7 7l1 12h6l1-12", **common), icon_line(16, 10, 21, 10, color), icon_line(17, 14, 21, 14, color), icon_line(18, 18, 21, 18, color)]
    elif name == "download":
        body += [path("M12 4v10M8 10l4 4 4-4M5 20h14", **common)]
    elif name == "upload":
        body += [path("M12 20V10M8 14l4-4 4 4M5 4h14", **common)]
    elif name == "edit":
        body.append(path("M4 17.5V21h3.5L18.5 10 15 6.5 4 17.5zM14 7l3 3", **common))
    elif name == "auto":
        body += [path("M12 3l1.8 4.8L19 9.5l-5.2 1.7L12 16l-1.8-4.8L5 9.5l5.2-1.7L12 3z", **common), path("M5 15l.8 2.2L8 18l-2.2.8L5 21l-.8-2.2L2 18l2.2-.8L5 15z", **common)]
    elif name == "link":
        body += [path("M10 13a5 5 0 0 0 7 0l2-2a5 5 0 0 0-7-7l-1 1", **common), path("M14 11a5 5 0 0 0-7 0l-2 2a5 5 0 0 0 7 7l1-1", **common)]
    elif name == "history":
        body += [path("M4 12a8 8 0 1 0 2.3-5.7L4 8", **common), path("M4 4v4h4M12 8v5l3 2", **common)]
    elif name == "play":
        body.append(polygon("8,5 19,12 8,19", fill=color, stroke="none"))
    elif name == "pause":
        body += [rect(7, 5, 3.5, 14, fill=color), rect(13.5, 5, 3.5, 14, fill=color)]
    elif name == "volume":
        body += [path("M4 10h4l5-4v12l-5-4H4v-4z", **common), path("M16 9a4 4 0 0 1 0 6M18 6a8 8 0 0 1 0 12", **common)]
    elif name == "rotate":
        body.append(path("M5 12a7 7 0 1 0 2-5M5 5v6h6", **common))
    elif name == "bookmark":
        body.append(path("M7 4h10v17l-5-3-5 3V4z", **common))
    elif name == "brush":
        body.append(path("M15 4l5 5-9 9a4 4 0 0 1-5 1l-2 2 2-6a4 4 0 0 1 1-2l8-9z", **common))
    elif name == "check":
        body.append(polyline("4,13 9,18 20,6", **common))
    elif name == "search":
        body += [circle(10, 10, 5.5, **common), icon_line(14, 14, 20, 20, color)]
    elif name == "folder":
        body.append(path("M3 7h7l2 3h9v10H3V7z", **common))
    elif name == "folder_special":
        body += [path("M3 7h7l2 3h9v10H3V7z", **common), path("M16 12l1 2.2 2.3.2-1.8 1.5.6 2.2-2.1-1.2-2 1.2.6-2.2-1.8-1.5 2.3-.2L16 12z", **common)]
    elif name == "restore":
        body.append(path("M6 8v5h5M6 13a7 7 0 1 0 2-5", **common))
    elif name == "open":
        body += [path("M5 5h7M5 5v14h14v-7", **common), path("M13 4h7v7M20 4l-9 9", **common)]
    elif name == "image":
        body += [rect(4, 5, 16, 14, **common), circle(9, 10, 2, **common), path("M5 18l5-5 3 3 2-2 4 4", **common)]
    elif name == "camera":
        body += [path("M5 8h3l2-3h4l2 3h3v11H5V8z", **common), circle(12, 13, 3.5, **common)]
    elif name == "settings":
        body += [circle(12, 12, 3, **common), path("M12 3v3M12 18v3M4.2 7.5l2.6 1.5M17.2 15l2.6 1.5M4.2 16.5l2.6-1.5M17.2 9l2.6-1.5", **common)]
    elif name == "tag":
        body += [path("M4 11V5h6l10 10-6 6L4 11z", **common), circle(8, 8, 1.5, fill=color)]
    elif name == "wallpaper":
        body += [rect(5, 4, 14, 16, **common), path("M8 17h8M9 8h6M9 12h4", **common)]
    elif name == "screenshot":
        body += [path("M5 8V5h3M16 5h3v3M19 16v3h-3M8 19H5v-3", **common), rect(8, 8, 8, 8, **common)]
    else:
        body.append(circle(12, 12, 8, **common))

    scale = size / 24
    return group("".join(body), transform=f"translate({cx - 12 * scale:.2f} {cy - 12 * scale:.2f}) scale({scale:.4f})")


def phone(screen_bg: str = APP_BG) -> list[str]:
    return [
        rect(PX, PY, PW, PH, "device", rx=34),
        rect(SX, SY, SW, SH, "screen", rx=18, fill=screen_bg),
        circle(SX + SW / 2, SY - 10, 4, "cameraDot"),
    ]


def app_bar(title: str, left: str = "menu", actions: list[str] | None = None, centered: bool = False, bg: str = BLACK, fg: str = TEXT) -> str:
    actions = actions or []
    parts = [rect(SX, SY, SW, 56, fill=bg)]
    if left:
        parts.append(stroke_icon(left, SX + 28, SY + 28, 24, fg))
    tx = SX + SW / 2 if centered else SX + 58
    parts.append(text(tx, SY + 36, title, "barTitle", fill=fg, text_anchor="middle" if centered else "start"))
    for i, icon_name in enumerate(reversed(actions)):
        x = SX + SW - 28 - i * 38
        parts.append(stroke_icon(icon_name, x, SY + 28, 22, fg))
    return "".join(parts)


def gallery_control_bar(disabled_filter: bool = False) -> str:
    y = SY + 56
    parts = [rect(SX, y, SW, 56, fill=CONTROL_BG, opacity=0.95)]
    icons = [("filter", MUTED if disabled_filter else TEXT), ("shield", "#4CAF50"), ("sort", MUTED if disabled_filter else TEXT)]
    for i, (icon_name, fill) in enumerate(icons):
        x = SX + SW - 28 - (len(icons) - 1 - i) * 42
        parts.append(circle(x, y + 28, 18, "iconCircle"))
        parts.append(stroke_icon(icon_name, x, y + 28, 19, fill))
    return "".join(parts)


def bottom_nav(active: str) -> str:
    labels = [("すべて", "home"), ("フォルダ", "list"), ("マイリスト", "heart"), ("本", "book"), ("ゴミ箱", "trash")]
    y = SY + SH - 80
    x = SX + 8
    w = SW - 16
    item = w / len(labels)
    parts = [rect(x, y, w, 64, "bottomPill", rx=32)]
    for i, (label, icon_name) in enumerate(labels):
        cx = x + item * i + item / 2
        selected = label == active
        parts.append(stroke_icon(icon_name, cx, y + 23, 20, TEXT if selected else "#8F8F8F"))
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
        parts.append(marker(item.n, LEGX + 12, y - 6))
        parts.append(text(LEGX + 34, y - 10, item.title, "legendTitle"))
        parts.append(text(LEGX + 34, y + 14, item.body, "legendBody"))
        y += 74
    return "".join(parts)


def fav_icon(x: float, y: float, small: bool = False) -> str:
    return stroke_icon("heart", x, y, 10 if small else 14, "#F44336")


def thumb(x: float, y: float, w: float, h: float, i: int, label: str = "", square: bool = False, favorite: bool | None = None) -> str:
    rx = 1 if square else 8
    parts = [rect(x, y, w, h, "thumb", rx=rx, fill=f"url(#thumb{i % 8})")]
    if label:
        parts.append(rect(x + w - 42, y + h - 18, 38, 15, "badge", rx=2))
        parts.append(text(x + w - 23, y + h - 7, label, "badgeText", text_anchor="middle"))
    is_fav = i % 6 == 0 if favorite is None else favorite
    if is_fav:
        parts.append(fav_icon(x + 11, y + h - 10, small=w < 55))
    return "".join(parts)


def media_grid(x: float, y: float, cols: int, rows: int, cell: float, gap: float = 1.0) -> str:
    parts: list[str] = []
    for r in range(rows):
        for c in range(cols):
            i = r * cols + c
            label = "GIF" if i in (2, 13) else ("0:12" if i in (5, 11) else "")
            parts.append(thumb(x + c * (cell + gap), y + r * (cell + gap), cell, cell, i, label, square=True))
    return "".join(parts)


def dense_grid(x: float, y: float, cols: int = 28, rows: int = 6) -> str:
    cell = (SW - 24) / cols
    parts: list[str] = []
    for r in range(rows):
        for c in range(cols):
            i = r * cols + c
            parts.append(rect(x + c * cell, y + r * cell, cell - 0.4, cell - 0.4, "thumb", rx=0.5, fill=f"url(#thumb{i % 8})"))
    return "".join(parts)


def category_tile(x: float, y: float, w: float, title: str, count: str, i: int, show_thumb: bool = True) -> str:
    parts: list[str] = [rect(x, y, w, 134 if show_thumb else 62, fill="transparent")]
    if show_thumb:
        parts.append(thumb(x, y, w, w, i, favorite=False))
        ty = y + w + 17
    else:
        ty = y + 22
    parts.append(text(x + 4, ty, title, "catTitle"))
    parts.append(text(x + 4, ty + 18, count, "catSub"))
    return "".join(parts)


def card_row(x: float, y: float, w: float, h: float, title: str, sub: str, icon_name: str = "") -> str:
    parts = [rect(x, y, w, h, "m3Card", rx=8)]
    if icon_name:
        parts.append(circle(x + 30, y + h / 2, 18, "iconCircle"))
        parts.append(stroke_icon(icon_name, x + 30, y + h / 2, 18, TEXT))
        tx = x + 62
    else:
        tx = x + 16
    parts.append(text(tx, y + 30, title, "cardTitle"))
    parts.append(text(tx, y + 52, sub, "cardSub"))
    return "".join(parts)


def button(x: float, y: float, w: float, label: str, color: str = CYAN, text_color: str = "#001216") -> str:
    return rect(x, y, w, 42, fill=color, rx=21) + text(x + w / 2, y + 27, label, "buttonText", fill=text_color, text_anchor="middle")


def outlined_button(x: float, y: float, w: float, label: str, color: str = TEXT) -> str:
    return rect(x, y, w, 40, fill="#00000000", stroke=color, stroke_width=1.1, rx=20) + text(x + w / 2, y + 26, label, "buttonText", fill=color, text_anchor="middle")


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
          .cameraDot{fill:#1E242C}
          .barTitle{font-size:20px;font-weight:500}
          .iconCircle{fill:#2D2D2D}
          .thumb{stroke:#222;stroke-width:.6}
          .badge{fill:#000000AA}
          .badgeText{fill:#fff;font-size:9px;font-weight:700}
          .txt{fill:#fff;font-size:13px}
          .headerText{fill:#fff;font-size:20px;font-weight:500}
          .small{fill:#E8EAED;font-size:12px}
          .muted{fill:#9E9E9E;font-size:12px}
          .bottomPill{fill:#000000D9}
          .navText{fill:#8F8F8F;font-size:9px}
          .navTextOn{fill:#FFFFFF;font-size:9px;font-weight:700}
          .scrollThumb{fill:#FFFFFFCC}
          .scrollTrack{fill:#FFFFFF40}
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
          .chip{fill:#2D2D2D}
          .chipText{fill:#E8EAED;font-size:11px;font-weight:700}
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
    e.append(app_bar("すべて"))
    e.append(gallery_control_bar())
    y = SY + 126
    e.append(text(SX + 16, y, "2026年6月25日", "headerText"))
    e.append(media_grid(SX + 10, y + 16, 4, 6, (SW - 44) / 4))
    e.append(rect(SX + SW - 10, SY + 138, 4, 520, "scrollThumb", rx=2))
    e.append(bottom_nav("すべて"))
    e += [marker(1, SX + 150, SY + 28), marker(2, SX + SW - 73, SY + 84), marker(3, SX + 92, y + 18), marker(4, SX + SW / 2, SY + SH - 48)]
    render(
        "メディア一覧UIモック",
        e,
        [
            Legend(1, "TopAppBar", "黒ヘッダーに「すべて」とドロワーメニューを表示します。"),
            Legend(2, "操作バー", "フィルタ、年齢制限、並び替えを右寄せのアイコンボタンで表示します。"),
            Legend(3, "LazyVerticalGrid", "日付ヘッダーと正方形サムネイルを同じグリッドに表示します。"),
            Legend(4, "ボトムナビ", "ホーム、フォルダ、マイリスト、本、ゴミ箱へ移動します。"),
        ],
        "01_media_gallery_ui.svg",
    )


def screen_media_gallery_scrollbar() -> None:
    e = phone(APP_BG)
    e.append(app_bar("すべて"))
    e.append(gallery_control_bar())
    e.append(text(SX + 16, SY + 184, "2025年11月", "headerText"))
    e.append(media_grid(SX + 10, SY + 200, 4, 5, (SW - 44) / 4))
    e.append(rect(SX + SW - 12, SY + 126, 6, 660, "scrollTrack", rx=3))
    e.append(rect(SX + SW - 13, SY + 428, 8, 96, "scrollThumb", rx=4))
    e.append(rect(SX + SW - 146, SY + 456, 126, 34, "scrollLabel", rx=7))
    e.append(text(SX + SW - 83, SY + 478, "2025年11月", "scrollLabelText", text_anchor="middle"))
    e.append(bottom_nav("すべて"))
    e += [marker(1, SX + SW - 36, SY + 474), marker(2, SX + SW - 10, SY + 476), marker(3, SX + 130, SY + 302)]
    render(
        "スクロールバー追従UIモック",
        e,
        [
            Legend(1, "年月日ラベル", "ドラッグ中だけスクロールバー左に現在位置の年月日を表示します。"),
            Legend(2, "高速スクロールバー", "つまみ位置から対象インデックスを計算し、グリッドを間引き追従します。"),
            Legend(3, "追従中グリッド", "重くならない範囲で表示位置を更新し、リリース時に最終補正します。"),
        ],
        "01_media_gallery_scrollbar_ui.svg",
    )


def screen_media_gallery_dense28() -> None:
    e = phone(APP_BG)
    e.append(app_bar("すべて"))
    e.append(gallery_control_bar())
    e.append(text(SX + 16, SY + 186, "2026年", "headerText"))
    e.append(dense_grid(SX + 12, SY + 206))
    e.append(text(SX + 16, SY + 312, "2025年", "headerText"))
    e.append(dense_grid(SX + 12, SY + 332))
    e.append(rect(SX + 18, SY + 456, SW - 36, 44, fill="#242424", rx=6))
    e.append(text(SX + 32, SY + 484, "28列では1年あたり6段まで表示", "small"))
    e.append(rect(SX + SW - 10, SY + 136, 4, 610, "scrollThumb", rx=2))
    e.append(bottom_nav("すべて"))
    e += [marker(1, SX + 104, SY + 186), marker(2, SX + 238, SY + 238), marker(3, SX + 178, SY + 484)]
    render(
        "28列高密度表示UIモック",
        e,
        [
            Legend(1, "年ヘッダー", "28列時は年単位で大量メディアの位置を俯瞰します。"),
            Legend(2, "軽量サムネイル", "小さいセルはキャッシュ優先で描画し、スクロール負荷を抑えます。"),
            Legend(3, "6段制限", "1年あたり6段までに抑え、初期表示と再配置を軽くします。"),
        ],
        "01_media_gallery_dense28_ui.svg",
    )


def screen_media_gallery_selection() -> None:
    e = phone(APP_BG)
    e.append(app_bar("すべて"))
    e.append(gallery_control_bar())
    y = SY + 126
    e.append(text(SX + 16, y, "2026年6月25日", "headerText"))
    e.append(media_grid(SX + 10, y + 16, 4, 5, (SW - 44) / 4))
    for x, yy in [(SX + 94, y + 102), (SX + 183, y + 190), (SX + 272, y + 279)]:
        e.append(circle(x, yy, 16, fill="#00BCD4"))
        e.append(stroke_icon("check", x, yy, 18, "#001216"))
    sel_y = SY + 618
    e.append(rect(SX, sel_y, SW, 56, fill=PURPLE_BAR))
    e.append(stroke_icon("close", SX + 28, sel_y + 28, 24, "#1D192B"))
    e.append(text(SX + 62, sel_y + 34, "8 件選択中", "cardTitle", fill="#1D192B"))
    for i, icon_name in enumerate(["heart", "more"]):
        e.append(stroke_icon(icon_name, SX + SW - 70 + i * 42, sel_y + 28, 22, "#1D192B" if icon_name != "heart" else "#B3261E"))
    e.append(bottom_nav("すべて"))
    e += [marker(1, SX + 108, y + 112), marker(2, SX + 140, sel_y + 28), marker(3, SX + SW - 48, sel_y + 28)]
    render(
        "選択モードUIモック",
        e,
        [
            Legend(1, "選択状態", "長押しやドラッグ範囲選択でセルにチェックを重ねます。"),
            Legend(2, "選択バー", "選択件数、閉じる、お気に入り操作を表示します。"),
            Legend(3, "その他メニュー", "削除、フォルダ移動、一括タグ・評価編集を開きます。"),
        ],
        "01_media_gallery_selection_ui.svg",
    )


def screen_media_viewer() -> None:
    e = phone(BLACK)
    e.append(rect(SX, SY, SW, SH, fill=BLACK))
    e.append(thumb(SX + 18, SY + 88, SW - 36, 590, 4, favorite=False))
    e.append(rect(SX, SY, SW, 60, fill="#00000099"))
    e.append(stroke_icon("close", SX + 28, SY + 30, 24))
    e.append(text(SX + 64, SY + 37, "IMG_20260625_001.jpg", "small"))
    for i, icon_name in enumerate(["more", "rotate", "heart"]):
        e.append(stroke_icon(icon_name, SX + SW - 30 - i * 42, SY + 30, 22, "#F44336" if icon_name == "heart" else TEXT))
    e.append(rect(SX, SY + SH - 92, SW, 92, fill="#000000D9"))
    for i, icon_name in enumerate(["trash", "folder_special", "tag", "wallpaper", "screenshot"]):
        cx = SX + 54 + i * 72
        e.append(circle(cx, SY + SH - 46, 18, "iconCircle"))
        e.append(stroke_icon(icon_name, cx, SY + SH - 46, 19, "#F44336" if icon_name == "trash" else TEXT))
    e += [marker(1, SX + SW / 2, SY + 382), marker(2, SX + 112, SY + 30), marker(3, SX + SW - 72, SY + 30), marker(4, SX + 220, SY + SH - 46)]
    render(
        "メディアビューアUIモック",
        e,
        [
            Legend(1, "全画面表示", "画像、GIF、動画を黒背景で大きく表示します。"),
            Legend(2, "上部操作", "閉じる、ファイル名、回転、お気に入り、その他を重ねます。"),
            Legend(3, "対象操作", "お気に入りやその他メニューは表示メディアに対して実行します。"),
            Legend(4, "下部メニュー", "削除、フォルダ、タグ、壁紙、フレーム保存を開きます。"),
        ],
        "02_media_viewer_ui.svg",
    )


def screen_media_viewer_video_controls() -> None:
    e = phone(BLACK)
    e.append(rect(SX, SY, SW, SH, fill=BLACK))
    e.append(thumb(SX + 18, SY + 104, SW - 36, 500, 5, label="0:12", favorite=False))
    overlay_y = SY + SH - 214
    e.append(rect(SX, overlay_y, SW, 214, fill="#000000D9"))
    e.append(text(SX + 16, overlay_y + 30, "0:08 / 1:20", "small"))
    e.append(rect(SX + 96, overlay_y + 21, SW - 156, 4, "progressTrack", rx=2))
    e.append(rect(SX + 96, overlay_y + 21, 112, 4, "progress", rx=2))
    e.append(stroke_icon("volume", SX + SW - 28, overlay_y + 28, 22))
    for i, icon_name in enumerate(["back", "play", "forward"]):
        cx = SX + 128 + i * 70
        e.append(circle(cx, overlay_y + 76, 20, "iconCircle"))
        e.append(stroke_icon(icon_name, cx, overlay_y + 76, 23))
    tx = SX + 10
    for i in range(8):
        e.append(thumb(tx + i * 47, overlay_y + 118, 42, 42, i, square=False, favorite=False))
    e += [marker(1, SX + 182, overlay_y + 22), marker(2, SX + 268, overlay_y + 76), marker(3, SX + 92, overlay_y + 140)]
    render(
        "動画操作パネルUIモック",
        e,
        [
            Legend(1, "シークバー", "再生時間とシーク位置、ミュートを表示します。"),
            Legend(2, "再生操作", "再生/一時停止、前後移動、フレーム送り操作を置きます。"),
            Legend(3, "サムネイル列", "前後メディアやGIFフレーム確認用の横リストを表示します。"),
        ],
        "02_media_viewer_video_controls_ui.svg",
    )


def screen_media_viewer_recommendation_panel() -> None:
    e = phone(BLACK)
    e.append(rect(SX, SY, SW, SH, fill=BLACK))
    e.append(thumb(SX + 30, SY + 96, SW - 60, 460, 2, favorite=False))
    panel_y = SY + 590
    e.append(rect(SX, panel_y, SW, 320, fill="#121212", rx=18))
    e.append(text(SX + 22, panel_y + 36, "関連メディア", "cardTitle"))
    for i, score in enumerate(["0.93", "0.89", "0.84"]):
        x = SX + 22 + i * 122
        e.append(thumb(x, panel_y + 58, 110, 110, i + 2, favorite=False))
        e.append(rect(x + 65, panel_y + 142, 40, 18, "badge", rx=3))
        e.append(text(x + 85, panel_y + 155, score, "badgeText", text_anchor="middle"))
    e.append(text(SX + 22, panel_y + 206, "タグ類似 / ベクトル類似 / ランダム候補", "muted"))
    e += [marker(1, SX + SW / 2, SY + 330), marker(2, SX + 112, panel_y + 36), marker(3, SX + 196, panel_y + 116)]
    render(
        "関連メディアパネルUIモック",
        e,
        [
            Legend(1, "閲覧中メディア", "左右スワイプで移動しながら関連候補を確認できます。"),
            Legend(2, "関連パネル", "ビューア下部におすすめ候補を重ねて表示します。"),
            Legend(3, "候補カード", "スコア付きサムネイルから近いメディアを開きます。"),
        ],
        "02_media_viewer_recommendation_ui.svg",
    )


def screen_mylist_ai() -> None:
    e = phone(APP_BG)
    e.append(app_bar("マイリスト", actions=["add", "auto"]))
    e.append(gallery_control_bar(disabled_filter=True))
    start_y = SY + 138
    labels = [("Favorites", "428 枚"), ("Untagged", "186 枚"), ("landscape", "94 枚"), ("portrait", "76 枚"), ("blue_sky", "51 枚"), ("AI未分析", "324 枚")]
    cell_w = (SW - 48) / 3
    for i, (title, count) in enumerate(labels):
        x = SX + 16 + (i % 3) * (cell_w + 8)
        y = start_y + (i // 3) * 80
        e.append(category_tile(x, y, cell_w, title, count, i, show_thumb=False))
    e.append(bottom_nav("マイリスト"))
    e += [marker(1, SX + 118, SY + 28), marker(2, SX + SW - 66, SY + 28), marker(3, SX + 68, start_y + 22), marker(4, SX + SW / 2, SY + SH - 48)]
    render(
        "My ListカテゴリUIモック",
        e,
        [
            Legend(1, "CategoryScreen", "My Listはカテゴリ一覧画面として表示します。"),
            Legend(2, "追加/AI分析", "タグ作成とAI分析を右上のアイコンボタンで開きます。"),
            Legend(3, "3列カテゴリ", "カテゴリ名と件数を軽いグリッドで並べます。"),
            Legend(4, "ボトムナビ", "マイリストタブが選択状態になります。"),
        ],
        "03_mylist_ai_ui.svg",
    )


def screen_mylist_ai_dialog() -> None:
    e = phone(APP_BG)
    e.append(app_bar("マイリスト", actions=["add", "auto"]))
    e.append(gallery_control_bar(disabled_filter=True))
    e.append(media_grid(SX + 16, SY + 160, 3, 3, 108, gap=8))
    e.append(dialog_box(SX + 32, SY + 348, SW - 64, 296, "AI分析の実行", ["分析対象の期間を選択してください:", "● 直近7日間", "○ 直近30日間", "○ すべての期間", "対象画像: 324 枚", "予測時間: 約16分"], "分析開始"))
    e += [marker(1, SX + SW - 66, SY + 28), marker(2, SX + SW / 2, SY + 390), marker(3, SX + SW / 2, SY + 606)]
    render(
        "AI分析ダイアログUIモック",
        e,
        [
            Legend(1, "AI分析ボタン", "右上の自動分析アイコンからダイアログを開きます。"),
            Legend(2, "期間選択", "分析対象期間と対象件数、予測時間を確認します。"),
            Legend(3, "実行ボタン", "選択条件でバックグラウンド分析を開始します。"),
        ],
        "03_mylist_ai_dialog_ui.svg",
    )


def screen_folder_trash_bulk() -> None:
    e = phone(APP_BG)
    e.append(app_bar("フォルダ", actions=["add"]))
    e.append(gallery_control_bar(disabled_filter=True))
    start_y = SY + 138
    folders = [("Camera", "142 枚"), ("Download", "283 枚"), ("X_Downloads", "71 枚"), ("References", "36 枚"), ("Screenshots", "204 枚"), ("Wallpapers", "58 枚"), ("Manga", "19 枚"), ("AI", "93 枚")]
    cell_w = (SW - 56) / 4
    for i, (title, count) in enumerate(folders):
        x = SX + 12 + (i % 4) * (cell_w + 10)
        y = start_y + (i // 4) * 128
        e.append(category_tile(x, y, cell_w, title, count, i, show_thumb=True))
    e.append(bottom_nav("フォルダ"))
    e += [marker(1, SX + 116, SY + 28), marker(2, SX + SW - 54, SY + 28), marker(3, SX + 64, start_y + 52), marker(4, SX + SW / 2, SY + SH - 48)]
    render(
        "フォルダ一覧UIモック",
        e,
        [
            Legend(1, "フォルダ画面", "黒ヘッダーと操作バーを持つCategoryScreenです。"),
            Legend(2, "フォルダ作成", "右上の追加アイコンから新規フォルダを作成します。"),
            Legend(3, "4列カテゴリ", "代表サムネイル、フォルダ名、件数を並べます。"),
            Legend(4, "ボトムナビ", "フォルダタブを選択状態で表示します。"),
        ],
        "04_folder_trash_bulk_ui.svg",
    )


def screen_folder_selection() -> None:
    e = phone(APP_BG)
    e.append(app_bar("フォルダ"))
    e.append(media_grid(SX + 10, SY + 150, 4, 4, (SW - 44) / 4))
    sel_y = SY + 520
    e.append(rect(SX, sel_y, SW, 56, fill=PURPLE_BAR))
    e.append(stroke_icon("close", SX + 28, sel_y + 28, 24, "#1D192B"))
    e.append(text(SX + 62, sel_y + 34, "8 件選択中", "cardTitle", fill="#1D192B"))
    for i, icon_name in enumerate(["heart", "more"]):
        e.append(stroke_icon(icon_name, SX + SW - 70 + i * 42, sel_y + 28, 22, "#1D192B" if icon_name != "heart" else "#B3261E"))
    menu_y = sel_y + 72
    e.append(rect(SX + 122, menu_y, 312, 132, "dialog", rx=8))
    for i, (icon_name, label) in enumerate([("trash", "ゴミ箱へ"), ("folder", "フォルダ移動"), ("edit", "一括タグ・評価編集")]):
        yy = menu_y + 30 + i * 36
        e.append(stroke_icon(icon_name, SX + 148, yy - 4, 18))
        e.append(text(SX + 176, yy, label, "dialogText"))
    e += [marker(1, SX + 135, sel_y + 28), marker(2, SX + SW - 28, sel_y + 28), marker(3, SX + 226, menu_y + 65)]
    render(
        "フォルダ選択モードUIモック",
        e,
        [
            Legend(1, "選択バー", "フォルダ内でも同じ選択バーで件数を表示します。"),
            Legend(2, "その他メニュー", "削除、移動、一括編集のメニューを開きます。"),
            Legend(3, "一括操作", "選択メディアに対して整理操作をまとめて実行します。"),
        ],
        "04_folder_selection_ui.svg",
    )


def screen_trash() -> None:
    e = phone(BLACK)
    e.append(app_bar("ゴミ箱"))
    y = SY + 94
    e.append(text(SX + 20, y, "30日以内に復元できます", "muted"))
    for i in range(4):
        yy = y + 24 + i * 104
        e.append(rect(SX + 16, yy, SW - 32, 88, "m3Card", rx=8))
        e.append(thumb(SX + 28, yy + 10, 72, 68, i, favorite=False))
        e.append(text(SX + 118, yy + 34, f"削除済みメディア {i + 1}", "cardTitle"))
        e.append(text(SX + 118, yy + 56, "2026/06/25 にゴミ箱へ移動", "cardSub"))
        e.append(stroke_icon("restore", SX + SW - 68, yy + 44, 22, CYAN))
        e.append(stroke_icon("trash", SX + SW - 30, yy + 44, 21, "#F44336"))
    e.append(bottom_nav("ゴミ箱"))
    e += [marker(1, SX + 118, SY + 28), marker(2, SX + 148, y + 58), marker(3, SX + SW - 68, y + 68), marker(4, SX + SW - 30, y + 68)]
    render(
        "ゴミ箱UIモック",
        e,
        [
            Legend(1, "TrashScreen", "黒TopAppBarのゴミ箱専用画面です。"),
            Legend(2, "削除済みカード", "サムネイル、移動日時、対象情報を表示します。"),
            Legend(3, "復元", "メディアを元の一覧に戻します。"),
            Legend(4, "完全削除", "端末から完全に削除する操作です。"),
        ],
        "04_trash_ui.svg",
    )


def screen_x_downloader() -> None:
    e = phone(BLACK)
    e.append(app_bar("Video Downloader"))
    y = SY + 88
    e.append(rect(SX + 16, y, SW - 32, 58, "inputBox", rx=4))
    e.append(text(SX + 30, y - 4, "X (Twitter) URL", "inputLabel"))
    e.append(stroke_icon("link", SX + 32, y + 30, 18, "#BDBDBD"))
    e.append(text(SX + 54, y + 36, "https://x.com/user/status/...", "inputText"))
    e.append(button(SX + 16, y + 78, SW - 32, "Show download options", X_BLUE, "#FFFFFF"))
    hist_y = y + 170
    e.append(stroke_icon("history", SX + 30, hist_y - 5, 20, "#80868B"))
    e.append(text(SX + 48, hist_y, "Download history", "cardTitle"))
    e.append(outlined_button(SX + SW - 126, hist_y - 25, 96, "Clear history", "#AAAAAA"))
    for i, status in enumerate(["COMPLETED", "COMPLETED", "FAILED"]):
        yy = hist_y + 22 + i * 86
        e.append(rect(SX + 16, yy, SW - 32, 74, "m3Card", rx=8))
        e.append(thumb(SX + 28, yy + 10, 96, 54, i, square=False, favorite=False))
        e.append(text(SX + 140, yy + 28, f"X download #{i + 1}", "cardTitle"))
        e.append(text(SX + 140, yy + 48, "https://x.com/...", "cardSub"))
        e.append(text(SX + SW - 50, yy + 64, status, "cardSub", fill="#4CAF50" if status == "COMPLETED" else "#F44336", text_anchor="middle"))
    e += [marker(1, SX + 110, SY + 28), marker(2, SX + 92, y + 30), marker(3, SX + SW / 2, y + 100), marker(4, SX + 120, hist_y + 58)]
    render(
        "XダウンロードUIモック",
        e,
        [
            Legend(1, "TopAppBar", "タイトルは Video Downloader。左にメニューを表示します。"),
            Legend(2, "URL入力欄", "共有、VIEW、クリップボード、手入力URLを受けます。"),
            Legend(3, "候補解決", "ボタンから保存候補と品質選択へ進みます。"),
            Legend(4, "履歴カード", "保存済みメディア、URL、状態を一覧します。"),
        ],
        "05_x_downloader_ui.svg",
    )


def screen_x_downloader_options() -> None:
    e = phone(BLACK)
    e.append(app_bar("Video Downloader"))
    e.append(rect(SX + 16, SY + 140, SW - 32, 78, "m3Card", rx=8))
    e.append(stroke_icon("download", SX + 44, SY + 179, 24, X_BLUE))
    e.append(text(SX + 78, SY + 170, "保存候補を検出しました", "cardTitle"))
    e.append(text(SX + 78, SY + 194, "video/mp4 1080p / GIF候補あり", "cardSub"))
    e.append(dialog_box(SX + 42, SY + 420, SW - 84, 272, "Download settings", ["Select quality:", "● High (1080p)", "○ Medium (720p)", "○ Low (480p)", "GIFとして保存: ON", "HOME     Cancel"], "Start download"))
    e += [marker(1, SX + 152, SY + 170), marker(2, SX + SW / 2, SY + 462), marker(3, SX + SW / 2, SY + 648)]
    render(
        "Xダウンロード設定UIモック",
        e,
        [
            Legend(1, "候補カード", "URL解析後に保存可能なメディア候補を表示します。"),
            Legend(2, "品質選択", "High/Medium/LowやGIF保存を選びます。"),
            Legend(3, "HOME導線", "ダイアログ表示中でもホームへ戻れる導線を残します。"),
        ],
        "05_x_downloader_options_ui.svg",
    )


def screen_x_downloader_history() -> None:
    e = phone(BLACK)
    e.append(app_bar("Video Downloader"))
    y = SY + 104
    e.append(text(SX + 24, y, "Download history", "cardTitle"))
    for i in range(5):
        yy = y + 24 + i * 96
        e.append(rect(SX + 16, yy, SW - 32, 82, "m3Card", rx=8))
        e.append(thumb(SX + 28, yy + 10, 104, 58, i, favorite=False))
        e.append(text(SX + 148, yy + 30, f"@creator/status/{1000+i}", "cardTitle"))
        e.append(text(SX + 148, yy + 52, "保存先: Gallery/X_Downloads", "cardSub"))
        e.append(stroke_icon("play", SX + SW - 44, yy + 42, 22, X_BLUE))
    e += [marker(1, SX + 114, y + 60), marker(2, SX + 220, y + 54), marker(3, SX + SW - 44, y + 66)]
    render(
        "Xダウンロード履歴UIモック",
        e,
        [
            Legend(1, "プレビュー", "保存済みメディアのサムネイルを履歴に表示します。"),
            Legend(2, "保存情報", "URL、保存先、状態、日時を確認できます。"),
            Legend(3, "再生/確認", "履歴からアプリ内ビューアで開けます。"),
        ],
        "05_x_downloader_history_ui.svg",
    )


def screen_book_viewer() -> None:
    e = phone(BLACK)
    e.append(rect(SX, SY, SW, SH, fill=BLACK))
    e.append(rect(SX, SY, SW, 56, fill="#00000099"))
    e.append(stroke_icon("close", SX + 28, SY + 28, 24))
    e.append(text(SX + 64, SY + 35, "sample_book.zip", "barTitle"))
    e.append(stroke_icon("bookmark", SX + SW - 70, SY + 28, 22, CYAN))
    e.append(stroke_icon("more", SX + SW - 28, SY + 28, 22))
    e.append(rect(SX + 38, SY + 110, 145, 440, fill="#141414"))
    e.append(thumb(SX + 48, SY + 124, 125, 412, 1, favorite=False))
    e.append(rect(SX + 202, SY + 110, 145, 440, fill="#141414"))
    e.append(thumb(SX + 212, SY + 124, 125, 412, 2, favorite=False))
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
            Legend(2, "ページ表示", "ZIP/PDFを単ページまたは見開きで黒背景に表示します。"),
            Legend(3, "下部操作", "ページ数、シークバー、前の本/次の本を表示します。"),
            Legend(4, "その他メニュー", "検索、表示設定、スクショ、画面回転を開きます。"),
        ],
        "06_book_viewer_ui.svg",
    )


def screen_book_list() -> None:
    e = phone(BLACK)
    e.append(app_bar("本", actions=["bookmark", "restore"]))
    y = SY + 92
    for i, title in enumerate(["sample_book.zip", "favorite_manga.pdf", "series_03.zip"]):
        yy = y + i * 154
        e.append(rect(SX + 16, yy, SW - 32, 132, "m3Card", rx=8))
        e.append(thumb(SX + 28, yy + 12, 88, 108, i, favorite=False))
        e.append(text(SX + 132, yy + 42, title, "cardTitle"))
        e.append(text(SX + 132, yy + 68, "240ページ / 最終閲覧 128P", "cardSub"))
        e.append(rect(SX + 132, yy + 88, 170, 5, "progressTrack", rx=3))
        e.append(rect(SX + 132, yy + 88, 82 + i * 20, 5, "progress", rx=3))
    e += [marker(1, SX + 116, SY + 28), marker(2, SX + 74, y + 64), marker(3, SX + 220, y + 93)]
    render(
        "漫画一覧UIモック",
        e,
        [
            Legend(1, "BookScreen", "スキャン、しおり一覧などを右上から開きます。"),
            Legend(2, "本カード", "表紙、ファイル名、ページ数、最終閲覧位置を表示します。"),
            Legend(3, "読書進捗", "現在ページまでの進捗をバーで示します。"),
        ],
        "06_book_list_ui.svg",
    )


def screen_book_settings() -> None:
    e = phone(BLACK)
    e.append(rect(SX, SY, SW, SH, fill=BLACK))
    e.append(thumb(SX + 64, SY + 116, SW - 128, 520, 3, favorite=False))
    panel_y = SY + 500
    e.append(rect(SX, panel_y, SW, 410, fill="#121212", rx=18))
    e.append(text(SX + 24, panel_y + 36, "表示設定", "cardTitle"))
    settings = [("見開き表示", "ON"), ("読む方向", "右開き"), ("背景", "黒"), ("先読み", "高")]
    for i, (label, value) in enumerate(settings):
        yy = panel_y + 76 + i * 54
        e.append(text(SX + 30, yy, label, "txt"))
        e.append(rect(SX + SW - 120, yy - 24, 88, 32, "chip", rx=16))
        e.append(text(SX + SW - 76, yy - 3, value, "chipText", text_anchor="middle"))
    e += [marker(1, SX + SW / 2, SY + 356), marker(2, SX + 88, panel_y + 36), marker(3, SX + SW - 76, panel_y + 106)]
    render(
        "漫画表示設定UIモック",
        e,
        [
            Legend(1, "ビューア表示", "本を開いたまま下部に設定パネルを出します。"),
            Legend(2, "設定パネル", "表示形式、読む方向、背景、画質を変更します。"),
            Legend(3, "現在値", "選択中の値をチップ状に表示します。"),
        ],
        "06_book_settings_ui.svg",
    )


def screen_reference_projects() -> None:
    e = phone(BLACK)
    e.append(app_bar("お絵描き資料", centered=True))
    y = SY + 78
    projects = [("キャラ立ち絵 参考", "進行中"), ("手とポーズ資料", "進行中"), ("背景ラフ用", "完了")]
    for i, (title, status) in enumerate(projects):
        yy = y + i * 86
        e.append(rect(SX + 16, yy, SW - 32, 70, "m3Card", rx=8))
        e.append(circle(SX + 48, yy + 35, 18, "iconCircle", fill="#263238"))
        e.append(stroke_icon("brush" if status == "進行中" else "check", SX + 48, yy + 35, 18, CYAN if status == "進行中" else MUTED))
        e.append(text(SX + 80, yy + 30, title, "cardTitle"))
        e.append(text(SX + 80, yy + 52, status, "cardSub", fill="#4CAF50" if status == "進行中" else MUTED))
        e.append(stroke_icon("trash", SX + SW - 36, yy + 35, 18, MUTED))
    e.append(circle(SX + SW - 50, SY + SH - 128, 28, fill=CYAN))
    e.append(stroke_icon("add", SX + SW - 50, SY + SH - 128, 26, "#001216"))
    e += [marker(1, SX + SW / 2, SY + 28), marker(2, SX + 120, y + 35), marker(3, SX + SW - 50, SY + SH - 128)]
    render(
        "お絵描き資料プロジェクトUIモック",
        e,
        [
            Legend(1, "CenterAlignedTopAppBar", "黒背景でタイトルを中央表示します。"),
            Legend(2, "プロジェクトカード", "進行中/完了、削除、資料画面への入口を表示します。"),
            Legend(3, "FAB", "新規プロジェクト作成ダイアログを開きます。"),
        ],
        "07_reference_projects_ui.svg",
    )


def screen_reference_detail() -> None:
    e = phone(BLACK)
    e.append(app_bar("手とポーズ資料", left="back", actions=["check"], centered=True))
    y = SY + 96
    e.append(button(SX + 22, y, SW - 44, "資料を追加", CYAN, "#001216"))
    for i in range(6):
        x = SX + 18 + (i % 2) * 180
        yy = y + 66 + (i // 2) * 158
        e.append(rect(x, yy, 166, 140, "m3Card", rx=8))
        e.append(thumb(x + 10, yy + 10, 146, 90, i, favorite=False))
        e.append(text(x + 12, yy + 124, "reference_image.jpg", "cardSub"))
        e.append(stroke_icon("download", x + 122, yy + 122, 16, CYAN))
        e.append(stroke_icon("trash", x + 148, yy + 122, 16, MUTED))
    e += [marker(1, SX + SW / 2, SY + 28), marker(2, SX + SW / 2, y + 22), marker(3, SX + 104, y + 118), marker(4, SX + 330, y + 188)]
    render(
        "お絵描き資料詳細UIモック",
        e,
        [
            Legend(1, "詳細TopAppBar", "戻る、完了切替、進行中切替の状態アイコンを表示します。"),
            Legend(2, "資料追加", "端末画像または検索画面から資料を追加します。"),
            Legend(3, "資料カード", "一時保存した参考画像をカードで並べます。"),
            Legend(4, "保存/削除", "資料の保存先反映や削除を個別に操作します。"),
        ],
        "07_reference_detail_ui.svg",
    )


def screen_reference_search() -> None:
    e = phone(BLACK)
    e.append(app_bar("Web検索", left="back", actions=["search"], centered=True))
    y = SY + 96
    e.append(rect(SX + 18, y, SW - 36, 54, "inputBox", rx=6))
    e.append(stroke_icon("search", SX + 42, y + 27, 18, MUTED))
    e.append(text(SX + 66, y + 34, "hand pose reference", "inputText"))
    e.append(rect(SX + 16, y + 78, SW - 32, 470, fill="#FFFFFF", rx=8))
    e.append(text(SX + 34, y + 112, "Google Images", "cardTitle", fill="#1A1A1A"))
    for i in range(6):
        x = SX + 34 + (i % 3) * 110
        yy = y + 140 + (i // 3) * 140
        e.append(thumb(x, yy, 96, 110, i, favorite=False))
    e.append(circle(SX + SW - 54, SY + SH - 128, 28, fill=CYAN))
    e.append(stroke_icon("camera", SX + SW - 54, SY + SH - 128, 24, "#001216"))
    e += [marker(1, SX + 130, y + 28), marker(2, SX + 96, y + 202), marker(3, SX + SW - 54, SY + SH - 128)]
    render(
        "お絵描き資料検索UIモック",
        e,
        [
            Legend(1, "検索欄", "プロジェクト用の資料検索キーワードを入力します。"),
            Legend(2, "WebView", "検索結果から画像長押しやURL選択で資料へ追加します。"),
            Legend(3, "スクショ保存", "検索画面を資料として保存できます。"),
        ],
        "07_reference_search_ui.svg",
    )


def screen_recommendations() -> None:
    e = phone(BLACK)
    e.append(app_bar("おすすめ"))
    e.append(text(SX + SW / 2, SY + SH / 2 - 20, "おすすめのデータがまだありません。", "muted", text_anchor="middle"))
    e.append(text(SX + SW / 2, SY + SH / 2 + 4, "計測モードで画像を鑑賞してください。", "muted", text_anchor="middle"))
    e += [marker(1, SX + 112, SY + 28), marker(2, SX + 76, SY + SH / 2 - 8)]
    render(
        "おすすめ・視聴履歴UIモック",
        e,
        [
            Legend(1, "おすすめ画面", "黒いTopAppBarと空状態メッセージのシンプルな構成です。"),
            Legend(2, "空状態", "閲覧データが貯まるまで案内文を中央表示します。"),
        ],
        "08_recommendations_history_ui.svg",
    )


def screen_history_panel() -> None:
    e = phone(BLACK)
    e.append(app_bar("おすすめ"))
    y = SY + 100
    e.append(text(SX + 24, y, "最近見たメディア", "cardTitle"))
    for i in range(4):
        yy = y + 30 + i * 118
        e.append(rect(SX + 16, yy, SW - 32, 98, "m3Card", rx=8))
        e.append(thumb(SX + 28, yy + 10, 92, 78, i + 1, favorite=False))
        e.append(text(SX + 136, yy + 34, f"IMG_20260625_{i + 1:03}.jpg", "cardTitle"))
        e.append(text(SX + 136, yy + 58, "閲覧 12回 / 4分21秒", "cardSub"))
        e.append(stroke_icon("play", SX + SW - 44, yy + 50, 22, CYAN))
    e += [marker(1, SX + 120, y + 64), marker(2, SX + 220, y + 88), marker(3, SX + SW - 44, y + 80)]
    render(
        "視聴履歴リストUIモック",
        e,
        [
            Legend(1, "履歴カード", "閲覧済みメディアを時系列で確認します。"),
            Legend(2, "閲覧統計", "閲覧回数、閲覧時間、最終閲覧を表示します。"),
            Legend(3, "再表示", "履歴からビューアへ再遷移できます。"),
        ],
        "08_viewing_history_ui.svg",
    )


def screen_creators_sites() -> None:
    e = phone(CREATOR_BG)
    e.append(app_bar("Creators", actions=["edit", "upload", "download"], centered=True, bg=CREATOR_BG, fg=CREATOR_INK))
    y = SY + 78
    creators = [("artist_name", "pixiv / X / Support"), ("illustrator_02", "User sites / custom"), ("Untitled creator", "URL未設定")]
    for i, (name, sub) in enumerate(creators):
        yy = y + i * 118
        e.append(rect(SX + 16, yy, SW - 32, 100, "brownCard", rx=8))
        e.append(text(SX + 32, yy + 31, name, "brownTitle"))
        e.append(rect(SX + 32, yy + 48, SW - 64, 34, "brownField", rx=8))
        e.append(text(SX + 46, yy + 70, sub, "brownText"))
        e.append(stroke_icon("open", SX + SW - 42, yy + 65, 18, CREATOR_ACCENT))
    e.append(circle(SX + SW - 50, SY + SH - 128, 28, fill=CREATOR_ACCENT))
    e.append(stroke_icon("add", SX + SW - 50, SY + SH - 128, 26, CREATOR_BG))
    e += [marker(1, SX + SW / 2, SY + 28), marker(2, SX + 108, y + 32), marker(3, SX + SW - 64, SY + 28), marker(4, SX + SW - 50, SY + SH - 128)]
    render(
        "お気に入り作家UIモック",
        e,
        [
            Legend(1, "Creators画面", "茶系テーマのCenterAlignedTopAppBarを使います。"),
            Legend(2, "作家カード", "作家名と登録済みリンクをCard/Surfaceで表示します。"),
            Legend(3, "書き出し/読み込み/編集", "バックアップ書き出し、読み込み、表示/編集切替を右上に置きます。"),
            Legend(4, "作家追加", "FABから新しい作家カードを追加します。"),
        ],
        "09_favorite_creators_sites_ui.svg",
    )


def screen_favorite_sites() -> None:
    e = phone(CREATOR_BG)
    e.append(app_bar("お気に入りサイト", actions=["edit"], centered=True, bg=CREATOR_BG, fg=CREATOR_INK))
    y = SY + 88
    sites = [("画像検索", "https://example.com/search?q={query}", "資料探し用"), ("ポーズ集", "https://poses.example.com", "人体参考"), ("素材サイト", "https://assets.example.com", "背景素材")]
    for i, (name, url, desc) in enumerate(sites):
        yy = y + i * 138
        e.append(rect(SX + 16, yy, SW - 32, 118, "brownCard", rx=8))
        e.append(text(SX + 32, yy + 32, name, "brownTitle"))
        e.append(text(SX + 32, yy + 58, desc, "brownText"))
        e.append(rect(SX + 32, yy + 72, SW - 64, 30, "brownField", rx=6))
        e.append(text(SX + 44, yy + 92, url, "brownAccent"))
        e.append(stroke_icon("open", SX + SW - 42, yy + 88, 17, CREATOR_ACCENT))
    e += [marker(1, SX + SW / 2, SY + 28), marker(2, SX + 110, y + 32), marker(3, SX + 148, y + 90), marker(4, SX + SW - 42, y + 88)]
    render(
        "お気に入りサイトUIモック",
        e,
        [
            Legend(1, "お気に入りサイト", "作家画面と同じ茶系テーマでサイトを管理します。"),
            Legend(2, "サイトカード", "サイト名と説明をカードで表示します。"),
            Legend(3, "URLテンプレート", "検索語を差し込めるURLを登録できます。"),
            Legend(4, "外部表示", "登録URLをブラウザやWebViewで開きます。"),
        ],
        "09_favorite_sites_ui.svg",
    )


def screen_shared_services() -> None:
    e = phone(APP_BG)
    e.append(app_bar("すべて"))
    e.append(gallery_control_bar())
    e.append(rect(SX, SY, SW * 0.76, SH, fill="#121212"))
    e.append(text(SX + 20, SY + 44, "Gallery", "barTitle"))
    e.append(text(SX + 20, SY + 72, "高機能ギャラリー", "muted"))
    drawer = ["ホーム", "フォルダ", "タグ", "本", "ゴミ箱", "動画DL", "お気に入りクリエイター", "お気に入りサイト", "お絵描き資料", "おすすめ（開発中）"]
    icons = ["home", "folder", "tag", "book", "trash", "download", "heart", "open", "brush", "auto"]
    y = SY + 108
    for i, label in enumerate(drawer):
        yy = y + i * 38
        if i == 0:
            e.append(rect(SX + 12, yy - 20, SW * 0.69, 34, fill="#2A2A2A", rx=17))
        e.append(stroke_icon(icons[i], SX + 32, yy - 5, 18, TEXT if i == 0 else "#BDBDBD"))
        e.append(text(SX + 58, yy, label, "txt"))
    e += [marker(1, SX + 52, SY + 28), marker(2, SX + 84, SY + 132), marker(3, SX + 242, SY + 278)]
    render(
        "共通ドロワーUIモック",
        e,
        [
            Legend(1, "ModalNavigationDrawer", "メニューから主要機能と設定系へ移動します。"),
            Legend(2, "ドロワー項目", "MainActivityの実装ラベルとアイコンを並べます。"),
            Legend(3, "共通状態", "GalleryState、Room、Repositoryが各画面を支えます。"),
        ],
        "10_shared_services_ui.svg",
    )


def screen_global_progress() -> None:
    e = phone(APP_BG)
    e.append(app_bar("すべて"))
    e.append(media_grid(SX + 10, SY + 142, 4, 5, (SW - 44) / 4))
    op_y = SY + SH - 188
    e.append(rect(SX + 16, op_y, SW - 32, 112, fill="#000000D9", stroke="#FFFFFF22", rx=12))
    e.append(text(SX + 32, op_y + 30, "サムネイル生成", "cardTitle"))
    e.append(text(SX + SW - 52, op_y + 30, "42%", "cardTitle", fill=CYAN, text_anchor="middle"))
    e.append(rect(SX + 32, op_y + 54, SW - 64, 5, "progressTrack", rx=3))
    e.append(rect(SX + 32, op_y + 54, 132, 5, "progress", rx=3))
    e.append(text(SX + 32, op_y + 82, "MediaStore同期 / AIモデル準備 / Room更新", "muted"))
    e += [marker(1, SX + 130, op_y + 30), marker(2, SX + 202, op_y + 56), marker(3, SX + 220, op_y + 82)]
    render(
        "共通進捗オーバーレイUIモック",
        e,
        [
            Legend(1, "GlobalProgressOverlay", "起動タスクや一括処理の進捗を黒いカードで重ねます。"),
            Legend(2, "進捗バー", "処理率をパーセントとバーで確認できます。"),
            Legend(3, "処理内容", "同期、モデル準備、Room更新などの現在作業を表示します。"),
        ],
        "10_global_progress_ui.svg",
    )


def main() -> None:
    screen_media_gallery()
    screen_media_gallery_scrollbar()
    screen_media_gallery_dense28()
    screen_media_gallery_selection()
    screen_media_viewer()
    screen_media_viewer_video_controls()
    screen_media_viewer_recommendation_panel()
    screen_mylist_ai()
    screen_mylist_ai_dialog()
    screen_folder_trash_bulk()
    screen_folder_selection()
    screen_trash()
    screen_x_downloader()
    screen_x_downloader_options()
    screen_x_downloader_history()
    screen_book_viewer()
    screen_book_list()
    screen_book_settings()
    screen_reference_projects()
    screen_reference_detail()
    screen_reference_search()
    screen_recommendations()
    screen_history_panel()
    screen_creators_sites()
    screen_favorite_sites()
    screen_shared_services()
    screen_global_progress()


if __name__ == "__main__":
    main()
