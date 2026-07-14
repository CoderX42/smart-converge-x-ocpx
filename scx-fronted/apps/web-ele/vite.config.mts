import { defineConfig } from '@boot/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async ({ mode }) => {
  const isDev = mode === 'development';

  return {
    application: {},
    vite: {
      plugins: [
        // 生产才启用按需
        !isDev &&
          ElementPlus({
            format: 'esm',
          }),
      ].filter(Boolean),
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // mock代理目标地址
            target: 'http://localhost:9999',
            ws: true,
          },
        },
      },
    },
  };
});
