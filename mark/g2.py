"""Generation 2 - the two he likes, made to survive the small end.

He picked D1b and D4, which are the two my own metric ranked weakest. That is
not a contradiction to argue with: the metric measures weight, his eye measures
character, and a logo is allowed to be light. What is NOT allowed is shipping
something whose counter closes at 16 px, because then the mark stops being a
mark exactly where an app icon is smallest.

So this sheet does not re-pitch other candidates. It takes the two he chose and
fixes the one real problem each has, then shows the result the only way it can
be judged:

  - the colour ladder at 96 / 48 / 32 / 24 / 16 px,
  - the same marks as the status bar keeps them (alpha only, dark and light),
  - the adaptive launcher icon in dark, light and monochrome,
  - a lockup set in the app's own Space Grotesk Bold, not a stand-in,
  - and an 8x nearest-neighbour blow-up of the 16 px render, because "the
    counter closes" is a claim that should be looked at, not trusted.

Before rows are the generation-1 shapes, unchanged, so the difference is visible
instead of asserted.

    python mark/g2.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import redesign as R

HERE = Path(__file__).resolve().parent
OUT = HERE / "flat-marks-g2.png"
V = 72
FONT = HERE.parent / "app/src/main/res/font/space_grotesk_bold.ttf"

INK, GOLD, DEEP, GROUND, BLOSSOM, PAPER = R.INK, R.GOLD, R.DEEP, R.GROUND, R.BLOSSOM, R.PAPER
poly, circle, capsule, rrect = R.poly, R.circle, R.capsule, R.rrect


# ---------- generation 2 ----------
def g1_counter():
    """The A he liked, unchanged - the flat apex and the closed counter are the
    thing under test."""
    return R.m_counter()


def g2_counter():
    """The counter, opened. Three changes, all of them the small end's fault:
    the apex is truncated so it does not end in one pixel, the legs move apart,
    and the hourglass counter gets wider than the stroke that holds it."""
    body = poly([(28, 6), (44, 6), (68, 68), (50, 68), (44, 50), (28, 50), (22, 68), (4, 68)])
    hole = poly([(36, 20), (48, 46), (24, 46)])
    return [(body, 1)], [hole]


def g1_critter_wide():
    return R.m_critter_wide()


def g2_critter_wide():
    """The critter, put back on a diet. The wide stance cost 17 pixels of body
    at 24 px, so the body grows to fill what the eyes moved out of, and the
    eyes shorten - a taller eye is a thinner bridge between them."""
    return [(circle(36, 36, 31), 1)], [capsule(20, 26, 11, 20), capsule(41, 26, 11, 20)]


ROWS = [
    ("D4  gen 1", "what he pointed at", g1_counter, DEEP),
    ("D4  gen 2", "flat apex, legs apart, counter wider than its stroke", g2_counter, DEEP),
    ("D1b gen 1", "what he pointed at", g1_critter_wide, BLOSSOM),
    ("D1b gen 2", "body grown back, eyes shortened", g2_critter_wide, BLOSSOM),
]


# ---------- extra renderers ----------
def zoom(maker, colour, factor=8):
    """The 16 px mark, blown up with no smoothing - what the claim is about."""
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


# ---------- the sheet ----------
SIZES = [96, 48, 32, 24, 16]
ROW_H = 250
sheet = Image.new("RGB", (1760, 90 + len(ROWS) * ROW_H), "#F7F5F1")
pen = ImageDraw.Draw(sheet)
try:
    big = ImageFont.truetype("arialbd.ttf", 20)
    small = ImageFont.truetype("arial.ttf", 14)
    tiny = ImageFont.truetype("arial.ttf", 12)
except OSError:
    big = small = tiny = ImageFont.load_default()

pen.text((16, 14), "Generation 2 - the two he chose, fixed at the small end", font=big, fill=INK)
pen.text((16, 42), "ladder   |   status bar (alpha only)   |   launcher: dark, light, monochrome   |   16 px blown up 8x   |   lockup in the app's own Space Grotesk Bold",
         font=small, fill="#5A5A5A")

y = 90
for title, note, maker, hue in ROWS:
    pen.text((16, y), title, font=big, fill=INK)
    pen.text((16, y + 24), note, font=small, fill="#5A5A5A")
    counts, band, apex = R.measure(maker)
    pen.text((16, y + 46), f"solid 16:{counts[0]}  24:{counts[1]}  32:{counts[2]}   band {band} px   apex {apex} px",
             font=small, fill="#5A5A5A")
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
    y += ROW_H

sheet.save(OUT)
print(f"wrote {OUT}  ({sheet.width}x{sheet.height})")
for title, _, maker, _ in ROWS:
    counts, band, apex = R.measure(maker)
    print(f"{title:14s} solid 16:{counts[0]:4d} 24:{counts[1]:4d} 32:{counts[2]:4d}  band {band} px  apex {apex} px")
