import { describe, expect, it } from 'vitest';

import { legacyWorkspaceTargets } from './legacy-delivery';

describe('legacy workspace routes', () => {
  it.each([
    ['/commercial/advertisers', '/commercial/entities', 'advertisers'],
    ['/delivery/documents', '/inventory/channel-access', 'documents'],
    ['/inventory/placements', '/inventory/supply', 'placements'],
    ['/campaign/reviews', '/campaign/plans', 'review'],
  ])('maps %s to the expected workspace tab', (source, path, tab) => {
    expect(legacyWorkspaceTargets[source]).toEqual({ path, tab });
  });
});
