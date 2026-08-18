import type { ConversionEvent } from './four-entity';

import { requestClient } from '#/api/request';

export type MonitorTaskIntegrationFeedback = {
  advertiserId?: string;
  androidId?: string;
  callbackAvailable: boolean;
  callbackUrl?: string;
  caid?: string;
  caid1?: string;
  clickId?: string;
  creativeId?: string;
  deviceOs?: string;
  externalAdvertiserId?: string;
  feedbackLogId: number;
  groupId?: string;
  idfa?: string;
  imei?: string;
  ip?: string;
  mac?: string;
  mediaId?: string;
  monitorTaskId?: string;
  oaid?: string;
  planId?: string;
  promotionLinkId?: string;
  receivedAt: string;
  requestId?: string;
  timestamp?: string;
  timestampMs?: string;
  ua?: string;
  userId?: string;
  userType?: string;
};

export type MonitorTaskIntegrationFeedbackPage = {
  current: number;
  records: MonitorTaskIntegrationFeedback[];
  size: number;
  total: number;
};

export type MonitorTaskIntegrationCallbackResult = {
  attemptId: string;
  currencyAmount?: number;
  durationMs: number;
  eventCode: ConversionEvent;
  eventType: number;
  message: string;
  partnerHttpStatus?: number;
  responsePreview?: string;
  resultStatus: 'FAILED' | 'SUCCESS';
  sentAt: string;
  sourceRequestId?: string;
};

export function getMonitorTaskIntegrationFeedback(
  taskId: string,
  params: {
    current?: number;
    endDate?: string;
    requestId?: string;
    size?: number;
    startDate?: string;
  },
) {
  return requestClient.get<MonitorTaskIntegrationFeedbackPage>(
    `/ocpx/monitor-tasks/${taskId}/integration/feedback-requests`,
    { params },
  );
}

export function sendMonitorTaskIntegrationCallback(
  taskId: string,
  data: {
    currencyAmount?: number;
    eventCode: ConversionEvent;
    feedbackLogId: number;
  },
) {
  return requestClient.post<MonitorTaskIntegrationCallbackResult>(
    `/ocpx/monitor-tasks/${taskId}/integration/callbacks:send`,
    data,
  );
}
