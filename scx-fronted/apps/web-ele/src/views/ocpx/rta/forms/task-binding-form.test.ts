import type { RtaRoute } from '#/api/ocpx/rta';

import { flushPromises, mount } from '@vue/test-utils';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import TaskBindingForm from './task-binding-form.vue';

const api = vi.hoisted(() => ({
  deleteRta: vi.fn(),
  queryRta: vi.fn(),
  rtaOptions: vi.fn(),
  rtaProtocols: vi.fn(),
  rtaReadiness: vi.fn(),
  saveRta: vi.fn(),
}));
vi.mock('#/api/ocpx/rta', () => api);
vi.mock('@boot/access', () => ({
  useAccess: () => ({ hasAccessByCodes: () => true }),
}));
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}));
vi.mock('../test-dialog.vue', () => ({ default: { render: () => null } }));

function route(overrides: Partial<RtaRoute> = {}): RtaRoute {
  return {
    advertiserId: 'adv-1',
    advertiserName: '广告主',
    allowCount24h: 0,
    binding: { label: '未绑定', state: 'MISSING' },
    checks: [
      {
        code: 'MEDIA_ACCESS_MISSING',
        fixTarget: 'media',
        hint: '先为媒体开通 RTA',
        label: '媒体未开通 RTA',
        level: 'BLOCK',
      },
    ],
    media: { label: '未开通', state: 'MISSING' },
    mediaId: 'media-1',
    mediaName: '媒体',
    monitorTaskId: 'task-1',
    requestCount24h: 0,
    status: 'NOT_CONFIGURED',
    monitorName: '任务',
    upstream: { label: '未选择', state: 'MISSING' },
    ...overrides,
  };
}

describe('task binding form', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.rtaProtocols.mockResolvedValue([]);
    api.queryRta.mockResolvedValue({
      current: 1,
      records: [],
      size: 1,
      total: 0,
    });
  });

  it('guides the user to configure missing segments first', async () => {
    api.rtaReadiness.mockResolvedValue({ notices: [], route: route() });
    api.rtaOptions.mockResolvedValue([]);
    const wrapper = mount(TaskBindingForm, {
      global: { config: { warnHandler: () => {} } },
      props: { taskId: 'task-1' },
    });
    await flushPromises();
    const html = wrapper.html();
    expect(api.rtaOptions).toHaveBeenCalledWith('upstreams', {
      advertiserId: 'adv-1',
    });
    expect(html).toContain('该广告主还没有 RTA 接入');
    expect(html).toContain('去配置');
    expect(html).toContain('媒体未开通 RTA');
    expect(html).toContain('修复');
  });

  it('emits the segment to open when a fix is requested', async () => {
    api.rtaReadiness.mockResolvedValue({ notices: [], route: route() });
    api.rtaOptions.mockResolvedValue([{ id: 'u1', name: '上游' }]);
    const wrapper = mount(TaskBindingForm, {
      global: { config: { warnHandler: () => {} } },
      props: { taskId: 'task-1' },
    });
    await flushPromises();
    const fix = wrapper
      .findAll('el-button')
      .find((button) => button.text() === '修复');
    expect(fix).toBeDefined();
    await fix?.trigger('click');
    expect(wrapper.emitted('open')?.[0]).toEqual([
      { id: 'media-1', target: 'media' },
    ]);
  });
});
