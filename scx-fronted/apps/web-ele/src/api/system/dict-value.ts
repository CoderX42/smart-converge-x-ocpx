import { requestClient } from '#/api/request';

/**
 * 获取字典值分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/dictvalue/page', {
    params: query,
    headers: {},
  });
}

/**
 * 根据ID获取字典值
 */
export async function getById(id: string) {
  return requestClient.get(`/system/dictvalue/${id}`, {
    headers: {},
  });
}

/**
 * 添加字典值
 */
export async function addObj(data: any) {
  return requestClient.post('/system/dictvalue', data, {
    headers: {},
  });
}

/**
 * 编辑字典值
 */
export async function editObj(data: any) {
  return requestClient.put('/system/dictvalue', data, {
    headers: {},
  });
}

/**
 * 删除字典值
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/dictvalue/${id}`, {
    headers: {},
  });
}

/**
 * 根据类型获取字典值列表
 */
export async function getList(type: string) {
  return requestClient.get(`/system/dictvalue/type/${type}`, {
    headers: {},
  });
}
