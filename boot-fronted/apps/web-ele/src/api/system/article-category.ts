import { requestClient } from '#/api/request';

/**
 * 获取文章分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/article-category/page', { params: query });
}
/**
 * 获取全部文章
 */
export async function getList(query: any) {
  return requestClient.get('/system/article-category/list', { params: query });
}

/**
 * 根据ID获取文章
 */
export async function getById(id: string) {
  return requestClient.get(`/system/article-category/${id}`);
}

/**
 * 添加文章
 */
export async function addObj(data: any) {
  return requestClient.post('/system/article-category', data);
}

/**
 * 编辑文章
 */
export async function editObj(data: any) {
  return requestClient.put('/system/article-category', data);
}

/**
 * 删除文章
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/article-category/${id}`);
}
