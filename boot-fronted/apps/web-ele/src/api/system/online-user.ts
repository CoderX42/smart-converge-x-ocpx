import { requestClient } from '#/api/request';

/**
 * 获取在线用户列表
 */
export async function getList(query: any) {
  return requestClient.get('/system/onlineuser/list', { params: query });
}

/**
 * 删除在线用户
 */
export async function delObj(token: string) {
  return requestClient.delete(`/system/onlineuser/${token}`);
}
