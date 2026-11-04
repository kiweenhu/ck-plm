/**
 * 流程变量读取的小工具（表单回填用）。
 *
 * <h3>为什么单独一个文件</h3>
 * <p>回填要读「流程变量」而不是组件自己的 model，而变量有两个不稳定点：
 * <ul>
 *   <li><b>形状</b>：同一份数据在不同接口里可能是 {@code {name: value}}，也可能是
 *       {@code [{name, value}]}（历史原因，见流程详情接口）；</li>
 *   <li><b>落库形态</b>：集合既可能是数组，也可能是 JSON 字符串或逗号串
 *       （Flowable 对 List 的存法不同，表单 collect() 里也有拼逗号串的）。</li>
 * </ul>
 * 这些差异不该由每张表单各写一遍 —— 写两遍就迟早有一遍只认一种形状。
 *
 * <p>放独立文件而不是 {@code taskForms/index.js}：那里 import 了全部表单组件，
 * 组件反过来 import 它会形成循环依赖。
 */

/**
 * 按名取变量值。
 *
 * @param {Record<string, unknown>|Array<{name: string, value: unknown}>|null|undefined} variables
 * @param {string} name
 * @returns {unknown|null} 没有该变量 → null
 */
export function variableOf(variables, name) {
  if (!variables) return null
  if (Array.isArray(variables)) {
    const hit = variables.find((v) => v && v.name === name)
    return hit ? hit.value : null
  }
  return variables[name] ?? null
}

/**
 * 多选值 → 数组（数组 / JSON 数组字符串两种落法）。
 *
 * @param {unknown} value
 * @returns {unknown[]}
 */
export function asArray(value) {
  if (Array.isArray(value)) return value
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

/**
 * 多选取值 → 字符串数组：数组、JSON 数组字符串、逗号串都认
 * （表单 collect() 里常把多选拼成逗号串，如 {@code ckplmTechReview_materialRefs}）。
 *
 * @param {unknown} value
 * @returns {string[]}
 */
export function asCodeList(value) {
  if (Array.isArray(value)) {
    return value.filter((v) => v != null && v !== '').map((v) => String(v))
  }
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text) return []
  const parsed = asArray(text)
  const rows = parsed.length ? parsed : text.split(',')
  return rows
    .filter((v) => v != null && v !== '')
    .map((v) => String(v).trim())
    .filter(Boolean)
}
