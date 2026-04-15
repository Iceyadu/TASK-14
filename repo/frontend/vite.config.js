import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '..')
const feNm = (pkg) => path.resolve(__dirname, 'node_modules', pkg)
const coverageTarget = Number.parseInt(process.env.VITEST_COVERAGE_THRESHOLD || '95', 10)

export default defineConfig({
  plugins: [vue()],
  resolve: {
    // Specs live under ../unit_tests/frontend; bare imports must still use frontend deps
    alias: {
      vue: feNm('vue'),
      'vue-router': feNm('vue-router'),
      pinia: feNm('pinia'),
      axios: feNm('axios'),
      'crypto-js': feNm('crypto-js'),
      '@vue/test-utils': feNm('@vue/test-utils')
    }
  },
  server: {
    port: 3000,
    // Allow Vitest to load specs under ../unit_tests/frontend
    fs: {
      allow: [repoRoot]
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: [path.join(repoRoot, 'unit_tests/frontend/**/*.spec.{js,jsx}')],
    coverage: {
      enabled: true,
      provider: 'v8',
      reporter: ['text', 'html'],
      reportsDirectory: path.join(repoRoot, 'coverage/frontend'),
      include: [path.join(__dirname, 'src/**/*.{js,vue}')],
      lines: coverageTarget,
      functions: coverageTarget,
      branches: coverageTarget,
      statements: coverageTarget
    }
  }
})
