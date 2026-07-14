import { requestClient } from '#/api/request';

// 获取小程序基本信息
export async function getOpenMiniBase(appId: string) {
  return requestClient.get(`/open-platform/open-mini-base/info/${appId}`);
}
