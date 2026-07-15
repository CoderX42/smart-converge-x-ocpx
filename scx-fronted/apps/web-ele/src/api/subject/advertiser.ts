import { requestClient } from '#/api/request';

export interface Advertiser {
  code: string;
  currency: string;
  id: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE';
  timezone: string;
}

/** Returns only advertisers authorized for the current user. */
export async function getAuthorizedAdvertisers() {
  return requestClient.get<Advertiser[]>('/subjects/advertisers');
}
