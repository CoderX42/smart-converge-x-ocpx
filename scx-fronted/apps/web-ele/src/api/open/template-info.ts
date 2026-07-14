import { requestClient } from '#/api/request';

/**
 * 获取模板库模板分页
 */
export async function getPage(query: any) {
  return requestClient.get('/open-platform/template-info/page', {
    params: query,
  });
}

/**
 * 获取全部模板库模板
 */
export async function getList(query: any) {
  return requestClient.get('/open-platform/template-info/list', {
    params: query,
  });
}
/**
 * 根据ID获取模板库模板
 */
export async function getById(id: string) {
  return requestClient.get(`/open-platform/template-info/${id}`);
}

/**
 * 添加模板库模板
 */
export async function addObj(data: any) {
  return requestClient.post('/open-platform/template-info', data);
}

/**
 * 编辑模板库模板
 */
export async function editObj(data: any) {
  return requestClient.put('/open-platform/template-info', data);
}

/**
 * 删除模板库模板
 */
export async function delObj(id: string) {
  return requestClient.delete(`/open-platform/template-info/${id}`);
}

/**
 * 同步模板
 */
export async function sync() {
  return requestClient.get(`/open-platform/template-info/sync`);
}

export async function submitVersion(data: any) {
  return requestClient.post(
    `/open-platform/template-info/submit-version`,
    data,
  );
}
