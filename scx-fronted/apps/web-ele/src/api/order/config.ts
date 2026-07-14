import { requestClient } from '#/api/request';

/**
 * 获取订单配置分页
 */
export async function getPage(query: any) {
  return requestClient.get('/order/order-config/page', { params: query });
}

/**
 * 根据ID获取订单配置
 */
export async function getById(id: string) {
  return requestClient.get(`/order/order-config/${id}`);
}

/**
 * 添加订单配置
 */
export async function addObj(data: any) {
  return requestClient.post('/order/order-config', data);
}

/**
 * 编辑订单配置
 */
export async function editObj(data: any) {
  return requestClient.put('/order/order-config', data);
}

/**
 * 删除订单配置
 */
export async function delObj(id: string) {
  return requestClient.delete(`/order/order-config/${id}`);
}
