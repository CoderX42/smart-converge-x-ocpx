import { requestClient } from '#/api/request';

/**
 * 发送短信验证码
 */
export async function sendCode(type: string = 'login', phone: string) {
  return requestClient.get(`/system/sms/${type}/${phone}/tob`);
}
