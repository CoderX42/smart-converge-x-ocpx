import { requestClient } from '#/api/request';

/**
 * 获取微信认证分页
 */
export async function getPage(query: any) {
  return requestClient.get('/open-platform/open-mini-auth/page', {
    params: query,
  });
}

/**
 * 获取全部微信认证
 */
export async function getList(query: any) {
  return requestClient.get('/open-platform/open-mini-auth/page', {
    params: query,
  });
}
/**
 * 根据ID获取微信认证
 */
export async function getById(id: string) {
  return requestClient.get(`/open-platform/open-mini-auth/${id}`);
}

/**
 * 添加微信认证
 */
export async function addObj(data: any) {
  return requestClient.post('/open-platform/open-mini-auth', data);
}

/**
 * 编辑微信认证
 */
export async function editObj(data: any) {
  return requestClient.put('/open-platform/open-mini-auth', data);
}

/**
 * 删除微信认证
 */
export async function delObj(id: string) {
  return requestClient.delete(`/open-platform/open-mini-auth/${id}`);
}

/**
 * 提交微信认证
 */
export async function submit(data: any) {
  return requestClient.post('/open-platform/open-mini-auth/submit', data);
}
/**
 *  查询个人认证身份选项列表
 */
export async function getIdentityTree(appId: string) {
  return requestClient.get(
    `/open-platform/open-mini-auth/identity-tree/${appId}`,
  );
}
/**
 *  最后一次认证
 */
export async function getLastAuth(appId: string) {
  return requestClient.get(`/open-platform/open-mini-auth/last-auth/${appId}`);
}
