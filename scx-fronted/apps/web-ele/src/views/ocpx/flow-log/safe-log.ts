import type { FlowLogRequestDetail } from '#/api/ocpx';

export const flowLogStageLabels: Record<string, string> = {
  ADVERTISER_CALLBACK: '广告主 Callback',
  ADVERTISER_DELIVERY: '广告主出站 / 响应',
  CALLBACK_ACCOUNTING: '广告主计费',
  LEGACY: '历史日志',
  MEDIA_DEDUCTION: '媒体扣量',
  MEDIA_DELIVERY: '媒体出站 / 响应',
  MEDIA_INGRESS: '媒体入站',
};

const safeParameterKeys = new Set([
  'adgroupid',
  'advertiserid',
  'advid',
  'campaignid',
  'clickid',
  'code',
  'creativeid',
  'event',
  'eventtype',
  'groupid',
  'impresssid',
  'message',
  'model',
  'msg',
  'mts',
  'os',
  'osv',
  'planid',
  'requestid',
  'ret',
  'status',
  'timestamp',
  'timestampms',
  'ts',
  'type',
  'unitid',
]);
const secretKeyParts = [
  'authorization',
  'callback',
  'credential',
  'ctx',
  'ext',
  'key',
  'password',
  'secret',
  'sign',
  'token',
];
const deviceKeyParts = [
  'aaid',
  'androidid',
  'caid',
  'deviceid',
  'idfa',
  'imei',
  'mac',
  'oaid',
  'sid',
  'userid',
];
const safeMacroStates = new Set([
  'ANDROID',
  'BLOCKED',
  'EXPANDED',
  'IOS',
  'OMITTED_EMPTY',
  'UNKNOWN',
]);

function normalizedKey(key: string) {
  return key.toLowerCase().replaceAll(/[^a-z0-9]/g, '');
}

function maskIdentifier(value: unknown): unknown {
  if (Array.isArray(value)) return value.map((item) => maskIdentifier(item));
  if (typeof value !== 'string') {
    return value === null || value === undefined ? value : '[已提供]';
  }
  if (!value) return value;
  if (value.length <= 8) return '****';
  return `${value.slice(0, 4)}****${value.slice(-4)}`;
}

function maskIp(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value === null || value === undefined ? value : '[已提供]';
  }
  if (value.includes('.')) {
    const parts = value.split('.');
    if (parts.length === 4) return `${parts.slice(0, 3).join('.')}.*`;
  }
  if (value.includes(':')) {
    const parts = value.split(':');
    return `${parts.slice(0, 2).join(':')}:****`;
  }
  return maskIdentifier(value);
}

function sanitizeValue(key: string, value: unknown): unknown {
  const normalized = normalizedKey(key);
  if (value === null || value === undefined) return value;
  if (secretKeyParts.some((part) => normalized.includes(part))) {
    return '[已隐藏]';
  }
  if (safeParameterKeys.has(normalized)) return value;
  if (normalized === 'ip' || normalized === 'ipv6') return maskIp(value);
  if (normalized === 'ua' || normalized === 'useragent') {
    return '[已提供，已脱敏]';
  }
  if (deviceKeyParts.some((part) => normalized.includes(part))) {
    return maskIdentifier(value);
  }
  if (Array.isArray(value)) {
    return value.map((item) =>
      typeof item === 'object' && item !== null
        ? sanitizeRecord(item as Record<string, unknown>)
        : '[已提供]',
    );
  }
  if (typeof value === 'object') {
    return sanitizeRecord(value as Record<string, unknown>);
  }
  if (typeof value === 'number' || typeof value === 'boolean') return value;
  return '[已提供]';
}

function sanitizeRecord(value: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [key, sanitizeValue(key, item)]),
  );
}

function formattedRecord(value: Record<string, unknown>) {
  if (Object.keys(value).length === 0) return '';
  return JSON.stringify(sanitizeRecord(value), null, 2);
}

function formattedMacroAudit(value: Record<string, unknown>) {
  if (Object.keys(value).length === 0) return '';
  return JSON.stringify(
    Object.fromEntries(
      Object.entries(value).map(([key, status]) => {
        if (typeof status === 'boolean') return [key, status ? '是' : '否'];
        return [
          key,
          typeof status === 'string' && safeMacroStates.has(status)
            ? status
            : '[状态不可用]',
        ];
      }),
    ),
    null,
    2,
  );
}

function sanitizedResponsePreview(value?: string) {
  if (!value) return '';
  try {
    const parsed = JSON.parse(value) as unknown;
    if (typeof parsed === 'object' && parsed !== null) {
      return JSON.stringify(
        sanitizeRecord(parsed as Record<string, unknown>),
        null,
        2,
      );
    }
  } catch {
    // 非 JSON 响应继续按常见敏感键做文本级隐藏。
  }
  return value.replaceAll(
    /((?:authorization|callback|credential|ctx|ext|password|secret|signature|token)\s*[=:]\s*)[^\s,;]+/gi,
    '$1[已隐藏]',
  );
}

function present(value?: null | number | string) {
  return value === null || value === undefined || value === ''
    ? '-'
    : String(value);
}

function safeEndpoint(value?: string) {
  if (!value) return '-';
  try {
    const endpoint = new URL(value);
    return `${endpoint.protocol}//${endpoint.host}${endpoint.pathname}`;
  } catch {
    return value.split(/[?#]/, 1)[0] || '-';
  }
}

function resultLabel(value: 'FAILED' | 'SUCCESS') {
  return value === 'SUCCESS' ? '成功' : '失败';
}

export function flowLogStageLabel(value: string) {
  return flowLogStageLabels[value] || value;
}

export function buildSafeFlowLogText(detail: FlowLogRequestDetail) {
  const lines = [
    'SCX 完整安全日志',
    `Trace ID：${present(detail.traceId)}`,
    `事件 ID：${present(detail.eventId)}`,
    `流程日志 ID：${present(detail.flowLogId)}`,
    `源事件 ID：${present(detail.sourceEventId)}`,
    `流程 / 事件：${detail.flowType} / ${detail.eventCode}`,
    `所选日志结果：${resultLabel(detail.resultStatus)}`,
    `业务结论：${present(detail.message)}`,
    `事件时间：${present(detail.eventTime)}`,
    `HTTP / 耗时：${present(detail.partnerHttpStatus)} / ${detail.durationMs} ms`,
    `详情覆盖：${detail.coverageStatus}`,
    '',
    `完整链路（${detail.stages.length} 个阶段）`,
  ];

  detail.stages.forEach((stage, index) => {
    lines.push(
      '',
      `[${index + 1}] ${flowLogStageLabel(stage.stageCode)} | ${resultLabel(stage.resultStatus)}`,
      `时间：${present(stage.eventTime)}`,
      `方向 / 事件：${stage.flowType} / ${stage.eventCode}`,
      `端点：${present(stage.requestMethod)} ${safeEndpoint(stage.requestEndpoint)}`,
      `HTTP / 耗时：${present(stage.partnerHttpStatus)} / ${stage.durationMs} ms`,
      `稳定结论码：${present(stage.message)}`,
    );
    const requestParams = formattedRecord(stage.requestParams);
    if (requestParams) {
      lines.push('请求参数（安全脱敏）：', requestParams);
      if (stage.requestTruncated) lines.push('请求快照：已达到大小上限');
    }
    const macroAudit = formattedMacroAudit(stage.macroAudit);
    if (macroAudit) lines.push('宏审计：', macroAudit);
    const responsePreview = sanitizedResponsePreview(stage.responsePreview);
    if (responsePreview) {
      lines.push('响应预览（已脱敏）：', responsePreview);
    }
  });

  lines.push(
    '',
    'SCX 路由关系',
    `mId=${present(detail.mediaId)} / tId=${present(detail.monitorTaskId)} / aId=${present(detail.advertiserId)} / lId=${present(detail.promotionLinkId)}`,
    `request_id=${present(detail.requestId)} / clickid=${present(detail.clickId)}`,
  );

  const selectedResponse = sanitizedResponsePreview(detail.responsePreview);
  if (selectedResponse) {
    lines.push('', '所选日志响应预览（已脱敏）：', selectedResponse);
  }

  lines.push(
    '',
    '安全说明：本日志不包含完整出站 URL、明文 Callback、签名、Token、凭据或设备标识原值；HTTP 成功不代表合作方已完成业务入库。',
  );
  return lines.join('\n');
}
