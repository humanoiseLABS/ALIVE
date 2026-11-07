/**
 * The flat family — 11 category members and 6 day states, as VectorDrawable.
 *
 * The contract is design-refs/out/family-bots.py, the generator behind the two
 * approved renders: one solid body, no outline, no shadow, two tall capsule
 * eyes, no mouth, no limbs. Members differ on silhouette, hue, eye treatment
 * (upright / tilt 14° / offset / wide / one / closed) and a gloss slash. That
 * file draws SVG; nothing in res/drawable is consumed as SVG, so this draws the
 * same geometry as filled path data — M/L/C/Z only, which is the subset
 * AliveCharacterSilhouetteTest can parse and Android can compile.
 *
 * Four translations are not literal, and each has a reason:
 *
 * - family-bots.py rounds a faceted body by stroking it with its own colour at
 *   7 px. A VectorDrawable can do that, but a same-coloured stroke is a border
 *   that a flat lock would have to be taught to forgive, so the corners are
 *   rounded here by offsetting the polygon and joining it with arcs: the same
 *   shape, authored as a fill.
 * - `hollow` is a dashed stroke there. pathData cannot dash, so the dashes are
 *   authored as segments laid along the rounded rectangle's own perimeter. The
 *   interior is filled with flat_ground: the app's ground IS flat_ground, so in
 *   the app the plate is invisible and the member reads as an outline with
 *   nothing inside — which is what MISSING≠ZERO means — while the silhouette
 *   lock still sees one connected body under it.
 * - `Tidur` takes the crescent the spec's day-state table names, not the dome
 *   of the concept sheet, because the dome is `Membaca`'s silhouette and the
 *   family has to be distinct at 24 px. Its closed eyes therefore move onto the
 *   crescent's band: at the dome's centre they would have sat in the bite.
 * - The star's and the cross's eyes are pulled inward from the sheet's default
 *   box. Both silhouettes are concave, and `--check` measures the eye corners
 *   against the body: at the default positions they spill into the notch.
 *
 *   node family/generate.mjs            write the seventeen drawables
 *   node family/generate.mjs --check    measure them first, and refuse to write
 *                                       a member whose eyes float off its body
 *
 * Nothing else in res/drawable is touched.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const drawable = join(here, '../app/src/main/res/drawable');

const V = 72;
const INK = '@color/flat_ink';
const PAPER = '@color/flat_paper';
const GROUND = '@color/flat_ground';

/** The dashed outline of `hollow`: 5 px would be 1.7 px at the 24 px floor. */
const DASHES = 8;
const DUTY = 0.66;
const DASH_WIDTH = 7;

// ---------- path ops: a transformable, renderable list of commands ----------
const f = (v) => {
    const r = Math.round(v * 100) / 100;
    return Object.is(r, -0) ? '0' : String(r);
};
const rad = (deg) => (deg * Math.PI) / 180;

/** A path is a list of ops, so a rotation can be applied at render time. */
const P = () => ({ ops: [], cur: null, start: null });
const M = (p, x, y) => {
    p.ops.push(['M', x, y]);
    p.cur = [x, y];
    p.start = [x, y];
    return p;
};
const L = (p, x, y) => {
    // A zero-length line is what rounding a capsule emits at its own cap seam.
    // It draws nothing, so it is dropped rather than written into the XML.
    if (p.cur && Math.hypot(x - p.cur[0], y - p.cur[1]) < 0.005) return p;
    p.ops.push(['L', x, y]);
    p.cur = [x, y];
    return p;
};
const C = (p, x1, y1, x2, y2, x, y) => {
    p.ops.push(['C', x1, y1, x2, y2, x, y]);
    p.cur = [x, y];
    return p;
};
const Z = (p) => {
    p.ops.push(['Z']);
    if (p.start) p.cur = p.start;
    return p;
};

/**
 * family-bots.py rotates a capsule about its own centre; this rotates a path.
 * The current-point bookkeeping is dropped because a rotation preserves it.
 */
function rotate(p, degrees, cx, cy) {
    const cos = Math.cos(rad(degrees));
    const sin = Math.sin(rad(degrees));
    return {
        ops: p.ops.map((op) => {
            const out = [op[0]];
            for (let i = 1; i < op.length; i += 2) {
                const dx = op[i] - cx;
                const dy = op[i + 1] - cy;
                out.push(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
            }
            return out;
        })
    };
}

const render = (p) => {
    let s = '';
    for (const op of p.ops) {
        s += op[0] === 'Z' ? 'Z ' : `${op[0]} ${op.slice(1).map(f).join(' ')} `;
    }
    return s.trim();
};

// ---------- primitives ----------
/** The arc from a0 to a1 in screen axes (increasing angle = clockwise). */
function arc(p, cx, cy, r, a0, a1) {
    const total = a1 - a0;
    const entry = [cx + r * Math.cos(a0), cy + r * Math.sin(a0)];
    if (p.cur && Math.hypot(entry[0] - p.cur[0], entry[1] - p.cur[1]) > 0.01) L(p, entry[0], entry[1]);
    const steps = Math.max(1, Math.ceil(Math.abs(total) / (Math.PI / 2)));
    const step = total / steps;
    const k = (4 / 3) * Math.tan(step / 4) * r;
    for (let i = 0; i < steps; i++) {
        const t0 = a0 + i * step;
        const t1 = t0 + step;
        C(p,
            cx + r * Math.cos(t0) - k * Math.sin(t0), cy + r * Math.sin(t0) + k * Math.cos(t0),
            cx + r * Math.cos(t1) + k * Math.sin(t1), cy + r * Math.sin(t1) - k * Math.cos(t1),
            cx + r * Math.cos(t1), cy + r * Math.sin(t1));
    }
    return p;
}

const circle = (p, cx, cy, r) => {
    M(p, cx + r, cy);
    arc(p, cx, cy, r, 0, Math.PI * 2);
    return Z(p);
};

function roundedRect(p, x, y, w, h, r) {
    M(p, x + r, y);
    L(p, x + w - r, y);
    arc(p, x + w - r, y + r, r, -Math.PI / 2, 0);
    L(p, x + w, y + h - r);
    arc(p, x + w - r, y + h - r, r, 0, Math.PI / 2);
    L(p, x + r, y + h);
    arc(p, x + r, y + h - r, r, Math.PI / 2, Math.PI);
    L(p, x, y + r);
    arc(p, x + r, y + r, r, Math.PI, Math.PI * 1.5);
    return Z(p);
}

/**
 * The boundary of a convex polygon grown by r in every direction, which is what
 * a round-joined stroke of width 2r draws: every edge pushed out by r, every
 * vertex replaced by an arc of radius r centred on the vertex itself.
 */
function roundedPoly(p, points, r) {
    const n = points.length;
    const area = points.reduce((s, q, i) => {
        const k = points[(i + 1) % n];
        return s + (q[0] * k[1] - k[0] * q[1]);
    }, 0) / 2;
    const v = area > 0 ? points : [...points].reverse();
    const at = (i) => v[((i % n) + n) % n];
    const edge = (i) => {
        const a = at(i - 1);
        const b = at(i);
        const dx = b[0] - a[0];
        const dy = b[1] - a[1];
        const len = Math.hypot(dx, dy);
        return { x: dx / len, y: dy / len };
    };
    // Screen axes, clockwise ring: the outward normal of (dx, dy) is (dy, -dx).
    const out = (e) => ({ x: e.y, y: -e.x });

    for (let i = 0; i < n; i++) {
        const q = at(i);
        const nIn = out(edge(i));
        const nOut = out(edge(i + 1));
        const aIn = Math.atan2(nIn.y, nIn.x);
        const turn = Math.atan2(nIn.x * nOut.y - nIn.y * nOut.x, nIn.x * nOut.x + nIn.y * nOut.y);
        const start = [q[0] + r * nIn.x, q[1] + r * nIn.y];
        if (i === 0) M(p, start[0], start[1]);
        else L(p, start[0], start[1]);
        arc(p, q[0], q[1], r, aIn, aIn + turn);
    }
    return Z(p);
}

/** The capsule of family-bots.py's `cap()`: rx = w/2, optionally tilted. */
function capsule(p, x, y, w, h, degrees = 0) {
    const local = P();
    roundedRect(local, x, y, w, h, w / 2);
    const spun = degrees ? rotate(local, degrees, x + w / 2, y + h / 2) : local;
    const emit = { M, L, C, Z };
    spun.ops.forEach((op) => emit[op[0]](p, ...op.slice(1)));
    return p;
}

/**
 * A polyline given thickness - the shape a stroke would draw, authored as a
 * fill. The closed eyes and the dashes of `hollow` both need it, because this
 * vocabulary has no stroke and no dash.
 */
function stroked(p, points, width, roundCaps) {
    const half = width / 2;
    const dirs = points.map((q, i) => {
        const a = points[Math.max(0, i - 1)];
        const b = points[Math.min(points.length - 1, i + 1)];
        const dx = b[0] - a[0];
        const dy = b[1] - a[1];
        const len = Math.hypot(dx, dy) || 1;
        return { x: dx / len, y: dy / len };
    });
    const left = points.map((q, i) => [q[0] - dirs[i].y * half, q[1] + dirs[i].x * half]);
    const right = points.map((q, i) => [q[0] + dirs[i].y * half, q[1] - dirs[i].x * half]);

    M(p, left[0][0], left[0][1]);
    left.slice(1).forEach((q) => L(p, q[0], q[1]));
    if (roundCaps) {
        const end = points[points.length - 1];
        const a = Math.atan2(dirs[dirs.length - 1].y, dirs[dirs.length - 1].x) - Math.PI / 2;
        arc(p, end[0], end[1], half, a, a + Math.PI);
    } else {
        const q = right[right.length - 1];
        L(p, q[0], q[1]);
    }
    right.slice(0, points.length - 1).reverse().forEach((q) => L(p, q[0], q[1]));
    if (roundCaps) {
        const a = Math.atan2(dirs[0].y, dirs[0].x) + Math.PI / 2;
        arc(p, points[0][0], points[0][1], half, a, a + Math.PI);
    } else {
        L(p, left[0][0], left[0][1]);
    }
    return Z(p);
}

/** A quadratic given thickness, which is how a closed eye is drawn. */
function lash(p, x0, y0, cx, cy, x1, y1, width) {
    const pts = Array.from({ length: 13 }, (_, i) => {
        const t = i / 12;
        const u = 1 - t;
        return [u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1];
    });
    return stroked(p, pts, width, true);
}

/** Points along a rounded rectangle's perimeter, dense enough to cut dashes. */
function rrectPerimeter(x, y, w, h, r, perUnit = 4) {
    const straight = (a, b) => {
        const steps = Math.max(1, Math.round(Math.hypot(b[0] - a[0], b[1] - a[1]) * perUnit));
        return Array.from({ length: steps }, (_, i) => {
            const t = i / steps;
            return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
        });
    };
    const curve = (cx, cy, a0, a1) => {
        const steps = Math.max(1, Math.round((a1 - a0) * r * perUnit));
        return Array.from({ length: steps + 1 }, (_, i) => {
            const t = a0 + ((a1 - a0) * i) / steps;
            return [cx + r * Math.cos(t), cy + r * Math.sin(t)];
        });
    };
    return [
        ...straight([x + r, y], [x + w - r, y]), ...curve(x + w - r, y + r, -Math.PI / 2, 0),
        ...straight([x + w, y + r], [x + w, y + h - r]), ...curve(x + w - r, y + h - r, 0, Math.PI / 2),
        ...straight([x + w - r, y + h], [x + r, y + h]), ...curve(x + r, y + h - r, Math.PI / 2, Math.PI),
        ...straight([x, y + h - r], [x, y + r]), ...curve(x + r, y + r, Math.PI, Math.PI * 1.5)
    ];
}

// ---------- the silhouettes ----------
const body = {
    teardrop: (p) => {
        M(p, 36, 7);
        C(p, 36, 7, 58, 30, 58, 44);
        C(p, 58, 58, 48, 66, 36, 66);
        C(p, 24, 66, 14, 58, 14, 44);
        C(p, 14, 30, 36, 7, 36, 7);
        return Z(p);
    },
    dome: (p) => {
        M(p, 11, 55);
        C(p, 11, 31, 22, 15, 36, 15);
        C(p, 50, 15, 61, 31, 61, 55);
        return Z(p);
    },
    circle: (p) => circle(p, 36, 37, 27),
    bean: (p) => {
        M(p, 18, 46);
        C(p, 12, 32, 24, 16, 40, 18);
        C(p, 56, 20, 64, 34, 58, 48);
        C(p, 52, 62, 26, 62, 18, 46);
        return Z(p);
    },
    square: (p) => roundedRect(p, 11, 13, 50, 48, 17),
    cloud: (p) => {
        circle(p, 24, 40, 14);
        circle(p, 38, 29, 16);
        circle(p, 52, 40, 13);
        return roundedRect(p, 16, 40, 40, 20, 10);
    },
    hexagon: (p) => roundedPoly(p, [[36, 9], [58, 23], [58, 49], [36, 63], [14, 49], [14, 23]], 3.5),
    triangle: (p) => roundedPoly(p, [[36, 13.5], [57.5, 54.5], [14.5, 54.5]], 5),
    // The sheet's pill is 32 units tall, which is 11 px at the 24 px floor the
    // approved render itself states - under the legibility lock. It grows to 40.
    pill: (p) => roundedRect(p, 7, 17, 58, 40, 20),
    pentagon: (p) => roundedPoly(p, [[36, 8], [61, 27], [51, 60], [21, 60], [11, 27]], 3.5),
    tallblob: (p) => {
        M(p, 36, 8);
        C(p, 51, 8, 58, 22, 56, 38);
        C(p, 54, 56, 47, 66, 36, 66);
        C(p, 25, 66, 18, 56, 16, 38);
        C(p, 14, 22, 21, 8, 36, 8);
        return Z(p);
    },
    // The five the day states add. The spec's own table names these, and each
    // one is a topology the eleven category bodies do not have.
    leaf: (p) => {
        // A vesica: two arcs meeting at points, top and bottom. `bean` is blunt
        // at both ends; this is pointed at both ends.
        const half = 29;
        const bulge = 20;
        const d = (half * half - bulge * bulge) / (2 * bulge);
        const r = d + bulge;
        const a = Math.atan2(half, d);
        M(p, 36, 36 - half);
        arc(p, 36 - d, 36, r, -a, a);
        arc(p, 36 + d, 36, r, Math.PI - a, Math.PI + a);
        return Z(p);
    },
    star: (p) => {
        // A four-point sparkle: concave sides, so the silhouette has a waist.
        M(p, 36, 6);
        C(p, 40, 26, 46, 32, 66, 36);
        C(p, 46, 40, 40, 46, 36, 66);
        C(p, 32, 46, 26, 40, 6, 36);
        C(p, 26, 32, 32, 26, 36, 6);
        return Z(p);
    },
    octagon: (p) => roundedPoly(p, Array.from({ length: 8 }, (_, i) => {
        const t = rad(22.5 + i * 45);
        return [36 + 27 * Math.cos(t), 36 + 27 * Math.sin(t)];
    }), 3.5),
    cross: (p) => {
        // An equal-armed plus. Two fills, one shape: the arms overlap the plate.
        roundedRect(p, 27, 9, 18, 54, 9);
        return roundedRect(p, 9, 27, 54, 18, 9);
    },
    crescent: (p) => {
        // circle r=27 at (36,37) minus circle r=24 at (48,30). The boundary is
        // the arc of the first that lies outside the second, and the arc of the
        // second that lies inside the first - which arc is which is decided by
        // testing its midpoint, not by a sign nobody can check.
        const A = { cx: 36, cy: 37, r: 27 };
        const B = { cx: 48, cy: 30, r: 24 };
        const dx = B.cx - A.cx;
        const dy = B.cy - A.cy;
        const d = Math.hypot(dx, dy);
        const a = (d * d + A.r * A.r - B.r * B.r) / (2 * d);
        const h = Math.sqrt(A.r * A.r - a * a);
        const mid = [A.cx + (a * dx) / d, A.cy + (a * dy) / d];
        const tips = [1, -1].map((s) => {
            const x = mid[0] + s * h * (-dy / d);
            const y = mid[1] + s * h * (dx / d);
            return { x, y };
        });
        const on = (c, q) => Math.hypot(q.x - c.cx, q.y - c.cy);
        const chosen = (c, from, to, keep) => {
            const delta = ((to - from) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
            const test = (sweep) => {
                const t = from + sweep / 2;
                return keep({ x: c.cx + c.r * Math.cos(t), y: c.cy + c.r * Math.sin(t) });
            };
            if (test(delta)) return arc(p, c.cx, c.cy, c.r, from, from + delta);
            return arc(p, c.cx, c.cy, c.r, from, from + delta - Math.PI * 2);
        };
        const [t0, t1] = tips;
        M(p, t0.x, t0.y);
        chosen(A, Math.atan2(t0.y - A.cy, t0.x - A.cx), Math.atan2(t1.y - A.cy, t1.x - A.cx),
            (q) => on(B, q) > B.r);
        chosen(B, Math.atan2(t1.y - B.cy, t1.x - B.cx), Math.atan2(t0.y - B.cy, t0.x - B.cx),
            (q) => on(A, q) < A.r);
        return Z(p);
    },
    hollow: (p) => roundedRect(p, 12, 14, 48, 46, 17),
    hollowRing: (p) => dashes(p)
};

/**
 * The dashes of `hollow`, laid along the rounded rectangle's own perimeter.
 * One function, because the drawable and the 24 px measurement must not be
 * allowed to disagree about what the outline is.
 */
function dashes(p) {
    const ring = rrectPerimeter(12, 14, 48, 46, 17);
    const pts = [...ring, ring[0]];
    const length = pts.reduce((s, q, i) => (i ? s + Math.hypot(q[0] - pts[i - 1][0], q[1] - pts[i - 1][1]) : 0), 0);
    const at = (s) => {
        let acc = 0;
        for (let i = 1; i < pts.length; i++) {
            const seg = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
            if (acc + seg >= s) {
                const t = seg ? (s - acc) / seg : 0;
                return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * t,
                    pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * t];
            }
            acc += seg;
        }
        return pts[0];
    };
    const period = length / DASHES;
    for (let i = 0; i < DASHES; i++) {
        const seg = Array.from({ length: 13 }, (_, k) => at(i * period + (period * DUTY * k) / 12));
        stroked(p, seg, DASH_WIDTH, false);
    }
    return p;
}

// ---------- the eyes ----------
const eyes = {
    upright: (p) => {
        capsule(p, 27, 32, 7, 15);
        return capsule(p, 40, 32, 7, 15);
    },
    tilt: (p) => {
        capsule(p, 26, 33, 7, 15, 14);
        return capsule(p, 40, 31, 7, 15, 14);
    },
    offset: (p) => {
        capsule(p, 25, 30, 7, 15);
        return capsule(p, 40, 37, 7, 15);
    },
    wide: (p) => {
        capsule(p, 20, 33, 8, 16);
        return capsule(p, 45, 33, 8, 16);
    },
    one: (p) => capsule(p, 32, 32, 8, 16),
    // Two closed lashes, sat on the crescent's band rather than on a centre the
    // crescent does not have.
    closed: (p) => {
        lash(p, 19, 47, 23, 52, 27, 47, 3.5);
        return lash(p, 25, 54, 29, 59, 33, 54, 3.5);
    }
};

/**
 * The two concave silhouettes need their eyes pulled in: the sheet's default
 * box is a rectangle 25..47 wide, and a sparkle and a plus do not have body
 * under its outer corners. Same treatment, narrower stance, and on the plus
 * the eyes sit inside the horizontal arm.
 */
const eyesNarrow = {
    tilt: (p) => {
        capsule(p, 29, 33, 6, 14, 14);
        return capsule(p, 38, 31, 6, 14, 14);
    },
    wide: (p) => {
        capsule(p, 26, 29, 7, 14);
        return capsule(p, 40, 29, 7, 14);
    }
};

/**
 * The gloss slash: a 4.5 x 12 capsule tilted 18°, near the shoulder. The sheet
 * puts it at (46,18), which is off the body on five of the silhouettes, so the
 * five get their own shoulder. Nothing here is taste - `--check` fails the
 * member if a gloss pixel lands outside its body.
 */
const GLOSS = {
    teardrop: [42, 24],
    triangle: [40, 32],
    cloud: [44, 20],
    cross: [37, 14],
    leaf: [42, 22]
};
const gloss = (p, shape) => {
    const [x, y] = GLOSS[shape] || [46, 18];
    return capsule(p, x, y, 4.5, 12, 18);
};

// ---------- the family ----------
const MEMBERS = [
    ['alive_member_study', '@color/alive_cat_study', 'teardrop', 'upright', true],
    ['alive_member_reading', '@color/alive_cat_reading', 'dome', 'offset', false],
    ['alive_member_exercise', '@color/alive_cat_exercise', 'circle', 'wide', true],
    ['alive_member_walking', '@color/alive_cat_walking', 'bean', 'tilt', false],
    ['alive_member_cleaning', '@color/alive_cat_cleaning', 'square', 'upright', false],
    ['alive_member_creative', '@color/alive_cat_creative', 'cloud', 'offset', true],
    ['alive_member_coding', '@color/alive_cat_coding', 'hexagon', 'tilt', false],
    ['alive_member_writing', '@color/alive_cat_writing', 'triangle', 'upright', true],
    ['alive_member_mindfulness', '@color/alive_cat_mindfulness', 'pill', 'wide', false],
    ['alive_member_social', '@color/alive_cat_social', 'pentagon', 'offset', true],
    ['alive_member_work', '@color/alive_cat_work', 'tallblob', 'tilt', false]
];

const STATES = [
    ['alive_state_normal', '@color/alive_traffic_green', 'leaf', 'upright', true],
    ['alive_state_near', '@color/alive_traffic_yellow', 'star', 'tilt', false],
    ['alive_state_spent', '@color/alive_traffic_red', 'octagon', 'offset', false],
    ['alive_state_recovery', '@color/flat_field_leaf', 'cross', 'wide', true],
    ['alive_state_unknown', '@color/alive_cat_cleaning', 'hollow', 'one', false],
    ['alive_state_rest', INK, 'crescent', 'closed', false]
];

/**
 * Which colour the eyes wear.
 *
 * Three rules answer this question in this project and only two of them can be
 * right. `family-bots.py`'s `_dark()` is a plain weighted sRGB average against
 * 0.32. The app's `textOn()` in AliveComponents is WCAG relative luminance
 * against the same 0.32. The spec claims they are one number on purpose, and
 * they are not: on `#FF5F57` - `Jatah habis` - the average says light and
 * gives ink eyes, the luminance says dark and would give paper eyes. Measured,
 * paper on that red is 2,99:1, so the app's rule is the one that is wrong here,
 * and the approved render - ink eyes on the red hexagon - agrees with the
 * average, not the luminance.
 *
 * So this asks neither proxy. It measures the requirement itself: ink if ink
 * clears 4.5:1, paper if paper does, and if neither does, a thrown error rather
 * than a guess. Checked against all 17 members, this rule and `_dark()` return
 * the same eye colour on every one of them, so nothing in the shipped art
 * moved - the change is that the answer is now measured instead of assumed.
 */
const srgb = (v) => (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));

/**
 * The hues are read out of `res/values/colors.xml`, not copied into this file.
 * A hand-kept table is a second source of truth for the same pixels - the one
 * failure decision 1 of this project exists to prevent - and the day a hue
 * moves, a copied table keeps emitting eyes chosen for the colour nobody ships.
 * Aliases are followed, because `flat_paper` is one.
 */
const COLOURS = (() => {
    const text = readFileSync(join(here, '../app/src/main/res/values/colors.xml'), 'utf8');
    const named = new Map();
    for (const m of text.matchAll(/<color name="([^"]+)">([^<]+)<\/color>/g)) named.set(m[1], m[2].trim());
    const resolve = (token, depth = 0) => {
        if (!token.startsWith('@color/')) return token;
        if (depth > 4) throw new Error(`colour alias loop at ${token}`);
        const value = named.get(token.slice('@color/'.length));
        if (value === undefined) throw new Error(`${token} is not in colors.xml`);
        return resolve(value, depth + 1);
    };
    return { resolve, size: named.size };
})();

function luminance(token) {
    const hex = COLOURS.resolve(token);
    if (!/^#[0-9A-Fa-f]{6}$/.test(hex)) throw new Error(`${token} resolves to "${hex}", not a hex`);
    const [r, g, b] = [1, 3, 5].map((i) => srgb(parseInt(hex.slice(i, i + 2), 16) / 255));
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(first, second) {
    const lighter = Math.max(luminance(first), luminance(second));
    const darker = Math.min(luminance(first), luminance(second));
    return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Both directions are measured. Asking only "does ink clear it" and defaulting
 * to paper would ship eyes nobody checked: the two requirements do not cover
 * the whole space, and a hue of the right middle luminance clears neither.
 */
function eyeColour(hue) {
    if (contrast(hue, INK) >= 4.5) return INK;
    if (contrast(hue, PAPER) >= 4.5) return PAPER;
    throw new Error(
        `${hue} (${COLOURS.resolve(hue)}) carries neither ink at ${contrast(hue, INK).toFixed(2)}:1 ` +
        'nor paper at ' + contrast(hue, PAPER).toFixed(2) + ':1 - no eye colour is legible on it'
    );
}

const eyeSet = (shape, kind) => (shape === 'star' || shape === 'cross') && eyesNarrow[kind]
    ? eyesNarrow[kind]
    : eyes[kind];

/** The three fills of one member, in draw order. */
function layersOf([, hue, shape, eye, shiny]) {
    const ink = eyeColour(hue);
    const plate = P();
    body[shape](plate);
    // On `hollow` the plate is the ground - invisible in the app - and the ring
    // that reads as the body is a separate fill.
    const layers = [[shape === 'hollow' ? GROUND : hue, plate]];
    if (shape === 'hollow') layers.push([hue, dashes(P())]);
    const face = P();
    eyeSet(shape, eye)(face);
    layers.push([ink, face]);
    if (shiny) {
        const shine = P();
        gloss(shine, shape);
        layers.push([PAPER, shine, '0.55']);
    }
    return layers;
}

function xmlFor(member) {
    const [name, hue, shape, eye, shiny] = member;
    const paths = layersOf(member).map(([fill, path, alpha]) =>
        `    <path\n        android:fillColor="${fill}"${alpha ? `\n        android:fillAlpha="${alpha}"` : ''}\n        android:pathData="${render(path)}" />`
    ).join('\n');
    return `<?xml version="1.0" encoding="utf-8"?>
<!-- ALIVE flat family: ${name.slice(6).replace(/_/g, ' ')}. Body ${shape}, eyes ${eye}${shiny ? ', gloss' : ''},
     hue ${hue.replace('@color/', '')}. One solid body, no outline, no shadow, no mouth,
     no limbs. Drawn by family/generate.mjs from the approved family-bots.py geometry -
     regenerate, do not edit paths. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="96dp"
    android:viewportWidth="${V}"
    android:viewportHeight="${V}">
${paths}
</vector>
`;
}

// ---------- the measurement the suite will repeat on real pixels ----------
/**
 * Flatten ops to rings, fill them, and count.
 *
 * This is NOT the lock. The lock is AliveCharacterSilhouetteTest on Android's
 * own rasteriser, and the two disagree by more than nothing: measured on the
 * shipped family, this filler put the closest pair at 0,919 while Android put
 * it at 0,936 - a different pair, 0,017 optimistic. It exists because a Gradle
 * run costs a minute and this costs milliseconds, and a member whose eyes sit
 * outside its own body should never reach a drawable at all.
 *
 * What it does NOT check, so nobody reads a green table as a passing suite:
 * the IoU matrix is printed and never asserted, and eye contrast is not
 * measured here at all. The Kotlin locks cover both. What it DOES check that
 * the Kotlin file cannot do cheaply at authoring time is containment: every
 * eye and every gloss slash is tested against its own body, and for `hollow`
 * against the hole inside the ring rather than the plate under it.
 */
function rings(path, n) {
    const scale = n / V;
    const out = [];
    let ring = [];
    let x = 0;
    let y = 0;
    let sx = 0;
    let sy = 0;
    const pt = (px, py) => [px * scale, py * scale];
    for (const op of path.ops) {
        if (op[0] === 'M') {
            if (ring.length > 2) out.push(ring);
            ring = [];
            [x, y] = [op[1], op[2]];
            [sx, sy] = [x, y];
            ring.push(pt(x, y));
        } else if (op[0] === 'L') {
            [x, y] = [op[1], op[2]];
            ring.push(pt(x, y));
        } else if (op[0] === 'C') {
            const [x1, y1, x2, y2] = [op[1], op[2], op[3], op[4]];
            const steps = 24;
            for (let i = 1; i <= steps; i++) {
                const t = i / steps;
                const u = 1 - t;
                const bx = u * u * u * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * op[5];
                const by = u * u * u * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * op[6];
                ring.push(pt(bx, by));
            }
            [x, y] = [op[5], op[6]];
        } else if (op[0] === 'Z') {
            ring.push(pt(sx, sy));
            [x, y] = [sx, sy];
        }
    }
    if (ring.length > 2) out.push(ring);
    return out;
}

function fill(path, n) {
    const mask = new Uint8Array(n * n);
    for (let y = 0; y < n; y++) {
        const yc = y + 0.5;
        const crossings = [];
        for (const ring of rings(path, n)) {
            for (let i = 0; i < ring.length; i++) {
                const [x0, y0] = ring[i];
                const [x1, y1] = ring[(i + 1) % ring.length];
                if ((y0 <= yc && y1 > yc) || (y1 <= yc && y0 > yc)) {
                    const t = (yc - y0) / (y1 - y0);
                    crossings.push({ x: x0 + t * (x1 - x0), dir: y1 > y0 ? 1 : -1 });
                }
            }
        }
        crossings.sort((a, b) => a.x - b.x);
        let w = 0;
        let start = 0;
        for (const c of crossings) {
            const before = w;
            w += c.dir;
            if (before === 0 && w !== 0) start = c.x;
            else if (before !== 0 && w === 0) {
                for (let x = Math.max(0, Math.ceil(start - 0.5)); x < Math.min(n, Math.ceil(c.x - 0.5)); x++) mask[y * n + x] = 1;
            }
        }
    }
    return mask;
}

const count = (m) => m.reduce((s, v) => s + v, 0);

function union(...masks) {
    const out = new Uint8Array(masks[0].length);
    masks.forEach((m) => m.forEach((v, i) => { if (v) out[i] = 1; }));
    return out;
}

function bbox(mask, n) {
    let x0 = n, y0 = n, x1 = -1, y1 = -1;
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
        if (!mask[y * n + x]) continue;
        x0 = Math.min(x0, x); x1 = Math.max(x1, x);
        y0 = Math.min(y0, y); y1 = Math.max(y1, y);
    }
    return { x0, y0, x1, y1, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}

/** The silhouette at 24 px: solid, and not the ground it sits on. */
function silhouette(member, n = 24) {
    const [, , shape] = member;
    if (shape !== 'hollow') return fill(body[shape](P()), n);
    // The plate is flat_ground, so on the app's ground it is not there; what
    // reads is the dashed ring and the one eye.
    const ring = P();
    dashes(ring);
    return union(fill(ring, n), fill(eyes.one(P()), n));
}

function bodyMaskOf(shape, n) {
    const plate = fill(body[shape](P()), n);
    // For `hollow` the body the eye has to sit in is the HOLE: the plate is the
    // page, and the ring is the member. Testing the eye against the plate would
    // let it land anywhere inside the outline, including on a dash.
    if (shape !== 'hollow') return plate;
    return subtract(plate, fill(dashes(P()), n));
}

function subtract(whole, cut) {
    const out = new Uint8Array(whole.length);
    for (let i = 0; i < whole.length; i++) out[i] = whole[i] && !cut[i] ? 1 : 0;
    return out;
}

function check(members) {
    const N = 144;
    const problems = [];
    const stats = [];
    const sil = members.map((m) => silhouette(m, 24));

    members.forEach((member, i) => {
        const [name, , shape, eye, shiny] = member;
        const bodyMask = bodyMaskOf(shape, N);
        const face = fill(eyeSet(shape, eye)(P()), N);
        const shine = shiny ? fill((() => { const p = P(); gloss(p, shape); return p; })(), N) : new Uint8Array(N * N);
        const adrift = (() => {
            let n = 0;
            face.forEach((v, k) => { if (v && !bodyMask[k]) n++; });
            shine.forEach((v, k) => { if (v && !bodyMask[k]) n++; });
            return n;
        })();
        const box = bbox(bodyMask, N);
        const scale = 24 / N;
        stats.push({
            name, shape, eye,
            area: count(bodyMask),
            at24: Math.round(count(sil[i]) * 1),
            span: `${Math.round(box.w * scale)}x${Math.round(box.h * scale)}`,
            adrift
        });
        if (adrift) problems.push(`${name}: ${adrift} px of eye/gloss outside the ${shape} body`);
        if (count(sil[i]) < 24 * 24 / 8) problems.push(`${name}: only ${count(sil[i])} opaque px at 24 (floor 72)`);
        if (Math.round(box.w * scale) < 12 || Math.round(box.h * scale) < 12) problems.push(`${name}: bbox ${Math.round(box.w * scale)}x${Math.round(box.h * scale)} at 24 px`);
    });

    const worst = [];
    for (let a = 0; a < members.length; a++) for (let b = a + 1; b < members.length; b++) {
        let inter = 0;
        let uni = 0;
        for (let k = 0; k < sil[a].length; k++) {
            if (sil[a][k] && sil[b][k]) inter++;
            if (sil[a][k] || sil[b][k]) uni++;
        }
        worst.push({ pair: `${members[a][0].slice(6)} / ${members[b][0].slice(6)}`, iou: inter / uni });
    }
    worst.sort((x, y) => y.iou - x.iou);

    console.table(stats);
    console.log('closest silhouette pairs (IoU at 24 px):');
    worst.slice(0, 8).forEach((p) => console.log(`  ${p.iou.toFixed(3)}  ${p.pair}`));
    console.log(problems.length ? `PROBLEMS\n  ${problems.join('\n  ')}` : 'no problems: every eye sits on its own body, every member clears the 24 px floor');
    return problems;
}

// ---------- write ----------
const all = [...MEMBERS, ...STATES];
const problems = check(all);
if (process.argv.includes('--check')) process.exit(problems.length ? 1 : 0);
if (problems.length) {
    console.error('refusing to write: fix the geometry first, or run with --check');
    process.exit(1);
}
all.forEach((member) => writeFileSync(join(drawable, `${member[0]}.xml`), xmlFor(member)));
console.log(`${all.length} members written to app/src/main/res/drawable`);
