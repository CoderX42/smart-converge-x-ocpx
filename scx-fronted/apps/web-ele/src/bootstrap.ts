import { createApp, watchEffect } from 'vue';

import { registerAccessDirective } from '@boot/access';
import { registerLoadingDirective } from '@boot/common-ui';
import { preferences } from '@boot/preferences';
import { initStores } from '@boot/stores';
import '@boot/styles';
import '@boot/styles/ele';

import { useTitle } from '@vueuse/core';
import ElementPlus from 'element-plus';

import { $t, setupI18n } from '#/locales';

import { initComponentAdapter } from './adapter/component';
import { initSetupBootForm } from './adapter/form';
import App from './app.vue';
import { router } from './router';

async function bootstrap(namespace: string) {
  // 初始化组件适配器
  await initComponentAdapter();

  // 初始化表单组件
  await initSetupBootForm();

  // // 设置弹窗的默认配置
  // setDefaultModalProps({
  //   fullscreenButton: false,
  // });
  // // 设置抽屉的默认配置
  // setDefaultDrawerProps({
  //   zIndex: 2000,
  // });
  const app = createApp(App);

  // Element Plus installs its v-loading directive with the plugin.
  app.use(ElementPlus);

  // 注册Boot提供的v-loading和v-spinning指令
  registerLoadingDirective(app, {
    loading: false, // Boot提供的v-loading指令和Element Plus提供的v-loading指令二选一即可，此处false表示不注册Boot提供的v-loading指令
    spinning: 'spinning',
  });

  // 国际化 i18n 配置
  await setupI18n(app);

  // 配置 pinia-tore
  await initStores(app, { namespace });

  // 安装权限指令
  registerAccessDirective(app);

  // 初始化 tippy
  const { initTippy } = await import('@boot/common-ui/es/tippy');
  initTippy(app);

  // 配置路由及路由守卫
  app.use(router);

  // 配置Motion插件
  const { MotionPlugin } = await import('@boot/plugins/motion');
  app.use(MotionPlugin);

  // 动态更新标题
  watchEffect(() => {
    if (preferences.app.dynamicTitle) {
      const routeTitle = router.currentRoute.value.meta?.title;
      const pageTitle =
        (routeTitle ? `${$t(routeTitle)} - ` : '') + preferences.app.name;
      useTitle(pageTitle);
    }
  });

  app.mount('#app');
}

export { bootstrap };
