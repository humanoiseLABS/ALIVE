"""Batch 4, part 2: the prose that still names the hourglass.

The repoint moved every reference. These seven spots are comments and a dead
constant that describe the old mark as if it still shipped - which is how a
codebase ends up with documentation that argues against its own files. Each
replacement asserts the exact text it expected to find, so a comment that moved
fails the run instead of being skipped.

    python mark/repoint2.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

EDITS = [
    (
        "app/src/main/java/com/projectego/app/ui/AliveComponents.kt",
        """        /** One quarter turn of the hourglass. Four of them is one full turn. */
        const val HOURGLASS_STEP_MILLIS = 700L
""",
        "",
    ),
    (
        "app/src/main/java/com/projectego/app/MainActivity.kt",
        """                        // The hourglass turns beside the name: it is the app's
                        // mark on the launcher, so the header carries the same one.""",
        """                        // The mark beside the name: the same one the launcher
                        // carries, and it no longer turns - it is a face now.""",
    ),
    (
        "app/src/main/java/com/projectego/app/ui/AliveDimens.kt",
        """    /** The hourglass beside the wordmark. Batch 4 redraws it flat; until then
     *  it is still pixel art and no longer snapped, so it can land on a half
     *  cell - the one softness this batch buys on purpose. */""",
        """    /** The mark beside the wordmark. It is flat curves now, so the size is
     *  simply the size: nothing about it needs to land on a whole cell. */""",
    ),
    (
        "app/src/main/res/drawable/ic_splash_mark.xml",
        """<!-- ALIVE splash icon: the hourglass mark at the size the loading screen gives
     an icon.""",
        """<!-- ALIVE splash icon: the mark at the size the loading screen gives an icon.""",
    ),
    (
        "app/src/main/res/drawable/ic_launcher_foreground.xml",
        """<!-- ALIVE launcher foreground: the hourglass mark, sized into the adaptive safe
     zone rather than filling the 108dp canvas - the system crops the outer ring,
     and a mark that touches the edge loses its frame on most launchers. -->""",
        """<!-- ALIVE launcher foreground: the mark, sized into the adaptive safe zone
     rather than filling the 108dp canvas - the system crops the outer ring, and a
     mark that touches the edge loses its frame on most launchers. This drawable
     serves the foreground AND the monochrome layer from one file, which is why
     the mark's eyes are cut out of it rather than drawn in a second colour:
     monochrome keeps only the alpha. -->""",
    ),
    (
        "app/src/main/res/values/themes.xml",
        """             hourglass mark on it. No plate behind it - the mark reads on its""",
        """             mark on it. No plate behind it - the mark reads on its""",
    ),
    (
        "app/src/main/res/values-night/themes.xml",
        """             hourglass mark on it. No plate behind it - the mark reads on its""",
        """             mark on it. No plate behind it - the mark reads on its""",
    ),
]

for relative, old, new in EDITS:
    path = ROOT / relative
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit(f"{relative}: expected exactly one copy of\n{old[:90]}...\nfound {text.count(old)}")
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"{relative:58s} {'constant removed' if not new else 'rewritten'}")

remaining = []
for path in sorted((ROOT / "app/src").rglob("*")):
    if path.is_file() and path.suffix in {".kt", ".xml"}:
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if "hourglass" in line.lower():
                remaining.append(f"{path.relative_to(ROOT)}:{number}: {line.strip()[:80]}")
print("\nremaining mentions of the hourglass in app/src:")
print("\n".join(remaining) if remaining else "  none")
