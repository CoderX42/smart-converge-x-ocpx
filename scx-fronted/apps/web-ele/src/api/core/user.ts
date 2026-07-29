import type { UserInfo } from '@boot/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/system/user/info', {
    headers: {},
    // Route initialization waits for this request when a persisted token
    // exists. Fail fast enough to recover to the login page instead of
    // leaving the application blank behind the global loading shell.
    timeout: 10_000,
  });
}
