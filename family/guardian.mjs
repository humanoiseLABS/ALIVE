import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const drawable = join(dirname(fileURLToPath(import.meta.url)), '../app/src/main/res/drawable');
const poses = {
    idle: {
        body: 'M 36 8 C 44 8 53 12 60 17 L 59 38 C 58 50 48 60 36 66 C 24 60 14 50 13 38 L 12 17 C 19 12 28 8 36 8 Z',
        eyes: [[23, 24, 9, 21, 0], [40, 24, 9, 21, 0]],
    },
    alert: {
        body: 'M 36 5 C 45 5 54 8 63 12 L 62 34 C 61 48 48 62 36 68 C 24 62 11 48 10 34 L 9 12 C 18 8 27 5 36 5 Z',
        eyes: [[21, 19, 10, 25, -14], [41, 19, 10, 25, 14]],
    },
    recovery: {
        body: 'M 36 20 C 46 20 56 23 63 27 L 61 45 C 58 54 48 59 36 64 C 24 59 14 54 11 45 L 9 27 C 16 23 26 20 36 20 Z',
        eyes: [[22, 34, 10, 16, 14], [40, 34, 10, 16, -14]],
    },
    restored: {
        body: 'M 36 5 C 49 5 57 13 57 25 L 56 41 C 55 51 45 61 36 67 C 27 61 17 51 16 41 L 15 25 C 15 13 23 5 36 5 Z',
        eyes: [[23, 19, 9, 22, 0], [40, 16, 9, 22, 0]],
    },
};

function capsule(x, y, width, height, tilt) {
    const r = width / 2;
    const k = r * 0.5522847498;
    const ops = [
        ['M', x + r, y],
        ['C', x + r + k, y, x + width, y + r - k, x + width, y + r],
        ['L', x + width, y + height - r],
        ['C', x + width, y + height - r + k, x + r + k, y + height, x + r, y + height],
        ['C', x + r - k, y + height, x, y + height - r + k, x, y + height - r],
        ['L', x, y + r],
        ['C', x, y + r - k, x + r - k, y, x + r, y],
        ['Z'],
    ];
    const angle = tilt * Math.PI / 180;
    const cx = x + r;
    const cy = y + height / 2;
    return ops.map(([cmd, ...points]) => {
        const out = [];
        for (let i = 0; i < points.length; i += 2) {
            const dx = points[i] - cx;
            const dy = points[i + 1] - cy;
            out.push(cx + dx * Math.cos(angle) - dy * Math.sin(angle), cy + dx * Math.sin(angle) + dy * Math.cos(angle));
        }
        return [cmd, ...out.map(value => Number(value.toFixed(3)))].join(' ');
    }).join(' ');
}

const check = process.argv.includes('--check');
for (const [pose, { body, eyes }] of Object.entries(poses)) {
    const paths = [
        ['body', 'alive_red_short_mid', body],
        ['eye_left', 'flat_ink', capsule(...eyes[0])],
        ['eye_right', 'flat_ink', capsule(...eyes[1])],
    ].map(([name, colour, data]) => `    <path android:name="${name}" android:fillColor="@color/${colour}"\n        android:pathData="${data}" />`).join('\n');
    const xml = `<?xml version="1.0" encoding="utf-8"?>
<!-- App-only Guardian; regenerate with node family/guardian.mjs. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp" android:height="96dp"
    android:viewportWidth="72" android:viewportHeight="72">
${paths}
</vector>
`;
    const file = join(drawable, `ic_guardian_${pose}.xml`);
    if (check) {
        if (readFileSync(file, 'utf8').replaceAll('\r\n', '\n') !== xml) throw new Error(`${pose}: XML differs from generator`);
    } else {
        writeFileSync(file, xml);
    }
}
console.log(`${check ? 'Verified' : 'Wrote'} 4 app-only Guardian vectors; guardian/ and web/ untouched.`);
