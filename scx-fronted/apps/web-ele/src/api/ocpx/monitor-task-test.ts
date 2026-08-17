import { requestClient } from '#/api/request';

export type MonitorTaskTestOs = 'ANDROID' | 'IOS';

export type MonitorTaskTestParameter = {
  configuredByMedia: boolean;
  editable: boolean;
  forwardedUpstream: boolean;
  group: string;
  key: string;
  label: string;
  macro: string;
  required: boolean;
  selected: boolean;
  value?: string;
};

export type MonitorTaskTestPreview = {
  advertiserName: string;
  availableBalance?: number;
  blockers: string[];
  event: 'CLK';
  executable: boolean;
  expectedCharge: number;
  mediaName: string;
  monitorName: string;
  os: MonitorTaskTestOs;
  parameters: MonitorTaskTestParameter[];
  platform: number;
  promotionLinkName: string;
  requestId: string;
  taskId: string;
  testUrl: string;
  warnings: string[];
};

export type MonitorTaskTestExecution = {
  alreadyExecuted: boolean;
  durationMs: number;
  engineHttpStatus?: number;
  engineResponse?: string;
  errorCode?: string;
  executedAt: string;
  requestId: string;
  testUrl: string;
};

export type MonitorTaskTestFact = {
  billingReasonCode?: string;
  billingStatus: string;
  eventCode: string;
  receivedAt: string;
  spendAmount: number;
};

export type MonitorTaskTestFlow = {
  durationMs: number;
  eventTime: string;
  message?: string;
  partnerHttpStatus?: number;
  resultStatus: 'FAILED' | 'SUCCESS';
};

export type MonitorTaskTestTimelineItem = {
  detail?: string;
  durationMs?: number;
  occurredAt?: string;
  stage: 'BILLING' | 'ENGINE_INGRESS' | 'UPSTREAM';
  status: string;
};

export type MonitorTaskTestResult = {
  conclusion: string;
  fact?: MonitorTaskTestFact;
  flow?: MonitorTaskTestFlow;
  requestId: string;
  state:
    | 'REJECTED'
    | 'UPSTREAM_FAILED'
    | 'UPSTREAM_PENDING'
    | 'UPSTREAM_RESPONDED'
    | 'WAITING';
  timeline: MonitorTaskTestTimelineItem[];
};

export function generateMonitorTaskTestLink(
  taskId: string,
  data: { os: MonitorTaskTestOs; parameters?: string[] },
) {
  return requestClient.post<MonitorTaskTestPreview>(
    `/ocpx/monitor-tasks/${taskId}/test-links:generate`,
    data,
  );
}

export function executeMonitorTaskTestLink(
  taskId: string,
  data: {
    os: MonitorTaskTestOs;
    parameters: Record<string, string>;
    requestId: string;
  },
) {
  return requestClient.post<MonitorTaskTestExecution>(
    `/ocpx/monitor-tasks/${taskId}/test-links:execute`,
    data,
  );
}

export function getMonitorTaskTestResult(taskId: string, requestId: string) {
  return requestClient.get<MonitorTaskTestResult>(
    `/ocpx/monitor-tasks/${taskId}/test-results/${encodeURIComponent(requestId)}`,
  );
}
