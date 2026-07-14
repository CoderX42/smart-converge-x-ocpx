import { requestClient } from '#/api/request';

/**
 * 获取短信配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/sms-config/page', {
    params: query,
  });
}

/**
 * 根据ID获取短信配置
 */
export async function getById(clientType: string) {
  return requestClient.get(`/system/sms-config/${clientType}`);
}

/**
 * 添加短信配置
 */
export async function addObj(data: any) {
  return requestClient.post('/system/sms-config', data);
}

/**
 * 编辑短信配置
 */
export async function editObj(data: any) {
  return requestClient.put('/system/sms-config', data);
}

/**
 * 删除短信配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/sms-config/${id}`);
}
