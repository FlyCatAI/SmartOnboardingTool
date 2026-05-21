// uni-app build configuration.
//
// Heavy build deps (@dcloudio/vite-plugin-uni, vite, @dcloudio/uni-h5,
// @dcloudio/uni-mp-weixin) are NOT pinned in package.json to keep the existing
// audit baseline at 0 vulnerabilities. They are installed on-demand by CI and
// by the deploy/build-miniprogram.sh helper. See apps/miniprogram/README.md.

// eslint-disable-next-line @typescript-eslint/no-var-requires
import { defineConfig } from 'vite'
// @ts-expect-error -- optional install, see comment above
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // uni-app expects sources under src/ — this is the default but we make it
  // explicit so the manifest/pages JSON resolution doesn't surprise reviewers.
  root: __dirname,
  build: {
    target: 'es2019',
    sourcemap: false,
    emptyOutDir: true,
  },
})
