import { requestClient } from '#/api/request';

/**
 * 获取连续签到奖励分页
 */
export async function getPage(query: any) {
  return requestClient.get('/user/signin-streak-reward/page', {
    params: query,
  });
}

/**
 * 根据ID获取连续签到奖励
 */
export async function getById(id: string) {
  return requestClient.get(`/user/signin-streak-reward/${id}`);
}

/**
 * 添加连续签到奖励
 */
export async function addObj(data: any) {
  return requestClient.post('/user/signin-streak-reward', data);
}

/**
 * 编辑连续签到奖励
 */
export async function editObj(data: any) {
  return requestClient.put('/user/signin-streak-reward', data);
}

/**
 * 删除连续签到奖励
 */
export async function delObj(id: string) {
  return requestClient.delete(`/user/signin-streak-reward/${id}`);
}
