# ALIVE — Flat Redesign Specification

**STATUS: FINAL DESIGN SPECIFICATION. The six open decisions are resolved and the four derived decisions that follow from them are recorded (Section 1). Implementation may begin at Batch 0.**

This document changes no source code. It records what was approved, what was measured, and the order the work must go in.

*Amended after `78d8460`: a throwaway spike measured whether this machine can rasterise a VectorDrawable under Robolectric. It can, but only with `@GraphicsMode(NATIVE)`, which moved Sections 11.1, 11.2, 13, 15 and the Batch 0a and 08 rows. The spike file was deleted; the tree is clean at the commit that carries this text.*

**Source documents used (in order of authority):**

1. The six decisions signed by the human reviewer on 2026-11-04 (Section 1) — highest authority for anything they decide.
2. `design-refs/out/approved-01-family-17-members.png` and `approved-02-six-screens.png` — the artefacts the approval was actually given on. Where a note and a render disagree, the render wins.
3. `design-refs/out/flat-system.css` — the approved token sheet.
4. `design-refs/out/family-bots.py` — the authoritative 17-member family spec.
5. This repository at `5d145ed` — the only source for what is true today.
6. `ALIVE_DESIGN_CONSTITUTION.md` — **explicitly deferred** (decision 6). It currently contradicts the approved direction; see Section 14.

---

## 0. Compliance Checklist

| # | Requirement | Status | Section |
|---|---|---|---|
| 1 | Canonical ground settled, one source of truth | RESOLVED | 1.1, 3 |
| 2 | Hourglass retired as pixel, made flat | RESOLVED | 1.2, 8 |
| 3 | Guardian in scope for the flat redraw | RESOLVED | 1.3, 9 |
| 4 | Island drops the face, keeps ring and arc | RESOLVED | 1.4, 10 |
| 5 | Six day-state members get their own silhouettes | RESOLVED | 1.5, 5 |
| 6 | App moves first, brand surfaces later | RESOLVED | 1.6, 14 |
| 6a | Derived — two near-whites, not three | RESOLVED | 1.1 |
| 6b | Derived — pastel fields carry ink text | RESOLVED | 1.1, 7 |
| 6c | Derived — a separate solid silhouette for status-bar art | RESOLVED | 1.2, 8 |
| 6d | Derived — `snap()` and `spritePx()` are two rules, removed in two batches | RESOLVED | 1.7, 12 |
| 7 | `sprite()` snap removed, in its own attributable batch | RESOLVED | 1.7, 12 |
| 8 | The 151 ground-token references repointed, old tokens deleted | RESOLVED | 3, 12 |
| 8 | Batch order follows the 2026-11-02 invariant-first ruling | RESOLVED | 12 |
| 9 | Compile-blocking batch isolated | RESOLVED | 12 (Batch 1) |
| 10 | Invariants ported before `res/drawable` is touched | RESOLVED | 11.1, 12 (Batch 0a) |
| 11 | Flat rules get tests — they have zero today | RESOLVED | 11.2, 12 (Batch 0b) |
| 12 | On-ink text mechanism defined (none exists today) | RESOLVED | 7 |
| 13 | Padding geometry change measured and attributed | RESOLVED | 6 |
| 14 | Ring status hues and pill window route locked | RESOLVED | 10.3, 13 |
| 15 | Enforcement UI never weakened to pass a test | RESOLVED | 13 |
| 15a | Invariants proved by pixels, not by an analytic geometry engine | RESOLVED | 11.1 |
| 15b | Stub-canvas false-green guarded by a non-zero floor in every rasterised test | RESOLVED | 13, 15 |
| 15c | A lock exists that fails if the old ground tokens survive the repoint | RESOLVED | 11.2 |
| 16 | Non-goals stated | RESOLVED | 14 |
| 17 | Verification rules carried forward | RESOLVED | 15 |

---

## 1. The ten decisions

Six were the human reviewer's to sign. Four follow from them — each one derived from an already-approved rule rather than from taste — and are recorded here so no implementer re-decides them.

### 1.1 Ground — the approved CSS wins

Canonical: **ground `#FBFAF8`, ink `#141414`**. The shipped `alive_paper #F5F1E8` / `alive_canvas #F1EBDC` / `alive_ink #1D1D1B` retire as page grounds.

**Derived 1a — there are exactly two near-whites, not three.** The approved CSS carries `--ground:#FBFAF8` (the page) and `--tile:#FFFFFF` (a face on the page). `family-bots.py` separately returns `#FFFDF8` as eye-paper, which is today's `alive_white`. Keeping it would create a third source of truth — the exact failure decision 1 exists to prevent. **`#FFFDF8` retires; eye-paper and on-ink text are `#FFFFFF`.**

**Derived 1b — pastel fields carry ink text, never white.** `AlivePaletteTest` reddens the build otherwise. The one exception is the dark family (Section 7).

### 1.2 Hourglass — retired as pixel, rebuilt flat

The mark becomes flat geometry like everything else. Its surface is large and was mis-recorded as "13"; measured at `5d145ed`:

| Reference | Sites | Where |
|---|---|---|
| `ic_alive_hourglass` direct | **16** | manifest activity icon `:122`; `widget_alive_stats.xml:73`; `AliveComponents.kt:982`; `ic_launcher_foreground.xml:9`; `ic_splash_hourglass.xml:9`; 9 `setSmallIcon` refs across 7 files (`AccessibilityWatch:133`, `DrinkReminderWatch:163,175,180`, `UsageAccessWatch:70`, `QuestDeadlineWatch:105`, `QuestWatch:59`, `PromiseWatch:71`, `InfoPillService:3152`); `InfoPillTile.kt:66` QS tile |
| `ic_splash_hourglass` (alias) | **3** | `values/themes.xml:27`, `values-night/themes.xml:28`, `xml/alive_stats_widget_info.xml:11` previewImage |
| `hourglassMark()` call sites | **2** | `MainActivity.kt:714`, `WelcomeActivity.kt:99` |

`ic_launcher_foreground` serves **foreground and monochrome from one drawable**, so a single redraw changes both.

**Derived 1c — the status-bar and QS-tile copy must be a solid silhouette, not the pastel mark.** Android alpha-masks `setSmallIcon` art: only the alpha channel survives, so thin strokes and soft edges read as a grey blob. Two flat marks are produced: `ic_alive_hourglass` (brand, full colour, used in-app and in the widget) and a solid opaque silhouette for the 9 `setSmallIcon` refs + QS tile. Reminders and the guard must not go invisible — a gap can disqualify the day.

### 1.3 Guardian — in scope

The 4 poses redraw flat. `GuardianSpriteTest` (5 tests, green today) is rewritten, not deleted; its two real invariants survive (Section 11.1).

### 1.4 Island — the face layer goes, the eyes stay

Measured: the pill carries **two separate character layers in two different states**.

| Layer | Visible in | What it is |
|---|---|---|
| `panelCharacter` (ImageView) | expanded panel — `eyesView` is `GONE` (`:2143`) | pixel sprite via `AliveStates.faceSpriteFor` (`:2671`) |
| `PillEyesView` (284 lines) | compact pill — `VISIBLE` (`:2212`) | two white ovals on a blue bloom, **drawn, not a sprite** |

`PillEyesView`'s own doc states it exists *because* "the shipped ALIVE faces are 16-cell pixel art, and the redesign forbids scaling that artwork". It is already outside the pixel language, so it does not conflict with the approved direction and **stays untouched**.

So "drop the face" means the sprite layer only: `panelCharacter`, `placeFace()`, `spritePx()`, the `characterAlpha` channel in `PillMorphChoreography`, and the crossfade machinery that exists to swap sprites. `PillEyesView`, `PillCharacterCycle` and `PillStatusRingTest` are not opened.

### 1.5 Six day-state silhouettes

The 17 members share only 12 silhouettes, so 5 day states collide with category members: circle (Olahraga ↔ Hari normal), teardrop (Belajar ↔ Mendekati batas), hexagon (Koding ↔ Jatah habis), pill (Meditasi ↔ Hari pemulihan), dome (Membaca ↔ Tidur).

The criterion is not "different polygon" but **distinct topology at 24 px** — the legibility floor the approved render states. Two first drafts were rejected against that criterion: `octagon`/`diamond` differ from `hexagon`/`square` only in side count and orientation, which is exactly what stops reading at 24 px.

| Member | Hue | Silhouette | Construction on the 72×72 box |
|---|---|---|---|
| Hari normal | `#28C840` | **leaf** | vesica — two arcs meeting at points top and bottom. Category `bean` is blunt at both ends; leaf is pointed |
| Mendekati batas | `#FEBC2E` | **star** | 4-point sparkle, concave. No category member is concave |
| Jatah habis | `#FF5F57` | **octagon** | regular 8-gon, rounded joins |
| Hari pemulihan | `#9FD98A` | **cross** | equal-armed plus, rounded joins. Concave |
| Belum terbaca | `#C9CDD2` | **hollow** (unchanged) | dashed outline on the ground — MISSING≠ZERO preserved |
| Tidur | `#141414` | **crescent** | circle r=27 at (36,37) minus circle r=24 at (48,30). Concave |

**Residual risk, stated not hidden:** `octagon` (Jatah habis) vs `hexagon` (Koding) remain the closest pair in the family. They are separated by hue (`#FF5F57` vs `#A99AF2`) and never render adjacently — day states appear in the strip and streak, category members in chips. The alternative, a ring-with-slash, is two shapes and breaks the "one flat body" DNA. Accepted as octagon.

### 1.6 Brand surfaces follow

`ALIVE_DESIGN_CONSTITUTION.md`, `web/`, `poster/`, `guardian/` are **out of scope for this spec**. Consequences are recorded in Section 14 rather than silently taken.

### 1.7 `sprite()` snap — removed, in Batch 3

`snap()` rounds sizes up to whole 16 px cells so pixel art stays crisp. Measured at density 1.75 it currently moves 4 of 7 sprite sizes: `96dp→176`, `48dp→80`, `32dp→64`, `20dp→32` (`64dp→112` is already 7 cells).

Two facts decide this, neither of them taste:

1. The approved family render carries a legibility ladder of **24 / 32 / 48 / 72 / 104 px**. 24, 72 and 104 are not multiples of 16. Keeping the snap would quietly reject part of what was approved.
2. The snap's stated justification — "a stretched pixel is not a pixel" — dies with the pixel language.

**It is removed in Batch 3, not Batch 1.** If sizes and colours move in the same batch, a red test cannot be attributed to either. Batch 3 is the only batch that moves geometry.

**`SPRITE_CELL` and `snap()` in `AliveDimens` are one thing; `spritePx()` in `InfoPillService` is a second, private copy of the same rule** (its own `SPRITE_CELL`/`SPRITE_HALF`, `:2010`). They are removed in different batches because they serve different surfaces: `snap()` (feeding `sprite()`/`spriteDp()`, called at `AliveComponents:733` and `:1112` plus the seven dimen-backed sizes) goes in Batch 3; `spritePx()` goes in Batch 6 with the pill's sprite layer.

---

## 2. What this project actually is

### 2.1 It is a Kotlin refactor, not an asset swap

Measured at `5d145ed`:

| Surface | Count |
|---|---|
| `res/drawable/` files | **65** (50 contain `<vector`) |
| `res/font/` files | **8**, 348.044 B total |
| `res/layout/` files | **2** — `activity_main.xml`, `widget_alive_stats.xml` |
| Kotlin files in `app/src/main/java` | **125** |
| `AliveComponents.kt` | **1.292 lines** |
| Test files locking this surface | **16**, 2.023 lines |
| References to the four retiring ground tokens | **151** (132 XML + 19 Kotlin) |
| `ALIVE_DESIGN_CONSTITUTION.md` | 501 lines |

The UI is built programmatically. The 65 drawables are the smaller half of the surface; `AliveComponents.kt` is the design system and every card, button, chip, tile and notice in the app is produced by it.

### 2.2 The four numbers everything flows through

```kotlin
private class Surface(val edge: Int, val shadow: Int, val radius: Int, @ColorInt val face: Int?)
```

`surfaceFor(weight, tone)` (`:375`) picks one, `drawableFor` (`:391`) renders it as `outline()` (`:403`) or `framed()` (`:422`), and `block()` / `rimmedBlock()` / `notice()` / `bentoCard()` consume it.

There is no `setShadowLayer`, no `elevation`, no `borderWidth` anywhere in main source — measured, zero matches. The "shadow" is a `LayerDrawable` whose second layer is inset by `shadow` on the right and bottom (`:441`). It is fake, and that is why it is load-bearing.

---

## 3. Token targets

From `flat-system.css`, the approved artefact.

**Ground and faces.** `ground #FBFAF8`, `tile #FFFFFF`, `ink #141414`, ink alphas 72 / 52 / 34.

**The real cost of decision 1, measured.** The retiring tokens are not dead. Measured at `f6a4d69` the four retiring grounds carry **151 references** — 132 in XML and 19 in Kotlin. An earlier count of 184 was wrong in its own favour: it matched `alive_ink` as a prefix and swept up the five alpha-variant tokens, which are **not** retired. The corrected picture is:

| Token | Fate | References |
|---|---|---|
| `alive_ink`, `alive_paper`, `alive_canvas`, `alive_white` | deleted | **151** to repoint |
| `alive_ink_secondary/muted/disabled/hairline/ripple` | name kept, **value** re-derived from `#141414` | 33, untouched |

So "change the ground" is a 151-site mechanical repoint plus one value block in `colors.xml`. It is still the largest single edit in this project, and it stays one step inside Batch 1, never smeared across batches.

**Strategy — introduce, repoint, then delete.** New `flat_*` tokens become the single source of truth; every app-side reference moves to them; the old tokens are then **deleted once nothing references them**. Deleting is what makes decision 1 stick — a leftover alias is exactly how two sources of truth come back. Mutating the old tokens' values in place was rejected: it bundles a 151-site colour shift with everything else, so a red test could not be attributed.

**The pill's 8 `alive_paper` references are not a ground.** They are text on the black shell — an on-ink foreground, which Derived 1a makes `#FFFFFF`. They migrate to `flat_paper` in Batch 6, the only batch that opens the pill. This does not create a second source of truth: it is one token answering a different question.

**Field colours — the structure.** peach `#FFD6C2`, lilac `#D9C6FF`, mint `#B9F0A6`, sky `#BFD9FF`, butter `#FFE9A8`, rose `#FFC7DE`, lime `#C8F06E`, coral `#FFB3A7`, plus the pass-2 set grape `#C7B2F0`, aqua `#A8E6D8`, blossom `#FFB8D6`, leaf `#9FD98A`, sun `#FFD36B`, clay `#F0A888`.

**Dark grounds.** `deep #2A2F7A`, `deep2 #1A1F54`, field `navy #3A4090`.

**Page grounds, varied per screen.** `g-lav #EFE7FB`, `g-mint #E6F6E4`, `g-blush #FBE9F0`, `g-butter #FBF3DC`, `g-sky #E6EEF9`, `g-peach #FDEDE4`.

**Radii.** card `28dp`, tile/field `22dp`, everything round is a pill. `radius_step 4dp` retires with the pixel grid.

**Zero border, zero shadow.** `border_bold`, `border_button`, `border_hairline`, `shadow_offset`, `shadow_minor` retire — but see Section 6, they cannot simply be zeroed.

**Type.** Space Grotesk 700 for display and numbers, Manrope for body. Both are already bundled and already used in `type.xml` (263 lines, 22 TextAppearance styles). No new font files, therefore no downloadable-font dependency, no `INTERNET` permission, and **no permission-shape change** — which keeps Play Protect's permission-shape block untriggered.

Silkscreen and `press_start_2p` retire: 118.204 + 27.960 + 29.532 = **175.696 B of 348.044 B (50,5 %)** — but only if the TTFs are actually deleted. Measured reach in main source:

| Font | Code references | XML references |
|---|---|---|
| `silkscreen_bold` | **3** — `BrutalistBarChart:94`, `BrutalistDonut:74`, `widget/AliveWidgetArt:121` | `type.xml:38,58,78` |
| `silkscreen_regular` | 0 | `type.xml:49` |
| `press_start_2p` | 0 | `type.xml:196` |

So Batch 1 touches three code files for type, and the rest is `type.xml`. Of the 22 TextAppearance styles, **exactly five** sit on the pixel voice and must be re-authored: `Display` (`:38`, silkscreen_bold), `Annotation` (`:49`, silkscreen_regular), `Timer` (`:58`), `ScoreLabel` (`:78`) and `Label` (`:196`, press_start_2p).

`Display` is the loudest of the five — it is the wordmark and the screen name, so the app's headline voice changes in this batch, not in Batch 2.

---

## 4. Category hues are unchanged

The 11 category hues in `family-bots.py` are already ALIVE's `colors.xml` category colours. Decision 1 changes the *ground*, not the category family. `cat_*` values stay, which keeps `PixelMarksTest` semantics intact even as the marks themselves redraw.

---

## 5. The family

17 members, one DNA — solid body, no outline, no shadow, two tall capsule eyes, no mouth, no limbs — differentiated on silhouette, hue, eye treatment (upright / tilt 14° / offset / wide / one / closed) and gloss slash.

`family-bots.py` is the spec. It draws SVG on a 72×72 viewBox. **There is no sheet generator in `design-refs/out/`** — the directory holds exactly five files and `HANDOFF.md` never mentions one. It must be written before Batch 3, emitting VectorDrawable XML rather than HTML-embedded SVG, because nothing in `res/drawable/` is consumed as SVG.

The eye-flip rule (`_dark()`, luminance < 0.32 → paper eyes) is approved behaviour. It currently lives only as generator logic. **Its lock belongs on the produced drawable, not on the Python** (Section 11.2).

---

## 6. The padding consequence — the part that is not a colour change

`block()` derives its padding from the dress:

```kotlin
setPadding(surface.edge + inset, surface.edge + inset,
           surface.edge + surface.shadow + inset, surface.edge + surface.shadow + inset)
```

Zeroing `edge` and `shadow` therefore **shrinks the padding of every card, tile and chip in the app** by 2–3 dp on two sides and by 2–3 dp + shadow on the other two. `rimmedBlock()` (`:463`) has the same shape.

`BackButtonTest` computes the button face as `dimens.touchMin - dimens.shadow` (`:49`) and asserts `arrow % 16 == 0` (`:54`). So `shadow` is geometry, and the `% 16` assertion belongs to the pixel grid, not to the screen.

**Rule for Batch 2:** the tokens split into *distance* (still a real inset, still measured) and *elevation* (goes to zero). Deleting one number that serves both is how a centred title silently shifts and reads as "no update". The 48 dp touch floor never moves.

---

## 7. On-ink text — a mechanism that does not exist yet

The approved renders include two dark screens: KUNCI and GALAKSI on `deep #2A2F7A` with white text. Measured at `5d145ed`, main source contains **zero** matches for `onInk`, `onDark`, `whiteOn`, `isDark`, `luminance` or `contrastRatio`. The contrast maths lives only inside `AlivePaletteTest`.

`surfaceFor` currently states the opposite policy in its own doc: *"No face is dark: white-on-forest would force every child TextView to be recoloured, and the calm reading order would go with it."* That objection is now a work item, not a reason to refuse.

**Design.** One rule, reused from the family rather than invented:

```kotlin
fun textOn(@ColorInt field: Int): Int = if (luminance(field) < 0.32) white else ink
```

The threshold is `family-bots.py`'s `_dark()` value, so characters and surfaces answer to a single number instead of two. `Surface` gains the face it already carries and derives its text colour from it. `AlivePaletteTest` measures **both directions** at 4.5:1 — ink-on-pastel and paper-on-dark — and every new field token registers as a text×ground pair, or it passes by omission.

The `*_mid` exclusion is not widened to make a new token pass.

---

## 8. The hourglass, flat

Two marks, per Derived 1c:

- **`ic_alive_hourglass`** — flat brand mark, full colour. Feeds `hourglassMark()` (MainActivity, Welcome), the widget layout, the manifest activity icon, and through aliases the launcher foreground + monochrome and the splash (day + night + widget preview).
- **A solid silhouette** for the 9 `setSmallIcon` refs and the QS tile — opaque, chunky, reads as a shape once Android throws away its colour.

The adaptive-icon foreground and the 96 dp splash stay square-cropped — `AliveMarkTest:44` already locks that, and it survives the redraw unchanged.

---

## 9. Guardian, flat

4 poses (`idle / alert / recovery / restored`) redraw as flat bodies on the same DNA. Its two real invariants survive Section 11.1. The amber-eyes-on-ink and crimson-rim numbers in `GuardianSpriteTest` are pixel-era measurements and are re-authored against the new art, not copied.

`rimmedBlock()` exists for Guardian's ink plate specifically (`:453`). Under flat, an ink plate on an ink ground needs the dark-ground text rule from Section 7, not a rim.

---

## 10. The island

### 10.1 Removed

`panelCharacter`, `placeFace()`, `spritePx()`, the `characterAlpha` channel in `PillMorphChoreography` and its ramp, `settleCharacterCrossfade()`, and the crossfade-swap machinery that exists only to change sprites.

`PillMorphChoreographyTest` asserts `characterAlpha` ranges at 4 sites — those assertions retire with the channel, and the rest of that choreography (shell, ring, bar, text) stays.

### 10.2 Kept untouched

`PillEyesView`, `PillCharacterCycle`, `PillStatusRingView`, `PillShellDrawable`, and every line of `PillStatusRingTest`. The compact pill keeps its drawn eyes.

### 10.3 Locked — an app restyle must not re-point these

- the pill's drawable ids
- its `TYPE_APPLICATION_OVERLAY` window route
- its notification small icon (which is why 1.2 produces a separate silhouette)
- the ring status hues: `#28C840` normal, `#FEBC2E` approaching, `#FF5F57` spent, `#9FD98A` recovery

---

## 11. Tests

### 11.1 Three invariants, ported to general form before any asset moves

The pixel-specific locks become shape-agnostic ones:

1. **one connected body** per character
2. **every silhouette and pose visually distinct**
3. **legible at 24 px**

`AliveCharacterSilhouetteTest` currently parses `pathData` with a **rectangles-only** engine (`Regex("M(-?\\d+),(-?\\d+)((?:[hv]-?\\d+)+)z")`) and asserts every `alive_pink_soft #E58CA4` cell touches the body. It understands only horizontal and vertical runs, so it cannot see a curve — and 11 of the 12 approved silhouettes are curves. Non-pink curved bodies make it yield zero cells, so `assertTrue("$name draws no pink at all")` goes hard red: the correct failure, the wrong test.

**Mechanism — authored geometry for connectivity, pixels for the rest.** Measured on this machine at `78d8460`: under `@Config(sdk = [36])` alone, Robolectric's canvas is a **stub** — `drawRect` on a 32×32 bitmap reported 63 opaque pixels of 1024, and a VectorDrawable reported **0**. Adding `@GraphicsMode(GraphicsMode.Mode.NATIVE)` makes it real: `drawRect` 1024/1024, `GradientDrawable` 1024, `ic_alive_character` at 64×64 → **1216 opaque pixels (29,7 %)**.

Connectivity, however, **cannot be measured on a composite**, and four red runs proved why:

1. At 24 px the pixel art is a 0,5× downscale of its 48 grid and scatters into sub-cell fragments — connectivity is a property of the artwork, not of a downscale, so it is measured at the authored size.
2. The flood fill seeded on an anti-aliased alpha-1 speck and reported a whole body adrift, so only pixels at ≥ 50 % coverage count as geometry.
3. `ic_alive_face_happy` carries four forest-green sparkles the design deliberately floats above the head; "every pixel connected" would outlaw them.
4. `ic_alive_pose_book` renders its body in three pieces because the held book occludes it — the occluded pink is gone from the raster and present in the XML.

So the body is rebuilt as a real `Path` from its own `pathData` (M/L/H/V/C/Z, throwing on anything else so a future curve command fails loudly instead of being skipped) and drawn **alone**, then flood-filled 8-way — 8 because pixel art joins its arms to the torso on the diagonal. The body is the colour that owns the raster, so nothing hardcodes `alive_pink_soft`.

| Invariant | How it is proved | Status today |
|---|---|---|
| one connected body | authored body paths drawn alone, flood-filled 8-way | **green on the legacy art** |
| legible at 24 px | rasterised: opaque floor + bbox span at the strip's size | **green on the legacy art** |
| eyes keep 4.5:1 on their body | rasterised histogram: body = dominant colour, eyes = darkest solid colour | **green on the legacy art** |
| every silhouette distinct at 24 px | rasterised pairwise IoU over the 17 members | **red until Batch 3** — the property belongs to the new family; the legacy set is 17 expressions of one body and is meant to share a silhouette |
| no part floats off a member | every solid component touches the body component | **red until Batch 3** — the legacy art floats sparkles on purpose; the flat DNA forbids it |

### 11.2 Flat locks that do not exist today

The approved flat rules currently have **zero** tests. These are written as red tests in Batch 0b, before the tokens that turn them green:

| Lock | Proof type | Status |
|---|---|---|
| no border and no shadow anywhere in the produced drawables | resource-set scan | red until Batch 2 |
| the radius scale 28 / 22 / pill, and no sixth radius | dimens + literal scan | red until Batch 1 |
| Space Grotesk 700 for display and numbers | `type.xml` on the five re-authored styles | red until Batch 1 |
| ink eyes on light bodies, paper eyes on dark bodies | rasterised (11.1) | **delivered in 0a, green on legacy art** |
| 24 px legibility | rasterised (11.1) | **delivered in 0a, green on legacy art** |
| one connected body | authored paths (11.1) | **delivered in 0a, green on legacy art** |
| every member silhouette distinct at 24 px | rasterised pairwise IoU | red until Batch 3 |
| no part floats off a member | component adjacency to the body | red until Batch 3 |
| ink-on-pastel **and** paper-on-dark both clear 4.5:1 | arithmetic on real resources | red until Batch 1 |
| **the ground is one token** — `alive_paper`, `alive_canvas`, `alive_ink`, `alive_white` no longer exist in the resource set | resource-set scan | red until Batch 1 |

The last one is the cheap proof that decision 1 actually stuck. Without it the migration can finish half-done — new `flat_*` tokens added, the 151 old references left pointing at the old creams — and every visible test would still pass, because both palettes would be legible. That is exactly the two-sources-of-truth failure decision 1 was written to prevent, arriving as a green suite.

### 11.3 Never allowed to regress

MISSING≠ZERO · 4.5:1 measured from real resources · 48 dp touch floor · the widget `RemoteViews` class list · TUTUP-is-not-zero · `includeFontPadding` · per-style weight assignment · the two `LayerDrawable.getDrawable(1)` casts (Section 13).

---

## 12. Batch order

Every batch ends with the full suite green and a report. Nothing is committed without the human reviewer's word.

| # | Batch | Why here | Exit |
|---|---|---|---|
| **0a** | Port the invariants to rasterised general form (11.1) and retire the rect-only parser; add the `@GraphicsMode(NATIVE)` + non-zero-floor pattern | Must happen while the suite is green, before `res/drawable` or `res/font` is touched | green, and the locks now guard the *rule* rather than the pixel grid |
| **0b** | Write the flat locks (11.2) as **red** tests | TDD — they define the target | red, expected, reported |
| **1** | Tokens + fonts: new `flat_*` ground/field/radius tokens, the **151-site repoint**, deletion of the four old ground tokens, `type.xml` re-authored, 3 TTFs deleted, 3 code files moved off `silkscreen_bold` | **Compile-blocking** — a deleted TTF makes `R.font.*` unresolvable, and a deleted colour makes `R.color.*` unresolvable, which fails the whole test source set so the suite cannot even report what broke. Must go first and alone | 0b turns green |
| **2** | `AliveComponents` surface model: `framed`/`outline` → flat colour field; edge/shadow split into distance vs elevation; `textOn()` + dark grounds | The design system. Padding moves here, attributable to one change | green |
| **3** | Rebuild the sheet generator → 17 members + 6 new silhouettes → VectorDrawable XML; remove `sprite()` snap | Only batch that moves geometry | green |
| **4** | Hourglass flat + status-bar silhouette, 16 + 3 + 2 refs | Derived 1c | green |
| **5** | Guardian flat, 4 poses, `GuardianSpriteTest` re-authored | decision 3 | green |
| **6** | Island: remove the sprite layer only | decision 4, scoped to `panelCharacter` | green, ring tests untouched |
| **7** | Geometry components: `BrutalistDonut`, `BrutalistBarChart`, `ArcadeTodayCard`, `widget/AliveWidgetArt`, `activity_main.xml`, `widget_alive_stats.xml` | Last, because it consumes every token above | green |
| **8** | Full suite + real-device frames for clipping, `RemoteViews`, elevation and motion only — static shape is already proved in 0a | The residual that no JVM test can reach | his verdict |

---

## 13. Known hazards

- **Two files cast the card background as a `LayerDrawable` layer 1.** `CriticalWarningCardTest.faceOf()` (~`:59`) returns a nullable `GradientDrawable`; `ArcadeTodayCardTest` (~`:96`) does `background.getDrawable(1) as GradientDrawable` — a hard cast. When a flat colour field replaces the layered background, the Arcade card throws **and the critical warning card — enforcement UI — loses the test that guards "red stays still when the animation dies / back does not dismiss / releases after the countdown"**. `faceOf` must tolerate both shapes. The three rules it also locks are never touched.
- **A stub canvas produces false greens.** Without `@GraphicsMode(NATIVE)`, Robolectric draws nothing and **throws nothing** — a VectorDrawable rasterised to 0 opaque pixels is a silent, successful no-op. Any assertion of the form "these two shapes are not identical" passes vacuously when both are empty, and "the silhouette is distinct at 24 px" would go green on a family that never rendered. Every rasterised test in 11.1 must therefore open by asserting a non-zero floor on its own bitmap before it measures anything.
- **The Home-screen widget renders in the launcher process** (`widget_alive_stats.xml`, `AliveWidgetArt`, `AliveStatsWidgetProvider`, `alive_stats_widget_info.xml`). No UI test in this suite can prove it. Real-device frames only, and only with permission.
- **An install can unbind the guard**, and a same-day gap disqualifies the day. Batch 8 stops at the built APK until he says install.
- **The host clock can sit 7 h off the phone.** Compare `date -u +%s`, never a timezone conversion.

---

## 14. Non-goals and deferred

- `ALIVE_DESIGN_CONSTITUTION.md`, `web/`, `poster/`, `guardian/` — deferred by decision 6. **Consequence recorded:** the app will be out of compliance with its own governing document, and §10 of that document forbids in words what was approved — *"avoid polished vector mascots, perfect geometric illustrations, glossy UI"*. There is no reconciliation available; §10 must be deleted or rewritten when the brand pass runs. The public site, including store screenshots, will not match the app until then.
- **Doc drift found while writing this spec, recorded not fixed:** constitution §15 states *"Three faces, and they are not interchangeable: `sans-serif-black` carries structure, `vt323` carries numbers, `press_start_2p` carries labels."* `vt323` has **zero** references anywhere in main source — the governing document names a font the app does not ship. It belongs to the deferred brand pass.
- No new permissions, no `INTERNET`, no downloadable fonts.
- No behaviour, enforcement, data or policy change. `LockActivity`'s `singleTask` route, the retry window, the tick and the report cadence are untouched.
- The island's shell geometry, motion and ring semantics are untouched.
- No layout XML is introduced; the app stays programmatic.

---

## 15. Verification rules carried forward

1. Baseline is measured, not assumed. At `5d145ed` the seven design classes run **27 tests, 0 failures, 0 errors in 44 s** — so any red later belongs to this project.
2. **What Robolectric can and cannot prove, now that rasterisation works.** Under `@GraphicsMode(NATIVE)` it gives real pixels, so *static shape* claims — connectivity, silhouette distinctness, 24 px legibility, eye contrast — have a runnable proof and belong in the suite. It still does **not** prove Android clipping, `RemoteViews` in the launcher process, elevation, or motion; those need recorded real frames, with permission. The stub-canvas hazard (13) means an un-annotated render test is worse than no test at all.
3. A self-authored patch gets at least two named-attack review rounds before it is called done.
4. Nothing is release-ready before a full green suite **and** his word on installing.
5. Enforcement is never weakened to make a test pass.
