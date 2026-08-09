import { describe, expect, it } from 'vitest';

import {
  createDebugClientId,
  formatJsonText,
  formatResponseText,
  maskSensitiveText,
  resolveIntegratedTarget,
  serializeRequestBody,
} from './model';

describe('aPI debugger editor model', () => {
  it('generates debug IDs when randomUUID is unavailable', () => {
    expect(
      createDebugClientId({ randomUUID: () => 'native-random-uuid' }),
    ).toBe('native-random-uuid');

    const generated = createDebugClientId({
      getRandomValues(array) {
        array.fill(171);
        return array;
      },
    });
    expect(generated).toBe('ab'.repeat(16));
    expect(createDebugClientId(null)).toMatch(/^[a-z0-9]+-[a-z0-9]+$/);
  });

  it('switches request body formats without leaking file bytes', () => {
    expect(serializeRequestBody('NONE', 'secret', {})).toBe('');
    expect(serializeRequestBody('TEXT', 'plain text', {})).toBe('plain text');
    expect(serializeRequestBody('FORM_DATA', '', { field: 'value' })).toBe(
      '{"field":"value"}',
    );
  });

  it('validates and formats JSON while leaving text responses intact', () => {
    expect(formatJsonText('{"ok":true}')).toBe('{\n  "ok": true\n}');
    expect(() => formatJsonText('{broken')).toThrow();
    expect(formatResponseText('{"status":200}')).toContain('\n');
    expect(formatResponseText('binary metadata only')).toBe(
      'binary metadata only',
    );
  });

  it('locks integrated method and host to the published route direction', () => {
    const target = {
      downstreamTargetHost: 'downstream.example.com',
      upstreamTargetHost: 'upstream.example.com',
    } as never;

    expect(resolveIntegratedTarget(target, 'DOWNSTREAM_TO_UPSTREAM')).toEqual({
      bodyType: 'JSON',
      method: 'POST',
      url: 'upstream.example.com',
    });
    expect(resolveIntegratedTarget(target, 'UPSTREAM_TO_DOWNSTREAM')).toEqual({
      bodyType: 'JSON',
      method: 'GET',
      url: 'downstream.example.com',
    });
  });

  it('masks stored secrets by default without changing ordinary fields', () => {
    const masked = maskSensitiveText(
      '{"Authorization":"Bearer secret-value","status":"ok","phone":"13800138000"}',
    );
    expect(masked).toContain('[MASKED]');
    expect(masked).toContain('"status": "ok"');
    expect(masked).not.toContain('secret-value');
    expect(masked).not.toContain('13800138000');
  });
});
