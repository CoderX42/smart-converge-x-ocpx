import { requestClient } from '#/api/request';

/**
 * 获取用户等级分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/level/page', { params: query });
}

/**
 * 获取用户等级列表
 */
export async function getList(query: any) {
  return requestClient.get('/user/level/list', { params: query });
}

/**
 * 根据ID获取用户等级
 */
export async function getById(id: string) {
  return requestClient.get(`/user/level/${id}`);
}

/**
 * 添加用户等级
 */
export async function addObj(data: any) {
  return requestClient.post('/user/level', data);
}

/**
 * 编辑用户等级
 */
export async function editObj(data: any) {
  return requestClient.put('/user/level', data);
}

/**
 * 删除用户等级
 */
export async function delObj(id: string) {
  return requestClient.delete(`/user/level/${id}`);
}
