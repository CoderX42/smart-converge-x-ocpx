import { requestClient } from '#/api/request';

/**
 * 获取订单退款分页
 */
export async function getPage(query: any) {
  return requestClient.get('/order/refund/page', { params: query });
}

/**
 * 根据ID获取订单退款信息
 */
export async function getById(id: string) {
  return requestClient.get(`/order/refund/${id}`);
}

/**
 * 添加订单退款
 */
export async function addObj(data: any) {
  return requestClient.post('/order/refund', data);
}

/**
 * 编辑订单退款
 */
export async function editObj(data: any) {
  return requestClient.put('/order/refund', data);
}

/**
 * 删除订单退款
 */
export async function delObj(id: string) {
  return requestClient.delete(`/order/refund/${id}`);
}

/**
 * 退款操作
 */
export async function refundObj(data: any) {
  return requestClient.post('/order/refund', data);
}
