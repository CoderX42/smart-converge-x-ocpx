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
  contactEmail?: string;
  contactName?: string;
  contactPhone?: string;
  industry?: string;
  orgId: string;
  orgName?: string;
  orgType?: string;
  websiteUrl?: string;
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
  expiredAt?: string;
  orgId: string;
  reviewComment?: string;
  reviewedAt?: string;
  submittedAt?: string;
};

export type AgreementTemplate = BaseRecord & {
  content: string;
  name: string;
  templateType: string;
  version: number;
};

export type AgreementInstance = BaseRecord & {
  expiredAt?: string;
  orgId: string;
  signedAt?: string;
  templateId: string;
  templateVersion: number;
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
  subjectId: string;
  subjectType: 'ORGANIZATION';
  userId: string;
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

export function deleteOrganization(id: string) {
  return requestClient.delete<boolean>(`/ad/commercial/organizations/${id}`);
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

export function deleteAdvertiser(id: string) {
  return requestClient.delete<boolean>(`/ad/commercial/advertisers/${id}`);
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

export function deleteAgencyRelation(id: string) {
  return requestClient.delete<boolean>(`/ad/commercial/agency-relations/${id}`);
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
    { params: { status } },
  );
}

export function deleteAdAccount(id: string) {
  return requestClient.delete<boolean>(`/ad/commercial/ad-accounts/${id}`);
}

export function getQualificationCases(params: PageQuery & { orgId?: string }) {
  return requestClient.get<PageResult<QualificationCase>>(
    '/ad/commercial/qualification-cases',
    { params },
  );
}

export function saveQualification(data: Partial<QualificationCase>) {
  return data.id
    ? requestClient.put(`/ad/commercial/qualification-cases/${data.id}`, {
        caseType: data.caseType,
      })
    : requestClient.post('/ad/commercial/qualification-cases', data);
}

export function submitQualification(id: string) {
  return requestClient.post(`/ad/commercial/qualification-cases/${id}/submit`);
}

export function deleteQualification(id: string) {
  return requestClient.delete<boolean>(
    `/ad/commercial/qualification-cases/${id}`,
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

export function getAgreementInstances(
  params: PageQuery & { organizationId?: string },
) {
  return requestClient.get<PageResult<AgreementInstance>>(
    '/ad/commercial/agreement-instances',
    { params },
  );
}

export function getAgreementTemplates(
  params: PageQuery & { templateType?: string },
) {
  return requestClient.get<PageResult<AgreementTemplate>>(
    '/ad/commercial/agreement-templates',
    { params },
  );
}

export function saveAgreementTemplate(data: Partial<AgreementTemplate>) {
  return data.id
    ? requestClient.put(`/ad/commercial/agreement-templates/${data.id}`, data)
    : requestClient.post('/ad/commercial/agreement-templates', data);
}

export function activateAgreementTemplate(id: string) {
  return requestClient.post(
    `/ad/commercial/agreement-templates/${id}/activate`,
  );
}

export function deleteAgreementTemplate(id: string) {
  return requestClient.delete<boolean>(
    `/ad/commercial/agreement-templates/${id}`,
  );
}

export function saveAgreementInstance(data: Partial<AgreementInstance>) {
  const payload = {
    expiredAt: data.expiredAt,
    organizationId: data.orgId,
    templateId: data.templateId,
  };
  return data.id
    ? requestClient.put(
        `/ad/commercial/agreement-instances/${data.id}`,
        payload,
      )
    : requestClient.post('/ad/commercial/agreement-instances', payload);
}

export function signAgreementInstance(id: string) {
  return requestClient.post(`/ad/commercial/agreement-instances/${id}/sign`);
}

export function terminateAgreementInstance(id: string) {
  return requestClient.post(
    `/ad/commercial/agreement-instances/${id}/terminate`,
  );
}

export function deleteAgreementInstance(id: string) {
  return requestClient.delete<boolean>(
    `/ad/commercial/agreement-instances/${id}`,
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

export function saveDataAuthorization(data: Partial<DataAuthorization>) {
  const payload = {
    authorizationType: data.authorizationType,
    endDate: data.endDate,
    granteeOrganizationId: data.granteeOrgId,
    grantorOrganizationId: data.grantorOrgId,
    scope: data.scope,
    startDate: data.startDate,
  };
  return data.id
    ? requestClient.put(
        `/ad/commercial/data-authorizations/${data.id}`,
        payload,
      )
    : requestClient.post('/ad/commercial/data-authorizations', payload);
}

export function deleteDataAuthorization(id: string) {
  return requestClient.delete<boolean>(
    `/ad/commercial/data-authorizations/${id}`,
  );
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
  userId: string;
}) {
  return requestClient.post<OrganizationScope>(
    '/ad/commercial/organization-scopes',
    data,
  );
}

export function revokeOrganizationScope(id: string, organizationId: string) {
  return requestClient.delete<boolean>(
    `/ad/commercial/organization-scopes/${id}`,
    { params: { organizationId } },
  );
}
