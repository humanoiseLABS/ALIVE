"""The mark, redesigned - five flat directions, including ones that are not an hourglass.

He said the logo may be replaced, which changes the question. The hourglass was
never the brand; it was the pixel era's answer to "show me time and show me what
I earned". Under flat, the app already has stronger things to be: the ring the
pill draws, the family of bodies, the streak. So this sheet proposes marks from
those, and judges every one of them the only way a mark can be judged:

  - at 96/48/32/24/16 px in colour,
  - as the status bar keeps it (alpha only, dark and light),
  - as a launcher icon, where the adaptive-icon safe zone crops 21 % of the box
    on every side and the monochrome layer gets no colour at all,
  - and by the thinnest part that survives at 24 px.

Holes are the whole trick for a mark: an eye drawn in a second colour is a
decoration the status bar throws away, while an eye PUNCHED out of the body is
still an eye when only alpha is left. Everything here is drawn as one flat
colour with real holes, so what the strip shows is what ships.

    python mark/redesign.py
"""
import math
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "flat-marks.png"
V = 72
TOKEN = re.compile(r"[MLCHVZmlchvz]|-?\d*\.?\d+")

INK = "#141414"
GOLD = "#E0A526"
DEEP = "#2A2F7A"
GROUND = "#FBFAF8"
BLOSSOM = "#FFB8D6"
PAPER = "#FFFFFF"


# ---------- path building, in the vocabulary a VectorDrawable allows ----------
def poly(points):
    d = f"M {points[0][0]:.2f} {points[0][1]:.2f}"
    d += "".join(f" L {x:.2f} {y:.2f}" for x, y in points[1:])
    return d + " Z"


def arc_pts(cx, cy, r, a0, a1, steps=48):
    return [(cx + r * math.cos(a0 + (a1 - a0) * i / steps),
             cy + r * math.sin(a0 + (a1 - a0) * i / steps)) for i in range(steps + 1)]


def circle(cx, cy, r):
    k = 0.5523 * r
    return (f"M {cx + r} {cy} "
            f"C {cx + r} {cy + k} {cx + k} {cy + r} {cx} {cy + r} "
            f"C {cx - k} {cy + r} {cx - r} {cy + k} {cx - r} {cy} "
            f"C {cx - r} {cy - k} {cx - k} {cy - r} {cx} {cy - r} "
            f"C {cx + k} {cy - r} {cx + r} {cy - k} {cx + r} {cy} Z")


def capsule(x, y, w, h):
    r = w / 2
    return (f"M {x + r} {y} L {x + w - r} {y} C {x + w - r + k_of(r)} {y} {x + w} {y + r - k_of(r)} {x + w} {y + r} "
            f"L {x + w} {y + h - r} C {x + w} {y + h - r + k_of(r)} {x + w - r + k_of(r)} {y + h} {x + w - r} {y + h} "
            f"L {x + r} {y + h} C {x + r - k_of(r)} {y + h} {x} {y + h - r + k_of(r)} {x} {y + h - r} "
            f"L {x} {y + r} C {x} {y + r - k_of(r)} {x + r - k_of(r)} {y} {x + r} {y} Z")


def k_of(r):
    return 0.5523 * r


def rrect(x, y, w, h, r):
    k = k_of(r)
    return (f"M {x + r} {y} L {x + w - r} {y} C {x + w - r + k} {y} {x + w} {y + r - k} {x + w} {y + r} "
            f"L {x + w} {y + h - r} C {x + w} {y + h - r + k} {x + w - r + k} {y + h} {x + w - r} {y + h} "
            f"L {x + r} {y + h} C {x + r - k} {y + h} {x} {y + h - r + k} {x} {y + h - r} "
            f"L {x} {y + r} C {x} {y + r - k} {x + r - k} {y} {x + r} {y} Z")


def subpaths(data):
    tokens = TOKEN.findall(data)
    i, rings, ring = 0, [], []
    x = y = sx = sy = 0.0
    while i < len(tokens):
        cmd = tokens[i]
        i += 1
        if cmd == "M":
            if len(ring) > 2:
                rings.append(ring)
            ring = []
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            sx, sy = x, y
            ring.append((x, y))
        elif cmd == "L":
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            ring.append((x, y))
        elif cmd in ("H", "h"):
            v = float(tokens[i]); i += 1
            x = x + v if cmd == "h" else v
            ring.append((x, y))
        elif cmd in ("V", "v"):
            v = float(tokens[i]); i += 1
            y = y + v if cmd == "v" else v
            ring.append((x, y))
        elif cmd == "C":
            x1, y1, x2, y2, nx, ny = (float(t) for t in tokens[i:i + 6])
            i += 6
            for s in range(1, 25):
                t = s / 24
                u = 1 - t
                ring.append((u**3 * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * nx,
                             u**3 * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * ny))
            x, y = nx, ny
        elif cmd in ("Z", "z"):
            ring.append((sx, sy))
            x, y = sx, sy
    if len(ring) > 2:
        rings.append(ring)
    return rings


# ---------- the five directions ----------
def m_critter():
    """The family, promoted: one flat body with its eyes punched out of it.
    The eyes survive the status bar because they are holes, not a second colour."""
    body = circle(36, 36, 28)
    holes = [capsule(25, 27, 9, 20), capsule(39, 27, 9, 20)]
    return [(body, 1)], holes


def m_ring():
    """The pill's own signature: an allowance ring with a gap and a head."""
    a0, a1 = math.radians(-58), math.radians(196)
    outer = arc_pts(36, 36, 28, a0, a1)
    inner = arc_pts(36, 36, 16, a1, a0)
    band = poly(outer + inner)
    head = circle(36 + 22 * math.cos(a0), 36 + 22 * math.sin(a0), 7)
    return [(band, 1), (head, 1)], []


def m_hourglass():
    """The old mark, re-drawn rather than kept: caps, and sand that reaches both
    bulbs so the silhouette is one shape with one waist."""
    cap_top = rrect(8, 3, 56, 12, 6)
    cap_bottom = rrect(8, 57, 56, 12, 6)
    glass = poly([(18, 15), (54, 15), (40, 33), (40, 39), (54, 57), (18, 57), (32, 39), (32, 33)])
    return [(cap_top, 1), (cap_bottom, 1), (glass, 1)], []


def m_counter():
    """A monogram. The counter of the A is the hourglass: the mark says ALIVE and
    says time in one shape, and only works if the hole stays open."""
    body = poly([(36, 4), (66, 68), (48, 68), (42, 52), (30, 52), (24, 68), (6, 68)])
    holes = [poly([(36, 22), (44, 44), (28, 44)])]
    return [(body, 1)], holes


def m_flame():
    """The streak. A flame reads as "kept going", which is the one thing this app
    actually rewards, and it is the only candidate that is not symmetric."""
    body = poly([(36, 4), (52, 24), (58, 40), (50, 62), (36, 68), (22, 62), (14, 40), (20, 24)])
    holes = [poly([(36, 24), (46, 42), (40, 56), (36, 58), (32, 56), (26, 42)])]
    return [(body, 1)], holes


def m_critter_wide():
    """D1 with the stance widened. At the first draft's spacing the two punched
    eyes are 2 px apart at 16 px and merge into one bar, which is a geometry
    mistake, not a verdict on the idea."""
    return [(circle(36, 36, 28), 1)], [capsule(21, 25, 11, 23), capsule(40, 25, 11, 23)]


def m_counter_wide():
    """D4 with legs thick enough to keep the counter open at 24 px."""
    return [(poly([(36, 4), (68, 68), (50, 68), (43, 50), (29, 50), (22, 68), (4, 68)]), 1)], \
           [poly([(36, 20), (46, 44), (26, 44)])]


def m_flame_wide():
    """D5 with a hole that survives: smaller, rounder, and centred low."""
    return [(poly([(36, 4), (54, 26), (60, 44), (50, 64), (36, 70), (22, 64), (12, 44), (18, 26)]), 1)], \
           [poly([(36, 30), (46, 46), (40, 58), (32, 58), (26, 46)])]


MARKS = [
    ("D1  the critter", "one flat body, eyes punched out - the family promoted to logo", m_critter, BLOSSOM),
    ("D1b the critter, wide stance", "same idea, eyes further apart so they stay two eyes at 16 px", m_critter_wide, BLOSSOM),
    ("D2  the ring", "the pill's allowance ring, with a gap and a head", m_ring, GOLD),
    ("D3  the hourglass, redrawn", "not kept: caps and a waist, sand in both bulbs", m_hourglass, INK),
    ("D4  the counter", "an A whose hole is an hourglass - brand and time in one shape", m_counter, DEEP),
    ("D4b the counter, thickened", "the same pun with legs that survive the crop", m_counter_wide, DEEP),
    ("D5  the streak", "a flame: the only thing the app actually rewards", m_flame, GOLD),
    ("D5b the streak, opened", "a hole small enough to still be a hole at 24 px", m_flame_wide, GOLD),
]


# ---------- the renderer ----------
def render(maker, size, colour=INK):
    """One mark. Holes are subtracted from the alpha, so they are real holes."""
    scale = size / V
    body = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pen = ImageDraw.Draw(body)
    layers, holes = maker()
    for data, _ in layers:
        for ring in subpaths(data):
            pen.polygon([(x * scale, y * scale) for x, y in ring], fill=colour)
    if holes:
        mask = Image.new("L", (size, size), 0)
        hp = ImageDraw.Draw(mask)
        for data in holes:
            for ring in subpaths(data):
                hp.polygon([(x * scale, y * scale) for x, y in ring], fill=255)
        body.putalpha(ImageChops.subtract(body.split()[3], mask))
    return body


def masked(maker, size, tint):
    shape = render(maker, size, INK)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (size, size), tint), (0, 0), shape.split()[3])
    return out


def strip(bg, tint, icon):
    h = 30
    img = Image.new("RGBA", (150, h), bg)
    pen = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    pen.text((8, 7), "09:41", font=font, fill=tint)
    img.paste(icon, (66, (h - icon.height) // 2), icon)
    pen.rectangle([92, 9, 114, 21], outline=tint, width=2)
    pen.rectangle([94, 11, 110, 19], fill=tint)
    return img


def launcher(maker, size, colour, dark=True):
    """An adaptive icon: the ground, the mark at the safe-zone size, and the
    circle the launcher actually shows. 66 % of 108 dp is the keep-in zone."""
    bg = DEEP if dark else GROUND
    img = Image.new("RGBA", (size, size), bg)
    pen = ImageDraw.Draw(img)
    inner = int(size * 0.66)
    mark = render(maker, inner, PAPER if dark else colour)
    img.paste(mark, ((size - inner) // 2, (size - inner) // 2), mark)
    pen.ellipse([size * 0.035, size * 0.035, size * 0.965, size * 0.965], outline="#8A8A8A", width=1)
    return img


def measure(maker):
    """Opaque pixel counts, and the thinnest crossing in the MIDDLE band only.

    The first version of this measured every row, which punished a pointed apex
    and a flame tip - shapes that are 1 px wide at their top by design - and
    made D4 and D5 look dead when the real question is whether their BODY parts
    survive. The middle 60 % of the mark is where a part has to stay thick.
    """
    counts = []
    for size in (16, 24, 32):
        a = render(maker, size, INK).split()[3]
        counts.append(sum(1 for p in a.getdata() if p >= 128))
    a = render(maker, 24, INK).split()[3]
    rows = []
    for y in range(24):
        run = longest = 0
        for x in range(24):
            run = run + 1 if a.getpixel((x, y)) >= 128 else 0
            longest = max(longest, run)
        if longest:
            rows.append((y, longest))
    if not rows:
        return counts, 0, 0
    top, bottom = rows[0][0], rows[-1][0]
    span = bottom - top
    band = [w for y, w in rows if top + span * 0.2 <= y <= top + span * 0.8]
    return counts, (min(band) if band else 0), min(w for _, w in rows)


# ---------- the sheet ----------
SIZES = [96, 48, 32, 24, 16]
ROW_H = 178
sheet = Image.new("RGB", (1700, 84 + len(MARKS) * ROW_H + 120), "#F7F5F1")
pen = ImageDraw.Draw(sheet)
try:
    big = ImageFont.truetype("arialbd.ttf", 20)
    small = ImageFont.truetype("arial.ttf", 14)
    tiny = ImageFont.truetype("arial.ttf", 12)
except OSError:
    big = small = tiny = ImageFont.load_default()

pen.text((16, 14), "The mark, redesigned - five flat directions", font=big, fill=INK)
pen.text((16, 42), "colour 96/48/32/24/16 px   |   status bar, alpha only   |   launcher icon: dark, light, and the monochrome layer",
         font=small, fill="#5A5A5A")

y = 84
for title, note, maker, hue in MARKS:
    pen.text((16, y), title, font=big, fill=INK)
    pen.text((16, y + 24), note, font=small, fill="#5A5A5A")
    x = 300
    for size in SIZES:
        cell = render(maker, size, hue)
        sheet.paste(cell, (x, y + 52), cell)
        pen.text((x, y + 156), str(size), font=tiny, fill="#8A8A8A")
        x += size + 16
    sheet.paste(strip("#1F2126", "#FFFFFF", masked(maker, 24, "#FFFFFF")), (x + 10, y + 52))
    sheet.paste(strip("#E8EAE6", INK, masked(maker, 24, INK)), (x + 10, y + 88))
    counts, band_thin, all_thin = measure(maker)
    pen.text((x + 176, y + 56), f"solid px  16:{counts[0]}  24:{counts[1]}  32:{counts[2]}", font=small, fill="#5A5A5A")
    pen.text((x + 176, y + 78), f"thinnest part at 24 px: middle band {band_thin} px (apex {all_thin})",
             font=small, fill="#5A5A5A")
    lx = x + 176
    sheet.paste(launcher(maker, 84, hue, dark=True), (lx + 40, y + 96))
    sheet.paste(launcher(maker, 84, hue, dark=False), (lx + 132, y + 96))
    sheet.paste(launcher(maker, 84, "#FFFFFF", dark=True), (lx + 224, y + 96))
    y += ROW_H

sheet.save(OUT)
print(f"wrote {OUT}  ({sheet.width}x{sheet.height})")
