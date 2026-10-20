import { flushPromises, shallowMount } from '@vue/test-utils';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import RtaOverview from './index.vue';

const api = vi.hoisted(() => ({ rtaOptions: vi.fn(), rtaOverview: vi.fn() }));
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock('#/api/ocpx/rta', () => api);
vi.mock('@boot/access', () => ({
  useAccess: () => ({ hasAccessByCodes: () => true }),
}));
vi.mock('@boot/common-ui', () => ({ Page: { render: () => null } }));
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { tab: 'x' } }),
  useRouter: () => router,
}));
vi.mock('../configuration-drawer.vue', () => ({
  default: { render: () => null },
}));

type View = {
  filterStatus: (status: string) => void;
  fix: (row: Record<string, string>, key: string) => void;
  load: (reset?: boolean) => Promise<void>;
  query: { onlyIssues: boolean; status: string };
};

describe('rTA overview', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.rtaOptions.mockResolvedValue([]);
    api.rtaOverview.mockResolvedValue({
      current: 1,
      notices: [],
      records: [],
      size: 30,
      summary: { disabled: 0, incomplete: 2, notConfigured: 0, ready: 1 },
      total: 0,
    });
  });

  it('filters to problem routes and by summary status', async () => {
    const wrapper = shallowMount(RtaOverview, {
      global: { config: { warnHandler: () => {} } },
    });
    await flushPromises();
    expect(api.rtaOverview).toHaveBeenLastCalledWith(
      expect.objectContaining({ onlyIssues: 'false', status: '' }),
    );
    const view = wrapper.vm as unknown as View;
    view.query.onlyIssues = true;
    await view.load(true);
    expect(api.rtaOverview).toHaveBeenLastCalledWith(
      expect.objectContaining({ current: 1, onlyIssues: 'true' }),
    );
    view.filterStatus('INCOMPLETE');
    await flushPromises();
    expect(api.rtaOverview).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'INCOMPLETE' }),
    );
    view.filterStatus('INCOMPLETE');
    await flushPromises();
    expect(view.query.status).toBe('');
  });

  it('opens the matching configuration in place', async () => {
    const wrapper = shallowMount(RtaOverview, {
      global: { config: { warnHandler: () => {} } },
    });
    await flushPromises();
    const view = wrapper.vm as unknown as View;
    const row = {
      advertiserId: 'adv-1',
      mediaId: 'media-1',
      monitorTaskId: 'task-1',
      upstreamId: 'up-1',
    };
    view.fix(row, 'upstream');
    expect(router.replace).toHaveBeenLastCalledWith({
      query: { rtaAdvertiserId: 'adv-1', rtaSelect: 'up-1', tab: 'x' },
    });
    view.fix(row, 'binding');
    expect(router.replace).toHaveBeenLastCalledWith({
      query: { rtaTaskId: 'task-1', tab: 'x' },
    });
  });
});
