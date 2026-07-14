import { requestClient } from '#/api/request';

/**
 * 获取充值订单分页
 */
export async function getPage(query: any) {
  return requestClient.get('/order/balance-order/page', { params: query });
}

/**
 * 根据ID获取充值订单
 */
export async function getById(id: string) {
  return requestClient.get(`/order/balance-order/${id}`);
}

/**
 * 充值订单退款
 */
export async function refundObj(data: any) {
  return requestClient.post('/order/balance-order/refunds', data);
}

/**
 * 删除充值订单
 */
export async function delObj(id: string) {
  return requestClient.delete(`/order/balance-order/${id}`);
}
