import type { PageResult } from '../business';

import { requestClient } from '#/api/request';

export type DeductionRule = {
  createTime: string;
  id: string;
  mediaId: string;
  mediaName: string;
  promotionLinkId: string;
  promotionLinkName: string;
  ratio: number;
  updateTime: string;
};

export type DeductionRulePayload = {
  mediaId: string;
  promotionLinkId: string;
  ratio: number;
};

export function listDeductionRules(params: Record<string, unknown>) {
  return requestClient.get<PageResult<DeductionRule>>('/ocpx/deduction-rules', {
    params,
  });
}
export function getDeductionRule(id: string) {
  return requestClient.get<DeductionRule>(`/ocpx/deduction-rules/${id}`);
}
export function createDeductionRule(data: DeductionRulePayload) {
  return requestClient.post<DeductionRule>('/ocpx/deduction-rules', data);
}
export function updateDeductionRule(id: string, data: DeductionRulePayload) {
  return requestClient.put<DeductionRule>(`/ocpx/deduction-rules/${id}`, data);
}
export function deleteDeductionRule(id: string) {
  return requestClient.delete(`/ocpx/deduction-rules/${id}`);
}
