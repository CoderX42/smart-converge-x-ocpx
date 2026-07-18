import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
    code?: string;
    randomStr: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    tokenValue: string;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}
/**
 * 手机号登录
 */
export async function mobileLoginApi(phone: string, code: string) {
  return requestClient.post<AuthApi.LoginResult>(
    '/auth/token/sms/login',
    {
      phone,
      code,
    },
    {
      headers: {
        isToken: false,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    },
  );
}
export async function socialLoginApi(code: string, type: string) {
  return requestClient.post<AuthApi.LoginResult>(
    '/auth/token/social/login',
    {
      code,
      type,
    },
    {
      headers: {
        isToken: false,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    },
  );
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/token/login', data, {
    headers: {
      isToken: false,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>('/auth/refresh', {
    withCredentials: true,
  });
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return requestClient.delete('/auth/token/logout', {
    headers: {},
  });
}

/**
 * 获取用户菜单
 */
export async function getAccessCodesApi() {
  return requestClient.get<any>('/system/menu');
}
