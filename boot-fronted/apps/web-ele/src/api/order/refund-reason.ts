import { requestClient } from '#/api/request';

/**
 * 获取退款原因分页
 */
export async function getPage(query: any) {
  return requestClient.get('/order/refund-reason/page', { params: query });
}

/**
 * 根据ID获取退款原因
 */
export async function getById(id: string) {
  return requestClient.get(`/order/refund-reason/${id}`);
}

/**
 * 添加退款原因
 */
export async function addObj(data: any) {
  return requestClient.post('/order/refund-reason', data);
}

/**
 * 编辑退款原因
 */
export async function editObj(data: any) {
  return requestClient.put('/order/refund-reason', data);
}

/**
 * 删除退款原因
 */
export async function delObj(id: string) {
  return requestClient.delete(`/order/refund-reason/${id}`);
}
