import { requestClient } from '#/api/request';

export interface RtaTestPreview {
  blockers: string[];
  endpoint: string;
  mediaName: string;
  mediaProtocol: string;
  plan: Record<string, string>;
  requestId: string;
  strategyId?: number;
  taskId: string;
  monitorName: string;
  upstreamName: string;
  upstreamProtocol: string;
}
export interface RtaTestResult {
  agreed?: boolean;
  completedAt?: number;
  deliveryStatus?: string;
  durationMs?: number;
  httpStatus?: number;
  message?: string;
  providerRequestId?: string;
  reasonCode?: string;
  relayRequestId?: string;
  requestId: string;
  ret?: number;
  status?: string;
  submission: string;
  upstreamAttempts?: number;
}
export function previewRtaTest(taskId: string) {
  return requestClient.get<RtaTestPreview>(
    `/ocpx/monitor-tasks/${taskId}/rta-test`,
  );
}
export function executeRtaTest(
  taskId: string,
  requestId: string,
  oaid: string,
) {
  return requestClient.post<RtaTestResult>(
    `/ocpx/monitor-tasks/${taskId}/rta-test`,
    { requestId, oaid },
  );
}
export function getRtaTestResult(taskId: string, requestId: string) {
  return requestClient.get<RtaTestResult>(
    `/ocpx/monitor-tasks/${taskId}/rta-test/${requestId}`,
  );
}
