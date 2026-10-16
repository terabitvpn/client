"""Renders every Terabit icon from one source: the letter "T" of Beeline Sans Black.

Writes
  - the Android adaptive icon, notification and splash vectors (androidApp/src/main/res/drawable)
  - the desktop PNG / ICO / ICNS, the tray PNG, the iOS 1024 icon and the F-Droid icon
  - the glyph outline used by the in-app mark (shared/.../ui/components/BrandGlyph.kt)

Usage: python3 tools/make_icons.py   (needs fonttools and Pillow; run from the repository root)
"""
import pathlib

from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw

FONT = "shared/src/commonMain/composeResources/font/beeline_sans_black.ttf"
GREEN = (0x01, 0x43, 0x20)
WHITE = (255, 255, 255)
DARK = (0x03, 0x0D, 0x08)


# ---------------------------------------------------------------- the glyph


def glyph_contours(char="T", flat=12):
    """The glyph outline as a list of closed polylines plus its bounds, y pointing down, in font units."""
    font = TTFont(FONT)
    gs = font.getGlyphSet()
    name = font.getBestCmap()[ord(char)]
    pen = DecomposingRecordingPen(gs)
    gs[name].draw(pen)

    contours, cur = [], []
    for op, args in pen.value:
        if op == "moveTo":
            cur = [args[0]]
        elif op == "lineTo":
            cur.append(args[0])
        elif op == "qCurveTo":
            pts = list(args)
            start = cur[-1]
            # implied on-curve points between consecutive off-curve ones
            offs, end = pts[:-1], pts[-1]
            ons = []
            for i in range(len(offs) - 1):
                ons.append(((offs[i][0] + offs[i + 1][0]) / 2, (offs[i][1] + offs[i + 1][1]) / 2))
            ends = ons + [end]
            p0 = start
            for c, p1 in zip(offs, ends):
                for j in range(1, flat + 1):
                    t = j / flat
                    cur.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
                                (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]))
                p0 = p1
        elif op == "curveTo":
            c1, c2, p1 = args
            p0 = cur[-1]
            for j in range(1, flat + 1):
                t = j / flat
                cur.append(((1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * c1[0] + 3 * (1 - t) * t * t * c2[0] + t ** 3 * p1[0],
                            (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * c1[1] + 3 * (1 - t) * t * t * c2[1] + t ** 3 * p1[1]))
        elif op in ("closePath", "endPath"):
            if cur:
                contours.append(cur)
            cur = []
    xs = [p[0] for c in contours for p in c]
    ys = [p[1] for c in contours for p in c]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    # normalise: height = 100, top-left at (0, 0), y down
    k = 100.0 / (y1 - y0)
    norm = [[((x - x0) * k, (y1 - y) * k) for x, y in c] for c in contours]
    return norm, (x1 - x0) * k  # polylines, width (height is 100)


CONTOURS, WIDTH = glyph_contours()


def svg_path(scale, cx, cy):
    """Path data of the glyph with its centre at (cx, cy), height = 100 * scale."""
    ox, oy = cx - WIDTH * scale / 2, cy - 50 * scale
    out = []
    for c in CONTOURS:
        out.append("M" + " L".join(f"{ox + x * scale:.2f},{oy + y * scale:.2f}" for x, y in c) + "Z")
    return "".join(out)


# ---------------------------------------------------------------- Android


def android():
    res = pathlib.Path("androidApp/src/main/res/drawable")

    def vec(name, body, size=108, viewport=108):
        (res / name).write_text(
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp" android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n{body}</vector>\n'
        )

    # adaptive icon: white background, green T well inside the 66dp safe circle
    vec("ic_launcher_background.xml", '    <path android:fillColor="#FFFFFFFF" android:pathData="M0,0h108v108h-108z" />\n')
    t = svg_path(0.40, 54, 54)
    vec("ic_launcher_foreground.xml", f'    <path android:fillColor="#FF014320" android:pathData="{t}" />\n')
    vec("ic_launcher_monochrome.xml", f'    <path android:fillColor="#FF000000" android:pathData="{t}" />\n')
    # splash: white T on the app's dark background
    vec("ic_splash.xml", f'    <path android:fillColor="#FFFFFFFF" android:pathData="{svg_path(0.40, 54, 54)}" />\n')
    # status-bar icon: a flat white T in a 24 dp box
    vec("ic_notification.xml", f'    <path android:fillColor="#FFFFFFFF" android:pathData="{svg_path(0.16, 12, 12)}" />\n', size=24, viewport=24)


# ---------------------------------------------------------------- Kotlin glyph


def kotlin():
    pts = ", ".join(f"{x:.3f}f, {y:.3f}f" for c in CONTOURS for x, y in c)
    out = pathlib.Path("shared/src/commonMain/kotlin/ru/terabitvpn/ui/components/BrandGlyph.kt")
    out.write_text(
        "package ru.terabitvpn.ui.components\n\n"
        "// Generated by tools/make_icons.py from the \"T\" of Beeline Sans Black: one closed outline as x, y pairs,\n"
        "// height 100, y down.\n"
        f"internal const val BRAND_T_WIDTH = {WIDTH:.3f}f\n\n"
        f"internal val BRAND_T_POINTS = floatArrayOf({pts})\n"
    )


# ---------------------------------------------------------------- raster


def tile(size, rounded, bg=WHITE, fg=GREEN, cap=0.50, margin=0.0):
    """The icon at [size] px: a [bg] tile (rounded corners when [rounded]) with the T [cap] of its height."""
    ss = 4
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = int(s * margin)
    box = (m, m, s - m - 1, s - m - 1)
    if rounded:
        d.rounded_rectangle(box, radius=int((s - 2 * m) * 0.225), fill=bg + (255,))
    else:
        d.rectangle(box, fill=bg + (255,))
    scale = (s - 2 * m) * cap / 100.0
    ox, oy = s / 2 - WIDTH * scale / 2, s / 2 - 50 * scale
    for c in CONTOURS:
        d.polygon([(ox + x * scale, oy + y * scale) for x, y in c], fill=fg + (255,))
    return img.resize((size, size), Image.LANCZOS)


def raster():
    big = tile(1024, rounded=True, margin=0.04)
    big.save("desktopApp/icons/terabit.png")
    big.resize((256, 256), Image.LANCZOS).save("desktopApp/src/main/resources/terabit.png")
    big.save("desktopApp/icons/terabit.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    big.save("desktopApp/icons/terabit.icns")
    # iOS wants a square full-bleed picture without transparency: the system rounds it
    tile(1024, rounded=False).convert("RGB").save("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png")
    for lang in ("en-US", "ru-RU"):
        tile(512, rounded=False).convert("RGB").save(f"fastlane/metadata/android/{lang}/images/icon.png")


if __name__ == "__main__":
    android()
    kotlin()
    raster()
    print("icons written")
