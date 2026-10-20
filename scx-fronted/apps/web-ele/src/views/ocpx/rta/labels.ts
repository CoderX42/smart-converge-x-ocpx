export type RtaTagType = 'danger' | 'info' | 'primary' | 'success' | 'warning';
export type RtaLabel = { text: string; type: RtaTagType };

function label(text: string, type: RtaTagType): RtaLabel {
  return { text, type };
}

export const decisionStatus: Record<string, RtaLabel> = {
  ALLOW: label('放行', 'success'),
  DENY: label('拒绝', 'info'),
  ERROR: label('错误', 'danger'),
  PENDING: label('待决策', 'primary'),
  TIMEOUT: label('超时', 'warning'),
};

export const deliveryStatus: Record<string, RtaLabel> = {
  DELIVERED: label('已通知', 'success'),
  FAILED: label('通知失败', 'danger'),
  PENDING: label('待通知', 'primary'),
  SYNC_READY: label('待同步返回', 'primary'),
  SYNC_RETURNED: label('已同步返回', 'success'),
};

export const dispatchStatus: Record<string, RtaLabel> = {
  AWAITING_CALLBACK: label('已提交，等待回调', 'primary'),
  QUEUED: label('排队中', 'info'),
  SENT: label('已派发', 'success'),
};

export const protocols: Record<string, RtaLabel> = {
  GENERIC_ASYNC: label('通用异步', 'primary'),
  OPPO: label('OPPO 同步', 'success'),
  WEIFENG_RTA: label('微风 RTA', 'warning'),
};

export const decisionModes: Record<string, string> = {
  ASYNC_CALLBACK: '异步回调决策',
  SYNC_HTTP: '同步返回决策',
};

export const logStages: Record<string, string> = {
  DECISION_TIMEOUT: '决策超时',
  EVENT_GATE: '点击/转化放行校验',
  MEDIA_ACCEPTED: '受理媒体请求',
  MEDIA_NOTIFY: '通知媒体',
  UPSTREAM_DECISION: '上游回调决策',
  UPSTREAM_SUBMIT: '派发上游',
  UPSTREAM_SYNC_DECISION: '上游同步决策',
};

export const logStatus: Record<string, RtaLabel> = {
  FAILED: label('失败', 'danger'),
  SUCCESS: label('成功', 'success'),
};

export const reasonCodes: Record<string, string> = {
  ACCEPTED: '已受理',
  ALLOW: '放行',
  NOTIFY_DEADLINE: '超过通知时限',
  NOTIFY_FAILED: '通知媒体失败',
  OPPO_CLICK_ENDPOINT_REQUIRED: '须使用 OPPO 点击地址',
  OPPO_STRATEGY_UNAVAILABLE: 'OPPO 策略不可用',
  RTA_ADMISSION_DENIED: '未获放行',
  RTA_ADMISSION_MISSING: '无放行记录',
  RTA_BINDING_AMBIGUOUS: '任务绑定不唯一',
  RTA_BINDING_CHANGED: '任务绑定已变更',
  RTA_CALLBACK_BASE_UNAVAILABLE: '回调基地址未配置',
  RTA_CREDENTIAL_UNAVAILABLE: '凭据未配置',
  RTA_DECISION_INVALID: '决策报文无效',
  RTA_DEVICE_REQUIRED: '缺少设备标识',
  RTA_DISABLED: 'RTA 未开启',
  RTA_ENDPOINT_REJECTED: '上游地址被拒绝',
  RTA_MATCH_INVALID: '命中任务无效',
  RTA_MEDIA_CHANGED: '媒体配置已变更',
  RTA_MEDIA_UNAVAILABLE: '媒体接入不可用',
  RTA_PLAN_MISMATCH: '计划参数与点击链接不一致',
  RTA_REPLAY: '重放请求',
  RTA_ROUTE_MISMATCH: '路由不一致',
  RTA_SIGNATURE_EXPIRED: '签名已过期',
  RTA_SIGNATURE_INVALID: '签名无效',
  RTA_SUBMIT_FAILED: '派发上游失败',
  RTA_TASK_UNAVAILABLE: '任务不可用',
  RTA_TIMEOUT: '决策超时',
  RTA_TRANSPORT_FAILED: '网络请求失败',
  RTA_TRANSPORT_TIMEOUT: '网络请求超时',
  RTA_UPSTREAM_NOT_ACCEPTED: '上游未受理',
  RTA_UPSTREAM_UNAVAILABLE: '上游接入不可用',
  SUBMIT_FAILED: '派发上游失败',
  UPSTREAM_ALLOW: '上游放行',
  UPSTREAM_DENY: '上游拒绝',
  UPSTREAM_ERROR: '上游返回错误',
  WEIFENG_DEVICE_REQUIRED: '微风要求设备标识',
  WEIFENG_HTTP_ERROR: '微风 HTTP 错误',
  WEIFENG_OS_INVALID: '系统类型无效',
  WEIFENG_RESPONSE_INVALID: '微风响应无效',
};

export const routeStatus: Record<string, RtaLabel> = {
  DISABLED: label('已停用', 'info'),
  INCOMPLETE: label('不完整', 'danger'),
  NOT_CONFIGURED: label('未配置', 'info'),
  READY: label('就绪', 'success'),
};

export const segmentState: Record<string, RtaLabel> = {
  DISABLED: label('已停用', 'warning'),
  ENABLED: label('已启用', 'success'),
  MISSING: label('未配置', 'info'),
};

export const checkLevel: Record<string, RtaLabel> = {
  BLOCK: label('阻断', 'danger'),
  OK: label('通过', 'success'),
  WARN: label('提醒', 'warning'),
};

const unknown = (value: string) => label(value || '—', 'info');

export function tagOf(
  table: Record<string, RtaLabel>,
  value: null | string | undefined,
): RtaLabel {
  return (value && table[value]) || unknown(value || '');
}

export function reasonText(code: null | string | undefined) {
  if (!code) return '—';
  if (reasonCodes[code]) return reasonCodes[code];
  if (/^HTTP_\d{3}$/.test(code)) return `媒体返回 ${code.slice(5)}`;
  return code;
}

export function formatTime(value: unknown) {
  const time = Number(value);
  return time > 0
    ? new Date(time).toLocaleString('zh-CN', { hour12: false })
    : '—';
}

export function formatRate(allow: number, total: number) {
  return total > 0 ? `${((allow / total) * 100).toFixed(2)}%` : '—';
}
