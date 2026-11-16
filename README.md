# capacitor-anyline

The [Anyline](https://anyline.com) scanner SDK for Capacitor: initialise it with a licence key, then open the scanner
with an Anyline scan view config and get the result back as JSON. Android and iOS.

It replaces `io-anyline-cordova` in a Capacitor app, with the same two calls the Cordova plugin's `window.Anyline` had,
`initAnylineSDK` and `scan`, as promises. The scan view config is the same JSON, and a scan resolves with the same result
JSON. It drives the SDK through Anyline's wrapper session, the SDK's own interface for wrapper frameworks, and lets the
SDK present its own full-screen scanner. The newer "Infinity" API of the Cordova plugin is not included.

| Platform | SDK                                                                                            |
| -------- | ---------------------------------------------------------------------------------------------- |
| Android  | `io.anyline:anylinesdk` 56.7.0, from `https://mobile-sdk-android.anyline.io/release/`          |
| iOS      | `https://github.com/Anyline/anyline-ocr-spm-module.git` from 56.6.0 (product `AnylinePackage`) |

## Why this plugin exists

This is an independent wrapper, not Anyline's. Anyline publishes the SDK and its own wrappers for Cordova and React
Native; this plugin is written from the SDK's public documentation and talks to the SDK the same way those wrappers do.
It is maintained separately so that a fix in the wrapper layer (how a Capacitor app loads assets, how initialisation is
serialised, what happens on a device the SDK cannot run on, how much of the SDK a build carries) ships as soon as it is
written, instead of waiting for a change to be accepted and released upstream. Anything that is a defect in the SDK
itself is still reported to Anyline.

The SDK is not part of this repository. It is fetched from Anyline at build time and is used under Anyline's own licence
terms, which require a licence key for the app.

## Install

```bash
npm i capacitor-anyline
npx cap sync
```

`cap sync` names the plugin from the package name: the Swift package and product `CapacitorAnyline` (and the
pod of the same name), and the Gradle project `:capacitor-anyline`.

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
import { Anyline, isScanCanceled } from 'capacitor-anyline';

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
apply from: project(':capacitor-anyline').file('anyline-assets.gradle')
```

Every other module is then left out of the APK through the app's `androidResources.ignoreAssetsPatterns`. The app module
has to apply it because the assets come from the SDK's AAR, and only the module that packages the APK can filter them.
A name that is not one of the modules fails the build, and so does a pattern that is set while no app module applies the
file.

### iOS

Add a build setting `ANYLINE_RETAIN_ASSETS_PATTERN` to the app target, with the modules to keep separated by spaces
(`module_barcode`), and a Run Script build phase **after** the frameworks are embedded:

```bash
"${SRCROOT}/../../node_modules/capacitor-anyline/scripts/ios/remove-unneeded-assets.sh"
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

## Robustness

- **One initialisation at a time.** The SDK's initialisation must not run twice at once, so the plugin never asks for a
  second one while the first is being answered. A call to `initAnylineSDK` made meanwhile with the same key settles
  together with the running one; a call with a different key waits for it and then runs. Once the SDK has accepted a
  key, calling `initAnylineSDK` again with that key resolves at once without initialising again; after a refusal, the
  next call initialises again.
- **One queue into the SDK on iOS.** Every call the plugin makes into the SDK runs on a single serial queue the plugin
  owns, so no two of them overlap, whatever thread a call or an SDK callback arrives on. The plugin draws no UI of its
  own (the SDK presents and dismisses its scanner), and it keeps a waiting call only until it settles, holding nothing
  of the SDK's scanner or its results after that.
- **Devices the SDK cannot run on (Android).** The SDK ships native code for ARM only (`arm64-v8a`, `armeabi-v7a`).
  Before its first use, the plugin checks that the device reports one of those ABIs and that the SDK's native library
  loads into the app's process; when either fails, `initAnylineSDK` and `scan` reject with `UNSUPPORTED_DEVICE` instead
  of the scanner crashing the app. Anything else the SDK throws while the plugin calls it, a failed native link
  included, rejects the call. The SDK's session is set up on the first call, so an app that never scans never loads the
  SDK's native code.

### Memory

`getMemoryInfo()` reads the app process's memory as the system reports it: on iOS what the process can still allocate
and its physical footprint, on Android the memory available to the system and the process's proportional set size. It
is cheap and carries nothing personal, so an app can record it before `initAnylineSDK`, after it and after `scan`, and
tell a termination by the system apart from a crash.

## Differences from io-anyline-cordova

- `Anyline.initAnylineSDK({ licenseKey })` and `Anyline.scan({ config })` take one options object and return promises,
  instead of `window.Anyline.initAnylineSDK(licenseKey, onSuccess, onFailure)` and `scan(config, onSuccess, onFailure)`.
- A rejection is an error with a `code` (`CANCELED`, `SCAN_FAILED`, `INIT_FAILED`, `INVALID_CONFIG`,
  `UNSUPPORTED_DEVICE`) and a message,
  not a bare string. The message is the string the Cordova plugin rejected with: `Canceled` for a cancel, the SDK's
  error for a failed scan, the SDK's initialisation response as JSON for a refused key. `isScanCanceled(error)` takes
  both forms.
- `initAnylineSDK` resolves with nothing (the Cordova plugin resolved with a message on Android and nothing on iOS).
- A composite scan resolves with `{ results: [...] }`; the Cordova plugin resolved with the array itself.
- The result callbacks named in a scan's callback config (`onResultEventName`) are not supported: the result is what
  `scan` resolves with.
- A second `initAnylineSDK` while one runs joins it or waits for it, instead of starting a second initialisation.
- Only `initAnylineSDK`, `scan` and `getMemoryInfo`: no `checkLicense`, `getSDKVersion`, `exportCachedEvents`, `tryStopScan` or Infinity
  API.
- Android: the asset prefix is `public/assets` instead of `www/assets`, and the module filter is the
  `anylineRetainAssetsPattern` Gradle property instead of the `anyline-retain-assets-pattern` preference in
  `config.xml`.

## Tests

- `npm test` runs the TypeScript unit tests, the parity check (both platforms implement exactly the API, with the same
  error codes and messages, version and module lists) and the iOS build-phase script against a fake app bundle.
- `npm run test:android` runs the JVM tests of the Kotlin (the plugin's name and methods, the result mapping, the
  waiting calls, the one-at-a-time initialisation, the device check), the check of the Android asset pattern, and the
  check that the ABIs the plugin accepts are the ones the SDK's AAR ships native code for.
- `npm run test:ios` also covers the one-at-a-time initialisation, including concurrent callers.
- `npm run test:ios` runs the Swift core's tests on a Mac (`swift test`). The Anyline SDK has no macOS or arm64
  simulator slice, so the plugin layer is built for a device (`npm run verify:ios`) rather than tested on a simulator.

A scan itself needs a camera, a licence key and a phone, so it is not covered by these tests.

## API

<docgen-index>

* [`initAnylineSDK(...)`](#initanylinesdk)
* [`scan(...)`](#scan)
* [`getMemoryInfo()`](#getmemoryinfo)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### initAnylineSDK(...)

```typescript
initAnylineSDK(options: InitAnylineSDKOptions) => Promise<void>
```

Initialises the Anyline SDK with a licence key. Call it once before the first scan; the SDK keeps the licence for
the life of the app process.

Resolves once the SDK is ready to scan. Rejects with `INIT_FAILED` when the SDK refuses the key, with
`INVALID_CONFIG` when `licenseKey` is missing, and with `UNSUPPORTED_DEVICE` when the SDK cannot run on the device.

One initialisation runs at a time. A call made while one with the same key runs settles with it; a call with
another key waits for it to settle, then runs. A call with the key the SDK was last initialised with resolves at
once, without initialising again.

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
`INVALID_CONFIG` when `config` is missing or the SDK cannot read it, and with `UNSUPPORTED_DEVICE` when the SDK
cannot run on the device. A scan started while another is open replaces it: the earlier call rejects with
`SCAN_FAILED`.

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#scanoptions">ScanOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#anylinescanresult">AnylineScanResult</a>&gt;</code>

**Since:** 0.1.0

--------------------


### getMemoryInfo()

```typescript
getMemoryInfo() => Promise<MemoryInfo>
```

Reads the app process's memory, cheaply and with nothing personal in it, for an app to record next to a scan
(before `initAnylineSDK`, after it, after `scan`), so a termination by the system can be told apart from a crash.

**Returns:** <code>Promise&lt;<a href="#memoryinfo">MemoryInfo</a>&gt;</code>

**Since:** 0.2.0

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


#### AnylineScanViewConfig

An Anyline scan view config: the JSON io-anyline-cordova's `scan` took, with `cameraConfig`, `flashConfig`,
`viewPluginConfig` (or `viewPluginCompositeConfig`) and `options`. It is handed to the SDK as it is, so every field
the SDK documents for its wrappers can be used.


#### MemoryInfo

The app process's memory, as the system reports it. A field the system does not report is left out.

| Prop                 | Type                | Description                                                                                                                                                                                                                                          |
| -------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`availableBytes`** | <code>number</code> | Bytes still available. iOS: what the process can allocate before the system terminates it (`os_proc_available_memory`). Android: the memory available to the whole system (`ActivityManager.<a href="#memoryinfo">MemoryInfo</a>.availMem`).         |
| **`footprintBytes`** | <code>number</code> | Bytes the process uses. iOS: its physical footprint (`phys_footprint`), the figure the system's limit is measured against. Android: its proportional set size (`Debug.getPss()`), which counts the Java and native heaps, graphics and mapped files. |

</docgen-api>
