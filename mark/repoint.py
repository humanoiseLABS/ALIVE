"""Batch 4, part 1: repoint every reference from the hourglass to the mark.

Mechanical, and asserted per file rather than trusted: each replacement names the
count it expects, so a reference that moved somewhere else - or a second one
nobody counted - fails the script instead of shipping a half-migrated app.

The hourglass drawable itself is NOT deleted here. `web/scripts/sprites.mjs`
still rasterises it into the site's sprite sheet, and decision 6 defers `web/`;
deleting the file would turn a green site workflow red for a name. That is left
as a visible decision, not taken silently.

    python mark/repoint.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# token -> the number of replacements the whole repo must yield
SWAPS = {
    "ic_alive_hourglass": 15,   # 10 system surfaces + manifest + widget + foreground + splash + AliveComponents + test
    "ic_splash_hourglass": 4,   # the file's own name, themes day, themes night, widget preview, test
    "hourglass_size": 2,        # dimens.xml and AliveDimens
    "hourglassSize": 3,         # the definition, MainActivity and WelcomeActivity
    "hourglassMark": 3,         # the definition and two call sites
}
REPLACEMENT = {
    "ic_alive_hourglass": "ic_alive_mark",
    "ic_splash_hourglass": "ic_splash_mark",
    "hourglass_size": "brand_mark_size",
    "hourglassSize": "brandMarkSize",
    "hourglassMark": "brandMark",
}

FILES = [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/projectego/app/MainActivity.kt",
    "app/src/main/java/com/projectego/app/service/AccessibilityWatch.kt",
    "app/src/main/java/com/projectego/app/service/DrinkReminderWatch.kt",
    "app/src/main/java/com/projectego/app/service/InfoPillService.kt",
    "app/src/main/java/com/projectego/app/service/InfoPillTile.kt",
    "app/src/main/java/com/projectego/app/service/PromiseWatch.kt",
    "app/src/main/java/com/projectego/app/service/QuestDeadlineWatch.kt",
    "app/src/main/java/com/projectego/app/service/QuestWatch.kt",
    "app/src/main/java/com/projectego/app/service/UsageAccessWatch.kt",
    "app/src/main/java/com/projectego/app/ui/AliveComponents.kt",
    "app/src/main/java/com/projectego/app/ui/AliveDimens.kt",
    "app/src/main/java/com/projectego/app/ui/WelcomeActivity.kt",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
    "app/src/main/res/drawable/ic_splash_hourglass.xml",
    "app/src/main/res/layout/widget_alive_stats.xml",
    "app/src/main/res/values-night/themes.xml",
    "app/src/main/res/values/dimens.xml",
    "app/src/main/res/values/themes.xml",
    "app/src/main/res/xml/alive_stats_widget_info.xml",
    "app/src/test/java/com/projectego/app/ui/AliveMarkTest.kt",
]

tallies = {token: 0 for token in SWAPS}
for relative in FILES:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    original = text
    for token, replacement in REPLACEMENT.items():
        count = text.count(token)
        if count:
            text = text.replace(token, replacement)
            tallies[token] += count
    if text == original:
        raise SystemExit(f"{relative} matched nothing - the reference list is wrong, not the code")
    path.write_text(text, encoding="utf-8")

# The splash alias is named after what it draws, so the file moves with it.
old = ROOT / "app/src/main/res/drawable/ic_splash_hourglass.xml"
new = ROOT / "app/src/main/res/drawable/ic_splash_mark.xml"
old.rename(new)
print(f"renamed {old.name} -> {new.name}")

bad = {token: (tallies[token], SWAPS[token]) for token in SWAPS if tallies[token] != SWAPS[token]}
for token, count in tallies.items():
    print(f"  {token:20s} {count:3d} replaced -> {REPLACEMENT[token]}")
if bad:
    raise SystemExit(f"unexpected counts, fix the expectation or find the missing reference: {bad}")
print("every reference accounted for; ic_alive_hourglass.xml left in place for web/scripts/sprites.mjs")
