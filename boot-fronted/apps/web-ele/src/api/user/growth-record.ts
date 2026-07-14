import { requestClient } from '#/api/request';

/**
 * 获取成长记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/growth-record/page', { params: query });
}
