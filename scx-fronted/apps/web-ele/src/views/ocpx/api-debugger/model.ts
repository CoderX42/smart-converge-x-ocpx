import type { ApiDebugRequest, ApiDebugTarget } from '#/api/ocpx';

const sensitiveKey =
  /authorization|cookie|password|passwd|secret|token|signature|x-sign|appsecret|access[_-]?key|phone|mobile|id[_-]?card|real[_-]?name/i;

type ClientCrypto = {
  getRandomValues?: (array: Uint8Array) => Uint8Array;
  randomUUID?: () => string;
};

/**
 * Generates a client-side trace value without assuming randomUUID is available.
 * These values are business debug samples, never authentication credentials.
 */
export function createDebugClientId(cryptoApi?: ClientCrypto | null) {
  const source = cryptoApi === undefined ? globalThis.crypto : cryptoApi;
  if (typeof source?.randomUUID === 'function') {
    try {
      return source.randomUUID();
    } catch {
      // Continue with getRandomValues for restricted browser/WebView contexts.
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
      // The final fallback is sufficient for a non-security debug sample ID.
    }
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export function serializeRequestBody(
  bodyType: ApiDebugRequest['bodyType'],
  text: string,
  form: Record<string, string>,
) {
  if (bodyType === 'NONE') return '';
  if (bodyType === 'FORM_DATA' || bodyType === 'FORM_URLENCODED') {
    return JSON.stringify(form);
  }
  return text;
}

export function formatJsonText(value: string) {
  return JSON.stringify(JSON.parse(value), null, 2);
}

export function formatResponseText(value?: string) {
  if (!value) return '';
  try {
    return formatJsonText(value);
  } catch {
    return value;
  }
}

export function maskSensitiveText(value?: string) {
  if (!value) return '';
  try {
    const mask = (item: unknown): unknown => {
      if (Array.isArray(item)) return item.map((value) => mask(value));
      if (item && typeof item === 'object') {
        return Object.fromEntries(
          Object.entries(item).map(([key, field]) => [
            key,
            sensitiveKey.test(key) ? '[MASKED]' : mask(field),
          ]),
        );
      }
      return item;
    };
    return JSON.stringify(mask(JSON.parse(value)), null, 2);
  } catch {
    return value
      .replaceAll(/Bearer\s+[^,;\s]+/gi, 'Bearer [MASKED]')
      .replaceAll(
        /(authorization|cookie|password|passwd|secret|token|signature|x-sign|phone|mobile)(\s*[:=]\s*)([^,;\s]+)/gi,
        '$1$2[MASKED]',
      );
  }
}

export function resolveIntegratedTarget(
  target: ApiDebugTarget,
  scenario: 'DOWNSTREAM_TO_UPSTREAM' | 'UPSTREAM_TO_DOWNSTREAM',
) {
  return scenario === 'DOWNSTREAM_TO_UPSTREAM'
    ? {
        bodyType: 'JSON' as const,
        method: 'POST' as const,
        url: target.upstreamTargetHost ?? '',
      }
    : {
        bodyType: 'JSON' as const,
        method: 'GET' as const,
        url: target.downstreamTargetHost ?? '',
      };
}
