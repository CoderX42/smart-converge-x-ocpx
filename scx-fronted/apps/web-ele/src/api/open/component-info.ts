import { requestClient } from '#/api/request';

/**
 * 获取开放平台分页
 */
export async function getPage(query: any) {
  return requestClient.get('/open-platform/component-info/page', {
    params: query,
  });
}

/**
 * 获取全部开放平台
 */
export async function getList(query: any) {
  return requestClient.get('/open-platform/component-info/list', {
    params: query,
  });
}

/**
 * 根据ID获取开放平台
 */
export async function getById(id: string) {
  return requestClient.get(`/open-platform/component-info/${id}`);
}

/**
 * 添加开放平台
 */
export async function addObj(data: any) {
  return requestClient.post('/open-platform/component-info', data);
}

/**
 * 编辑开放平台
 */
export async function editObj(data: any) {
  return requestClient.put('/open-platform/component-info', data);
}

/**
 * 删除开放平台
 */
export async function delObj(id: string) {
  return requestClient.delete(`/open-platform/component-info/${id}`);
}
/**
 * 刷新已授权账号
 */
export async function refreshAuthAccount() {
  return requestClient.post('/open-platform/component-info/refresh-auth');
}
