/**
 * 自定义变量列表的纯逻辑 —— 面板渲染之外的判断都放这里。
 *
 * <p>之所以抽出来：这些判断有具体边界（名字为空、与已有变量重名、默认值取 0），
 * 埋在组件的弹框回调里就只能靠手点去验；抽成纯函数后可以直接单测。
 */
import type { VariableDef } from '@flow-dsl-core'

/**
 * 变量行摘要：把"除名字之外还设了什么"压成一行。
 *
 * <p>与内置变量行的说明同一位置、同一字号 —— 两栏并排时阅读节奏一致，
 * 也能一眼看出这个变量是不是只声明了个名字。
 */
export function variableSummary(variable: VariableDef): string {
  const parts: string[] = []
  if (variable.label) {
    parts.push(`显示名 ${variable.label}`)
  }
  // 默认值 0 / false 是有效取值，不能用 falsy 判断（那会把它们当成"没设"）
  if (variable.defaultValue !== undefined && variable.defaultValue !== null && variable.defaultValue !== '') {
    parts.push(`默认值 ${variable.defaultValue}`)
  }
  if (variable.visible) {
    parts.push('任务页可见')
  }
  if (variable.readonly) {
    parts.push('只读')
  }
  if (variable.writable) {
    parts.push('允许任务中重写')
  }
  return parts.length ? parts.join(' · ') : '未设置显示名 / 默认值'
}

/**
 * 变量名校验，返回错误文案（`null` = 通过）。
 *
 * <p>两条规则：必填（DSL schema 要求 `min(1)`）、不与其它变量重名
 * （重名在 DSL 里合法但毫无意义 —— 节点引用到的是哪一个看不出来）。
 *
 * <p>与<b>内置变量</b>同名不在这里拦：那由 `dsl-core` 的校验给出 WARNING
 * （它是"配了也不生效"，不阻断保存），弹框里另给一行提示。
 *
 * @param editingIndex 正在编辑的下标（-1 = 新增）；编辑自己不算重名
 */
export function variableNameError(
  name: string,
  list: VariableDef[],
  editingIndex = -1,
): string | null {
  const trimmed = name.trim()
  if (!trimmed) {
    return '请填写变量名'
  }
  const duplicated = list.some((v, i) => v.name === trimmed && i !== editingIndex)
  return duplicated ? `变量名「${trimmed}」已存在` : null
}

/**
 * 应用弹框草稿，返回新的变量列表（不可变）。
 *
 * <p>名字统一去空格：前后带空格的名字在 `${...}` 引用里根本写不出来。
 *
 * @param index 编辑位置（-1 = 追加）
 */
export function applyVariableDraft(
  list: VariableDef[],
  draft: VariableDef,
  index: number,
): VariableDef[] {
  const item: VariableDef = { ...draft, name: draft.name.trim() }
  const next = [...list]
  if (index >= 0) {
    next[index] = item
  } else {
    next.push(item)
  }
  return next
}
