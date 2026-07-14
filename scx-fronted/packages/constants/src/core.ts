/**
 * @zh_CN 登录页面 url 地址
 */
export const LOGIN_PATH = '/auth/login';

export interface LanguageOption {
  label: string;
  value: 'en-US' | 'zh-CN';
}

/**
 * Supported languages
 */
export const SUPPORT_LANGUAGES: LanguageOption[] = [
  {
    label: '简体中文',
    value: 'zh-CN',
  },
  {
    label: 'English',
    value: 'en-US',
  },
];
/**
 * @zh_CN 文档地址
 */
export const HX_DOC_URL = 'https://example.com/docs';
/**
 * @zh_CN 源码地址
 */
export const HX_GITEE_URL = 'https://example.com';
