import { requestClient } from '#/api/request';

/**
 * 根据ID获取地区信息
 */
export async function getById(id: string) {
  return requestClient.get(`/system/region/${id}`, {
    headers: {
    },
  });
}

/**
 * 获取地区树
 */
export async function getTree() {
  return requestClient.get('/system/region/tree', {
    headers: {
    },
  });
}
export async function getPage(query: any) {
  return requestClient.get('/system/region/page', {
    params: query,
    headers: {
    },
  });
}

/**
 * 添加地区
 */
export async function addObj(data: any) {
  return requestClient.post('/system/region', data, {
    headers: {
    },
  });
}

/**
 * 编辑地区
 */
export async function editObj(data: any) {
  return requestClient.put('/system/region', data, {
    headers: {
    },
  });
}

/**
 * 删除地区
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/region/${id}`, {
    headers: {
    },
  });
}
