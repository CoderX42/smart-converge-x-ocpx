import { requestClient } from '#/api/request';

/**
 * 获取签到配置
 */
export async function getConfig() {
  return requestClient.get('/user/signin-config');
}

/**
 * 编辑签到配置
 */
export async function editObj(data: any) {
  return requestClient.put('/user/signin-config', data);
}
