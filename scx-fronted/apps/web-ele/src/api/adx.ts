import type { PageResult } from './business';

import { requestClient } from '#/api/request';

export type CatalogRecord = {
  createTime?: string;
  id: string;
  updateTime?: string;
};

export type Channel = CatalogRecord & {
  channelName: string;
  macroParams: string;
  secretConfigured: boolean;
  secretKey?: string;
  uri: string;
};

export type Advertiser = CatalogRecord & {
  account: string;
  advertiserName: string;
  balance: number;
  bidUrl: string;
  macroParams?: string;
  password?: string;
  secretConfigured: boolean;
  secretKey?: string;
};

export type AppLink = CatalogRecord & {
  advertiserId: string;
  advertiserName?: string;
  deeplink?: string;
  linkChannel?: string;
  linkExt?: string;
  linkName: string;
  linkPlatform: number;
  linkRemark?: string;
  linkUrl: string;
};

export type MonitorLink = CatalogRecord & {
  advertiserId: string;
  advertiserName?: string;
  appLinkId: string;
  appLinkName?: string;
  bizId?: string;
  channelId: string;
  channelName?: string;
  clickUrl?: string;
  monitorName: string;
  remark?: string;
  showUrl?: string;
  unitPrice: number;
};

export type DeductionRule = CatalogRecord & {
  appLinkId: string;
  appLinkName?: string;
  channelId: string;
  channelName?: string;
  ratio: number;
};

export type ReportRow = {
  acquisitionCount: number;
  activateCount: number;
  advertiserId: string;
  advertiserName: string;
  appLinkId: string;
  appLinkName: string;
  authenticationCount: number;
  channelId: string;
  channelName: string;
  clickRate: number;
  clicks: number;
  costAmount: number;
  deductionRatio: number;
  evokeCount: number;
  finishOrderCount: number;
  impressions: number;
  loginCount: number;
  monitorId: string;
  monitorName: string;
  nextStayCount: number;
  orderCount: number;
  paymentCount: number;
  registerCount: number;
  revenueAmount: number;
  timeBucket: string;
  visitCount: number;
};

export type Clue = CatalogRecord & {
  carMileage?: number;
  clueAreaId?: string;
  clueBrandId?: string;
  clueCarModelId?: string;
  clueCarSeriesId?: string;
  clueCityId?: string;
  clueIndex?: string;
  clueName?: string;
  cluePhone: string;
  clueStatus: number;
  firstCarRegistrationTime?: string;
  remark?: string;
  sonTaskId?: string;
  sourceType: number;
};

export type DashboardSummary = {
  sevenDayIncome: number;
  thirtyDayIncome: number;
  totalIncome: number;
  trend: Array<{ income: number; reportDate: string }>;
  yesterdayIncome: number;
};

export type ReportOptions = {
  advertisers: Advertiser[];
  appLinks: AppLink[];
  channels: Channel[];
  monitors: MonitorLink[];
};

type Query = {
  [key: string]: any;
  current?: number;
  keyword?: string;
  size?: number;
};

const endpoints = {
  advertiser: '/adx/advertisers',
  appLink: '/adx/app-links',
  channel: '/adx/channels',
  deduction: '/adx/deduction-rules',
  monitor: '/adx/monitors',
} as const;

export type CatalogKind = keyof typeof endpoints;

type KindRecord<K extends CatalogKind> = K extends 'channel'
  ? Channel
  : K extends 'advertiser'
    ? Advertiser
    : K extends 'appLink'
      ? AppLink
      : K extends 'monitor'
        ? MonitorLink
        : DeductionRule;

export function getCatalog<K extends CatalogKind>(kind: K, params: Query) {
  return requestClient.get<PageResult<KindRecord<K>>>(endpoints[kind], {
    params,
  });
}

export function saveCatalog<K extends CatalogKind>(
  kind: K,
  data: Partial<KindRecord<K>>,
) {
  return data.id
    ? requestClient.put(`${endpoints[kind]}/${data.id}`, data)
    : requestClient.post(endpoints[kind], data);
}

export function deleteCatalog(kind: CatalogKind, id: string) {
  return requestClient.delete<boolean>(`${endpoints[kind]}/${id}`);
}

export function rechargeAdvertiser(id: string, amount: number) {
  return requestClient.post(`/adx/advertisers/${id}/recharge`, { amount });
}

export function getReport(params: Query) {
  return requestClient.get<PageResult<ReportRow>>('/adx/reports/hourly', {
    params,
  });
}

export function getReportOptions() {
  return requestClient.get<ReportOptions>('/adx/report-options');
}

export function exportReport(params: Query) {
  return requestClient.get<Blob>('/adx/reports/hourly/export', {
    params,
    responseReturn: 'body',
    responseType: 'blob',
  });
}

export function getDashboardSummary() {
  return requestClient.get<DashboardSummary>('/adx/dashboard/summary');
}

export function getClues(params: Query) {
  return requestClient.get<PageResult<Clue>>('/adx/clues', { params });
}

export function saveClue(data: Partial<Clue>) {
  return data.id
    ? requestClient.put(`/adx/clues/${data.id}`, data)
    : requestClient.post('/adx/clues', data);
}

export function uploadClues(file: File) {
  const data = new FormData();
  data.append('file', file);
  return requestClient.post<number>('/adx/clues/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function updateClueStatus(ids: string[], status: number) {
  return requestClient.put<number>('/adx/clues/batch-status', { ids, status });
}

export function updateClueStatusByFilter(data: {
  cluePhone?: string;
  endDate: string;
  keyword?: string;
  sourceStatus: number;
  sourceType?: number;
  startDate: string;
  status: number;
}) {
  return requestClient.put<number>('/adx/clues/batch-status', {
    ...data,
    mode: 'FILTER',
  });
}

export function submitClues() {
  return requestClient.post<Record<string, any>>('/adx/clues/submit');
}

export function getClueTaskStatus() {
  return requestClient.get<Record<string, any>>('/adx/clues/status');
}
