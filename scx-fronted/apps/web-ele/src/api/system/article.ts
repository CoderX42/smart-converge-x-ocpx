import { requestClient } from '#/api/request';

/**
 * 获取文章分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/article/page', { params: query });
}

/**
 * 根据ID获取文章
 */
export async function getById(id: string) {
  return requestClient.get(`/system/article/${id}`);
}

/**
 * 添加文章
 */
export async function addObj(data: any) {
  return requestClient.post('/system/article', data);
}

/**
 * 编辑文章
 */
export async function editObj(data: any) {
  return requestClient.put('/system/article', data);
}

/**
 * 删除文章
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/article/${id}`);
}
