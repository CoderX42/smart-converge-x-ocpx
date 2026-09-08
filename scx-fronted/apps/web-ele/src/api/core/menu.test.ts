import { describe, expect, it, vi } from 'vitest';

import { getAllMenusApi } from './menu';

const client = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('#/api/request', () => ({ requestClient: client }));
describe('rTA menu permissions', () => {
  it('hides a legacy parent without any permitted children', async () => {
    client.get.mockResolvedValue([
      { path: '/ocpx/rta', children: [] },
      { path: '/ocpx/media' },
    ]);
    expect(await getAllMenusApi()).toEqual([{ path: '/ocpx/media' }]);
  });
  it('selects the first child returned for the signed-in role', async () => {
    client.get.mockResolvedValue([
      {
        path: '/ocpx/rta',
        children: [{ path: '/ocpx/rta/records' }, { path: '/ocpx/rta/logs' }],
      },
    ]);
    const menus = await getAllMenusApi();
    expect(menus[0].redirect).toBe('/ocpx/rta/records');
  });
});
