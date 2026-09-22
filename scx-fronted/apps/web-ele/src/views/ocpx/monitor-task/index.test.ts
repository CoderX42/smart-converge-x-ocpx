import type { MonitorTask, MonitorTaskTestPreview } from '#/api/ocpx';

import { shallowMount } from '@vue/test-utils';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { copyLongText } from '../table';
import MonitorTaskManagement from './index.vue';

const api = vi.hoisted(() => ({
  createMonitorTask: vi.fn(),
  deleteMonitorTask: vi.fn(),
  executeMonitorTaskTestLink: vi.fn(),
  generateMonitorTaskTestLink: vi.fn(),
  getMonitorTaskIntegrationFeedback: vi.fn(),
  getMonitorTaskTestResult: vi.fn(),
  listMonitorTasks: vi.fn(),
  mediaOptions: vi.fn(),
  monitorTaskOptions: vi.fn(),
  promotionLinkAdvertisers: vi.fn(),
  promotionLinkOptions: vi.fn(),
  sendMonitorTaskIntegrationCallback: vi.fn(),
  updateMonitorTask: vi.fn(),
}));
const confirm = vi.hoisted(() => vi.fn());

vi.mock('#/api/ocpx', () => api);
vi.mock('@boot/access', () => ({
  useAccess: () => ({ hasAccessByCodes: () => false }),
}));
vi.mock('@boot/common-ui', () => ({ Page: { render: () => null } }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm },
}));
vi.mock('#/components/right-toolbar/index.vue', () => ({
  default: { render: () => null },
}));
vi.mock('../long-text.vue', () => ({ default: { render: () => null } }));
vi.mock('../rta/configuration-drawer.vue', () => ({
  default: { render: () => null },
}));
vi.mock('../rta/test-dialog.vue', () => ({
  default: { render: () => null },
}));
vi.mock('../table', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../table')>()),
  copyLongText: vi.fn(),
}));

function preview(requestId = 'SCX-UAT-first'): MonitorTaskTestPreview {
  return {
    advertiserName: '测试广告主',
    blockers: [],
    callbackDisplayUrl: 'https://media.example.test/callback',
    callbackMode: 'REAL_MEDIA',
    callbackToken: 'test-token',
    mediaCallbackMode: 'SIMULATED_MEDIA',
    event: 'CLK',
    executable: true,
    expectedCharge: 0,
    mediaName: '测试媒体',
    monitorName: '测试任务',
    os: 'ANDROID',
    parameters: [
      {
        configuredByMedia: true,
        editable: true,
        forwardedUpstream: true,
        group: '设备标识',
        key: 'oaid',
        label: 'OAID',
        macro: '__OAID__',
        required: false,
        selected: true,
        value: 'device-value',
      },
    ],
    platform: 1,
    promotionLinkName: '测试推广链接',
    requestId,
    taskId: 'task-1',
    testUrl: `https://engine.example.test/feedback?request_id=${requestId}&oaid=device-value`,
    warnings: [],
  };
}

type TestView = {
  closeTestDialog: () => void;
  copyTestLink: () => Promise<void>;
  currentTestUrl: string;
  executeTest: () => Promise<void>;
  openTest: (row: MonitorTask) => Promise<void>;
  testPreview?: MonitorTaskTestPreview;
  validateSelected: () => Promise<void>;
};

describe('monitor-task test actions', () => {
  let wrapper: ReturnType<typeof shallowMount>;
  let view: TestView;
  const task = { id: 'task-1', platform: 1 } as MonitorTask;

  beforeEach(() => {
    vi.clearAllMocks();
    api.mediaOptions.mockResolvedValue([]);
    api.monitorTaskOptions.mockResolvedValue([]);
    api.promotionLinkAdvertisers.mockResolvedValue([]);
    api.listMonitorTasks.mockResolvedValue({ records: [], total: 0 });
    api.generateMonitorTaskTestLink.mockResolvedValue(preview());
    confirm.mockResolvedValue('confirm');
    wrapper = shallowMount(MonitorTaskManagement, {
      global: { config: { warnHandler: () => {} } },
    });
    view = wrapper.vm as unknown as TestView;
  });

  afterEach(() => wrapper.unmount());

  it('copies and executes the unchanged server-approved preview', async () => {
    const generated = preview();
    api.generateMonitorTaskTestLink.mockResolvedValue(generated);
    api.executeMonitorTaskTestLink.mockResolvedValue({
      alreadyExecuted: false,
      requestId: generated.requestId,
      testUrl: generated.testUrl,
    });
    api.getMonitorTaskTestResult.mockResolvedValue({ state: 'COMPLETED' });
    await view.openTest(task);
    await view.copyTestLink();
    await view.executeTest();

    expect(copyLongText).toHaveBeenCalledWith(generated.testUrl);
    expect(api.executeMonitorTaskTestLink).toHaveBeenCalledWith('task-1', {
      callbackToken: generated.callbackToken,
      mediaCallbackMode: 'SIMULATED_MEDIA',
      os: 'ANDROID',
      parameters: { oaid: 'device-value' },
      requestId: generated.requestId,
    });
    expect(api.executeMonitorTaskTestLink).toHaveBeenCalledTimes(1);
  });

  it('rejects direct copy and execute calls for a blocked preview', async () => {
    api.generateMonitorTaskTestLink.mockResolvedValue({
      ...preview(),
      blockers: ['缺少有效设备参数'],
      executable: false,
    });
    await view.openTest(task);
    await view.copyTestLink();
    await view.executeTest();

    expect(view.currentTestUrl).toBe('');
    expect(copyLongText).not.toHaveBeenCalled();
    expect(confirm).not.toHaveBeenCalled();
    expect(api.executeMonitorTaskTestLink).not.toHaveBeenCalled();
  });

  it('blocks edited values immediately and preflights selected empty values', async () => {
    await view.openTest(task);
    const parameter = view.testPreview?.parameters[0];
    if (!parameter) throw new Error('Missing test parameter');
    parameter.value = '';
    await view.copyTestLink();
    await view.executeTest();

    expect(view.currentTestUrl).toBe('');
    expect(copyLongText).not.toHaveBeenCalled();
    expect(api.executeMonitorTaskTestLink).not.toHaveBeenCalled();
    api.generateMonitorTaskTestLink.mockRejectedValue(
      new Error('invalid OAID'),
    );
    await view.validateSelected();
    expect(api.generateMonitorTaskTestLink).toHaveBeenLastCalledWith('task-1', {
      mediaCallbackMode: 'SIMULATED_MEDIA',
      os: 'ANDROID',
      parameters: ['oaid'],
      parameterValues: { oaid: '' },
    });
    expect(view.currentTestUrl).toBe('');
  });

  it('does not use a previous URL while the next preview is pending', async () => {
    await view.openTest(task);
    let finish = (_value: MonitorTaskTestPreview) => {};
    api.generateMonitorTaskTestLink.mockReturnValue(
      new Promise<MonitorTaskTestPreview>((resolve) => {
        finish = resolve;
      }),
    );
    const validation = view.validateSelected();
    await view.copyTestLink();
    await view.executeTest();

    expect(view.currentTestUrl).toBe('');
    expect(copyLongText).not.toHaveBeenCalled();
    expect(api.executeMonitorTaskTestLink).not.toHaveBeenCalled();
    finish(preview('SCX-UAT-second'));
    await validation;
    expect(view.currentTestUrl).toContain('SCX-UAT-second');
  });

  it('ignores a late response after closing and reopening the dialog', async () => {
    let finish = (_value: MonitorTaskTestPreview) => {};
    api.generateMonitorTaskTestLink.mockReturnValueOnce(
      new Promise<MonitorTaskTestPreview>((resolve) => {
        finish = resolve;
      }),
    );
    const firstOpen = view.openTest(task);
    view.closeTestDialog();
    api.generateMonitorTaskTestLink.mockResolvedValue(
      preview('SCX-UAT-second'),
    );
    await view.openTest(task);
    finish(preview('SCX-UAT-first'));
    await firstOpen;

    expect(view.currentTestUrl).toContain('SCX-UAT-second');
    expect(view.testPreview?.requestId).toBe('SCX-UAT-second');
  });

  it('rechecks dialog identity after the user confirms execution', async () => {
    await view.openTest(task);
    let finish = (_value: string) => {};
    confirm.mockReturnValue(
      new Promise<string>((resolve) => {
        finish = resolve;
      }),
    );
    const execution = view.executeTest();
    view.closeTestDialog();
    finish('confirm');
    await execution;

    expect(api.executeMonitorTaskTestLink).not.toHaveBeenCalled();
  });
});
