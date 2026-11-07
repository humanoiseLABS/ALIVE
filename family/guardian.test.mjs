import assert from 'node:assert/strict';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const here = dirname(fileURLToPath(import.meta.url));
const names = ['idle', 'alert', 'recovery', 'restored'].map(pose => `ic_guardian_${pose}.xml`).sort();

function fixture(t) {
    const root = mkdtempSync(join(tmpdir(), 'alive-guardian-'));
    t.after(() => rmSync(root, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 }));
    const drawable = join(root, 'app/src/main/res/drawable');
    mkdirSync(drawable, { recursive: true });
    mkdirSync(join(root, 'family'));
    mkdirSync(join(root, 'guardian'));
    mkdirSync(join(root, 'web/public/images'), { recursive: true });
    copyFileSync(join(here, 'guardian.mjs'), join(root, 'family/guardian.mjs'));
    writeFileSync(join(root, 'guardian/preview.png'), 'deferred Guardian sentinel');
    writeFileSync(join(root, 'web/public/images/guardian.png'), 'deferred site sentinel');
    const run = (...args) => spawnSync(process.execPath, [join(root, 'family/guardian.mjs'), ...args], { encoding: 'utf8', cwd: root });
    return { root, drawable, run };
}

test('regeneration writes only the four app poses and leaves deferred artwork untouched', t => {
    const { root, drawable, run } = fixture(t);
    const result = run();
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(readdirSync(drawable).sort(), names);
    for (const name of names) {
        assert.equal(readFileSync(join(drawable, name), 'utf8'), readFileSync(join(here, '../app/src/main/res/drawable', name), 'utf8').replaceAll('\r\n', '\n'));
    }
    assert.equal(readFileSync(join(root, 'guardian/preview.png'), 'utf8'), 'deferred Guardian sentinel');
    assert.equal(readFileSync(join(root, 'web/public/images/guardian.png'), 'utf8'), 'deferred site sentinel');
    assert.deepEqual(readdirSync(join(root, 'guardian')), ['preview.png']);
    assert.deepEqual(readdirSync(join(root, 'web/public/images')), ['guardian.png']);
});

test('check rejects missing or drifted XML without writing and accepts regenerated XML', t => {
    const { drawable, run } = fixture(t);
    assert.notEqual(run('--check').status, 0);
    assert.equal(existsSync(join(drawable, names[0])), false);
    assert.equal(run().status, 0);
    assert.equal(run('--check').status, 0);
    const file = join(drawable, 'ic_guardian_idle.xml');
    const bad = readFileSync(file, 'utf8').replace('M 36 8', 'M 36 9');
    writeFileSync(file, bad);
    const result = run('--check');
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /idle: XML differs from generator/);
    assert.equal(readFileSync(file, 'utf8'), bad);
    assert.equal(run().status, 0);
    assert.equal(run('--check').status, 0);
});
