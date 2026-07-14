import { requestClient } from '#/api/request';

/**
 * 获取开放平台应用列表
 */
export async function getPage(query: any) {
  return requestClient.get('/open-platform/openPlatformApp/page', {
    params: query,
  });
}

/**
 * 获取全部开放平台应用
 */
export async function getList(query: any) {
  return requestClient.get('/open-platform/openPlatformApp/list', {
    params: query,
  });
}

/**
 * 根据ID获取开放平台应用
 */
export async function getById(id: string) {
  return requestClient.get(`/open-platform/openPlatformApp/${id}`);
}

/**
 * 添加微信开放平台应用
 */
export async function addObj(data: any) {
  return requestClient.post('/open-platform/openPlatformApp', data);
}

/**
 * 编辑微信开放平台应用
 */
export async function editObj(data: any) {
  return requestClient.put('/open-platform/openPlatformApp', data);
}

/**
 * 删除微信开放平台应用
 */
export async function delObj(id: string) {
  return requestClient.delete(`/open-platform/openPlatformApp/${id}`);
}

/**
 * 转正小程序
 */
export async function convertToFormal(id: string) {
  return requestClient.put(`/open-platform/wxapp/${id}/convert`);
}

/**
 * 获取微信小程序码
 */
export async function getQrCode(data: any) {
  return requestClient.post('/open-platform/wx/qrcode/get', data);
}
