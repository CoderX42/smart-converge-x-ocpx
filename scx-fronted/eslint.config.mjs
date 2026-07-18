// @ts-check

import { defineConfig } from '@boot/eslint-config';

export default defineConfig([
  {
    ignores: [
      'apps/web-ele/src/components/verifition/**',
      'apps/web-ele/src/views/gen/**',
      'apps/web-ele/src/views/miniapp/**',
      'apps/web-ele/src/views/open/mini-auth/**',
    ],
  },
  {
    files: ['**/*.vue'],
    rules: {
      // Prettier owns template wrapping. Enabling these Vue layout rules at the
      // same time creates non-converging --fix cycles in existing pages.
      'vue/html-closing-bracket-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      // Required props intentionally have no runtime fallback. Enforcing a
      // default conflicts with vue/no-required-prop-with-default.
      'vue/require-default-prop': 'off',
    },
  },
  {
    rules: {
      // `vsh lint` runs Prettier separately; keeping the ESLint mirror causes
      // it to fight the Vue layout fixer without adding validation coverage.
      'prettier/prettier': 'off',
      'n/prefer-global/process': 'off',
    },
  },
  {
    files: [
      'packages/effects/layouts/src/widgets/preferences/preferences-drawer.vue',
    ],
    rules: {
      // Vue macro models are consumed by the template, but the current lint
      // parser reports them as unused before template macro expansion.
      'no-unused-vars': 'off',
      'unused-imports/no-unused-vars': 'off',
    },
  },
]);
