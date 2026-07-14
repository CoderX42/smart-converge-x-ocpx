import { requestClient } from '#/api/request';

/**
 * 获取系统协议分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/agreement/page', { params: query });
}

/**
 * 根据ID获取系统协议
 */
export async function getById(id: string) {
  return requestClient.get(`/system/agreement/${id}`);
}

/**
 * 添加系统协议
 */
export async function addObj(data: any) {
  return requestClient.post('/system/agreement', data);
}

/**
 * 编辑系统协议
 */
export async function editObj(data: any) {
  return requestClient.put('/system/agreement', data);
}

/**
 * 删除系统协议
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/agreement/${id}`);
}
