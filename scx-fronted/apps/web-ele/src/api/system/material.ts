import { requestClient } from '#/api/request';

/**
 * 获取素材分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/material/page', { params: query });
}

/**
 * 添加素材
 */
export async function addObj(data: any) {
  return requestClient.post('/system/material', data);
}

/**
 * 删除素材
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/material/${id}`);
}

/**
 * 编辑素材
 */
export async function editObj(data: any) {
  return requestClient.put('/system/material', data);
}
