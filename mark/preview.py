"""The hourglass, flat - the second sheet, wider on purpose.

The first sheet offered three candidates and he said "ga cocok" without naming
which part. That is data, not a failure: it means the question was wrong. So
this sheet does not ask "which shape", it asks the three separate questions a
rejection like that can mean, and puts the shipped mark in as a baseline so
"keep it" is a choice with a picture next to it, not a regression.

  row 0  the mark that ships today, pixel, unmodified - the thing at stake
  row 1  shape: the old identity kept (plinth wider than glass, sand low, a
         fall through an open neck), re-drawn as curves
  row 2  shape: the same identity, but every part thickened for 16 dp
  row 3  one mark for every surface, so the answer is not two artefacts
  row 4  what the tick does, three ways, as filmstrips

Nothing here touches res/drawable. The path data is M/L/C/Z on a 72 box, which
is the subset the app's own parsers accept, so a winner goes straight into a
VectorDrawable.

    python mark/preview.py
"""
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
HERE = Path(__file__).resolve().parent
OUT = HERE / "hourglass-candidates.png"
V = 72
TOKEN = re.compile(r"[MLCHVZmlchvz]|-?\d*\.?\d+")

INK = "#141414"
GOLD = "#E0A526"
GOLD_PALE = "#F6E7C2"
PAPER = "#FFFFFF"
GROUND = "#FBFAF8"


# ---------- path building, in the vocabulary a VectorDrawable allows ----------
def poly(points, close=True):
    d = f"M {points[0][0]} {points[0][1]}"
    d += "".join(f" L {x} {y}" for x, y in points[1:])
    return d + (" Z" if close else "")


def rrect(x, y, w, h, r):
    k = 0.5523 * r
    return (
        f"M {x + r} {y} L {x + w - r} {y} "
        f"C {x + w - r + k} {y} {x + w} {y + r - k} {x + w} {y + r} "
        f"L {x + w} {y + h - r} "
        f"C {x + w} {y + h - r + k} {x + w - r + k} {y + h} {x + w - r} {y + h} "
        f"L {x + r} {y + h} "
        f"C {x + r - k} {y + h} {x} {y + h - r + k} {x} {y + h - r} "
        f"L {x} {y + r} "
        f"C {x} {y + r - k} {x + r - k} {y} {x + r} {y} Z"
    )


def subpaths(data):
    """M/L/H/V/C/Z - H and V only so the shipped pixel mark can be read in."""
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
            x = x + float(tokens[i]) if cmd == "h" else float(tokens[i]); i += 1
            ring.append((x, y))
        elif cmd in ("V", "v"):
            y = y + float(tokens[i]) if cmd == "v" else float(tokens[i]); i += 1
            ring.append((x, y))
        elif cmd == "C":
            x1, y1, x2, y2, nx, ny = (float(t) for t in tokens[i:i + 6])
            i += 6
            for k in range(1, 25):
                t = k / 24
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


# ---------- the shipped mark, read from the file it lives in ----------
def legacy_layers():
    xml = (ROOT / "app/src/main/res/drawable/ic_alive_hourglass.xml").read_text(encoding="utf-8")
    vp = float(re.search(r'viewportWidth="(\d+)"', xml).group(1))
    scale = V / vp
    out = []
    for m in re.finditer(r'fillColor="([^"]+)"\s+(?:android:)?pathData="([^"]+)"', xml):
        token, data = m.group(1), m.group(2)
        hexes = dict(re.findall(r'<color name="([^"]+)">([^<]+)</color>',
                                (ROOT / "app/src/main/res/values/colors.xml").read_text(encoding="utf-8")))
        colour = hexes[token.replace("@color/", "")].strip()
        # Scale the coordinates, and rewrite the relative h/v runs as absolute.
        nums = re.findall(r"-?\d*\.?\d+", data)
        letters = re.findall(r"[MmHhVvZz]", data)
        rebuilt, j = [], 0
        for cmd in letters:
            rebuilt.append(cmd)
            take = 0 if cmd.upper() == "Z" else 2 if cmd.upper() == "M" else 1
            for _ in range(take):
                rebuilt.append(str(float(nums[j]) * scale))
                j += 1
        out.append((" ".join(rebuilt), colour))
    return out


# ---------- the candidates ----------
def frame(wall, neck, sand=0.62):
    """The old identity: caps wider than the glass, walls converging on an open
    neck, sand only in the lower bulb, and a fall through the neck."""
    top_cap = rrect(6, 2, 60, 12, 5)
    bottom_cap = rrect(6, 58, 60, 12, 5)
    left = poly([(16, 14), (16 + wall, 14), (36 - neck / 2, 36 - neck / 2),
                 (36 - neck / 2, 36 + neck / 2), (16 + wall, 58), (16, 58),
                 (28, 36)])
    right = poly([(56 - wall, 14), (56, 14), (44, 36), (56, 58), (56 - wall, 58),
                  (36 + neck / 2, 36 + neck / 2), (36 + neck / 2, 36 - neck / 2)])
    lower = poly([(36 - neck / 2 - 2, 36 + neck / 2 + 2), (36 + neck / 2 + 2, 36 + neck / 2 + 2),
                  (52 - wall, 56), (20 + wall, 56)])
    fall = rrect(34, 36 - neck / 2, 4, neck, 1.5)
    return [(left, INK), (right, INK), (top_cap, INK), (bottom_cap, INK), (lower, GOLD), (fall, GOLD)]


def solid_mark(neck, cap):
    """One shape, no second colour: caps and bulbs fused. The neck is the only
    thing that keeps it from being the diamond the old comment warns about."""
    upper = poly([(8, 4), (64, 4), (64, 4 + cap), (36 + neck / 2, 34), (36 - neck / 2, 34), (8, 4 + cap)])
    lower = poly([(8, 68), (64, 68), (64, 68 - cap), (36 + neck / 2, 38), (36 - neck / 2, 38), (8, 68 - cap)])
    return [(upper, INK), (lower, INK)]


def one_mark():
    """Row 3: a single artefact that is both the brand mark and the small icon -
    the frame reading, but every part at the weight the status bar needs."""
    cap_top = rrect(6, 2, 60, 14, 6)
    cap_bottom = rrect(6, 56, 60, 14, 6)
    body = poly([(18, 16), (54, 16), (40, 33), (40, 39), (54, 56), (18, 56), (32, 39), (32, 33)])
    return [(cap_top, INK), (cap_bottom, INK), (body, GOLD)]


CANDIDATES = [
    ("0  what ships today", "the pixel mark, read straight out of res/drawable - the thing at stake", None),
    ("1  identity kept, flat", "caps wider than the glass, walls, sand low, fall through an open neck", lambda: frame(6, 8)),
    ("2  identity kept, chunky", "same reading, every part thickened because 16 dp deletes thin parts", lambda: frame(10, 12)),
    ("3  one mark everywhere", "no second artefact: the frame reading at the weight the bar needs", one_mark),
]

SIZES = [96, 48, 32, 24, 16]


# ---------- the renderer ----------
def draw(layers, size, rotation=0):
    scale = size / V
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pen = ImageDraw.Draw(img)
    for data, colour in layers:
        for ring in subpaths(data):
            pen.polygon([(x * scale, y * scale) for x, y in ring], fill=colour)
    if rotation:
        img = img.rotate(rotation, expand=False)
    return img


def masked(layers, size, tint):
    """What Android keeps of a small icon: the alpha, and nothing else."""
    shape = draw(layers, size)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    solid = Image.new("RGBA", (size, size), tint)
    out.paste(solid, (0, 0), shape.split()[3])
    return out


def strip(bg, tint, icon, label="09:41"):
    h = 30
    w = 150
    img = Image.new("RGBA", (w, h), bg)
    pen = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    pen.text((8, 7), label, font=font, fill=tint)
    img.paste(icon, (66, (h - icon.height) // 2), icon)
    pen.rectangle([92, 9, 114, 21], outline=tint, width=2)
    pen.rectangle([94, 11, 110, 19], fill=tint)
    return img


def measure(layers):
    """Opaque pixel counts, and the thinnest crossing the mark still has at 24 px.

    The second number is the one that decides a small icon: a part that is one
    pixel wide at 24 px is a part the status bar anti-aliases out of existence.
    """
    import numpy as np
    counts = []
    for size in (16, 24, 32):
        counts.append(int((np.array(draw(layers, size).split()[3]) >= 128).sum()))
    a = np.array(draw(layers, 24).split()[3]) >= 128
    crossings = []
    for row in a:
        run = longest = 0
        for solid in row:
            run = run + 1 if solid else 0
            longest = max(longest, run)
        if longest:
            crossings.append(longest)
    return counts, (min(crossings) if crossings else 0)


# ---------- the sheet ----------
LEGACY = legacy_layers()
ROW_H = 176
sheet = Image.new("RGB", (1620, 74 + len(CANDIDATES) * ROW_H + 150), "#F7F5F1")
pen = ImageDraw.Draw(sheet)
try:
    big = ImageFont.truetype("arialbd.ttf", 20)
    small = ImageFont.truetype("arial.ttf", 14)
    tiny = ImageFont.truetype("arial.ttf", 12)
except OSError:
    big = small = tiny = ImageFont.load_default()

pen.text((16, 14), "The hourglass, flat - sheet 2: the three questions a 'ga cocok' can mean", font=big, fill=INK)
pen.text((16, 42), "colour 96/48/32/24/16 px   then   what a dark status bar keeps (alpha only)   then   a light one   then   the tick",
         font=small, fill="#5A5A5A")

y = 78
rows = []
for title, note, maker in CANDIDATES:
    layers = LEGACY if maker is None else maker()
    rows.append((title, layers))
    pen.text((16, y), title, font=big, fill=INK)
    pen.text((16, y + 24), note, font=small, fill="#5A5A5A")
    x = 300
    for size in SIZES:
        cell = draw(layers, size)
        sheet.paste(cell, (x, y + 44), cell)
        pen.text((x, y + 148), str(size), font=tiny, fill="#8A8A8A")
        x += size + 16
    sheet.paste(strip("#1F2126", PAPER, masked(layers, 24, PAPER)), (x + 14, y + 44))
    sheet.paste(strip("#E8EAE6", INK, masked(layers, 24, INK)), (x + 14, y + 80))
    counts, thinnest = measure(layers)
    pen.text((x + 174, y + 50), f"solid px  16:{counts[0]}  24:{counts[1]}  32:{counts[2]}", font=small, fill="#5A5A5A")
    pen.text((x + 174, y + 72), f"thinnest part at 24 px: {thinnest} px", font=small, fill="#5A5A5A")
    y += ROW_H

# ---------- the tick, three ways ----------
pen.text((16, y + 6), "the tick - what hourglassMark() does to each of them", font=big, fill=INK)
ty = y + 36
for label, frames in [
    ("quarter turns (what ships)", [(0, None), (90, None), (180, None), (270, None)]),
    ("flip 180 only", [(0, None), (180, None), (0, None), (180, None)]),
    ("sand falls, glass stays", None),
]:
    pen.text((16, ty + 8), label, font=small, fill="#5A5A5A")
    x = 240
    for i, size in enumerate([96, 48, 32, 24, 16]):
        if frames:
            cell = draw(rows[2][1], size, rotation=frames[i % 4][0])
        else:
            t = (i % 4) / 3
            upper = poly([(20, 18), (52, 18), (38, 18 + 16 * (1 - t)), (34, 18 + 16 * (1 - t))])
            lower = poly([(34, 40 + 14 * t), (38, 40 + 14 * t), (52, 56), (20, 56)])
            cell = draw([*frame(10, 12)[:4], (upper, GOLD_PALE), (lower, GOLD)], size)
        sheet.paste(cell, (x, ty + 2), cell)
        x += size + 16
    ty += 46

sheet.save(OUT)
print(f"wrote {OUT}  ({sheet.width}x{sheet.height})")
