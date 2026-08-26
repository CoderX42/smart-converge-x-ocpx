import type { FlowLogRequestDetail } from '#/api/ocpx';

import { describe, expect, it } from 'vitest';

import { buildSafeFlowLogText } from './safe-log';

function detail(): FlowLogRequestDetail {
  return {
    advertiserId: 'advertiser-1',
    callbackUrl: 'https://callback.example.test?token=top-secret',
    clickId: 'SCX-UAT-CLICK-1',
    coverageStatus: 'TRACE_AVAILABLE',
    durationMs: 36,
    eventCode: 'CLK',
    eventId: 'SCX-UAT-CLICK-1',
    eventTime: '2026-08-26 17:00:48',
    flowCreatedAt: '2026-08-26 17:00:48',
    flowLogId: 'flow-1',
    flowType: 'FEEDBACK',
    mediaId: 'media-1',
    message: 'MEDIA_INGRESS_ACCEPTED',
    monitorTaskId: 'task-1',
    promotionLinkId: 'link-1',
    requestId: 'SCX-UAT-CLICK-1',
    responsePreview:
      '{"code":200,"requestId":"partner-request-1","token":"response-secret"}',
    resultStatus: 'SUCCESS',
    stages: [
      {
        durationMs: 36,
        eventCode: 'CLK',
        eventTime: '2026-08-26 17:00:48',
        flowType: 'FEEDBACK',
        id: 'stage-1',
        macroAudit: {
          __CALLBACK__: 'EXPANDED',
          __IDFA__: 'EXPANDED',
          __REQID__: 'EXPANDED',
          __UNEXPECTED__: 'raw-macro-secret',
        },
        message: 'RENREN_VIDEO_DELIVERED',
        partnerHttpStatus: 200,
        requestEndpoint:
          'http://monitor.example.test/ad-action/click?token=endpoint-secret',
        requestMethod: 'GET',
        requestParams: {
          callback_url: 'https://callback.example.test?ext=encrypted-value',
          clickid: 'SCX-UAT-CLICK-1',
          ctx: 'encrypted-context',
          event: 'CLK',
          idfa: '2680ACA9-B63B-421B-8D7A-D4A84478478B',
          idfa_md5: 'b197aad3ac89ab37e550dcaff4a70a8a',
          ip: '203.0.113.38',
          model: 'iPhone 15',
          token: 'request-secret',
          ua: 'Mozilla/5.0 private fingerprint',
        },
        requestTruncated: false,
        responsePreview:
          '{"code":200,"requestId":"partner-request-1","token":"stage-secret"}',
        resultStatus: 'SUCCESS',
        stageCode: 'ADVERTISER_DELIVERY',
      },
    ],
    traceId: 'trace-1',
  };
}

describe('complete safe flow log copy', () => {
  it('keeps partner debugging evidence and masks device and secret values', () => {
    const text = buildSafeFlowLogText(detail());

    expect(text).toContain('SCX 完整安全日志');
    expect(text).toContain('Trace ID：trace-1');
    expect(text).toContain('事件 ID：SCX-UAT-CLICK-1');
    expect(text).toContain('广告主出站 / 响应 | 成功');
    expect(text).toContain('HTTP / 耗时：200 / 36 ms');
    expect(text).toContain(
      '端点：GET http://monitor.example.test/ad-action/click',
    );
    expect(text).toContain('RENREN_VIDEO_DELIVERED');
    expect(text).toContain('partner-request-1');
    expect(text).toContain('2680****478B');
    expect(text).toContain('b197****0a8a');
    expect(text).toContain('203.0.113.*');
    expect(text).toContain('[已提供，已脱敏]');
    expect(text).toContain('"callback_url": "[已隐藏]"');
    expect(text).toContain('"token": "[已隐藏]"');
    expect(text).toContain('"ctx": "[已隐藏]"');
    expect(text).toContain('"__UNEXPECTED__": "[状态不可用]"');
    expect(text).not.toContain('top-secret');
    expect(text).not.toContain('request-secret');
    expect(text).not.toContain('raw-macro-secret');
    expect(text).not.toContain('response-secret');
    expect(text).not.toContain('stage-secret');
    expect(text).not.toContain('encrypted-context');
    expect(text).not.toContain('encrypted-value');
    expect(text).not.toContain('endpoint-secret');
    expect(text).not.toContain('Mozilla/5.0 private fingerprint');
    expect(text).not.toContain('2680ACA9-B63B-421B-8D7A-D4A84478478B');
  });

  it('still produces useful output when seven-day request detail is absent', () => {
    const expired = detail();
    expired.coverageStatus = 'DETAIL_EXPIRED';
    expired.stages = [];
    expired.responsePreview = undefined;

    const text = buildSafeFlowLogText(expired);

    expect(text).toContain('详情覆盖：DETAIL_EXPIRED');
    expect(text).toContain('完整链路（0 个阶段）');
    expect(text).toContain('mId=media-1 / tId=task-1');
    expect(text).toContain('安全说明');
  });
});
