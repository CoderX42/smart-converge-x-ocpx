import { requestClient } from '#/api/request';

/**
 * 获取系统登录日志分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/sysloginlog/page', {
    params: query,
  });
}

/**
 * 获取登录统计信息
 */
export async function getStatistics() {
  return requestClient.get('/system/sysloginlog/statistics');
}
