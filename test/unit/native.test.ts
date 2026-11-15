import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';

import type { AnylinePlugin } from '../../src';

// What the native side of Capacitor injects into the web view: a bridge object that makes the platform 'android', the
// headers of the installed plugins, and nativePromise, which every promise method of a plugin goes through.
const methods = ['getMemoryInfo', 'initAnylineSDK', 'scan'];
const nativePromise = vi.fn<(...args: unknown[]) => Promise<unknown>>(async () => undefined);
const g = globalThis as Record<string, unknown>;

let Anyline: AnylinePlugin;

beforeAll(async () => {
  g.androidBridge = {};
  g.Capacitor = {
    PluginHeaders: [{ name: 'Anyline', methods: methods.map((name) => ({ name, rtype: 'promise' })) }],
    nativePromise,
  };
  ({ Anyline } = await import('../../src'));
});

afterAll(() => {
  delete g.androidBridge;
  delete g.Capacitor;
});

const config = {
  viewPluginConfig: {
    pluginConfig: { id: 'barcode', barcodeConfig: { barcodeFormats: ['ALL'] }, cancelOnResult: true },
  },
};

describe('on a native platform', () => {
  it('sends the licence key to the native plugin named Anyline', async () => {
    await Anyline.initAnylineSDK({ licenseKey: 'key', enableOfflineCache: true });
    expect(nativePromise).toHaveBeenLastCalledWith('Anyline', 'initAnylineSDK', {
      licenseKey: 'key',
      enableOfflineCache: true,
    });
  });

  it('sends the scan view config as it is, and resolves with what the native side resolves with', async () => {
    const result = { barcodeResult: { barcodes: [{ value: '372123456789012', format: 'CODE_128' }] } };
    nativePromise.mockResolvedValueOnce(result);
    await expect(Anyline.scan({ config })).resolves.toEqual(result);
    expect(nativePromise).toHaveBeenLastCalledWith('Anyline', 'scan', { config });
  });

  it('passes on a cancel with its code and the message io-anyline-cordova used', async () => {
    nativePromise.mockRejectedValueOnce(Object.assign(new Error('Canceled'), { code: 'CANCELED' }));
    await expect(Anyline.scan({ config })).rejects.toMatchObject({ code: 'CANCELED', message: 'Canceled' });
  });

  it('resolves getMemoryInfo with what the native side reads', async () => {
    nativePromise.mockResolvedValueOnce({ availableBytes: 1024, footprintBytes: 2048 });
    await expect(Anyline.getMemoryInfo()).resolves.toEqual({ availableBytes: 1024, footprintBytes: 2048 });
    expect(nativePromise.mock.lastCall?.slice(0, 2)).toEqual(['Anyline', 'getMemoryInfo']);
  });

  it('passes on an unsupported device with its code', async () => {
    nativePromise.mockRejectedValueOnce(Object.assign(new Error('unsupported'), { code: 'UNSUPPORTED_DEVICE' }));
    await expect(Anyline.initAnylineSDK({ licenseKey: 'key' })).rejects.toMatchObject({ code: 'UNSUPPORTED_DEVICE' });
  });
});
