/**
 * 开发期错误可视面（仅 DEV 生效）。
 *
 * <p><b>为什么需要它</b>：Vue 的渲染错误只会打到控制台，页面表现是「内容区整片空白」。
 * 一旦这种状态出现，从页面本身得不到任何线索（我们为此付出的代价是：
 * 只能靠截图里的 94 个错误计数去猜）。本模块把错误<b>直接画在页面上</b>：
 * 首次错误即弹出浮层，列出错误消息、来源与堆栈首行，并带复制按钮。
 *
 * <p>刻意不吞掉错误：仍然 `console.error`，只是多了一条「能看见」的通道。
 * 生产环境完全不挂载（由调用方以 `import.meta.env.DEV` 控制）。
 */

const MAX_ITEMS = 8
const seen = new Set()

/** 收集到的错误条目 */
const items = []

let panel = null
let list = null

function ensurePanel() {
  if (panel) {
    return panel
  }
  panel = document.createElement('div')
  panel.setAttribute('data-dev-error-overlay', 'true')
  panel.style.cssText = [
    'position:fixed',
    'left:12px',
    'right:12px',
    'top:12px',
    'z-index:99999',
    'max-height:52vh',
    'overflow:auto',
    'background:#fff2f0',
    'border:2px solid #ff4d4f',
    'border-radius:8px',
    'padding:10px 12px',
    'font:12px/1.6 Menlo,Consolas,monospace',
    'color:#a8071a',
    'box-shadow:0 8px 24px rgba(0,0,0,.18)',
    'text-align:left',
  ].join(';')

  const head = document.createElement('div')
  head.style.cssText = 'display:flex;align-items:center;gap:8px;margin-bottom:6px;font-weight:700;font-size:13px'

  const title = document.createElement('span')
  title.textContent = '页面发生错误（开发期浮层）'

  const copy = document.createElement('button')
  copy.textContent = '复制全部'
  copy.style.cssText = 'margin-left:auto;padding:2px 8px;border:1px solid #ff4d4f;background:#fff;color:#a8071a;border-radius:4px;cursor:pointer'
  copy.onclick = () => {
    const text = items.join('\n\n---\n\n')
    navigator.clipboard?.writeText(text)
    copy.textContent = '已复制'
    setTimeout(() => (copy.textContent = '复制全部'), 1200)
  }

  const close = document.createElement('button')
  close.textContent = '隐藏'
  close.style.cssText = 'padding:2px 8px;border:1px solid #ffa39e;background:#fff;color:#a8071a;border-radius:4px;cursor:pointer'
  close.onclick = () => panel.remove()

  head.append(title, copy, close)

  list = document.createElement('div')
  panel.append(head, list)
  document.body.appendChild(panel)
  return panel
}

/**
 * 记录一条错误并显示。
 *
 * @param {string} source 来源（如 `vue:render` / `window.onerror` / `unhandledrejection`）
 * @param {unknown} error 错误对象或任意值
 * @param {string} [info] Vue 传入的生命周期信息（如 `render function`）
 */
export function reportDevError(source, error, info) {
  const message = error && error.message ? error.message : String(error)
  const stack = error && error.stack ? String(error.stack).split('\n').slice(0, 4).join('\n') : ''
  // 去重：同一错误反复触发（渲染循环）只留一条，避免刷屏
  const key = `${source}|${message}`
  if (seen.has(key) || items.length >= MAX_ITEMS) {
    return
  }
  seen.add(key)

  const text = [`[${source}${info ? ` · ${info}` : ''}] ${message}`, stack].filter(Boolean).join('\n')
  items.push(text)

  ensurePanel()
  const block = document.createElement('pre')
  block.style.cssText = 'white-space:pre-wrap;word-break:break-all;margin:0 0 8px;padding-bottom:8px;border-bottom:1px dashed #ffccc7'
  block.textContent = text
  list.appendChild(block)
}

/**
 * 安装全局错误钩子。
 *
 * @param {import('vue').App} app Vue 应用实例
 */
export function installDevErrorOverlay(app) {
  app.config.errorHandler = (error, _instance, info) => {
    reportDevError('vue', error, info)
    console.error('[dev-error-overlay]', info, error)
  }

  window.addEventListener('error', (event) => {
    reportDevError('window.onerror', event.error ?? event.message)
  })

  window.addEventListener('unhandledrejection', (event) => {
    reportDevError('unhandledrejection', event.reason)
  })
}
