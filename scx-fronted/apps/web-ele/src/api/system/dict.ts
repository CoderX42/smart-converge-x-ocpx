import { requestClient } from '#/api/request';

/**
 * 获取字典分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/dict/page', {
    params: query,
    headers: {},
  });
}

/**
 * 根据ID获取字典
 */
export async function getById(id: string) {
  return requestClient.get(`/system/dict/${id}`, {
    headers: {},
  });
}

/**
 * 添加字典
 */
export async function addObj(data: any) {
  return requestClient.post('/system/dict', data, {
    headers: {},
  });
}

/**
 * 编辑字典
 */
export async function editObj(data: any) {
  return requestClient.put('/system/dict', data, {
    headers: {},
  });
}

/**
 * 删除字典
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/dict/${id}`, {
    headers: {},
  });
}

/**
 * 刷新字典缓存
 */
export async function refresh() {
  return requestClient.get('/system/dict/refresh', {
    headers: {},
  });
}
