import { requestClient } from '#/api/request';

/**
 * 获取短信发送记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/sms-send-record/page', {
    params: query,
  });
}

/**
 * 根据ID获取短信发送记录
 */
export async function getById(clientType: string) {
  return requestClient.get(`/system/sms-send-record/${clientType}`);
}
