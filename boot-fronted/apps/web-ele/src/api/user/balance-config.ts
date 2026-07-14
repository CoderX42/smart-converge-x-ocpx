import { requestClient } from '#/api/request';

/**
 * 获取余额配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/balanceconfig/page', { params: query });
}

/**
 * 根据ID获取余额配置
 */
export async function getById(id: string) {
  return requestClient.get(`/user/balanceconfig/${id}`);
}

/**
 * 添加余额配置
 */
export async function addObj(data: any) {
  return requestClient.post('/user/balanceconfig', data);
}

/**
 * 编辑余额配置
 */
export async function editObj(data: any) {
  return requestClient.put('/user/balanceconfig', data);
}

/**
 * 删除余额配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/user/balanceconfig/${id}`);
}
