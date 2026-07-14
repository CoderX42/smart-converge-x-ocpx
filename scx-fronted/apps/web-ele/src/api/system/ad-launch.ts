import { requestClient } from '#/api/request';

/**
 * 根据ID获取启动广告配置
 */
export async function getConfig() {
  return requestClient.get('/system/ad-launch');
}

/**
 * 编辑启动广告配置
 */
export async function editObj(data: any) {
  return requestClient.put('/system/ad-launch', data);
}
