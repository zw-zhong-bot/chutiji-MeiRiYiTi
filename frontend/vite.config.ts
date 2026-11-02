import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 开发期用 proxy 把 /api 转到后端，避免 CORS。
const BACKEND = 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: true,
      },
    },
  },
})
