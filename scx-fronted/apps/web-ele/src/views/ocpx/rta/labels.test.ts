import { describe, expect, it } from 'vitest';

import {
  decisionStatus,
  deliveryStatus,
  formatRate,
  reasonText,
  routeStatus,
  tagOf,
} from './labels';

describe('rTA labels', () => {
  it('maps raw enums to Chinese text and tag colors', () => {
    expect(tagOf(decisionStatus, 'ALLOW')).toEqual({
      text: '放行',
      type: 'success',
    });
    expect(tagOf(decisionStatus, 'TIMEOUT')).toEqual({
      text: '超时',
      type: 'warning',
    });
    expect(tagOf(deliveryStatus, 'SYNC_RETURNED').text).toBe('已同步返回');
    expect(tagOf(routeStatus, 'INCOMPLETE')).toEqual({
      text: '不完整',
      type: 'danger',
    });
  });

  it('falls back to the raw value for unknown codes', () => {
    expect(tagOf(decisionStatus, 'NEW_STATE')).toEqual({
      text: 'NEW_STATE',
      type: 'info',
    });
    expect(tagOf(decisionStatus, undefined).text).toBe('—');
  });

  it('explains reason codes and rates', () => {
    expect(reasonText('RTA_SIGNATURE_INVALID')).toBe('签名无效');
    expect(reasonText('HTTP_502')).toBe('媒体返回 502');
    expect(reasonText('')).toBe('—');
    expect(formatRate(1, 4)).toBe('25.00%');
    expect(formatRate(0, 0)).toBe('—');
  });
});
