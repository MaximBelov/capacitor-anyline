import { CapacitorException, ExceptionCode } from '@capacitor/core';
import { describe, expect, it } from 'vitest';

import { Anyline } from '../../src';
import { AnylineWeb } from '../../src/web';

// No native bridge on globalThis, so Capacitor reports the platform as 'web' and loads src/web.ts.
describe('on the web', () => {
  it('rejects every call with UNAVAILABLE through the registered plugin', async () => {
    await expect(Anyline.initAnylineSDK({ licenseKey: 'key' })).rejects.toMatchObject({
      code: ExceptionCode.Unavailable,
    });
    await expect(Anyline.scan({ config: {} })).rejects.toMatchObject({ code: ExceptionCode.Unavailable });
  });

  it('rejects with a CapacitorException for each method', async () => {
    const web = new AnylineWeb();
    for (const call of [web.initAnylineSDK(), web.scan()]) {
      expect(await call.catch((e: unknown) => e)).toBeInstanceOf(CapacitorException);
    }
  });
});
