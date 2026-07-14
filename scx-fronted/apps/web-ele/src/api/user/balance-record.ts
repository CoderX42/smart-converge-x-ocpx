import { requestClient } from '#/api/request';

/**
 * 获取余额记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/balance-record/page', { params: query });
}
