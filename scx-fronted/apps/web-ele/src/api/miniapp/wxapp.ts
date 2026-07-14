import { requestClient } from '#/api/request';

/**
 * 获取微信小程序分页
 */
export async function getPage(query: any) {
  return requestClient.get('/miniapp/wxapp/page', { params: query });
}

/**
 * 获取全部微信小程序
 */
export async function getList(query: any) {
  return requestClient.get('/miniapp/wxapp/list', { params: query });
}

/**
 * 根据ID获取微信小程序
 */
export async function getById(id: string) {
  return requestClient.get(`/miniapp/wxapp/${id}`);
}

/**
 * 添加微信小程序
 */
export async function addObj(data: any) {
  return requestClient.post('/miniapp/wxapp', data);
}

/**
 * 编辑微信小程序
 */
export async function editObj(data: any) {
  return requestClient.put('/miniapp/wxapp', data);
}

/**
 * 删除微信小程序
 */
export async function delObj(id: string) {
  return requestClient.delete(`/miniapp/wxapp/${id}`);
}

/**
 * 转正小程序
 */
export async function convertToFormal(id: string) {
  return requestClient.put(`/miniapp/wxapp/${id}/convert`);
}

/**
 * 获取微信小程序码
 */
export async function getQrCode(data: any) {
  return requestClient.post('/miniapp/wx/qrcode/get', data);
}
