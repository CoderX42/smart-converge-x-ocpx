import { defineConfig } from '@boot/vite-config';

export default defineConfig(async () => {
  const apiProxyTarget =
    process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:9999';

  return {
    application: {},
    vite: {
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
