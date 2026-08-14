import type { Preferences } from '@boot-core/preferences';
import type { DeepPartial } from '@boot-core/typings';

/**
 * 如果你想所有的app都使用相同的默认偏好设置，你可以在这里定义
 * 而不是去修改 @boot-core/preferences 中的默认偏好设置
 * @param preferences
 * @returns
 */

function defineOverridesPreferences(preferences: DeepPartial<Preferences>) {
  preferences.app = {
    accessMode: 'backend',
    name: 'OmniLink',
  };
  preferences.theme = {
    mode: 'light',
  };
  preferences.logo = {
    enable: true,
    source: '/static/logo.png',
  };
  preferences.copyright = {
    companyName: 'OmniLink',
    companySiteLink: 'https://example.com',
    date: '2025',
    enable: true,
  };
  return preferences;
}

export { defineOverridesPreferences };

export * from '@boot-core/preferences';
