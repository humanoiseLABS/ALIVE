"""Batch 7, as a picture: the four geometry surfaces, current vs flat.

Every "current" number comes from the inventory of the code at e4ebf66 (stroke
widths, radii, alphas, paddings, fonts), and every "flat" number comes from
flat-system.css and FLAT_REDESIGN_SPEC.md. This is a review artefact, not a
renderer of the app: it draws primitives at the sizes the code uses so a human
can judge the change before a line of Kotlin moves.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "app/src/main/res/font"

INK = (0x14, 0x14, 0x14)
GROUND = (0xFB, 0xFA, 0xF8)
TILE = (0xFF, 0xFF, 0xFF)
INK72 = (20, 20, 20, 184)
INK52 = (20, 20, 20, 133)
INK34 = (20, 20, 20, 87)
GOLD = (0xD9, 0xA5, 0x2E)          # alive_gold_reward_mid
BLUE = (0x7F, 0xA8, 0xCB)          # alive_blue_work_mid
FOREST = (0xDC, 0xE5, 0xDC)        # alive_soft_forest
SHORT_PALE = (0xF3, 0xDA, 0xD3)
REWARD_PALE = (0xFD, 0xEE, 0xC8)
REWARD_MID = (0xE8, 0xC0, 0x60)
PINK_SOFT = (0xE5, 0x8C, 0xA4)

CELL = (300, 230)
PAD = 18


def font(name, size):
    for candidate in FONTS.glob(name):
        return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def ink(w, a=255):
    return (INK[0], INK[1], INK[2], a)


def donut(d, box, flat):
    cx = box[0] + box[2] // 2
    cy = box[1] + box[3] // 2
    outer = min(box[2], box[3]) // 2 - 12
    hole = int(outer * 0.68)
    slices = [(INK, 40), (GOLD, 25), (BLUE, 20), (FOREST, 9), (PINK_SOFT, 6)]
    total = sum(m for _, m in slices)
    start = -90
    for i, (colour, minutes) in enumerate(slices):
        sweep = 360 * minutes / total
        aggregate = i == len(slices) - 1
        gap = 2 if flat else 0
        fill = colour + ((115,) if aggregate and flat else (255,))
        layer = Image.new("RGBA", (outer * 2 + 4, outer * 2 + 4), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.pieslice([2, 2, outer * 2 + 2, outer * 2 + 2], start + gap / 2,
                    start + sweep - gap / 2, fill=fill)
        ld.ellipse([2 + outer - hole, 2 + outer - hole, 2 + outer + hole, 2 + outer + hole],
                   fill=(0, 0, 0, 0))
        d._image.paste(layer, (cx - outer - 2, cy - outer - 2), layer)
        if not flat:
            d.arc([cx - outer, cy - outer, cx + outer, cy + outer], start, start + sweep,
                  fill=INK, width=2)
        start += sweep
    if not flat:
        d.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], outline=INK, width=2)
        d.ellipse([cx - hole, cy - hole, cx + hole, cy + hole], outline=INK, width=3)
    d.text((cx, cy - 10), "2j 35", font=font("space_grotesk_bold*", 26), fill=INK,
           anchor="mm")
    d.text((cx, cy + 14), "TOTAL", font=font("manrope_medium*", 8), fill=INK52, anchor="mm")


def dashed_capsule(d, x0, y0, x1, y1, colour, width=2, dashes=9):
    """A capsule outline drawn as dashes: the family's word for "belum terbaca"."""
    import math

    r = (x1 - x0) / 2
    cy = (y0 + y1) / 2
    points = []
    for i in range(dashes * 8 + 1):
        t = i / (dashes * 8)
        # Perimeter of a capsule: right cap 0..pi, straight down, left cap, up.
        if t < 0.25:
            a = -math.pi / 2 + (t / 0.25) * math.pi
            points.append((x1 - r + r * math.cos(a), cy + r * math.sin(a)))
        elif t < 0.5:
            points.append((x0 + r, y1 - (t - 0.25) / 0.25 * (y1 - r - (y0 + r))))
        elif t < 0.75:
            a = math.pi / 2 + (t - 0.5) / 0.25 * math.pi
            points.append((x0 + r + r * math.cos(a), cy + r * math.sin(a)))
        else:
            points.append((x0 + r, y0 + r + (t - 0.75) / 0.25 * (y1 - r - (y0 + r))))
    for i in range(dashes):
        a = int(i * 2 * len(points) / (dashes * 2))
        b = int((i * 2 + 1) * len(points) / (dashes * 2))
        d.line(points[a:b + 1], fill=colour, width=width, joint="curve")


def donut_empty(d, box, flat):
    cx = box[0] + box[2] // 2
    cy = box[1] + box[3] // 2
    outer = min(box[2], box[3]) // 2 - 12
    hole = int(outer * 0.68)
    if flat:
        # One dashed band, not two dashed circles: at 140dp a double outline
        # reads as a spinner, and one band is the same word the family uses for
        # "belum terbaca".
        mid = (outer + hole) / 2
        band = int(outer - hole)
        d.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], fill=TILE)
        for i in range(14):
            a0 = i * 360 / 14
            d.arc([cx - mid, cy - mid, cx + mid, cy + mid], a0, a0 + 16,
                  fill=INK34, width=band)
    else:
        d.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], fill=TILE, outline=INK, width=2)
        d.ellipse([cx - hole, cy - hole, cx + hole, cy + hole], fill=GROUND, outline=INK, width=3)
    d.text((cx, cy - 8), "—", font=font("space_grotesk_bold*", 24), fill=INK, anchor="mm")
    d.text((cx, cy + 14), "BELUM ADA", font=font("manrope_medium*", 8), fill=INK52, anchor="mm")


def bars(d, box, flat):
    days = [30, 45, 20, None, 50, 35, 10]
    left, top, width, height = box[0] + 26, box[1] + 26, box[2] - 46, box[3] - 74
    peak = 60
    slot = width / len(days)
    bar_w = int(slot * 0.62)
    base = top + height
    for i, value in enumerate(days):
        x = int(left + i * slot + (slot - bar_w) / 2)
        r = bar_w // 2 if flat else 4
        if value is None:
            if flat:
                # Hollow, not zero-height: a 12dp-wide dash is noise at this
                # size, so the word "belum terbaca" is carried by an empty body
                # with an ink-34 rim and the em dash, not by a stroke pattern.
                d.rounded_rectangle([x, base - 22, x + bar_w, base], radius=bar_w // 2,
                                    fill=None, outline=INK34, width=2)
                d.text((x + bar_w // 2, base - 32), "—", font=font("manrope_medium*", 9),
                       fill=INK52, anchor="mm")
            else:
                h = 18
                d.rectangle([x, base - h, x + bar_w, base], fill=GROUND)
                d.rectangle([x, base - h, x + bar_w, base], outline=INK, width=2)
        else:
            h = max(3, int(height * value / peak))
            top_y = base - h
            if flat:
                d.rounded_rectangle([x, top_y, x + bar_w, base], radius=r, fill=GOLD)
            else:
                d.rectangle([x, top_y, x + bar_w, base], fill=GOLD)
                d.rectangle([x, top_y, x + bar_w, base], outline=INK, width=2)
    avg = base - int(height * 32 / peak)
    for x in range(left, left + width, 8):
        d.line([x, avg, min(x + 4, left + width), avg], fill=INK52, width=1)
    d.line([left, base, left + width, base], fill=INK, width=2 if not flat else 1)


def arcade(d, box, flat):
    x, y = box[0] + 20, box[1] + 20
    w, h = box[2] - 40, box[3] - 40
    r_card, r_cell, r_pip, r_tag = (22, 22, 999, 999) if flat else (11, 9, 2, 4)
    d.rounded_rectangle([x, y, x + w, y + 58], radius=r_card, fill=SHORT_PALE)
    d.text((x + 14, y + 12), "HARI INI", font=font("manrope_medium*", 9), fill=INK72)
    d.text((x + 14, y + 24), "2j 35", font=font("space_grotesk_bold*", 24), fill=INK)
    d.text((x + 108, y + 40), "sisa", font=font("manrope_medium*", 9), fill=INK72)
    for i, (label, colour) in enumerate((("TERPAKAI", REWARD_PALE), ("BESOK", REWARD_PALE))):
        cx = x + i * (w // 2 + 4)
        d.rounded_rectangle([cx, y + 66, cx + w // 2 - 4, y + 116], radius=r_cell, fill=colour)
        d.text((cx + 10, y + 74), label, font=font("manrope_medium*", 8), fill=INK72)
        d.text((cx + 10, y + 88), "12" if i == 0 else "20", font=font("space_grotesk_bold*", 16),
               fill=INK)
    for i in range(7):
        on = i < 5
        px = x + 14 + i * 14
        py = y + 128
        if r_pip > 500:
            d.ellipse([px, py, px + 12, py + 6], fill=REWARD_MID if on else TILE)
        else:
            d.rounded_rectangle([px, py, px + 12, py + 6], radius=r_pip,
                                fill=REWARD_MID if on else TILE)
    if r_tag > 500:
        d.rounded_rectangle([x + 14, y + 142, x + 92, y + 160], radius=r_tag, fill=REWARD_PALE)
    else:
        d.rounded_rectangle([x + 14, y + 142, x + 92, y + 160], radius=r_tag, fill=REWARD_PALE)
    d.text((x + 22, y + 148), "5 hari", font=font("manrope_medium*", 8), fill=INK)


def widget(d, box, flat):
    x, y = box[0] + 16, box[1] + 24
    w, h = box[2] - 32, box[3] - 56
    if flat:
        d.rounded_rectangle([x, y, x + w, y + h], radius=22, fill=TILE)
        px = 18
    else:
        d.rounded_rectangle([x + 6, y + 6, x + w + 6, y + h + 6], radius=16, fill=INK)
        d.rounded_rectangle([x, y, x + w, y + h], radius=16, fill=TILE, outline=INK, width=3)
        px = 18
    d.text((x + px, y + 10), "ALIVE", font=font("manrope_medium*", 8), fill=INK72)
    number_font = font("space_grotesk_bold*", 20) if flat else font("manrope_medium*", 15)
    d.text((x + px, y + 22), "2j 35", font=number_font, fill=INK)
    meter_y = y + 54
    meter_w = w - px * 2
    if flat:
        d.rounded_rectangle([x + px, meter_y, x + px + meter_w, meter_y + 8], radius=4,
                            fill=FOREST)
        cut = int(meter_w * 0.62)
        d.rounded_rectangle([x + px, meter_y, x + px + cut, meter_y + 8], radius=4, fill=GOLD)
    else:
        d.rectangle([x + px, meter_y, x + px + meter_w, meter_y + 8], fill=TILE)
        d.rectangle([x + px, meter_y, x + px + meter_w, meter_y + 8], outline=INK, width=2)
        for i in range(1, 3):
            sx = x + px + int(meter_w * i / 3)
            d.rectangle([sx, meter_y, sx + 2, meter_y + 8], fill=INK)
    for i, (label, colour) in enumerate((("HIBURAN", INK), ("PRODUKTIF", GOLD), ("LAINNYA", BLUE))):
        cx = x + px + i * (meter_w // 3)
        d.rectangle([cx, meter_y + 16, cx + 8, meter_y + 24], fill=colour)
        d.text((cx + 12, meter_y + 17), label, font=font("manrope_medium*", 7), fill=INK72)


PAIRS = [
    ("DONUT / ADA DATA", donut, donut),
    ("DONUT / BELUM ADA", donut_empty, donut_empty),
    ("BAR CHART / ADA HARI KOSONG", bars, bars),
    ("KARTU HARI INI", arcade, arcade),
    ("WIDGET", widget, widget),
]

sheet_w = PAD * 3 + CELL[0] * 2
sheet_h = PAD + 46 + len(PAIRS) * (CELL[1] + 52)
sheet = Image.new("RGB", (sheet_w, sheet_h), GROUND)
draw = ImageDraw.Draw(sheet, "RGBA")
draw.text((PAD, 12), "BATCH 7 / GEOMETRY COMPONENTS", font=font("space_grotesk_bold*", 16),
          fill=INK)
draw.text((PAD, 32), "KIRI: apa yang ada di kode sekarang. KANAN: usulan flat. "
                     "Skala 1:1 pada 160 dpi.", font=font("manrope_medium*", 9), fill=INK72)

for index, (label, current, flat) in enumerate(PAIRS):
    top = 46 + PAD + index * (CELL[1] + 52)
    draw.text((PAD, top), label, font=font("manrope_medium*", 9), fill=INK72)
    for column, (name, render) in enumerate((("SEKARANG", current), ("FLAT", flat))):
        left = PAD + column * (CELL[0] + PAD)
        draw.rounded_rectangle([left, top + 16, left + CELL[0], top + 16 + CELL[1]],
                               radius=22, fill=TILE)
        render(draw, (left, top + 16, CELL[0], CELL[1]), flat=(column == 1))
        draw.text((left + 10, top + 16 + CELL[1] - 16), name,
                  font=font("manrope_medium*", 8), fill=INK34)

out = Path(__file__).resolve().parent / "batch7-preview.png"
sheet.save(out)
print(out)
