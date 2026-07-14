import { requestClient } from '#/api/request';

/**
 * 获取三方平台账号分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/social-account/page', { params: query });
}

/**
 * 根据ID获取三方平台账号
 */
export async function getById(id: string) {
  return requestClient.get(`/system/social-account/${id}`);
}

/**
 * 添加三方平台账号
 */
export async function addObj(data: any) {
  return requestClient.post('/system/social-account', data);
}

/**
 * 编辑三方平台账号
 */
export async function editObj(data: any) {
  return requestClient.put('/system/social-account', data);
}

/**
 * 删除三方平台账号
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/social-account/${id}`);
}

/**
 * 通过类型查询三方平台账号
 */
export async function getByType(type: string) {
  return requestClient.get(`/system/social-account/type/${type}`);
}
