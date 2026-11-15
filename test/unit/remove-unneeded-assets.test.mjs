// The iOS build-phase script against a fake built app: an AnylineResources.bundle inside Anyline.framework, with every
// module. Plain Node and bash; code signing is off, so nothing is signed.
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('../../scripts/ios/remove-unneeded-assets.sh', import.meta.url));
const modules = [
  'module_anyline_ocr',
  'module_barcode',
  'module_document',
  'module_energy',
  'module_id',
  'module_license_plate',
  'module_tire',
];

let app;
let bundle;

beforeEach(() => {
  app = mkdtempSync(join(tmpdir(), 'anyline-app-'));
  bundle = join(app, 'Frameworks', 'Anyline.framework', 'AnylineResources.bundle');
  for (const module of modules) {
    mkdirSync(join(bundle, module), { recursive: true });
  }
});

afterEach(() => rmSync(app, { recursive: true, force: true }));

const run = (env) =>
  spawnSync('bash', [script], {
    env: { PATH: process.env.PATH, CODESIGNING_FOLDER_PATH: app, CODE_SIGNING_ALLOWED: 'NO', ...env },
    encoding: 'utf8',
  });

test('keeps the named modules and removes the rest', () => {
  const result = run({ ANYLINE_RETAIN_ASSETS_PATTERN: 'module_barcode' });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(readdirSync(bundle), ['module_barcode']);
  assert.match(result.stdout, /code signing is off/);
});

test('keeps several modules, space-separated', () => {
  const result = run({ ANYLINE_RETAIN_ASSETS_PATTERN: 'module_barcode module_id' });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(readdirSync(bundle).sort(), ['module_barcode', 'module_id']);
});

test('removes nothing when the setting is unset or empty', () => {
  for (const env of [{}, { ANYLINE_RETAIN_ASSETS_PATTERN: '' }]) {
    assert.equal(run(env).status, 0);
    assert.deepEqual(readdirSync(bundle).sort(), modules);
  }
});

test('fails when the setting is given and the bundle is not there yet', () => {
  rmSync(join(app, 'Frameworks'), { recursive: true });
  const result = run({ ANYLINE_RETAIN_ASSETS_PATTERN: 'module_barcode' });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /AnylineResources\.bundle was not found/);
});

test('is executable, so a build phase can run it by path', () => {
  assert.ok(existsSync(script));
  execFileSync('test', ['-x', script]);
});
