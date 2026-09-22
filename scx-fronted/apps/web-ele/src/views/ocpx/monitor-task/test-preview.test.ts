import type { MonitorTaskTestPreview } from '#/api/ocpx';

import { beforeEach, describe, expect, it } from 'vitest';

import { testLinkAvailability, testParameterSnapshot } from './test-preview';

describe('monitor-task test link preflight', () => {
  let preview: MonitorTaskTestPreview;
  let validatedParameters: string;

  beforeEach(() => {
    preview = {
      advertiserName: '测试广告主',
      blockers: [],
      callbackDisplayUrl: 'https://media.example.test/callback',
      callbackMode: 'REAL_MEDIA',
      callbackToken: 'test-token',
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
      platform: 3,
      promotionLinkName: '测试推广链接',
      requestId: 'SCX-UAT-test',
      taskId: 'task-1',
      testUrl: 'https://engine.example.test/feedback?oaid=device-value',
      warnings: [],
    };
    validatedParameters = testParameterSnapshot(preview.os, preview.parameters);
  });

  function availability(overrides = {}) {
    return testLinkAvailability({
      error: '',
      os: preview.os,
      preview,
      validatedParameters,
      validating: false,
      ...overrides,
    });
  }

  it('uses the exact server-approved URL without reconstructing it', () => {
    expect(availability()).toEqual({ message: '', url: preview.testUrl });
  });

  it.each([
    { blockers: [], executable: false },
    { blockers: ['AERIM 缺少有效设备参数'], executable: true },
  ])('hides a server URL whenever the preview is blocked: %j', (blocked) => {
    Object.assign(preview, blocked);
    expect(availability().url).toBe('');
    expect(availability().message).not.toBe('');
  });

  it('shows the actual server rejection reason', () => {
    preview.blockers = ['AERIM 缺少有效设备参数'];
    expect(availability().message).toBe('AERIM 缺少有效设备参数');
  });

  it.each(['value', 'selection', 'os'])(
    'requires a new preflight after %s changes',
    (change) => {
      const parameter = preview.parameters[0];
      if (!parameter) throw new Error('Missing test parameter');
      if (change === 'value') parameter.value = '';
      if (change === 'selection') parameter.selected = false;
      const state = availability(change === 'os' ? { os: 'IOS' } : {});
      expect(state.url).toBe('');
      expect(state.message).toContain('校验当前参数');
    },
  );

  it('hides the previous valid URL throughout a new preflight', () => {
    expect(availability({ validating: true }).url).toBe('');
    expect(availability({ validating: true }).message).toContain('正在校验');
  });

  it('keeps the previous URL unavailable when validation fails', () => {
    expect(availability({ error: '参数校验失败' })).toEqual({
      message: '参数校验失败',
      url: '',
    });
  });

  it('does not expose a missing preview or an empty server URL', () => {
    expect(availability({ preview: undefined }).url).toBe('');
    preview.testUrl = '';
    expect(availability().url).toBe('');
    expect(availability().message).toContain('未生成有效测试链接');
  });

  it('only re-enables an edited value after the server returns an approved preview', () => {
    const parameter = preview.parameters[0];
    if (!parameter) throw new Error('Missing test parameter');
    parameter.value = 'new-device-value';
    expect(availability().url).toBe('');
    preview.testUrl =
      'https://engine.example.test/feedback?oaid=new-device-value';
    validatedParameters = testParameterSnapshot(preview.os, preview.parameters);
    expect(availability()).toEqual({ message: '', url: preview.testUrl });
  });
});
