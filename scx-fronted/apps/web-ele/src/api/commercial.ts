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
  responsibleOrgId?: string;
};

export type QualificationCase = BaseRecord & {
  caseType: string;
  orgId: string;
  submittedAt?: string;
};

export type AgreementInstance = BaseRecord & {
  orgId: string;
  templateId: string;
  templateVersion: number;
};

export type DataAuthorization = BaseRecord & {
  authorizationType: string;
  granteeOrgId: string;
  grantorOrgId: string;
};

export function getOrganizations(
  params: PageQuery & { orgType?: string },
) {
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

export function getQualificationCases(
  params: PageQuery & { orgId?: string },
) {
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
  return requestClient.post(
    `/ad/commercial/qualification-cases/${id}/review`,
    { comment, decision },
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

export function getDataAuthorizations(
  params: PageQuery & { organizationId?: string },
) {
  return requestClient.get<PageResult<DataAuthorization>>(
    '/ad/commercial/data-authorizations',
    { params },
  );
}
