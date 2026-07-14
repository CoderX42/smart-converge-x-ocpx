import { requestClient } from '#/api/request';

// 请求授权
export async function openAuth() {
  return requestClient.get('/open-platform/open-auth/goto-auth-url', {
    headers: {
      pageHost: window.location.host,
    },
  });
}
