from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RASTERS = ROOT / "app/build/reports/guardian-native"
COLOURS = {
    element.attrib["name"]: element.text
    for element in ET.parse(ROOT / "app/src/main/res/values/colors.xml").getroot().findall("color")
}
POSES = ("idle", "alert", "recovery", "restored")
GROUND = COLOURS["flat_ground"]
INK = COLOURS["flat_ink"]
SHEET = Image.new("RGB", (1000, 710), GROUND)
DRAW = ImageDraw.Draw(SHEET)
DRAW.text((24, 18), "GUARDIAN / FLAT / ANDROID NATIVE RASTER", fill=INK)
DRAW.text((24, 40), "Actual compiled vectors, rendered by Robolectric. Not a phone screenshot.", fill=INK)

for index, pose in enumerate(POSES):
    left = index * 244 + 16
    DRAW.text((left + 12, 78), pose.upper(), fill=INK)
    for y, background, size, zoom, label in (
        (108, GROUND, 96, 1, "96 px / ground"),
        (246, INK, 96, 1, "96 px / ink plate"),
        (384, GROUND, 24, 1, "24 px / actual size"),
        (468, GROUND, 24, 4, "24 px / 4x inspection"),
    ):
        image = Image.open(RASTERS / f"{pose}-{size}.png").convert("RGBA")
        if image.size != (size, size) or not image.getbbox():
            raise ValueError(f"Missing native raster: {pose}-{size}")
        image = image.resize((size * zoom, size * zoom), Image.Resampling.NEAREST)
        field = Image.new("RGBA", (224, max(64, size * zoom + 22)), background)
        field.alpha_composite(image, ((224 - image.width) // 2, 8))
        SHEET.paste(field.convert("RGB"), (left, y))
        DRAW.text((left + 12, y + field.height + 4), label, fill=INK)

DRAW.text((28, 640), "One crimson body. Two capsule eyes. No rim, shadow or limbs.", fill=INK)
DRAW.text((28, 666), "Phone clipping, motion and SystemUI remain unverified until Batch 8.", fill=INK)
OUTPUT = ROOT / "family/guardian.png"
SHEET.save(OUTPUT)
print(OUTPUT)
