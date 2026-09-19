"""Ikona aplikace (pírko) ze JEDNÉ geometrie pro všechny výstupy:

    play/graphics/icon/icon.png                                    – Play, 512 × 512
    iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png   – iOS, 1024 × 1024, bez alfy
    client/androidApp/src/main/res/drawable/ic_launcher_foreground.xml – vektor adaptivní ikony

Spuštění: python3 play/tools/icon.py (Pillow, shapely). Barvy pozadí musí sedět
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
TOP = (92, 64, 190)      # #5C40BE
BOTTOM = (44, 30, 118)   # #2C1E76
INK = (255, 243, 232)    # #FFF3E8


def bez(p0, p1, p2, p3, n=240):
    return [tuple((1 - t) ** 3 * p0[i] + 3 * (1 - t) ** 2 * t * p1[i] + 3 * (1 - t) * t ** 2 * p2[i]
                  + t ** 3 * p3[i] for i in range(2)) for t in (k / n for k in range(n + 1))]


def feather():
    tip, base = (0.78, 0.15), (0.34, 0.68)
    vane = Polygon(bez(base, (0.16, 0.50), (0.46, 0.20), tip) + bez(tip, (0.80, 0.40), (0.58, 0.60), base))
    notches = [Polygon(t) for t in (
        [(0.62, 0.535), (0.745, 0.50), (0.60, 0.50)],
        [(0.735, 0.36), (0.80, 0.335), (0.70, 0.345)],
    )]
    shaft = LineString(bez(base, (0.45, 0.52), (0.60, 0.34), (0.72, 0.22), 60)).buffer(0.007, quad_segs=32)
    stem = LineString(bez((0.345, 0.67), (0.30, 0.74), (0.25, 0.80), (0.19, 0.85), 60)).buffer(0.015, quad_segs=32)
    shape = unary_union([vane.difference(unary_union(notches + [shaft])), stem])
    return shape.simplify(0.0002)


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
    P = lambda c: [(ox + x * s * scale, oy + y * s * scale) for x, y in c]
    for poly in polygons(feather()):
        d.polygon(P(poly.exterior.coords), fill=255)
        for hole in poly.interiors:
            d.polygon(P(hole.coords), fill=0)
    return m


def raster(size):
    n = size * 4
    im = gradient(n, n)
    im.paste(Image.new("RGB", (n, n), INK), (0, 0), feather_mask(n))
    return im.resize((size, size), Image.LANCZOS)


def vector():
    """Popředí adaptivní ikony: 108 dp, pírko celé uvnitř bezpečného kruhu (r = 33 dp)."""
    shape = feather()
    cx, cy = shape.centroid.x, shape.centroid.y
    reach = max(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 for p in polygons(shape) for x, y in p.exterior.coords)
    k = 31 / reach
    fmt = lambda c: "M" + " L".join(f"{54 + (x - cx) * k:.2f},{54 + (y - cy) * k:.2f}" for x, y in c) + " Z"
    data = " ".join(fmt(r.coords) for p in polygons(shape) for r in [p.exterior, *p.interiors])
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Vygenerováno z play/tools/icon.py, ručně neupravovat. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFF3E8"
        android:fillType="evenOdd"
        android:pathData="{data}" />
</vector>
"""


if __name__ == "__main__":
    raster(512).save(ROOT / "play/graphics/icon/icon.png")
    raster(1024).save(ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
    (ROOT / "client/androidApp/src/main/res/drawable/ic_launcher_foreground.xml").write_text(vector(), encoding="utf-8")
