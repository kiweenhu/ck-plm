import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 8080,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        timeout: 60000,
      },
      // 代理文件资源请求（路径格式：/{tenantOid}/{category}/{fileName}）
      '^/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      }
    }
  }
})
