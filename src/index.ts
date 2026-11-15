import { registerPlugin } from '@capacitor/core';

import type { AnylinePlugin } from './definitions';

const Anyline = registerPlugin<AnylinePlugin>('Anyline', {
  web: () => import('./web').then((m) => new m.AnylineWeb()),
});

export * from './definitions';
export { isScanCanceled, SCAN_CANCELED_MESSAGE } from './errors';
export { Anyline };
