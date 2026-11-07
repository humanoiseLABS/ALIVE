"""Render the shipped drawable, not the generator's idea of it.

`mark/redesign.py` draws from Python geometry. This reads
`app/src/main/res/drawable/ic_alive_mark.xml` - the bytes Android will compile -
and draws it the same way, with the subpaths after the first treated as holes
because that is what `android:fillType="evenOdd"` means. If the two ever
disagree, this is the one that matters.

    python mark/verify.py
"""
import re
from pathlib import Path

from PIL import Image

import redesign as R
from d4 import lockup, zoom

HERE = Path(__file__).resolve().parent
XML = HERE.parent / "app/src/main/res/drawable/ic_alive_mark.xml"
OUT = HERE / "mark-shipped.png"

INK, PAPER, DEEP, GROUND, BLOSSOM = R.INK, R.PAPER, R.DEEP, R.GROUND, R.BLOSSOM

text = XML.read_text(encoding="utf-8")
data = re.search(r'pathData="([^"]+)"', text).group(1)
fill = re.search(r'fillColor="([^"]+)"', text).group(1)
evenodd = 'fillType="evenOdd"' in text
rings = R.subpaths(data)
print(f"read {XML.name}: {len(rings)} subpaths, fill {fill}, evenOdd={evenodd}")

BODY, HOLES = rings[0], rings[1:]


def maker():
    return [(R.poly(BODY), 1)], [R.poly(r) for r in HOLES]


counts, band, apex = R.measure(maker)
print(f"solid px  16:{counts[0]}  24:{counts[1]}  32:{counts[2]}   thinnest in the middle band {band} px")

sheet = Image.new("RGB", (1180, 420), "#F7F5F1")
from PIL import ImageDraw, ImageFont
pen = ImageDraw.Draw(sheet)
try:
    big = ImageFont.truetype("arialbd.ttf", 20)
    tiny = ImageFont.truetype("arial.ttf", 12)
    small = ImageFont.truetype("arial.ttf", 14)
except OSError:
    big = small = tiny = ImageFont.load_default()

pen.text((16, 14), f"ic_alive_mark.xml as written - {len(rings)} subpaths, evenOdd={evenodd}", font=big, fill=INK)
pen.text((16, 42), f"solid px 16:{counts[0]}  24:{counts[1]}  32:{counts[2]}   thinnest part in the middle band: {band} px",
         font=small, fill="#5A5A5A")

x = 16
for size in (96, 48, 32, 24, 16):
    cell = R.render(maker, size, INK)
    sheet.paste(cell, (x, 96), cell)
    pen.text((x, 96 + size + 4), f"{size} ink", font=tiny, fill="#8A8A8A")
    x += size + 22
x += 10
for size in (96, 48, 32, 24, 16):
    cell = R.render(maker, size, BLOSSOM)
    sheet.paste(cell, (x, 96), cell)
    pen.text((x, 96 + size + 4), f"{size} blossom", font=tiny, fill="#8A8A8A")
    x += size + 22

y = 250
x = 16
for bg, tint, label in (("#1F2126", PAPER, "status bar, dark"), ("#E8EAE6", INK, "status bar, light")):
    sheet.paste(R.strip(bg, tint, R.masked(maker, 24, tint)), (x, y))
    pen.text((x, y + 34), label + " (alpha only)", font=tiny, fill="#8A8A8A")
    x += 170
sheet.paste(R.launcher(maker, 84, INK, dark=False), (x, y - 6))
pen.text((x, y + 34), "launcher: ink on paper", font=tiny, fill="#8A8A8A")
x += 100
sheet.paste(R.launcher(maker, 84, PAPER, dark=True), (x, y - 6))
pen.text((x, y + 34), "launcher: paper on deep", font=tiny, fill="#8A8A8A")
x += 100
blown = zoom(maker, INK)
sheet.paste(blown, (x, y - 6), blown)
pen.text((x, y + 34), "16 px, 8x", font=tiny, fill="#8A8A8A")
x += 140
word = lockup(maker, INK)
sheet.paste(word, (x, y - 6), word)

sheet.save(OUT)
print(f"wrote {OUT}  ({sheet.width}x{sheet.height})")
