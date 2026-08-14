import { initPreferences, updatePreferences } from '@boot/preferences';
import { unmountGlobalLoading } from '@boot/utils';

import { overridesPreferences } from './preferences';

import 'element-plus/dist/index.css';

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  // app偏好设置初始化
  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  // 品牌名称不属于用户偏好。每次启动都以当前构建配置为准，避免旧缓存
  // 在改名后继续覆盖导航、标题或版权区域，同时保留主题和布局等设置。
  const appTitle = import.meta.env.VITE_APP_TITLE || 'OmniLink';
  updatePreferences({
    app: { name: appTitle },
    copyright: { companyName: appTitle },
  });

  // 启动应用并挂载
  // vue应用主要逻辑及视图
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(namespace);

  // 移除并销毁loading
  unmountGlobalLoading();
}

initApplication();
