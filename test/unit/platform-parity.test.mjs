// Every method of the TypeScript API has its native method on both platforms and its section in the README, and neither
// platform has a method the API does not declare. A miss on either side compiles fine and fails only on a phone. Plain
// Node, no install.
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');

const definitions = read('src/definitions.ts');
const api = [...definitions.matchAll(/^\s+(\w+)\([^)]*\):\s*Promise</gm)].map((m) => m[1]).sort();
const swift = read('ios/Sources/CapacitorAnylinePlugin/AnylinePlugin.swift');
const kotlinDir = 'android/src/main/kotlin/io/github/maximbelov/anyline';
const kotlin = read(`${kotlinDir}/AnylinePlugin.kt`);
const ios = [...swift.matchAll(/CAPPluginMethod\(name:\s*"(\w+)"/g)].map((m) => m[1]).sort();
const iosFunctions = [...swift.matchAll(/@objc (?:override )?(?:public )?func (\w+)\(_ call: CAPPluginCall\)/g)]
  .map((m) => m[1])
  .sort();
const android = [...kotlin.matchAll(/@PluginMethod\s+(?:override\s+)?fun (\w+)\(/g)].map((m) => m[1]).sort();
const swiftErrors = read('ios/Sources/CapacitorAnylineCore/AnylineErrors.swift');
const kotlinErrors = read(`${kotlinDir}/AnylineErrors.kt`);

test('the API declares its methods', () => {
  assert.deepEqual(api, ['initAnylineSDK', 'scan']);
});

test('iOS registers exactly the methods of the API', () => {
  assert.deepEqual(ios, api);
});

test('iOS has an @objc function for every registered method', () => {
  assert.deepEqual(iosFunctions, api);
});

test('Android annotates exactly the methods of the API', () => {
  assert.deepEqual(android, api);
});

test('the plugin has the same name in JavaScript, Swift and Kotlin', () => {
  assert.match(read('src/index.ts'), /registerPlugin<AnylinePlugin>\('Anyline'/);
  assert.match(swift, /jsName = "Anyline"/);
  assert.match(kotlin, /@CapacitorPlugin\(name = "Anyline"\)/);
});

test('both platforms reject with the same error codes, and only codes the API declares', () => {
  const declared = [...definitions.matchAll(/^export type AnylineErrorCode = (.*);$/gm)][0][1]
    .match(/'([A-Z_]+)'/g)
    .map((code) => code.slice(1, -1))
    .sort();
  const iosCodes = [...swiftErrors.matchAll(/static let \w+ = "([A-Z_]+)"/g)].map((m) => m[1]).sort();
  const androidCodes = [...kotlinErrors.matchAll(/const val \w+ = "([A-Z_]+)"/g)].map((m) => m[1]).sort();
  assert.deepEqual(iosCodes, declared);
  assert.deepEqual(androidCodes, declared);
});

test('both platforms use the same messages, the cancel one being the string io-anyline-cordova used', () => {
  const iosMessages = [...swiftErrors.matchAll(/static let (\w+) = "([^"]*)"/g)]
    .filter((m) => !/^[A-Z_]+$/.test(m[2]))
    .map((m) => m[2])
    .sort();
  const androidMessages = [...kotlinErrors.matchAll(/const val (\w+) = "([^"]*)"/g)]
    .filter((m) => !/^[A-Z_]+$/.test(m[2]))
    .map((m) => m[2])
    .sort();
  assert.deepEqual(iosMessages, androidMessages);
  assert.ok(iosMessages.includes('Canceled'));
  assert.match(read('src/errors.ts'), /SCAN_CANCELED_MESSAGE = 'Canceled'/);
});

test('both platforms report the version in package.json to the SDK', () => {
  const { version } = JSON.parse(read('package.json'));
  assert.match(swift, new RegExp(`static let version = "${version.replaceAll('.', '\\.')}"`));
  assert.match(kotlin, new RegExp(`const val VERSION = "${version.replaceAll('.', '\\.')}"`));
});

test('the plugin ships the SDK on both platforms', () => {
  assert.match(read('Package.swift'), /\.package\(url: "https:\/\/github\.com\/Anyline\/anyline-ocr-spm-module\.git", from: "[\d.]+"\)/);
  assert.match(read('Package.swift'), /\.product\(name: "AnylinePackage", package: "anyline-ocr-spm-module"\)/);
  const gradle = read('android/build.gradle');
  assert.match(gradle, /implementation "io\.anyline:anylinesdk:\$anylineSdkVersion"/);
  assert.doesNotMatch(gradle, /compileOnly ["']io\.anyline:anylinesdk/);
});

test('the iOS and Android lists of Anyline modules agree', () => {
  const script = read('scripts/ios/remove-unneeded-assets.sh');
  const iosModules = [...script.matchAll(/^DIR_\w+="(module_\w+)"$/gm)].map((m) => m[1]).sort();
  const gradleBlock = read('android/anyline-assets.gradle').match(/ext\.anylineModules = \[([^\]]*)\]/)[1];
  const androidModules = [...gradleBlock.matchAll(/'(module_\w+)'/g)].map((m) => m[1]).sort();
  assert.deepEqual(iosModules, androidModules);
});

test('the npm package carries the files an app applies or runs', () => {
  const { files } = JSON.parse(read('package.json'));
  for (const path of ['android/anyline-assets.gradle', 'scripts/ios/remove-unneeded-assets.sh']) {
    assert.ok(files.includes(path), `${path} is not in package.json files`);
  }
});

test('the README documents every method', () => {
  const readme = read('README.md');
  for (const method of api) {
    assert.match(readme, new RegExp(`### ${method}\\(`), `README.md has no section for ${method}()`);
  }
});
