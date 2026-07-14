import { requestClient } from '#/api/request';

/**
 * 获取积分配置
 */
export async function getConfig() {
  return requestClient.get('/user/point-config');
}

/**
 * 编辑积分配置
 */
export async function editObj(data: any) {
  return requestClient.put('/user/point-config', data);
}
