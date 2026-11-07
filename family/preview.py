"""Render the generated family so a human can look at it.

Reads the XML the generator wrote - not the generator's own geometry - so what
appears here is what Android will be handed. Advisory only: the lock is
AliveCharacterSilhouetteTest.
"""
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
DRAWABLE = ROOT / "app/src/main/res/drawable"
OUT = Path(__file__).resolve().parent / "family.png"

COLOURS = {
    "@color/flat_ink": (0x14, 0x14, 0x14, 255),
    "@color/flat_paper": (0xFF, 0xFF, 0xFF, 255),
    "@color/flat_ground": (0xFB, 0xFA, 0xF8, 255),
    "@color/flat_field_leaf": (0x9F, 0xD9, 0x8A, 255),
    "@color/alive_traffic_green": (0x28, 0xC8, 0x40, 255),
    "@color/alive_traffic_yellow": (0xFE, 0xBC, 0x2E, 255),
    "@color/alive_traffic_red": (0xFF, 0x5F, 0x57, 255),
    "@color/alive_cat_study": (0xF5, 0xCE, 0x7E, 255),
    "@color/alive_cat_reading": (0x9C, 0xC9, 0xE8, 255),
    "@color/alive_cat_exercise": (0x63, 0xC9, 0x8A, 255),
    "@color/alive_cat_walking": (0xB7, 0xE0, 0x6B, 255),
    "@color/alive_cat_cleaning": (0xC9, 0xCD, 0xD2, 255),
    "@color/alive_cat_creative": (0xF5, 0x8F, 0xA6, 255),
    "@color/alive_cat_coding": (0xA9, 0x9A, 0xF2, 255),
    "@color/alive_cat_writing": (0xF4, 0xB1, 0x68, 255),
    "@color/alive_cat_mindfulness": (0xC9, 0xA3, 0xDE, 255),
    "@color/alive_cat_social": (0x77, 0xC9, 0xB6, 255),
    "@color/alive_cat_work": (0x7F, 0xA8, 0xCB, 255),
}

TOKEN = re.compile(r"[MLCZ]|-?\d*\.?\d+")


def subpaths(data):
    tokens = TOKEN.findall(data)
    i = 0
    rings = []
    ring = []
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
        elif cmd == "C":
            x1, y1, x2, y2, nx, ny = (float(t) for t in tokens[i:i + 6])
            i += 6
            for k in range(1, 25):
                t = k / 24
                u = 1 - t
                ring.append((u**3 * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * nx,
                             u**3 * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * ny))
            x, y = nx, ny
        elif cmd == "Z":
            ring.append((sx, sy))
            x, y = sx, sy
    if len(ring) > 2:
        rings.append(ring)
    return rings


def attr(el, name):
    return el.get(name) or el.get("{http://schemas.android.com/apk/res/android}" + name)


def render(name, size, ground=(0xFB, 0xFA, 0xF8, 255)):
    root = ET.parse(DRAWABLE / f"{name}.xml").getroot()
    vp = float(attr(root, "viewportWidth"))
    scale = size / vp
    img = Image.new("RGBA", (size, size), ground)
    NS = "{http://schemas.android.com/apk/res/android}"
    paths = root.findall("path") or root.findall(NS + "path")
    for path in paths:
        colour = COLOURS[attr(path, "fillColor")]
        alpha = float(attr(path, "fillAlpha") or 1.0)
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        for ring in subpaths(attr(path, "pathData")):
            draw.polygon([(x * scale, y * scale) for x, y in ring],
                         fill=colour[:3] + (int(colour[3] * alpha),))
        img = Image.alpha_composite(img, layer)
    return img


MEMBERS = [f"alive_member_{k}" for k in
           "study reading exercise walking cleaning creative coding writing mindfulness social work".split()]
STATES = [f"alive_state_{k}" for k in "normal near spent recovery unknown rest".split()]

PAD, LABEL = 14, 26


def sheet_at(size, path):
    cols = 6
    rows = 3
    out = Image.new("RGBA", (cols * (size + PAD) + PAD, rows * (size + PAD + LABEL) + PAD),
                    (250, 248, 245, 255))
    pen = ImageDraw.Draw(out)
    for index, name in enumerate(MEMBERS + STATES):
        r, c = divmod(index, cols)
        x = PAD + c * (size + PAD)
        y = PAD + r * (size + PAD + LABEL)
        out.paste(render(name, size), (x, y))
        pen.text((x, y + size + 4), name.replace("alive_", ""), fill=(60, 60, 60, 255))
    out.save(path)
    return out.size


sheet_at(96, OUT)
sheet_at(24, Path(__file__).resolve().parent / "family-24.png")
print(f"wrote family.png and family-24.png (24 px is the legibility floor the lock measures at)")
