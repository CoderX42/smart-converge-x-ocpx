import { requestClient } from '#/api/request';

/**
 * 获取微信公众号分页
 */
export async function getPage(query: any) {
  return requestClient.get('/mp/mp-app/page', { params: query });
}

/**
 * 获取全部微信公众号
 */
export async function getList(query: any) {
  return requestClient.get('/mp/mp-app/list', { params: query });
}

/**
 * 根据ID获取微信公众号
 */
export async function getById(id: string) {
  return requestClient.get(`/mp/mp-app/${id}`);
}

/**
 * 添加微信公众号
 */
export async function addObj(data: any) {
  return requestClient.post('/mp/mp-app', data);
}

/**
 * 编辑微信公众号
 */
export async function editObj(data: any) {
  return requestClient.put('/mp/mp-app', data);
}

/**
 * 删除微信公众号
 */
export async function delObj(id: string) {
  return requestClient.delete(`/mp/mp-app/${id}`);
}
