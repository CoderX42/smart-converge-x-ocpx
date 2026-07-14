import { requestClient } from '#/api/request';
/**
 * 获取小程序服务器域名配置
 */
export function getServerDomain(appId: string) {
  return requestClient.get(`/open/open-mini-domain/server/${appId}`);
}

/**
 * 更新小程序服务器域名配置
 */
export function updateServerDomain(data: {
  appId: string;
  downloadDomain: string[];
  requestDomain: string[];
  uploadDomain: string[];
  wsRequestDomain: string[];
}) {
  return requestClient.post(
    `/open/open-mini-domain/server/${data.appId}`,
    data,
  );
}

/**
 * 获取小程序业务域名配置
 */
export function getBusinessDomain(appId: string) {
  return requestClient.get(`/open/open-mini-domain/business/${appId}`);
}

/**
 * 更新小程序业务域名配置
 */
export function updateBusinessDomain(data: {
  appId: string;
  webViewDomain: string[];
}) {
  return requestClient.post(
    `/open/open-mini-domain/business/${data.appId}`,
    data,
  );
}

/**
 * 下载域名校验文件
 */
export function downloadDomainVerifyFile(appId: string) {
  return requestClient.post(`/open/open-mini-domain/business-file/${appId}`);
}
