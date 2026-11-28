"""Ikona aplikace (pírko) ze JEDNÉ geometrie pro všechny výstupy:

    play/graphics/icon/icon.png                                    – Play, 512 × 512
    iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png   – iOS, 1024 × 1024, bez alfy
    client/androidApp/src/main/res/drawable/ic_launcher_foreground.xml – vektor adaptivní ikony
    client/shared/src/androidMain/res/drawable/ic_notification.xml    – malá ikona notifikace
    client/resources/src/commonMain/composeResources/drawable/plume_logo_24dp.xml – logo v „O aplikaci“
    client/desktopApp/icons/plume.{ico,icns,png}                     – desktop (Windows, macOS, Linux)
    client/desktopApp/src/main/resources/plume-icon.png              – ikona okna a oznamovací oblasti
    client/tvApp/src/main/res/drawable-xhdpi/tv_banner.png           – banner v launcheru Android TV
    play/graphics/tvBanner/tv_banner.png                              – banner Android TV v obchodě, 1280 × 720

Spuštění (z kořene repa): python3 play/tools/icon.py (Pillow, shapely), jen desktop:
python3 play/tools/icon.py desktop, jen banner televize: python3 play/tools/icon.py tv. Bez Pillow a shapely v systému:
uv run --with pillow --with shapely python3 play/tools/icon.py. Barvy pozadí musí sedět
s `ic_launcher_background.xml`.

Pírko se skládá v jednotkovém čtverci (0..1) jako tvar: vlajka mínus klínové zářezy a osten,
plus stopka. Raster i vektor kreslí tentýž tvar, takže se ikona v obchodě a v launcheru
nemůže rozejít.
"""
from pathlib import Path

from PIL import Image, ImageDraw
from shapely.geometry import LineString, MultiPolygon, Polygon
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parents[2]
# Gradient z modré aplikace (seed `ThemeColor.BLUE` #2AABEE): nahoře živější tón (L* 56), dole
# hluboká modrá (L* 26). Ne přímo seed: světle modrý kruh s bílým symbolem by se blížil oficiálnímu
# logu Telegramu, které podle API ToS nesmíme napodobit.
TOP = (30, 136, 229)     # #1E88E5
BOTTOM = (11, 61, 134)   # #0B3D86
INK = (255, 243, 232)    # #FFF3E8
RASTER_REACH = 0.42      # PNG pro obchod: pírko do 42 % strany od středu (maska obchodu zaobluje rohy)


def bez(p0, p1, p2, p3, n=240):
    return [tuple((1 - t) ** 3 * p0[i] + 3 * (1 - t) ** 2 * t * p1[i] + 3 * (1 - t) * t ** 2 * p2[i]
                  + t ** 3 * p3[i] for i in range(2)) for t in (k / n for k in range(n + 1))]


def notch(edge, t, depth, width):
    """Klínový zářez do okraje vlajky: z bodu obrysu šikmo dovnitř, směrem ke stopce."""
    i = int(t * (len(edge) - 1))
    (x0, y0), (x1, y1) = edge[i - 2], edge[i + 2]
    tx, ty = x1 - x0, y1 - y0
    length = (tx * tx + ty * ty) ** 0.5
    tx, ty = tx / length, ty / length
    nx, ny = ty, -tx                       # normála ven z vlajky (obrys jde po směru hodin)
    px, py = edge[i]
    return Polygon([
        (px + nx * 0.03 - tx * width, py + ny * 0.03 - ty * width),
        (px + nx * 0.03 + tx * width, py + ny * 0.03 + ty * width),
        (px - nx * depth + tx * depth * 0.9, py - ny * depth + ty * depth * 0.9),
    ])


def feather(shaft_width=0.009):
    tip, base = (0.78, 0.14), (0.30, 0.68)
    left = bez(base, (0.06, 0.48), (0.46, 0.14), tip)
    right = bez(tip, (0.92, 0.34), (0.70, 0.74), base)
    vane = Polygon(left + right)
    notches = [notch(right, 0.32, 0.08, 0.012), notch(right, 0.60, 0.075, 0.012), notch(left, 0.45, 0.07, 0.012)]
    shaft = LineString(bez(base, (0.44, 0.52), (0.58, 0.36), (0.71, 0.22), 60)).buffer(shaft_width, quad_segs=32)
    stem = LineString(bez((0.31, 0.67), (0.28, 0.71), (0.24, 0.75), (0.20, 0.78), 60)).buffer(0.022, quad_segs=32)
    shape = unary_union([vane.difference(unary_union(notches + [shaft])), stem])
    return shape.simplify(0.0002)


def fitted(shape, reach, box=False):
    """Posune a zvětší tvar do středu jednotkového čtverce.

    Bez `box` do kruhu: těžiště ve středu, nejvzdálenější bod v `reach` — pro ikony, které
    maska ořízne do kruhu. S `box` do čtverce o straně 2 × `reach` podle obdélníku kolem
    tvaru — pro ikonu notifikace, kterou nic neořezává a každý bod plochy se počítá.
    """
    if box:
        x0, y0, x1, y1 = shape.bounds
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        k = 2 * reach / max(x1 - x0, y1 - y0)
    else:
        cx, cy = shape.centroid.x, shape.centroid.y
        far = max(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 for p in polygons(shape) for x, y in p.exterior.coords)
        k = reach / far
    return lambda c: [(0.5 + (x - cx) * k, 0.5 + (y - cy) * k) for x, y in c]


def polygons(shape):
    return list(shape.geoms) if isinstance(shape, MultiPolygon) else [shape]


def gradient(w, h):
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        d.line([(0, y), (w, y)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * y / h) for i in range(3)))
    return im


def feather_mask(s, ox=0, oy=0, scale=1.0):
    """Maska pírka ve čtverci `s` px (i pro feature graphic)."""
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    shape = feather()
    fit = fitted(shape, RASTER_REACH)
    P = lambda c: [(ox + x * s * scale, oy + y * s * scale) for x, y in fit(c)]
    for poly in polygons(shape):
        d.polygon(P(poly.exterior.coords), fill=255)
        for hole in poly.interiors:
            d.polygon(P(hole.coords), fill=0)
    return m


def raster(size):
    n = size * 4
    im = gradient(n, n)
    im.paste(Image.new("RGB", (n, n), INK), (0, 0), feather_mask(n))
    return im.resize((size, size), Image.LANCZOS)


def vector(size=108, reach_dp=31, color="#FFF3E8", shaft_width=0.009, box=False):
    """Pírko jako vector drawable `size` dp, celé do `reach_dp` od středu.

    Adaptivní ikona: 108 dp, bezpečný kruh r = 33 dp. Notifikace: 24 dp, obsah ve čtverci
    22 dp podle obrysu (`box`) a silnější osten — ten původní by v téhle velikosti splynul
    s vlajkou.
    """
    shape = feather(shaft_width)
    fit = fitted(shape, reach_dp / size, box)
    fmt = lambda c: "M" + " L".join(f"{x * size:.2f},{y * size:.2f}" for x, y in fit(c)) + " Z"
    data = " ".join(fmt(r.coords) for p in polygons(shape) for r in [p.exterior, *p.interiors])
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Vygenerováno z play/tools/icon.py, ručně neupravovat. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}">
    <path
        android:fillColor="{color}"
        android:fillType="evenOdd"
        android:pathData="{data}" />
</vector>
"""


def desktop(size):
    """Ikona pro desktop: zaoblený čtverec s průhlednými rohy.

    Na rozdíl od mobilů ji nikdo neořezává maskou — tvar si nese sama. Rozměry podle mřížky
    ikon macOS (plocha 824 z 1024, poloměr rohu 185, zbytek okraj na stín a optické vyrovnání);
    Windows i Linux berou čtvercový obrázek s alfou, takže se hodí i tam.
    """
    n = size * 4
    inset, radius = round(n * 100 / 1024), round(n * 185 / 1024)
    plate = Image.new("L", (n, n), 0)
    ImageDraw.Draw(plate).rounded_rectangle([inset, inset, n - inset, n - inset], radius=radius, fill=255)
    im = gradient(n, n).convert("RGBA")
    im.paste(Image.new("RGBA", (n, n), INK + (255,)), (0, 0), feather_mask(n, inset, inset, (n - 2 * inset) / n))
    im.putalpha(plate)
    return im.resize((size, size), Image.LANCZOS)


def write_desktop():
    icons = ROOT / "client/desktopApp/icons"
    icons.mkdir(parents=True, exist_ok=True)
    big = desktop(1024)
    # ICO: Windows vybírá podle velikosti (Průzkumník, hlavní panel, Start); 256 je největší.
    big.save(icons / "plume.ico", sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
    big.save(icons / "plume.icns")
    desktop(512).save(icons / "plume.png")
    desktop(512).save(ROOT / "client/desktopApp/src/main/resources/plume-icon.png")


def tv_banner(out, w, h):
    """Banner Android TV [w] × [h] px do [out]: pírko a jméno na gradientu ikony.

    Launcher televize ukazuje místo ikony banner (320 × 180 dp, xhdpi 640 × 360) a Google chce,
    aby nesl jméno aplikace (bez něj ho uživatel v řadě aplikací nepozná). Obchod chce tentýž
    banner zvlášť v 1280 × 720 (`tvBanner` v záznamu). Jméno je Bricolage z písem aplikace, jako
    velké nadpisy; vektor text neumí, proto PNG.
    """
    from PIL import ImageFont

    k = 4
    n_w, n_h = w * k, h * k
    im = gradient(n_w, n_h)
    font = ImageFont.truetype(
        str(ROOT / "client/resources/src/commonMain/composeResources/font/bricolage_grotesque_semibold.ttf"),
        round(n_h * 0.26),
    )
    d = ImageDraw.Draw(im)
    x0, y0, x1, y1 = d.textbbox((0, 0), "Plume", font=font)
    # Pírko zabírá 2 × RASTER_REACH své čtvercové masky; pírko, mezera a jméno uprostřed.
    side = round(n_h * 0.9)
    visible = side * 2 * RASTER_REACH
    gap = n_h * 0.06
    left = (n_w - (visible + gap + (x1 - x0))) / 2
    fx = round(left - side * (0.5 - RASTER_REACH))
    im.paste(Image.new("RGB", (side, side), INK), (fx, (n_h - side) // 2), feather_mask(side))
    d.text((left + visible + gap - x0, (n_h - (y1 - y0)) / 2 - y0), "Plume", font=font, fill=INK)
    out.parent.mkdir(parents=True, exist_ok=True)
    im.resize((w, h), Image.LANCZOS).save(out)


def write_tv_banners():
    tv_banner(ROOT / "client/tvApp/src/main/res/drawable-xhdpi/tv_banner.png", 640, 360)
    tv_banner(ROOT / "play/graphics/tvBanner/tv_banner.png", 1280, 720)


if __name__ == "__main__":
    import sys

    if sys.argv[1:] == ["tv"]:
        write_tv_banners()
        sys.exit()
    write_desktop()
    if sys.argv[1:] == ["desktop"]:
        sys.exit()
    write_tv_banners()
    raster(512).save(ROOT / "play/graphics/icon/icon.png")
    raster(1024).save(ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
    (ROOT / "client/androidApp/src/main/res/drawable/ic_launcher_foreground.xml").write_text(vector(), encoding="utf-8")
    notification = ROOT / "client/shared/src/androidMain/res/drawable/ic_notification.xml"
    notification.parent.mkdir(parents=True, exist_ok=True)
    notification.write_text(vector(24, 11, "#FFFFFFFF", 0.02, box=True), encoding="utf-8")
    # Tentýž tvar jako ikona notifikace; barvu mu v Compose dává `tint` podle motivu.
    logo = ROOT / "client/resources/src/commonMain/composeResources/drawable/plume_logo_24dp.xml"
    logo.write_text(vector(24, 11, "#FF000000", 0.02, box=True), encoding="utf-8")
