import { requestClient } from '#/api/request';

/**
 * 获取积分记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/point-record/page', { params: query });
}
