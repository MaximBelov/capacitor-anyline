/** The message a scan the user backs out of rejects with: the string io-anyline-cordova rejected with. */
export const SCAN_CANCELED_MESSAGE = 'Canceled';

/**
 * Whether a rejection of `scan` means the user closed the scanner, rather than a failure. It takes the `CANCELED`
 * code of this plugin, and also the bare `'Canceled'` string io-anyline-cordova rejected with, so code written for
 * either reads the same.
 */
export function isScanCanceled(error: unknown): boolean {
  if (error === SCAN_CANCELED_MESSAGE) {
    return true;
  }
  return typeof error === 'object' && error !== null && (error as { code?: unknown }).code === 'CANCELED';
}
