import { requestClient } from '#/api/request';

export type MonitorTaskTestOs = 'ANDROID' | 'IOS';

export type MonitorTaskTestMediaCallbackMode = 'REAL_MEDIA' | 'SIMULATED_MEDIA';

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
  callbackDisplayUrl: null | string;
  callbackFeedbackLogId?: number;
  callbackMode: 'FORMAL_UPSTREAM' | 'REAL_MEDIA';
  callbackToken: string;
  event: 'CLK';
  executable: boolean;
  expectedCharge: number;
  mediaCallbackMode: MonitorTaskTestMediaCallbackMode;
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

export type MonitorTaskTestCallbackFact = {
  billingReasonCode?: string;
  billingStatus: string;
  currencyAmount: number;
  deductionDecidedAt?: string;
  deductionDecision: 'DEDUCTED' | 'PASSED' | 'PENDING';
  deductionRatio?: number;
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
  stage:
    | 'ADVERTISER_CALLBACK'
    | 'BILLING'
    | 'CALLBACK_BILLING'
    | 'DOWNSTREAM_CALLBACK'
    | 'ENGINE_INGRESS'
    | 'MEDIA_DEDUCTION'
    | 'UPSTREAM';
  status: string;
};

export type MonitorTaskTestResult = {
  callbackFact?: MonitorTaskTestCallbackFact;
  callbackFlow?: MonitorTaskTestFlow;
  conclusion: string;
  downstreamFlow?: MonitorTaskTestFlow;
  fact?: MonitorTaskTestFact;
  flow?: MonitorTaskTestFlow;
  requestId: string;
  state:
    | 'CALLBACK_DEDUCTED'
    | 'CALLBACK_FAILED'
    | 'CALLBACK_PROCESSING'
    | 'COMPLETED'
    | 'DOWNSTREAM_FAILED'
    | 'DOWNSTREAM_PENDING'
    | 'REJECTED'
    | 'UPSTREAM_FAILED'
    | 'UPSTREAM_PENDING'
    | 'UPSTREAM_RESPONDED'
    | 'WAITING';
  timeline: MonitorTaskTestTimelineItem[];
};

export function generateMonitorTaskTestLink(
  taskId: string,
  data: {
    feedbackLogId?: number;
    mediaCallbackMode?: MonitorTaskTestMediaCallbackMode;
    os: MonitorTaskTestOs;
    parameters?: string[];
    parameterValues?: Record<string, string>;
  },
) {
  return requestClient.post<MonitorTaskTestPreview>(
    `/ocpx/monitor-tasks/${taskId}/test-links:generate`,
    data,
  );
}

export function executeMonitorTaskTestLink(
  taskId: string,
  data: {
    callbackToken: string;
    feedbackLogId?: number;
    mediaCallbackMode?: MonitorTaskTestMediaCallbackMode;
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
