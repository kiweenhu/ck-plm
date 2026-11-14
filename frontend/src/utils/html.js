/**
 * 富文本 → 纯文本（去标签、解实体、保留段落换行）。
 *
 * <p><b>为什么需要</b>：变更请求的「变更原因 / 建议方案」在页面布局里是 `rich-text` 控件，
 * 落库存的是 `<p>…</p>` 形式的 HTML；而变更通告上这两个字段是 textarea（原因只读带入、
 * 方案可改写）。直接把库里的值塞进 textarea，用户看到的就是一堆标签。
 *
 * <p><b>为什么不用 v-html 渲染</b>：通告侧本来就没有富文本编辑能力，渲染出段落格式会让
 * "能改"与"不能改格式"自相矛盾；而且 v-html 会把用户输入当 HTML 执行。
 *
 * @param {string|null|undefined} value 富文本或纯文本
 * @returns {string} 纯文本（已 trim；空值返回空串）
 */
export function htmlToText(value) {
  if (value == null || value === '') return ''
  const raw = String(value)
  // 不含标签也不含实体：直接返回。走一遍 DOMParser 反而会把 "a & b" 里的 & 处理掉
  if (!/[<&]/.test(raw)) return raw

  const withBreaks = raw
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|h[1-6]|tr)>/gi, '\n')
  // textContent 在解析阶段就会解掉 &lt; &amp; 这类实体
  const doc = new DOMParser().parseFromString(withBreaks, 'text/html')
  return (doc.body?.textContent || '')
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

/** 富文本白名单：只留排版标签，script / iframe / style / on* 事件一律去掉 */
const ALLOWED_TAGS = new Set([
  'P', 'BR', 'DIV', 'SPAN', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'DEL', 'SUB', 'SUP',
  'UL', 'OL', 'LI', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
  'A', 'IMG', 'CODE', 'PRE', 'HR', 'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD'
])
const SAFE_ATTRS = new Set(['alt', 'title', 'width', 'height'])

/**
 * 富文本消毒（配 `v-html` 使用）。
 *
 * <p>正文是用户提交的富文本，直接 `v-html` 等于让任意租户往别人的浏览器里塞脚本。
 * 这里按白名单过滤：只留排版标签与安全属性，`script` / `iframe` / `on*` 事件 / `style` 全部去掉；
 * `a[href]` 与 `img[src]` 另做协议校验（只允许 http(s) / mailto / 站内路径 / data:image）。
 *
 * <p>与 FeedbackManage 共用同一份实现：白名单散成两份，迟早一份加了标签另一份没加。
 *
 * @param {string|null|undefined} html 原始富文本
 * @returns {string} 可安全交给 v-html 的 HTML
 */
export function sanitizeHtml(html) {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(String(html), 'text/html')
  const walk = (node) => {
    Array.from(node.children).forEach((el) => {
      const tag = el.tagName
      if (!ALLOWED_TAGS.has(tag)) {
        // 不认识的标签整块丢掉（含 script / style / iframe 里的内容）
        el.remove()
        return
      }
      Array.from(el.attributes).forEach((attr) => {
        const name = attr.name.toLowerCase()
        const value = attr.value || ''
        const safeHref = tag === 'A' && name === 'href' && /^(https?:|mailto:|\/)/i.test(value)
        const safeSrc = tag === 'IMG' && name === 'src' && /^(https?:|data:image\/|\/)/i.test(value)
        if (!safeHref && !safeSrc && !SAFE_ATTRS.has(name)) {
          el.removeAttribute(attr.name)
        }
      })
      if (tag === 'A' && el.getAttribute('href')) {
        el.setAttribute('target', '_blank')
        el.setAttribute('rel', 'noopener noreferrer')
      }
      walk(el)
    })
  }
  walk(doc.body)
  return doc.body.innerHTML
}
