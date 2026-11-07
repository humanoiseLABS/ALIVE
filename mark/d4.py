"""D4, five ways - variations on the generation-1 A, not on generation 2.

He approved the critter as it was drawn (D1b gen 1) and asked for the A to be
modified further, meaning the gen-1 A: the pointed apex, the long legs, the
slim waist. Those are the mark's character, so they stay, and only the part
that dies at 16 px is touched - the counter, which gen 1 closes into a notch.

The pun is worth making literal: the counter of an A is already two triangles,
and an hourglass is two triangles. Where that is what the mark means, the hole
should be drawn as one.

    python mark/d4.py
"""
from pathlib import Path

import redesign as R
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "flat-marks-d4.png"
V = 72
poly, circle, capsule = R.poly, R.circle, R.capsule
INK, DEEP, BLOSSOM, PAPER, GROUND = R.INK, R.DEEP, R.BLOSSOM, R.PAPER, R.GROUND
FONT = HERE.parent / "app/src/main/res/font/space_grotesk_bold.ttf"


# ---------- the A ----------
def a_body(apex=4, spread=66, foot=68, bar=52, cw=6, legw=18):
    """The gen-1 silhouette: a point at the top, two long legs, a crossbar.

    Traced in one direction so the polygon does not fold through itself: apex,
    right outer foot, right inner foot, the crossbar's two ends, then down the
    left leg's inner and outer edges.
    """
    ri = spread - legw
    li = 72 - ri
    return poly([
        (36, apex), (spread, foot), (ri, foot),
        (36 + cw, bar), (36 - cw, bar), (li, foot), (72 - spread, foot)
    ])


def counter_notch():
    """gen 1, unchanged: one triangle."""
    return [poly([(36, 22), (44, 44), (28, 44)])]


def counter_raised():
    """The same triangle, moved up and opened wider than the stroke holding it."""
    return [poly([(36, 17), (49, 47), (23, 47)])]


def counter_hourglass():
    """Two triangles meeting at a waist - the hourglass drawn inside the letter."""
    return [poly([(25, 17), (47, 17), (38, 32), (47, 47), (25, 47), (34, 32)])]


def counter_hourglass_stream():
    """As above, with a slit through the apex: the neck the sand falls through."""
    return counter_hourglass() + [poly([(34, 5), (38, 5), (38, 17), (34, 17)])]


def counter_split():
    """Two holes, the crossbar between them - an A that is also a glass."""
    return [poly([(27, 17), (45, 17), (37, 30), (35, 30)]),
            poly([(35, 36), (37, 36), (46, 49), (26, 49)])]


def counter_two_triangles():
    """The hourglass said without saying it: two triangles with a BRIDGE between
    them, not a waist. The old mark's own comment already knew this - "a neck
    that stays open" - and E2 and E5 forgot it, which is why their thickest part
    fell to 1 px. The bridge is the neck; the neck is solid."""
    return [poly([(28, 15), (44, 15), (36, 27)]),
            poly([(36, 34), (47, 47), (25, 47)])]


VARIANTS = [
    ("D4 gen 1  (baseline)", "what he pointed at - one triangular counter, apex to a point",
     lambda: [(a_body(), 1)], counter_notch),
    ("E1  raised counter", "same A, hole moved up and widened past the stroke that holds it",
     lambda: [(a_body(), 1)], counter_raised),
    ("E2  hourglass counter", "the pun made literal: two triangles meeting at a waist",
     lambda: [(a_body(), 1)], counter_hourglass),
    ("E3  with the stream", "E2 plus a slit through the apex - the neck the sand falls through",
     lambda: [(a_body(), 1)], counter_hourglass_stream),
    ("E4  wide stance", "E2 on legs that reach the edge, so the strokes thicken without fattening the A",
     lambda: [(a_body(spread=70, legw=20, bar=50, cw=7), 1)], counter_hourglass),
    ("E5  split counter", "two holes and the crossbar between them: an A that reads as a glass",
     lambda: [(a_body(), 1)], counter_split),
    ("E6  two triangles, bridged", "the glass without the pinch: the neck is solid material, not a meeting point",
     lambda: [(a_body(bar=54, cw=7), 1)], counter_two_triangles),
    ("E7  E6, wider legs", "same, on legs that reach the edge so every stroke gains weight",
     lambda: [(a_body(spread=70, legw=20, bar=54, cw=8), 1)], counter_two_triangles),
]

# The critter he approved, plus the only change the small end needs.
CRITTER_APPROVED = lambda: R.m_critter_wide()
CRITTER_SMALL = lambda: ([(circle(36, 36, 28), 1)], [capsule(19, 26, 12, 21), capsule(41, 26, 12, 21)])


def make(layers_fn, holes_fn):
    def maker():
        return layers_fn(), holes_fn()
    return maker


def zoom(maker, colour, factor=8):
    small = R.render(maker, 16, colour)
    return small.resize((16 * factor, 16 * factor), Image.NEAREST)


def lockup(maker, colour, height=96, word="ALIVE"):
    try:
        font = ImageFont.truetype(str(FONT), int(height * 0.52))
    except OSError:
        font = ImageFont.load_default()
    probe = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
    box = probe.textbbox((0, 0), word, font=font)
    text_w = box[2] - box[0]
    img = Image.new("RGBA", (height + 20 + text_w + 8, height), (0, 0, 0, 0))
    pen = ImageDraw.Draw(img)
    mark = R.render(maker, height, colour)
    img.paste(mark, (0, 0), mark)
    pen.text((height + 20 - box[0], height // 2 - (box[3] - box[1]) // 2 - box[1]), word, font=font, fill=INK)
    return img


SIZES = [96, 48, 32, 24, 16]
ROWS = [(t, n, make(l, h), DEEP) for t, n, l, h in VARIANTS] + [
    ("D1b gen 1  (approved)", "kunci - tidak diubah; baris terakhir cuma buat lihat apa yang terjadi di 16 px", CRITTER_APPROVED, BLOSSOM),
    ("D1b small variant", "bukan pengganti: ini yang dipakai 10 permukaan sistem kalau matanya nyatu di 16 px", CRITTER_SMALL, BLOSSOM),
]

sheet = Image.new("RGB", (1760, 96 + len(ROWS) * 250), "#F7F5F1")
pen = ImageDraw.Draw(sheet)
try:
    big = ImageFont.truetype("arialbd.ttf", 20)
    small = ImageFont.truetype("arial.ttf", 14)
    tiny = ImageFont.truetype("arial.ttf", 12)
except OSError:
    big = small = tiny = ImageFont.load_default()

pen.text((16, 14), "D4 dimodifikasi - lima turunan dari gen 1, plus critter yang udah di-approve", font=big, fill=INK)
pen.text((16, 42), "ladder 96-16 px   |   status bar (alpha only)   |   launcher: dark, light, monochrome   |   16 px 8x   |   lockup Space Grotesk Bold",
         font=small, fill="#5A5A5A")

y = 96
lines = []
for title, note, maker, hue in ROWS:
    pen.text((16, y), title, font=big, fill=INK)
    pen.text((16, y + 24), note, font=small, fill="#5A5A5A")
    counts, band, apex = R.measure(maker)
    pen.text((16, y + 46), f"solid 16:{counts[0]}  24:{counts[1]}  32:{counts[2]}   band {band} px   apex {apex} px",
             font=small, fill="#5A5A5A")
    lines.append(f"{title:24s} 16:{counts[0]:4d} 24:{counts[1]:4d} 32:{counts[2]:4d}  band {band}  apex {apex}")
    x = 300
    for size in SIZES:
        cell = R.render(maker, size, hue)
        sheet.paste(cell, (x, y + 84), cell)
        pen.text((x, y + 84 + size + 4), str(size), font=tiny, fill="#8A8A8A")
        x += size + 18
    sheet.paste(R.strip("#1F2126", PAPER, R.masked(maker, 24, PAPER)), (x + 8, y + 84))
    sheet.paste(R.strip("#E8EAE6", INK, R.masked(maker, 24, INK)), (x + 8, y + 120))
    lx = x + 170
    sheet.paste(R.launcher(maker, 84, hue, dark=True), (lx, y + 84))
    sheet.paste(R.launcher(maker, 84, hue, dark=False), (lx + 96, y + 84))
    sheet.paste(R.launcher(maker, 84, PAPER, dark=True), (lx + 192, y + 84))
    blown = zoom(maker, INK)
    sheet.paste(blown, (lx + 292, y + 84), blown)
    pen.text((lx + 292, y + 220), "16 px, 8x", font=tiny, fill="#8A8A8A")
    word = lockup(maker, hue)
    sheet.paste(word, (lx + 440, y + 92), word)
    y += 250

sheet.save(OUT)
print(f"wrote {OUT}  ({sheet.width}x{sheet.height})")
print("\n".join(lines))
