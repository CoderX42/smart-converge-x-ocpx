import CryptoJS from 'crypto-js';

/** 是否开启接口签名校验（请求头 X-Timestamp / X-Nonce / X-Sign） */
export function isApiSignEnabled(): boolean {
  return import.meta.env.VITE_ENABLE_API_SIGN === 'true';
}

/**
 * 生成签名（与后端约定：HMAC-SHA256，key=秘钥，message=时间戳+随机串）
 * 秘钥来自环境变量 VITE_API_SIGN_SECRET；未配置时返回 null
 */
export function createSign(): null | {
  nonce: string;
  sign: string;
  timestamp: string;
} {
  const secret = String(import.meta.env.VITE_API_SIGN_SECRET ?? '').trim();
  if (!secret) {
    return null;
  }
  const timestamp = Date.now().toString();
  const nonce = Math.random().toString(36).slice(2);
  const message = timestamp + nonce;
  const sign = CryptoJS.HmacSHA256(message, secret).toString(CryptoJS.enc.Hex);
  return { timestamp, nonce, sign };
}
