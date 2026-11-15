# @maximbelov/capacitor-anyline

The [Anyline](https://anyline.com) scanner SDK for Capacitor: initialise it with a licence key, then open the scanner
with an Anyline scan view config and get the result back as JSON. Android and iOS.

It replaces `io-anyline-cordova` in a Capacitor app, with the same two calls the Cordova plugin's `window.Anyline` had,
`initAnylineSDK` and `scan`, as promises. The scan view config is the same JSON, and a scan resolves with the same result
JSON. It drives the SDK through Anyline's wrapper session, the SDK's own interface for wrapper frameworks, and lets the
SDK present its own full-screen scanner. The newer "Infinity" API of the Cordova plugin is not included.

| Platform | SDK                                                                                            |
| -------- | ---------------------------------------------------------------------------------------------- |
| Android  | `io.anyline:anylinesdk` 56.6.0, from `https://mobile-sdk-android.anyline.io/release/`          |
| iOS      | `https://github.com/Anyline/anyline-ocr-spm-module.git` from 56.6.0 (product `AnylinePackage`) |

## Install

The package is published to GitHub Packages. Point the `@maximbelov` scope at it in the app's `.npmrc`, with a token
that can read packages (`read:packages`):

```ini
@maximbelov:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}
```

```bash
npm install @maximbelov/capacitor-anyline
npx cap sync
```

`cap sync` names the plugin from the package name: the Swift package and product `MaximbelovCapacitorAnyline` (and the
pod of the same name), and the Gradle project `:maximbelov-capacitor-anyline`.

Requires Capacitor 8, Android 8.0 (API 26) or newer and iOS 15 or newer. You need an Anyline licence key for the app's
bundle identifier and application id. There is no web implementation: every call rejects with `UNAVAILABLE` in a
browser.

The plugin ships the Anyline SDK. Remove `io-anyline-cordova` from the app when you install it, so the app carries one
copy of the SDK.

- **Android**: add Anyline's Maven repository to the app's `android/build.gradle`. The plugin's module declares it for
  itself, but the app module packages the SDK and resolves it through its own repositories, so without it the build fails
  with `Could not find io.anyline:anylinesdk`:

  ```groovy
  allprojects {
      repositories {
          google()
          mavenCentral()
          maven { url 'https://mobile-sdk-android.anyline.io/release/' }
      }
  }
  ```

  The plugin declares the `CAMERA` and `VIBRATE` permissions; the SDK declares its scan activity. The SDK
  version can be changed with `anylineSdkVersion` in the app's `variables.gradle`. Make sure the camera permission is
  granted before a scan, as with `io-anyline-cordova`.
- **iOS**: add `NSCameraUsageDescription` to the app's `Info.plist`. The SDK asks for the camera itself when the scanner
  opens. With CocoaPods the plugin depends on the `Anyline` pod (`~> 56.6`).

## Usage

```ts
import { Anyline, isScanCanceled } from '@maximbelov/capacitor-anyline';

await Anyline.initAnylineSDK({ licenseKey: 'YOUR_LICENSE_KEY' });

try {
  const result = await Anyline.scan({
    config: {
      cameraConfig: { captureResolution: '1080p' },
      flashConfig: { mode: 'auto', alignment: 'top' },
      viewPluginConfig: {
        pluginConfig: { id: 'barcode', barcodeConfig: { barcodeFormats: ['ALL'] }, cancelOnResult: true },
        cutoutConfig: { maxWidthPercent: '80%', alignment: 'top_half', ratioFromSize: { width: 3, height: 1 } },
      },
      options: { doneButtonConfig: { title: 'Cancel' } },
    },
  });
  console.log(result.barcodeResult?.barcodes?.[0]?.value);
} catch (error) {
  if (isScanCanceled(error)) {
    // the user closed the scanner
  } else {
    throw error;
  }
}
```

Without a bundler the plugin is `Capacitor.Plugins.Anyline`.

## Keeping only the scanner modules the app uses

The SDK carries the assets of every scanner module: `module_anyline_ocr`, `module_barcode`, `module_document`,
`module_energy`, `module_id`, `module_license_plate` and `module_tire`. An app that scans barcodes needs only
`module_barcode`. Both platforms can leave the rest out of the build; with neither setting, every module is kept.

### Android

Name the modules to keep, colon-separated, in `android/variables.gradle`:

```groovy
ext {
    anylineRetainAssetsPattern = 'module_barcode'
}
```

and apply the plugin's Gradle file from `android/app/build.gradle`:

```groovy
apply from: project(':maximbelov-capacitor-anyline').file('anyline-assets.gradle')
```

Every other module is then left out of the APK through the app's `androidResources.ignoreAssetsPatterns`. The app module
has to apply it because the assets come from the SDK's AAR, and only the module that packages the APK can filter them.
A name that is not one of the modules fails the build, and so does a pattern that is set while no app module applies the
file.

### iOS

Add a build setting `ANYLINE_RETAIN_ASSETS_PATTERN` to the app target, with the modules to keep separated by spaces
(`module_barcode`), and a Run Script build phase **after** the frameworks are embedded:

```bash
"${SRCROOT}/../../node_modules/@maximbelov/capacitor-anyline/scripts/ios/remove-unneeded-assets.sh"
```

It deletes the other modules from `AnylineResources.bundle` inside the embedded `Anyline.framework` and re-signs the
framework with the build's identity. With the setting set but the bundle not found (a phase placed before the embed),
the build fails.

## Asset paths

A relative path in a scan view config (a model file, a sound) is resolved by the SDK.

- **Android**: the SDK reads it from the APK's assets under `public/assets`, which is where Capacitor packages the web
  build's `assets` folder. `io-anyline-cordova` passed Cordova's `www/assets`.
- **iOS**: the plugin passes the app bundle as the config's location and no asset prefix, as `io-anyline-cordova` did, so
  a relative path is read from the app bundle, where Capacitor's web build is `public/`.

## Differences from io-anyline-cordova

- `Anyline.initAnylineSDK({ licenseKey })` and `Anyline.scan({ config })` take one options object and return promises,
  instead of `window.Anyline.initAnylineSDK(licenseKey, onSuccess, onFailure)` and `scan(config, onSuccess, onFailure)`.
- A rejection is an error with a `code` (`CANCELED`, `SCAN_FAILED`, `INIT_FAILED`, `INVALID_CONFIG`) and a message,
  not a bare string. The message is the string the Cordova plugin rejected with: `Canceled` for a cancel, the SDK's
  error for a failed scan, the SDK's initialisation response as JSON for a refused key. `isScanCanceled(error)` takes
  both forms.
- `initAnylineSDK` resolves with nothing (the Cordova plugin resolved with a message on Android and nothing on iOS).
- A composite scan resolves with `{ results: [...] }`; the Cordova plugin resolved with the array itself.
- The result callbacks named in a scan's callback config (`onResultEventName`) are not supported: the result is what
  `scan` resolves with.
- Only `initAnylineSDK` and `scan`: no `checkLicense`, `getSDKVersion`, `exportCachedEvents`, `tryStopScan` or Infinity
  API.
- Android: the asset prefix is `public/assets` instead of `www/assets`, and the module filter is the
  `anylineRetainAssetsPattern` Gradle property instead of the `anyline-retain-assets-pattern` preference in
  `config.xml`.

## Tests

- `npm test` runs the TypeScript unit tests, the parity check (both platforms implement exactly the API, with the same
  error codes and messages, version and module lists) and the iOS build-phase script against a fake app bundle.
- `npm run test:android` runs the JVM tests of the Kotlin (the plugin's name and methods, the result mapping, the
  waiting calls) and the check of the Android asset pattern.
- `npm run test:ios` runs the Swift core's tests on a Mac (`swift test`). The Anyline SDK has no macOS or arm64
  simulator slice, so the plugin layer is built for a device (`npm run verify:ios`) rather than tested on a simulator.

A scan itself needs a camera, a licence key and a phone, so it is not covered by these tests.

## API

<docgen-index>

* [`initAnylineSDK(...)`](#initanylinesdk)
* [`scan(...)`](#scan)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### initAnylineSDK(...)

```typescript
initAnylineSDK(options: InitAnylineSDKOptions) => Promise<void>
```

Initialises the Anyline SDK with a licence key. Call it once before the first scan; the SDK keeps the licence for
the life of the app process.

Resolves once the SDK is ready to scan. Rejects with `INIT_FAILED` when the SDK refuses the key, and with
`INVALID_CONFIG` when `licenseKey` is missing.

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code><a href="#initanylinesdkoptions">InitAnylineSDKOptions</a></code> |

**Since:** 0.1.0

--------------------


### scan(...)

```typescript
scan(options: ScanOptions) => Promise<AnylineScanResult>
```

Opens the scanner full screen with a scan view config, and resolves with the first result once the scanner has
closed on it (the config's `cancelOnResult: true`).

Rejects with `CANCELED` (message `Canceled`) when the user closes the scanner (the Done button on iOS, Back on
Android), with `SCAN_FAILED` when the SDK cannot scan (for example before `initAnylineSDK` succeeded), and with
`INVALID_CONFIG` when `config` is missing or the SDK cannot read it. A scan started while another is open
replaces it: the earlier call rejects with `SCAN_FAILED`.

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#scanoptions">ScanOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#anylinescanresult">AnylineScanResult</a>&gt;</code>

**Since:** 0.1.0

--------------------


### Interfaces


#### InitAnylineSDKOptions

The options of `initAnylineSDK`.

| Prop                     | Type                 | Description                                                                                                                                   |
| ------------------------ | -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| **`licenseKey`**         | <code>string</code>  | The Anyline licence key for this app's bundle identifier (iOS) or application id (Android).                                                   |
| **`enableOfflineCache`** | <code>boolean</code> | Lets the SDK cache the licence check, so a device that is offline at the next start can still scan. Off by default, as in io-anyline-cordova. |


#### AnylineScanResult

What a scan resolves with: the scan result JSON io-anyline-cordova returned, as the SDK's wrapper session writes it.
Its fields depend on the scan plugin: a barcode scan has `barcodeResult.barcodes`, a meter scan `meterResult`, and so
on, next to `pluginID`, `confidence`, `imagePath` and `fullImagePath` (the files of the cutout and the full frame).

A composite scan (`viewPluginCompositeConfig`) produces one result per child plugin. io-anyline-cordova returned
those as an array; a Capacitor call resolves with an object, so they arrive as `results`.

| Prop                | Type                                                                    | Description                                                                               |
| ------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| **`barcodeResult`** | <code>{ [field: string]: unknown; barcodes?: AnylineBarcode[]; }</code> | A barcode scan's barcodes.                                                                |
| **`results`**       | <code>AnylineScanResult[]</code>                                        | A composite scan's results, one per child scan plugin, in the order the SDK reports them. |


#### AnylineBarcode

One barcode of a barcode scan.

| Prop              | Type                  | Description                                     |
| ----------------- | --------------------- | ----------------------------------------------- |
| **`value`**       | <code>string</code>   | The decoded content.                            |
| **`format`**      | <code>string</code>   | The symbology, such as `CODE_128` or `QR_CODE`. |
| **`base64value`** | <code>string</code>   | The content as base64, for binary content.      |
| **`coordinates`** | <code>number[]</code> | The corners of the barcode in the image.        |


#### ScanOptions

The options of `scan`.

| Prop         | Type                                                                    | Description           |
| ------------ | ----------------------------------------------------------------------- | --------------------- |
| **`config`** | <code><a href="#anylinescanviewconfig">AnylineScanViewConfig</a></code> | The scan view config. |


### Type Aliases


#### AnylineScanViewConfig

An Anyline scan view config: the JSON io-anyline-cordova's `scan` took, with `cameraConfig`, `flashConfig`,
`viewPluginConfig` (or `viewPluginCompositeConfig`) and `options`. It is handed to the SDK as it is, so every field
the SDK documents for its wrappers can be used.

<code><a href="#record">Record</a>&lt;string, unknown&gt;</code>


#### Record

Construct a type with a set of properties K of type T

<code>{
 [P in K]: T;
 }</code>

</docgen-api>
