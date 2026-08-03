import { describe, expect, it } from 'vitest';

import dashboardRoutes from '../routes/modules/dashboard';

describe('analytics compatibility route', () => {
  it('redirects the legacy analytics URL to the ADX dashboard', () => {
    expect(dashboardRoutes).toContainEqual(
      expect.objectContaining({
        path: '/analytics',
        redirect: '/adx/dashboard',
      }),
    );
  });
});
