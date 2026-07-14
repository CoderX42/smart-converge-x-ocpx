import { requestClient } from '#/api/request';

/**
 * 获取签到记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/signin-record/page', { params: query });
}

/**
 * 根据ID获取签到记录
 */
export async function getById(id: string) {
  return requestClient.get(`/user/signin-record/${id}`);
}

/**
 * 添加签到记录
 */
export async function addObj(data: any) {
  return requestClient.post('/user/signin-record', data);
}

/**
 * 编辑签到记录
 */
export async function editObj(data: any) {
  return requestClient.put('/user/signin-record', data);
}

/**
 * 删除签到记录
 */
export async function delObj(id: string) {
  return requestClient.delete(`/user/signin-record/${id}`);
}
