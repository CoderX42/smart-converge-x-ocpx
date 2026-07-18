import { requestClient } from '#/api/request';

/**
 * 获取系统配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/config/page', {
    params: query,
    headers: {},
  });
}

/**
 * 根据ID获取系统配置
 */
export async function getById(clientType: string) {
  return requestClient.get(`/system/config/${clientType}`, {
    headers: {},
  });
}

/**
 * 添加系统配置
 */
export async function addObj(data: any) {
  return requestClient.post('/system/config', data, {
    headers: {},
  });
}

/**
 * 编辑系统配置
 */
export async function editObj(data: any) {
  return requestClient.put('/system/config', data, {
    headers: {},
  });
}

/**
 * 删除系统配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/config/${id}`, {
    headers: {},
  });
}
