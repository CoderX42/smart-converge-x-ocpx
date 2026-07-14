import { requestClient } from '#/api/request';

/**
 * 用户统计-用户来源
 */
export async function getUserSourceStatistics(query: any) {
  return requestClient.get('/user/userinfo/source/statistics', {
    params: query,
  });
}

/**
 * 用户趋势统计 (新增用户)
 */
export async function getUserOverview(query: any) {
  return requestClient.get('/user/user/statistics/overview', {
    params: query,
  });
}
