import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      // 流程设计器分层（见 docs/ck-plm-flow-designer-spec.md §2.2）
      '@flow-dsl-core': path.resolve(__dirname, 'src/flow-designer/dsl-core'),
      '@flow-canvas': path.resolve(__dirname, 'src/flow-designer/canvas'),
      '@flow-compiler': path.resolve(__dirname, 'src/flow-designer/bpmn-compiler'),
      '@flow-app': path.resolve(__dirname, 'src/flow-designer/app')
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
  },
  // 只有「懒加载路由」才会用到的重依赖，必须提前声明预构建。
  // 否则 Vite 要等到第一次真正进入该路由时才发现它们 → 中途重新预构建 → 强制整页 reload；
  // 这个窗口里页面会表现为「内容区空白 + 大量模块加载失败错误」，极易被误判为业务缺陷。
  optimizeDeps: {
    include: [
      '@antv/x6',
      '@antv/x6-plugin-snapline',
      '@antv/x6-plugin-selection',
      '@antv/x6-plugin-keyboard',
      'zod'
    ]
  }
})
