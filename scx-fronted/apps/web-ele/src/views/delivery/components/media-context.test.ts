import { describe, expect, it } from 'vitest';

import { resolveMediaId, withMediaContext } from './media-context';

describe('media context', () => {
  const media = [{ id: 'm1' }, { id: 'm2' }];

  it('requires an explicit selection when multiple media are accessible', () => {
    expect(resolveMediaId(media, undefined)).toBe('');
  });

  it('restores a valid media from the URL and ignores an invalid one', () => {
    expect(resolveMediaId(media, 'm2')).toBe('m2');
    expect(resolveMediaId(media, 'missing')).toBe('');
  });

  it('selects the only accessible media and preserves other query context', () => {
    expect(resolveMediaId([{ id: 'm1' }], undefined)).toBe('m1');
    expect(withMediaContext({ tab: 'products' }, 'm1')).toEqual({
      mediaId: 'm1',
      tab: 'products',
    });
  });
});
