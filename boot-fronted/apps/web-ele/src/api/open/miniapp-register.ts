import { requestClient } from '#/api/request';

/**
 * 获取小程序注册分页
 */
export async function getPage(query: any) {
  return requestClient.get('/open-platform/miniapp-register/page', {
    params: query,
  });
}

/**
 * 获取全部小程序注册
 */
export async function getList(query: any) {
  return requestClient.get('/open-platform/miniapp-register/list', {
    params: query,
  });
}

/**
 * 根据ID获取小程序注册
 */
export async function getById(id: string) {
  return requestClient.get(`/open-platform/miniapp-register/${id}`);
}

/**
 * 添加小程序注册
 */
export async function addObj(data: any) {
  return requestClient.post('/open-platform/miniapp-register', data);
}

/**
 * 编辑小程序注册
 */
export async function editObj(data: any) {
  return requestClient.put('/open-platform/miniapp-register', data);
}

/**
 * 删除小程序注册
 */
export async function delObj(id: string) {
  return requestClient.delete(`/open-platform/miniapp-register/${id}`);
}
/**
 * 小程序注册提交
 */
export async function submit(id: string) {
  return requestClient.post(`/open-platform/miniapp-register/submit/${id}`);
}
