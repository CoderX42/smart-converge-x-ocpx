import type { BaseRecord, PageQuery, PageResult } from './business';

import { requestClient } from '#/api/request';

export type Organization = BaseRecord & {
  contactEmail?: string;
  contactName?: string;
  contactPhone?: string;
  legalPerson?: string;
  name: string;
  orgType: string;
  parentOrgId?: string;
};

export type Advertiser = BaseRecord & {
  advertiserName: string;
  industry?: string;
  orgId: string;
};

export type AgencyRelation = BaseRecord & {
  authorizedScopes?: string;
  childOrgId: string;
  endDate?: string;
  parentAgencyOrgId: string;
  relationType: string;
  startDate?: string;
};

export type AdAccount = BaseRecord & {
  accountName: string;
  advertiserOrgId: string;
  currency?: string;
  rebateOrgId?: string;
  refundOrgId?: string;
  responsibleOrgId?: string;
  timezone?: string;
};

export type QualificationCase = BaseRecord & {
  caseType: string;
  orgId: string;
  submittedAt?: string;
};

export type AgreementInstance = BaseRecord & {
  expiredAt?: string;
  orgId: string;
  signedAt?: string;
  templateId: string;
  templateVersion: number;
};

export type AgreementTemplate = BaseRecord & {
  content: string;
  name: string;
  templateType: string;
  version: number;
};

export type DataAuthorization = BaseRecord & {
  authorizationType: string;
  endDate?: string;
  granteeOrgId: string;
  grantorOrgId: string;
  scope?: string;
  startDate?: string;
};

export type OrganizationScope = BaseRecord & {
  scopeType: 'READ' | 'WRITE';
  subjectId: string;
  subjectType: 'ORGANIZATION';
  userId: string;
};

export type PublishingEligibility = {
  eligible: boolean;
  reasonCodes: string[];
};

export type QualificationDocument = BaseRecord & {
  caseId: string;
  docName: string;
  docType: string;
  fileSize?: number;
  mimeType?: string;
  originalFileName?: string;
};

export type QualificationReviewLog = {
  comment?: string;
  createdAt: string;
  fromStatus?: string;
  id: string;
  operatorName?: string;
  toStatus: string;
};

export function getOrganizations(params: PageQuery & { orgType?: string }) {
  return requestClient.get<PageResult<Organization>>(
    '/ad/commercial/organizations',
    { params },
  );
}

export function saveOrganization(data: Partial<Organization>) {
  return data.id
    ? requestClient.put<Organization>(
        `/ad/commercial/organizations/${data.id}`,
        data,
      )
    : requestClient.post<Organization>('/ad/commercial/organizations', data);
}

export function changeOrganizationStatus(id: string, status: string) {
  return requestClient.put<Organization>(
    `/ad/commercial/organizations/${id}/status`,
    undefined,
    { params: { status } },
  );
}

export function getAdvertisers(params: PageQuery) {
  return requestClient.get<PageResult<Advertiser>>(
    '/ad/commercial/advertisers',
    { params },
  );
}

export function saveAdvertiser(data: Partial<Advertiser>) {
  return data.id
    ? requestClient.put(`/ad/commercial/advertisers/${data.id}`, data)
    : requestClient.post('/ad/commercial/advertisers', data);
}

export function changeAdvertiserStatus(id: string, status: string) {
  return requestClient.put(
    `/ad/commercial/advertisers/${id}/status`,
    undefined,
    { params: { status } },
  );
}

export function getAgencyRelations(
  params: PageQuery & { parentOrgId?: string },
) {
  return requestClient.get<PageResult<AgencyRelation>>(
    '/ad/commercial/agency-relations/page',
    { params },
  );
}

export function saveAgencyRelation(data: Partial<AgencyRelation>) {
  const { id, ...payload } = data;
  return id
    ? requestClient.put(`/ad/commercial/agency-relations/${id}`, payload)
    : requestClient.post('/ad/commercial/agency-relations', payload);
}

export function revokeAgencyRelation(id: string) {
  return requestClient.delete(`/ad/commercial/agency-relations/${id}`);
}

export function getAdAccounts(
  params: PageQuery & { advertiserOrgId?: string },
) {
  return requestClient.get<PageResult<AdAccount>>(
    '/ad/commercial/ad-accounts',
    { params },
  );
}

export function saveAdAccount(data: Partial<AdAccount>) {
  return data.id
    ? requestClient.put(`/ad/commercial/ad-accounts/${data.id}`, data)
    : requestClient.post('/ad/commercial/ad-accounts', data);
}

export function changeAdAccountStatus(id: string, status: string) {
  return requestClient.put(
    `/ad/commercial/ad-accounts/${id}/status`,
    undefined,
    {
      params: { status },
    },
  );
}

export function getPublishingEligibility(id: string) {
  return requestClient.get<PublishingEligibility>(
    `/ad/commercial/ad-accounts/${id}/publishing-eligibility`,
  );
}

export function getQualificationCases(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<QualificationCase>>(
    '/ad/commercial/qualification-cases',
    { params },
  );
}

export function reviewQualification(
  id: string,
  decision: 'APPROVE' | 'REJECT',
  comment?: string,
) {
  return requestClient.post(`/ad/commercial/qualification-cases/${id}/review`, {
    comment,
    decision,
  });
}

export function createQualificationCase(data: {
  caseType: string;
  orgId: string;
}) {
  return requestClient.post<QualificationCase>(
    '/ad/commercial/qualification-cases',
    data,
  );
}

export function submitQualification(id: string) {
  return requestClient.post(`/ad/commercial/qualification-cases/${id}/submit`);
}

export function suspendQualification(id: string, reason: string) {
  return requestClient.post(
    `/ad/commercial/qualification-cases/${id}/suspend`,
    undefined,
    { params: { reason } },
  );
}

export function uploadQualificationDocument(
  id: string,
  docType: string,
  file: File,
) {
  const body = new FormData();
  body.append('file', file);
  return requestClient.post<QualificationDocument>(
    `/ad/commercial/qualification-cases/${id}/documents`,
    body,
    { params: { docType } },
  );
}

export function getQualificationDocuments(id: string) {
  return requestClient.get<QualificationDocument[]>(
    `/ad/commercial/qualification-cases/${id}/documents`,
  );
}

export function getQualificationReviewLogs(id: string) {
  return requestClient.get<QualificationReviewLog[]>(
    `/ad/commercial/qualification-cases/${id}/review-logs`,
  );
}

export function downloadQualificationDocument(id: string) {
  return requestClient.get<Blob>(
    `/ad/commercial/qualification-documents/${id}/content`,
    { responseType: 'blob' },
  );
}

export function getAgreementTemplates(params: PageQuery) {
  return requestClient.get<PageResult<AgreementTemplate>>(
    '/ad/commercial/agreement-templates',
    { params },
  );
}

export function createAgreementTemplate(data: Partial<AgreementTemplate>) {
  return requestClient.post<AgreementTemplate>(
    '/ad/commercial/agreement-templates',
    data,
  );
}

export function activateAgreementTemplate(id: string) {
  return requestClient.post(
    `/ad/commercial/agreement-templates/${id}/activate`,
  );
}

export function getAgreementInstances(
  params: PageQuery & { organizationId?: string },
) {
  return requestClient.get<PageResult<AgreementInstance>>(
    '/ad/commercial/agreement-instances',
    { params },
  );
}

export function createAgreementInstance(data: {
  expiredAt?: string;
  organizationId: string;
  templateId: string;
}) {
  return requestClient.post<AgreementInstance>(
    '/ad/commercial/agreement-instances',
    data,
  );
}

export function changeAgreementInstanceStatus(
  id: string,
  action: 'sign' | 'terminate',
) {
  return requestClient.post(
    `/ad/commercial/agreement-instances/${id}/${action}`,
  );
}

export function getDataAuthorizations(
  params: PageQuery & { organizationId?: string },
) {
  return requestClient.get<PageResult<DataAuthorization>>(
    '/ad/commercial/data-authorizations',
    { params },
  );
}

export function createDataAuthorization(data: Record<string, unknown>) {
  return requestClient.post<DataAuthorization>(
    '/ad/commercial/data-authorizations',
    data,
  );
}

export function revokeDataAuthorization(id: string) {
  return requestClient.post(`/ad/commercial/data-authorizations/${id}/revoke`);
}

export function getOrganizationScopes(
  params: PageQuery & { organizationId?: string; userId?: string },
) {
  return requestClient.get<PageResult<OrganizationScope>>(
    '/ad/commercial/organization-scopes',
    { params },
  );
}

export function grantOrganizationScope(data: {
  organizationId: string;
  scopeType: 'READ' | 'WRITE';
  userId: string;
}) {
  return requestClient.post<OrganizationScope>(
    '/ad/commercial/organization-scopes',
    data,
  );
}

export function revokeOrganizationScope(id: string, organizationId: string) {
  return requestClient.delete(`/ad/commercial/organization-scopes/${id}`, {
    params: { organizationId },
  });
}
