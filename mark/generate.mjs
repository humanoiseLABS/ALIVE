/**
 * ALIVE's mark - the critter, flat, as VectorDrawable.
 *
 * He approved one shape (D1b generation 1 on the sheet in mark/) and rejected
 * every variant, including the ones I argued were better at 16 px. The shape is
 * therefore frozen: a body of r=28 on a 72 box and two capsules of 11 x 23 at
 * x 21 and x 40. What this file adds is only the translation into Android.
 *
 * The one real decision the translation makes: the eyes are HOLES, not a second
 * colour. A notification small icon and the adaptive icon's monochrome layer
 * keep the alpha and throw the colour away, so eyes drawn in ink on a pink body
 * become eyes drawn in ink on an ink silhouette - invisible - while eyes cut out
 * of the body stay cut out whatever the surface does to colour. The same rule is
 * why the body is one flat colour and nothing is stroked.
 *
 *   node mark/generate.mjs
 *
 * Writes ic_alive_mark.xml. The launcher foreground, the splash alias and the 17
 * references are repointed by the batch, not by this file.
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const drawable = join(here, '../app/src/main/res/drawable');

const V = 72;
const f = (v) => {
    const r = Math.round(v * 100) / 100;
    return Object.is(r, -0) ? '0' : String(r);
};

// ---------- path building: M/L/C/Z only ----------
class Chain {
    constructor() { this.ops = []; this.cur = null; this.start = null; }
    moveTo(x, y) { this.ops.push(`M ${f(x)} ${f(y)}`); this.cur = [x, y]; this.start = [x, y]; return this; }
    lineTo(x, y) { this.ops.push(`L ${f(x)} ${f(y)}`); this.cur = [x, y]; return this; }
    cubic(x1, y1, x2, y2, x, y) {
        this.ops.push(`C ${f(x1)} ${f(y1)} ${f(x2)} ${f(y2)} ${f(x)} ${f(y)}`);
        this.cur = [x, y]; return this;
    }
    arc(cx, cy, r, a0, a1) {
        const total = a1 - a0;
        const steps = Math.max(1, Math.ceil(Math.abs(total) / (Math.PI / 2)));
        const step = total / steps;
        const k = (4 / 3) * Math.tan(step / 4) * r;
        for (let i = 0; i < steps; i++) {
            const t0 = a0 + i * step;
            const t1 = t0 + step;
            this.cubic(
                cx + r * Math.cos(t0) - k * Math.sin(t0), cy + r * Math.sin(t0) + k * Math.cos(t0),
                cx + r * Math.cos(t1) + k * Math.sin(t1), cy + r * Math.sin(t1) - k * Math.cos(t1),
                cx + r * Math.cos(t1), cy + r * Math.sin(t1)
            );
        }
        return this;
    }
    close() { this.ops.push('Z'); if (this.start) this.cur = this.start; return this; }
    get data() { return this.ops.join(' '); }
}

const circle = (c, cx, cy, r) => c.moveTo(cx + r, cy).arc(cx, cy, r, 0, Math.PI * 2).close();

/** The approved geometry, in one place so the check and the art cannot disagree. */
const BODY = { cx: 36, cy: 36, r: 28 };
const EYES = [
    { x: 21, y: 25, w: 11, h: 23 },
    { x: 40, y: 25, w: 11, h: 23 }
];

const capsule = (c, { x, y, w, h }) => {
    const r = w / 2;
    return c
        .moveTo(x + r, y)
        .lineTo(x + w - r, y)
        .arc(x + w - r, y + r, r, -Math.PI / 2, 0)
        .lineTo(x + w, y + h - r)
        .arc(x + w - r, y + h - r, r, 0, Math.PI / 2)
        .lineTo(x + r, y + h)
        .arc(x + r, y + h - r, r, Math.PI / 2, Math.PI)
        .lineTo(x, y + r)
        .arc(x + r, y + r, r, Math.PI, Math.PI * 1.5)
        .close();
};

// ---------- the self-check that needs no rasteriser ----------
/**
 * An eye that is not wholly inside the body is not a hole, it is a bite out of
 * the silhouette. The capsules are convex and the body is a circle, so the four
 * extreme points of each capsule are enough: if they are inside, everything
 * between them is, and the margin is the smallest distance any of them has to
 * the edge.
 */
function containment() {
    const problems = [];
    for (const eye of EYES) {
        const r = eye.w / 2;
        const extreme = [
            [eye.x + r, eye.y], [eye.x + r, eye.y + eye.h],
            [eye.x, eye.y + r], [eye.x + eye.w, eye.y + r],
            [eye.x + r - r * 0.7071, eye.y + r - r * 0.7071],
            [eye.x + r + r * 0.7071, eye.y + r - r * 0.7071],
            [eye.x + r - r * 0.7071, eye.y + eye.h - r + r * 0.7071],
            [eye.x + r + r * 0.7071, eye.y + eye.h - r + r * 0.7071]
        ];
        let margin = Infinity;
        for (const [x, y] of extreme) {
            margin = Math.min(margin, BODY.r - Math.hypot(x - BODY.cx, y - BODY.cy));
        }
        if (margin <= 0) problems.push(`an eye bites the body edge by ${(-margin).toFixed(2)} units`);
        else console.log(`eye at x ${eye.x}: ${margin.toFixed(2)} units of body left around it`);
    }
    const gap = EYES[1].x - (EYES[0].x + EYES[0].w);
    console.log(`the bridge between the eyes is ${gap.toFixed(1)} units - ${((gap / V) * 16).toFixed(2)} px at 16 px`);
    if (gap < 4) problems.push(`the eyes are ${gap.toFixed(1)} units apart; below 4 they merge into one slot`);
    return problems;
}

// ---------- the drawable ----------
const path = new Chain();
circle(path, BODY.cx, BODY.cy, BODY.r);
EYES.forEach((eye) => capsule(path, eye));

/**
 * The monochrome layer, on its own 108 canvas.
 *
 * `ic_launcher_foreground` is a layer-list whose only job is to hold the mark at
 * 64dp inside the 108dp adaptive canvas, because the launcher crops the outer
 * ring. The adaptive-icon XML fed that same layer-list to `<monochrome>`, which
 * asks a launcher to tint a LayerDrawable - and no JVM test can prove a given
 * launcher does. Themed icons then go blank on the one surface nobody can
 * screenshot from a test. So the inset is baked into a real vector here: the
 * mark is drawn on a 108 viewport at the same 64dp box, and the monochrome
 * layer is a plain VectorDrawable with holes in it.
 */
function monochromePath() {
    // The same path, moved and scaled into the adaptive-icon box: 64 of 108,
    // centred. Every number in the emitted data is a viewport coordinate, so a
    // single affine pass over the numbers is the whole transform - and it keeps
    // this file and ic_alive_mark.xml describing one shape rather than two.
    const scale = 64 / V;
    const shift = (108 - 64) / 2;
    return path.data.replace(/-?\d*\.?\d+/g, (n) => f(shift + parseFloat(n) * scale));
}

const xml = `<?xml version="1.0" encoding="utf-8"?>
<!-- ALIVE's mark: the critter. One flat body, two eyes cut OUT of it rather than
     drawn on it - a notification small icon and the adaptive icon's monochrome
     layer keep only alpha, so eyes in a second colour would vanish exactly where
     the mark is most often seen. No outline, no shadow, no second fill.
     Shape approved by the human reviewer as D1b generation 1; regenerate with
     mark/generate.mjs rather than editing paths. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="96dp"
    android:viewportWidth="${V}"
    android:viewportHeight="${V}">
    <path
        android:fillColor="@color/flat_ink"
        android:fillType="evenOdd"
        android:pathData="${path.data}" />
</vector>
`;

const monochrome = `<?xml version="1.0" encoding="utf-8"?>
<!-- ALIVE's themed-icon layer: the mark, with the adaptive-icon inset baked in
     (64dp of a 108dp canvas) so this file is a plain vector rather than a
     layer-list a launcher has to tint. Regenerate with mark/generate.mjs. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@color/flat_ink"
        android:fillType="evenOdd"
        android:pathData="${monochromePath()}" />
</vector>
`;

const problems = containment();
if (problems.length) {
    console.error('refusing to write:\n  ' + problems.join('\n  '));
    process.exit(1);
}

function write(name, text) {
    const target = join(drawable, name);
    const previous = existsSync(target) ? readFileSync(target, 'utf8') : null;
    writeFileSync(target, text);
    console.log(`${previous === text ? 'unchanged' : 'wrote'}    ${name}`);
    return target;
}

write('ic_alive_mark.xml', xml);
write('ic_launcher_monochrome.xml', monochrome);
