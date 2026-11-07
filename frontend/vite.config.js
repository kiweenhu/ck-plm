import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import { readFileSync, existsSync, statSync, createReadStream, mkdirSync, copyFileSync } from 'fs'
import { execSync } from 'node:child_process'

const pkg = JSON.parse(readFileSync(path.resolve(__dirname, 'package.json'), 'utf-8'))

/**
 * 把仓库根 docs/ 下的文档挂到前端静态路径 /docs/ 下，供「使用说明」弹窗 iframe 直接引用。
 *
 * <p>dev 用中间件直接读 docs（不复制）；build 时复制进 dist/docs 交给静态服务器。
 * 文档只在 docs/ 里存一份 —— 复制一份到 public/ 会立刻出现"改了 md 忘了同步 html"的漂移。
 */
const DOCS_DIR = path.resolve(__dirname, '../docs')
const DOC_FILES = ['ck-plm-user-guide.html']

function docsPlugin() {
  return {
    name: 'ckplm-docs',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (!req.url || !req.url.startsWith('/docs/')) return next()
        const rel = decodeURIComponent(req.url.split('?')[0].slice('/docs/'.length))
        const file = path.resolve(DOCS_DIR, rel)
        if (!file.startsWith(DOCS_DIR) || !existsSync(file) || !statSync(file).isFile()) return next()
        res.setHeader('Content-Type',
          file.endsWith('.html') ? 'text/html; charset=utf-8' : 'application/octet-stream')
        createReadStream(file).pipe(res)
      })
    },
    closeBundle() {
      const outDir = path.resolve(__dirname, 'dist/docs')
      mkdirSync(outDir, { recursive: true })
      DOC_FILES.forEach((name) => {
        const src = path.resolve(DOCS_DIR, name)
        // docs/ 不进版本库（.gitignore 排除），新克隆的仓库里没有这些文档 ——
        // 跳过而不是让 copyFileSync 抛 ENOENT 把整个构建搞失败。
        // 「使用说明」入口届时指向 404，但不影响其余功能。
        if (!existsSync(src)) {
          console.warn(`[ckplm-docs] 跳过缺失文档（docs/ 未入库）: docs/${name}`)
          return
        }
        copyFileSync(src, path.resolve(outDir, name))
        // 只带这份 html 真正引用的 images/ 图片：docs/images 里还有几十张架构图，
        // 整目录复制会白白多出 10MB 静态资源
        const html = readFileSync(src, 'utf-8')
        const assets = [...html.matchAll(/src="(images\/[^"]+)"/g)].map(m => m[1])
        assets.forEach((rel) => {
          const from = path.resolve(DOCS_DIR, rel)
          if (!existsSync(from) || !statSync(from).isFile()) return
          const to = path.resolve(outDir, rel)
          mkdirSync(path.dirname(to), { recursive: true })
          copyFileSync(from, to)
        })
      })
    }
  }
}

/**
 * 版本号以 git tag 为准（本地 tag 与 GitHub 上的 tag 同源），界面（登录页 / 关于）用 __APP_VERSION__ 引用。
 *
 * <p>以前只认 package.json 的 version，于是出现了"关于"里 v0.2.1、GitHub 最新 tag 已是 v0.3.1 的漂移。
 * 现在发版打 tag 即完成对齐，无需再手改 package.json；构建环境没有 .git / 没有 tag 时才退回 package.json。
 */
function resolveAppVersion() {
  try {
    const tag = execSync('git describe --tags --abbrev=0', {
      cwd: path.resolve(__dirname, '..'),
      stdio: ['ignore', 'pipe', 'ignore'],
    }).toString().trim()
    return tag.replace(/^v/, '') || pkg.version
  } catch {
    return pkg.version
  }
}

const appVersion = resolveAppVersion()

export default defineConfig({
  plugins: [vue(), docsPlugin()],
  define: {
    __APP_VERSION__: JSON.stringify(appVersion)
  },
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
