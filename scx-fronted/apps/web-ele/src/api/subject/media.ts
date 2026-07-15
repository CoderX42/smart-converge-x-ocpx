import { requestClient } from '#/api/request';

export interface Media {
  code: string;
  id: string;
  name: string;
  settlementCurrency: string;
  status: 'ACTIVE' | 'INACTIVE';
  timezone: string;
}

/** Returns only media entities authorized for the current user. */
export async function getAuthorizedMedia() {
  return requestClient.get<Media[]>('/subjects/media');
}
