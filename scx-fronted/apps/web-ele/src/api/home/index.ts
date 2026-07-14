import { requestClient } from '#/api/request';
/**
 * 获取用户数量
 */
export async function getUserCount(query: any) {
  return requestClient.get('/user/userinfo/statistics', { params: query });
}

/**
 * 获取业务范围数量
 */
export async function getShopCount(query: any) {
  return requestClient.get('/shop/shopinfo/count', { params: query });
}
/**
 * 获取订单数量
 */
export async function getOrderCount(query: any) {
  return requestClient.get('/order/orderinfo/count', { params: query });
}
/**
 * 获取业务对象数量
 */
export async function getGoodsCount(query: any) {
  return requestClient.get('/product/goodsspu/count', { params: query });
}
/**
 * 订单统计-销售趋势
 */
export async function getOrderStatistics(query: any) {
  return requestClient.get('/order/orderinfo/statistics', {
    params: query,
  });
}
/**
 * 订单统计-支付类型
 */
export async function getPayTypeStatistics(query: any) {
  return requestClient.get('/order/orderinfo/pay-type/statistics', {
    params: query,
  });
}
/**
 * 订单统计-下单端
 */
export async function getChannelTypeStatistics(query: any) {
  return requestClient.get('/order/orderinfo/channel-type/statistics', {
    params: query,
  });
}
/**
 * 用户统计-用户来源
 */
export async function getUserSourceStatistics(query: any) {
  return requestClient.get('/user/userinfo/source/statistics', {
    params: query,
  });
}
