import { Anyline, isScanCanceled } from 'capacitor-anyline';

// Every call is written into #log and kept in window.anylineLog, so a person can read it and a script can poll it.
window.anylineLog = [];
const log = (line) => {
  window.anylineLog.push(line);
  document.getElementById('log').textContent = window.anylineLog.join('\n');
};
const run = (name, action) => async () => {
  try {
    log(`${name}: ${JSON.stringify((await action()) ?? 'ok')}`);
  } catch (error) {
    const kind = isScanCanceled(error) ? 'CANCELED' : 'FAIL';
    log(`${name}: ${kind} ${error.code ?? ''} ${error.message ?? error}`);
  }
};

// A barcode scan view, as in Anyline's documentation for its wrappers: one barcode plugin that closes the scanner on its
// first result. It can be edited on the page before a scan.
const DEFAULT_SCAN_CONFIG = {
  cameraConfig: { captureResolution: '1080p' },
  flashConfig: { mode: 'auto', alignment: 'top' },
  viewPluginConfig: {
    pluginConfig: { id: 'barcode', barcodeConfig: { barcodeFormats: ['ALL'] }, cancelOnResult: true },
    cutoutConfig: { maxWidthPercent: '80%', alignment: 'top_half', ratioFromSize: { width: 3, height: 1 } },
  },
  options: { doneButtonConfig: { title: 'Cancel' } },
};
document.getElementById('config').value = JSON.stringify(DEFAULT_SCAN_CONFIG, null, 2);

const click = (id, handler) => document.getElementById(id).addEventListener('click', handler);
click(
  'init',
  run('initAnylineSDK', () =>
    Anyline.initAnylineSDK({
      licenseKey: document.getElementById('licence').value.trim(),
      enableOfflineCache: document.getElementById('offline').checked,
    }),
  ),
);
// The plugin does not ask for the camera: an app does that before a scan. The web view's own request is enough here,
// because Capacitor turns it into the system's camera permission prompt.
click(
  'camera',
  run('camera', async () => {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true });
    stream.getTracks().forEach((track) => track.stop());
    return 'granted';
  }),
);
click(
  'scan',
  run('scan', async () => {
    const result = await Anyline.scan({ config: JSON.parse(document.getElementById('config').value) });
    return result.barcodeResult?.barcodes?.[0]?.value ?? result;
  }),
);
click(
  'memory',
  run('getMemoryInfo', () => Anyline.getMemoryInfo()),
);
