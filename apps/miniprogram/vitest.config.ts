import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// uni-app templates use <view>, <text>, <button>, <scroll-view> etc.
// Tell the Vue SFC compiler to treat them as native (custom) elements so
// they pass through to the DOM as-is during unit tests.
const UNI_APP_TAGS = new Set([
  'view',
  'text',
  'button',
  'image',
  'scroll-view',
  'navigator',
  'icon',
])

export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: (tag) => UNI_APP_TAGS.has(tag),
        },
      },
    }),
  ],
  test: {
    globals: true,
    environment: 'happy-dom',
    include: ['tests/**/*.test.ts'],
  },
})
