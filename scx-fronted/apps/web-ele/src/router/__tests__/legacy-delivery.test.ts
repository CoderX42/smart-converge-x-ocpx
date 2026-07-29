import type { RouteLocationNormalized } from 'vue-router';

import { describe, expect, it } from 'vitest';

import {
  legacyCustomerRedirect,
  legacyMasterDataRedirect,
} from '../routes/modules/legacy-delivery';

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

describe('legacy customer route', () => {
  it('redirects to partners while preserving query and hash', () => {
    const redirect = legacyCustomerRedirect.redirect;
    if (typeof redirect !== 'function') {
      throw new TypeError('Expected a redirect function');
    }
    const location = {
      hash: '#history',
      query: { mediaId: 'media-1', source: 'bookmark' },
    } as unknown as RouteLocationNormalized;
    const target = redirect(location, location);

    expect(target).toEqual({
      hash: '#history',
      path: '/delivery/partners',
      query: { mediaId: 'media-1', source: 'bookmark' },
    });
  });
});
