import { requestClient } from '#/api/request';

/**
 * 获取模板库草稿分页
 */
export async function getPage(query: any) {
  return requestClient.get('/open-platform/template-draft/page', {
    params: query,
  });
}

/**
 * 根据ID获取模板库草稿
 */
export async function getById(id: string) {
  return requestClient.get(`/open-platform/template-draft/${id}`);
}

/**
 * 添加模板库草稿
 */
export async function addObj(data: any) {
  return requestClient.post('/open-platform/template-draft', data);
}

/**
 * 编辑模板库草稿
 */
export async function editObj(data: any) {
  return requestClient.put('/open-platform/template-draft', data);
}

/**
 * 删除模板库草稿
 */
export async function delObj(id: string) {
  return requestClient.delete(`/open-platform/template-draft/${id}`);
}
/**
 * 草稿添加模板库
 */
export async function addToTemplate(draftId: string) {
  return requestClient.get(
    `/open-platform/template-draft/${draftId}/add-to-template`,
  );
}

/**
 * 同步草稿
 */
export async function syncDraft() {
  return requestClient.get(`/open-platform/template-draft/sync-draft`);
}
