import { defineConfig } from '@boot/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async ({ mode }) => {
  const isDev = mode === 'development';
  const apiProxyTarget =
    process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:9999';

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
            // Defaults to the standard backend; local MVP checks can override
            // it without editing a tracked environment file.
            target: apiProxyTarget,
            ws: true,
          },
        },
      },
    },
  };
});
