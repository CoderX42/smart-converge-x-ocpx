import { describe, expect, it } from 'vitest';

import dashboardRoutes from '../routes/modules/dashboard';

describe('analytics compatibility route', () => {
  it('redirects the legacy analytics URL to the OCPX dashboard', () => {
    expect(dashboardRoutes).toContainEqual(
      expect.objectContaining({
        path: '/analytics',
        redirect: '/ocpx/dashboard',
      }),
    );
  });
});
