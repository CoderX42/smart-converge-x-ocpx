import type { RtaKind, RtaRow } from '#/api/ocpx/rta';

export const titles: Record<RtaKind, string> = {
  bindings: '任务绑定',
  logs: '流程日志',
  media: '媒体接入',
  records: '请求记录',
  reports: '统计报表',
  upstreams: '广告主接入',
};
export const descriptions: Record<RtaKind, string> = {
  bindings:
    '媒体和广告主取自监测任务。每个任务配置一个上游产品与任务映射，修改绑定后原放行结果失效。',
  logs: '仅展示脱敏阶段摘要；成功请求按关联 ID 稳定采样 10%，失败全量记录。XXL-JOB 异步落库，保留 24 小时。',
  media: '每个媒体维护一份 RTA 配置。OPPO 同步返回策略，通用协议异步通知决策。',
  records:
    '近 24 小时实时状态。OPPO 同步返回命中策略；通用协议的 202 只代表受理，须等待决策通知。',
  reports:
    '候选任务各计一次受理；RTA 命中率 = 允许数 / 受理数，与转化率无关。报表由 XXL-JOB 异步更新。',
  upstreams:
    '当前广告主可维护多份 RTA 接入。选择协议后配置对应参数；点击/转化协议独立维护。',
};
type Column = { key: keyof RtaRow; label: string; width?: number };
export const columns: Record<RtaKind, Column[]> = {
  upstreams: [
    { key: 'name', label: '接入名称' },
    { key: 'advertiserName', label: '广告主' },
    { key: 'protocol', label: '协议' },
    { key: 'endpoint', label: '请求地址', width: 260 },
    { key: 'callbackUrl', label: '决策回调地址', width: 260 },
    { key: 'timeoutMs', label: '等待时限 / ms' },
    { key: 'admissionSeconds', label: '放行时长 / 秒' },
    { key: 'enabled', label: '状态' },
  ],
  media: [
    { key: 'mediaName', label: '媒体' },
    { key: 'mediaId', label: '媒体 ID' },
    { key: 'protocol', label: '媒体协议' },
    { key: 'requestUrl', label: 'SCX 请求地址', width: 260 },
    { key: 'notifyUrl', label: '决策通知地址', width: 260 },
    { key: 'credentialRef', label: '凭据引用' },
    { key: 'enabled', label: '状态' },
  ],
  bindings: [
    { key: 'taskName', label: '监测任务' },
    { key: 'strategyId', label: '对外策略 ID' },
    { key: 'advertiserName', label: '广告主' },
    { key: 'mediaName', label: '媒体' },
    { key: 'upstreamId', label: '上游接入 ID' },
    { key: 'productId', label: '上游产品 ID' },
    { key: 'externalTaskId', label: '上游任务 ID' },
    { key: 'version', label: '绑定版本' },
    { key: 'enabled', label: '状态' },
  ],
  records: [
    { key: 'requestId', label: '媒体请求 ID', width: 220 },
    { key: 'protocol', label: 'RTA 协议' },
    { key: 'mediaName', label: '媒体' },
    { key: 'status', label: '决策状态' },
    { key: 'matchedTaskIds', label: '命中任务' },
    { key: 'deliveryStatus', label: '媒体通知' },
    { key: 'receivedAt', label: '受理时间', width: 190 },
    { key: 'expiresAt', label: '放行失效时间', width: 190 },
    { key: 'reasonCode', label: '原因' },
  ],
  logs: [
    { key: 'requestId', label: '媒体请求 ID', width: 220 },
    { key: 'mediaName', label: '媒体' },
    { key: 'taskName', label: '任务' },
    { key: 'stage', label: '阶段' },
    { key: 'status', label: '状态' },
    { key: 'reason', label: '原因' },
    { key: 'durationMs', label: '耗时 / ms' },
    { key: 'eventTime', label: '事件时间', width: 190 },
    { key: 'updateTime', label: '落库时间', width: 190 },
  ],
  reports: [
    { key: 'timeHour', label: '接收时段', width: 190 },
    { key: 'mediaName', label: '媒体' },
    { key: 'taskName', label: '任务' },
    { key: 'requestCount', label: '受理' },
    { key: 'pendingCount', label: '待决策' },
    { key: 'allowCount', label: '允许' },
    { key: 'denyCount', label: '拒绝' },
    { key: 'errorCount', label: '错误' },
    { key: 'timeoutCount', label: '超时' },
    { key: 'allowRate', label: 'RTA 命中率' },
    { key: 'deliverySuccessCount', label: '通知成功' },
    { key: 'deliveryFailedCount', label: '通知失败' },
  ],
};
export function displayRta(value: unknown, key: keyof RtaRow) {
  if (key === 'enabled') return value ? '已启用' : '已停用';
  if (key === 'allowRate') return `${(Number(value || 0) * 100).toFixed(2)}%`;
  if (['completedAt', 'deadline', 'expiresAt', 'receivedAt'].includes(key))
    return value
      ? new Date(Number(value)).toLocaleString('zh-CN', { hour12: false })
      : '—';
  if (Array.isArray(value)) return value.join('、') || '—';
  return value === undefined || value === null || value === ''
    ? '—'
    : String(value);
}
