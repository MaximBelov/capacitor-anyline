import { CapacitorException } from '@capacitor/core';
import { describe, expect, it } from 'vitest';

import { isScanCanceled, SCAN_CANCELED_MESSAGE } from '../../src';

describe('isScanCanceled', () => {
  it('is the message io-anyline-cordova rejected a cancel with', () => {
    expect(SCAN_CANCELED_MESSAGE).toBe('Canceled');
  });

  it('takes a rejection with the CANCELED code', () => {
    expect(isScanCanceled(new CapacitorException('Canceled', 'CANCELED' as never))).toBe(true);
    expect(isScanCanceled({ code: 'CANCELED' })).toBe(true);
  });

  it("takes the bare 'Canceled' string of io-anyline-cordova", () => {
    expect(isScanCanceled('Canceled')).toBe(true);
  });

  it('refuses every other rejection', () => {
    expect(isScanCanceled({ code: 'SCAN_FAILED', message: 'Canceled' })).toBe(false);
    expect(isScanCanceled(new Error('Canceled'))).toBe(false);
    expect(isScanCanceled('canceled')).toBe(false);
    expect(isScanCanceled(null)).toBe(false);
    expect(isScanCanceled(undefined)).toBe(false);
    expect(isScanCanceled(7)).toBe(false);
  });
});
