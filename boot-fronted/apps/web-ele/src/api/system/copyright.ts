import { requestClient } from '#/api/request';

/**
 * 获取系统版权分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/copyright/page', {
    params: query,
    headers: {
    },
  });
}

/**
 * 根据ID获取系统版权
 */
export async function getById(clientType: string) {
  return requestClient.get(`/system/copyright/${clientType}`, {
    headers: {
    },
  });
}

/**
 * 添加系统版权
 */
export async function addObj(data: any) {
  return requestClient.post('/system/copyright', data, {
    headers: {
    },
  });
}

/**
 * 编辑系统版权
 */
export async function editObj(data: any) {
  return requestClient.put('/system/copyright', data, {
    headers: {
    },
  });
}

/**
 * 删除系统版权
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/copyright/${id}`, {
    headers: {
    },
  });
}
/**
 * 获取系统版权
 */
export async function getCopyright(clientType: string) {
  return requestClient.get(`/system/copyright/get/${clientType}`, {
    headers: {
    },
  });
}
