import { requestClient } from '#/api/request';

/**
 * 获取任务分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/mission/page', { params: query });
}

/**
 * 根据ID获取任务
 */
export async function getById(id: string) {
  return requestClient.get(`/user/mission/${id}`);
}

/**
 * 添加任务
 */
export async function addObj(data: any) {
  return requestClient.post('/user/mission', data);
}

/**
 * 编辑任务
 */
export async function editObj(data: any) {
  return requestClient.put('/user/mission', data);
}

/**
 * 删除任务
 */
export async function delObj(id: string) {
  return requestClient.delete(`/user/mission/${id}`);
}
