import { requestClient } from '#/api/request';

/**
 * 获取用户分页
 */
export async function getPage(query: any) {
  return requestClient.get('/system/user/page', {
    params: query,
  });
}

/**
 * 获取全部用户
 */
export async function getList(query: object) {
  return requestClient.get('/system/user/list', {
    params: query,
  });
}

/**
 * 根据ID获取用户
 */
export async function getById(id: string) {
  return requestClient.get(`/system/user/${id}`);
}

/**
 * 添加用户
 */
export async function addObj(data: any) {
  return requestClient.post('/system/user', data);
}

/**
 * 编辑用户
 */
export async function editObj(data: any) {
  return requestClient.put('/system/user', data);
}

/**
 * 删除用户
 */
export async function delObj(id: string) {
  return requestClient.delete(`/system/user/${id}`);
}

/**
 * 检查手机号数量
 */
export async function checkPhoneCount(query: any) {
  return requestClient.get('/system/user/check/phone', {
    params: query,
    headers: {
      isToken: false,
    },
  });
}

/**
 * 修改密码
 */
export type SelfPasswordPayload = {
  checkPassword: string;
  newPassword: string;
  password: string;
};

export async function editPassword(data: SelfPasswordPayload) {
  return requestClient.post(
    '/system/user/password',
    {
      checkPassword: data.checkPassword,
      newPassword: data.newPassword,
      password: data.password,
    },
    { headers: {} },
  );
}

/**
 * 绑定第三方平台（openId）
 */
export async function bindOpenId(data: any) {
  return requestClient.post('/system/user/bind-account', data, {
    headers: {},
  });
}

/**
 * 解绑第三方平台（openId）
 */
export async function unBindOpenId(data: any) {
  return requestClient.post('/system/user/unbind-account', data, {
    headers: {},
  });
}

/**
 * 修改个人信息
 */
export async function updateInfo(data: any) {
  return requestClient.post('/system/user/update-info', data, {
    headers: {},
  });
}
/**
 * 重置密码
 */
export async function resetPassword(data: any) {
  return requestClient.post('/system/user/reset-password', data);
}
