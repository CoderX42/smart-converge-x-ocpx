import type { UserInfo } from '@boot/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/system/user/info', {
    headers: {
    },
  });
}
