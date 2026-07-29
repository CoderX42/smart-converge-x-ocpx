import type { RouteLocationNormalized } from 'vue-router';

import { describe, expect, it } from 'vitest';

import { legacyMasterDataRedirect } from './legacy-delivery';

describe('legacy master-data route', () => {
  it('redirects to media scopes while preserving query and hash', () => {
    const redirect = legacyMasterDataRedirect.redirect;
    if (typeof redirect !== 'function') {
      throw new TypeError('Expected a redirect function');
    }
    const location = {
      hash: '#scope',
      query: { mediaId: 'media-1', source: 'bookmark' },
    } as unknown as RouteLocationNormalized;
    const target = redirect(location, location);

    expect(target).toEqual({
      hash: '#scope',
      path: '/delivery/media-scopes',
      query: { mediaId: 'media-1', source: 'bookmark' },
    });
  });
});
