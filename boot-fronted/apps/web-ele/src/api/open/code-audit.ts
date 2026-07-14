import { requestClient } from '#/api/request';

/**
 * 获取代码审核记录分页
 */
export async function getPage(query: any) {
  return requestClient.get('/open-platform/code-audit/page', {
    params: query,
  });
}

/**
 * 获取全部代码审核记录
 */
export async function getList(query: any) {
  return requestClient.get('/open-platform/code-audit/page', {
    params: query,
  });
}
/**
 * 根据ID获取代码审核记录
 */
export async function getById(id: string) {
  return requestClient.get(`/open-platform/code-audit/${id}`);
}

/**
 * 添加代码审核记录
 */
export async function addObj(data: any) {
  return requestClient.post('/open-platform/code-audit', data);
}

/**
 * 编辑代码审核记录
 */
export async function editObj(data: any) {
  return requestClient.put('/open-platform/code-audit', data);
}

/**
 * 删除代码审核记录
 */
export async function delObj(id: string) {
  return requestClient.delete(`/open-platform/code-audit/${id}`);
}

export async function submitAudit(id: string) {
  return requestClient.post(`/open-platform/code-audit/submit/${id}`);
}

export async function getAuditStatus(id: string) {
  return requestClient.get(`/open-platform/code-audit/status/${id}`);
}
/**
 * 发布已通过小程序版本
 */
export async function releaseObj(id: string) {
  return requestClient.post(`/open-platform/code-audit/release/${id}`);
}

export async function getQrCode(id: string) {
  return requestClient.get(`/open-platform/code-audit/qrcode/${id}`);
}

export async function undoCodeAudit(id: string) {
  return requestClient.post(`/open-platform/code-audit/undo-code-audit/${id}`);
}
/**
 * 小程序版本回退
 */
export async function revertCodeRelease(id: string) {
  return requestClient.post(
    `/open-platform/code-audit/revert-code-release/${id}`,
  );
}
