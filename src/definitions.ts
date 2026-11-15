/** The options of `initAnylineSDK`. */
export interface InitAnylineSDKOptions {
  /** The Anyline licence key for this app's bundle identifier (iOS) or application id (Android). */
  licenseKey: string;
  /**
   * Lets the SDK cache the licence check, so a device that is offline at the next start can still scan. Off by
   * default, as in io-anyline-cordova.
   */
  enableOfflineCache?: boolean;
}

/**
 * An Anyline scan view config: the JSON io-anyline-cordova's `scan` took, with `cameraConfig`, `flashConfig`,
 * `viewPluginConfig` (or `viewPluginCompositeConfig`) and `options`. It is handed to the SDK as it is, so every field
 * the SDK documents for its wrappers can be used.
 */
// An index type rather than Record<string, unknown>: docgen prints a Record's expansion with whitespace that differs
// between macOS and Linux, so the README check in CI would fail on a README built on a Mac.
export interface AnylineScanViewConfig {
  [key: string]: unknown;
}

/** The options of `scan`. */
export interface ScanOptions {
  /** The scan view config. */
  config: AnylineScanViewConfig;
}

/** One barcode of a barcode scan. */
export interface AnylineBarcode {
  /** The decoded content. */
  value: string;
  /** The symbology, such as `CODE_128` or `QR_CODE`. */
  format?: string;
  /** The content as base64, for binary content. */
  base64value?: string;
  /** The corners of the barcode in the image. */
  coordinates?: number[];
  [field: string]: unknown;
}

/**
 * What a scan resolves with: the scan result JSON io-anyline-cordova returned, as the SDK's wrapper session writes it.
 * Its fields depend on the scan plugin: a barcode scan has `barcodeResult.barcodes`, a meter scan `meterResult`, and so
 * on, next to `pluginID`, `confidence`, `imagePath` and `fullImagePath` (the files of the cutout and the full frame).
 *
 * A composite scan (`viewPluginCompositeConfig`) produces one result per child plugin. io-anyline-cordova returned
 * those as an array; a Capacitor call resolves with an object, so they arrive as `results`.
 */
export interface AnylineScanResult {
  /** A barcode scan's barcodes. */
  barcodeResult?: { barcodes?: AnylineBarcode[]; [field: string]: unknown };
  /** A composite scan's results, one per child scan plugin, in the order the SDK reports them. */
  results?: AnylineScanResult[];
  [field: string]: unknown;
}

/**
 * What a call rejects with, in the error's `code`:
 *
 * - `CANCELED`: the user closed the scanner without a result. The message is `Canceled`, the string
 *   io-anyline-cordova rejected with.
 * - `SCAN_FAILED`: the SDK could not open or run the scanner; the message is the SDK's.
 * - `INIT_FAILED`: the SDK refused to initialise, usually over the licence key. The message is the SDK's
 *   initialisation response as JSON, as io-anyline-cordova reported it.
 * - `INVALID_CONFIG`: a required option is missing or is not of the right type, or the SDK could not read the scan
 *   view config.
 * - `UNSUPPORTED_DEVICE`: the SDK cannot run on this device, because its native code is not available for the CPU the
 *   app runs on (Android: the SDK ships ARM code only). Raised by `initAnylineSDK` and `scan`, instead of the scanner
 *   crashing the app. Never raised on iOS.
 */
export type AnylineErrorCode = 'CANCELED' | 'SCAN_FAILED' | 'INIT_FAILED' | 'INVALID_CONFIG' | 'UNSUPPORTED_DEVICE';

/**
 * The app process's memory, as the system reports it. A field the system does not report is left out.
 */
export interface MemoryInfo {
  /**
   * Bytes still available. iOS: what the process can allocate before the system terminates it
   * (`os_proc_available_memory`). Android: the memory available to the whole system (`ActivityManager.MemoryInfo.availMem`).
   */
  availableBytes?: number;
  /**
   * Bytes the process uses. iOS: its physical footprint (`phys_footprint`), the figure the system's limit is measured
   * against. Android: its proportional set size (`Debug.getPss()`), which counts the Java and native heaps, graphics
   * and mapped files.
   */
  footprintBytes?: number;
}

export interface AnylinePlugin {
  /**
   * Initialises the Anyline SDK with a licence key. Call it once before the first scan; the SDK keeps the licence for
   * the life of the app process.
   *
   * Resolves once the SDK is ready to scan. Rejects with `INIT_FAILED` when the SDK refuses the key, with
   * `INVALID_CONFIG` when `licenseKey` is missing, and with `UNSUPPORTED_DEVICE` when the SDK cannot run on the device.
   *
   * One initialisation runs at a time. A call made while one with the same key runs settles with it; a call with
   * another key waits for it to settle, then runs. A call with the key the SDK was last initialised with resolves at
   * once, without initialising again.
   *
   * @since 0.1.0
   */
  initAnylineSDK(options: InitAnylineSDKOptions): Promise<void>;

  /**
   * Opens the scanner full screen with a scan view config, and resolves with the first result once the scanner has
   * closed on it (the config's `cancelOnResult: true`).
   *
   * Rejects with `CANCELED` (message `Canceled`) when the user closes the scanner (the Done button on iOS, Back on
   * Android), with `SCAN_FAILED` when the SDK cannot scan (for example before `initAnylineSDK` succeeded), and with
   * `INVALID_CONFIG` when `config` is missing or the SDK cannot read it, and with `UNSUPPORTED_DEVICE` when the SDK
   * cannot run on the device. A scan started while another is open replaces it: the earlier call rejects with
   * `SCAN_FAILED`.
   *
   * @since 0.1.0
   */
  scan(options: ScanOptions): Promise<AnylineScanResult>;

  /**
   * Reads the app process's memory, cheaply and with nothing personal in it, for an app to record next to a scan
   * (before `initAnylineSDK`, after it, after `scan`), so a termination by the system can be told apart from a crash.
   *
   * @since 0.2.0
   */
  getMemoryInfo(): Promise<MemoryInfo>;
}
