import { requestClient } from '#/api/request';

/**
 * 获取订单信息分页
 */
export async function getPage(query: any) {
  return requestClient.get('/order/info/page', { params: query });
}

/**
 * 根据ID获取订单信息
 */
export async function getById(id: string) {
  return requestClient.get(`/order/info/${id}`);
}

/**
 * 添加订单信息
 */
export async function addObj(data: any) {
  return requestClient.post('/order/info', data);
}

/**
 * 编辑订单信息
 */
export async function editObj(data: any) {
  return requestClient.put('/order/info', data);
}

/**
 * 删除订单信息
 */
export async function delObj(id: string) {
  return requestClient.delete(`/order/info/${id}`);
}

/**
 * 获取订单统计信息
 */
export async function getStatistics(query: any) {
  return requestClient.get('/order/info/statistics', {
    params: query,
  });
}

/**
 * 获取订单数量
 */
export async function getCount() {
  return requestClient.get('/order/info/count');
}

/**
 * 订单发货
 */
export async function deliverOrder(data: any) {
  return requestClient.post('/order/info/deliver', data);
}

/**
 * 取消订单
 */
export async function cancelObj(id: string) {
  return requestClient.get(`/order/info/cancel/${id}`);
}

/**
 * 自提订单
 */
export async function selffetchObj(data: any) {
  return requestClient.post('/order/info/selffetch', data);
}
/**
 * 获取全部订单状态数量
 */
export async function getAllCount() {
  return requestClient.get('/order/info/status/count');
}

/**
 * 调整价格
 */
export async function adjustPriceObj(data: any) {
  return requestClient.post('/order/info/adjust-price', data);
}

/**
 * 同步物流
 */
export async function syncOrderDelivery(id: string) {
  return requestClient.get(`/order/delivery/${id}`);
}
