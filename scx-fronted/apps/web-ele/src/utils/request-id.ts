type ClientCrypto = {
  getRandomValues?: (array: Uint8Array) => Uint8Array;
  randomUUID?: () => string;
};

let fallbackSequence = 0;

/**
 * Creates a browser-compatible request ID for idempotency and local row keys.
 * The value is not an authentication credential or cryptographic secret.
 */
export function createClientRequestId(cryptoApi?: ClientCrypto | null) {
  const source = cryptoApi === undefined ? globalThis.crypto : cryptoApi;
  if (typeof source?.randomUUID === 'function') {
    try {
      const value = source.randomUUID().replaceAll('-', '');
      if (/^[\w-]{16,64}$/.test(value)) return value;
    } catch {
      // Continue with getRandomValues in restricted browser/WebView contexts.
    }
  }
  if (typeof source?.getRandomValues === 'function') {
    try {
      const bytes = new Uint8Array(16);
      source.getRandomValues(bytes);
      return Array.from(bytes, (value) =>
        value.toString(16).padStart(2, '0'),
      ).join('');
    } catch {
      // Continue with a non-security fallback when Web Crypto is unavailable.
    }
  }

  fallbackSequence = (fallbackSequence + 1) % 36 ** 4;
  const timestamp = Date.now().toString(36).padStart(10, '0');
  const sequence = fallbackSequence.toString(36).padStart(4, '0');
  const random = Math.random()
    .toString(36)
    .slice(2)
    .padEnd(16, '0')
    .slice(0, 16);
  return `${timestamp}${sequence}${random}`;
}
