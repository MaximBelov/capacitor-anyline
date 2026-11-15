import { WebPlugin } from '@capacitor/core';

import type { AnylinePlugin, AnylineScanResult, MemoryInfo } from './definitions';

/** The Anyline SDK has no web version: every call rejects with Capacitor's `UNAVAILABLE`. */
export class AnylineWeb extends WebPlugin implements AnylinePlugin {
  async initAnylineSDK(): Promise<void> {
    throw this.unavailable('Anyline is not available on the web.');
  }

  async scan(): Promise<AnylineScanResult> {
    throw this.unavailable('Anyline is not available on the web.');
  }

  async getMemoryInfo(): Promise<MemoryInfo> {
    throw this.unavailable('Anyline is not available on the web.');
  }
}
