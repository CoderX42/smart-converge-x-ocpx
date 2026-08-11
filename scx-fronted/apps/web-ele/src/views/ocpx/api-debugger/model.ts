import type { ApiDebugRequest, ApiDebugTarget } from '#/api/ocpx';

const sensitiveKey =
  /authorization|cookie|password|passwd|secret|token|signature|x-sign|appsecret|access[_-]?key|phone|mobile|id[_-]?card|real[_-]?name/i;

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
