/**
 * CK-PLM 流程设计器 · 声明式字段框架（类型与纯函数）
 *
 * <p><b>为什么声明式</b>：属性面板若为每种节点手写表单，就成了「第四处」业务知识
 * （画布、校验、编译之外），必然随迭代与 DSL schema 分叉。声明式把「有哪些字段、
 * 什么控件、何时可见、是否必填」抽成数据，渲染器只有一份。
 *
 * <p>字段声明与 {@code dsl-core} 的 zod schema 是<b>同一结构的两种投影</b>：
 * schema 管「合不合法」，本文件管「怎么让人填」。两者靠 `__tests__` 的覆盖测试锁定一致。
 *
 * <p>本文件零 Vue / 零 DOM 依赖，可直接单测。
 */

import type { FlowDsl, ServiceParamOptionSource } from '@flow-dsl-core'

/** 控件类型：渲染器按此分发 */
export type FieldControl =
  /** 「设置审批人」任务表单预览（只读，不写入 DSL） */
  | 'assignee-setup-preview'
  /** 连线类型（单选而非下拉：「默认分支」必须一眼可见） */
  | 'edge-kind'
  /** 单行文本 */
  | 'text'
  /** 密码 / 密钥（输入时遮蔽，面板上不明文展示） */
  | 'password'
  /** 多行文本 */
  | 'textarea'
  /** 数字 */
  | 'number'
  /** 开关 */
  | 'switch'
  /** 单选下拉 */
  | 'select'
  /** 多选下拉 */
  | 'multi-select'
  /** 自由标签（字符串数组） */
  | 'tags'
  /** 复合：审批人策略（{@link AssigneeSpec}） */
  | 'assignee'
  /** 复合：键值映射（Record<string,string>） */
  | 'key-value'
  /** 复合：选同模板内的节点（驳回目标） */
  | 'node-ref'
  /** 复合：选内置服务 */
  | 'service-ref'
  /** 复合：可选子对象的启用开关（如 deadline / reject / binding） */
  | 'object-toggle'
  /** 复合：对象数组（如结束回调、流程变量） */
  | 'object-list'
  /** 只读提示（用于「分支条件在连线上配」这类引导） */
  | 'hint'

/**
 * 选项来源 id（由 options.ts 解析为异步加载器）。
 *
 * <p>与 dsl-core 的服务参数声明（{@link ServiceParamOptionSource}）是同一套字面量：
 * 服务声明"这个参数要从哪份清单里选"，面板负责去取那份清单 —— 声明与取数对不上时
 * 编译期就会报错，不会退化成"下拉永远是空的"。
 */
export type OptionSourceId = ServiceParamOptionSource

export interface OptionItem {
  value: string
  label: string
}

/** 当前编辑上下文 */
export interface SchemaContext {
  /** 整份 DSL（node-ref / 分支判定需要） */
  dsl: FlowDsl
  /** 被编辑对象（节点 / 连线） */
  target: Record<string, unknown>
  /** 编辑对象类别 */
  kind: 'node' | 'edge' | 'meta'
}

export interface FieldSpec {
  /** 相对目标的点号路径，如 `assignee.strategy`、`passRule.percent`、`deadline` */
  key: string
  label: string
  control: FieldControl
  /** 静态选项（与 optionsSource 二选一） */
  options?: OptionItem[]
  /** 动态选项来源（异步接口） */
  optionsSource?: OptionSourceId
  /** 来自 DSL 自身的选项（如模板已声明的流程变量、同模板内的节点） */
  optionsFromDsl?: (ctx: SchemaContext) => OptionItem[]
  placeholder?: string
  /** 字段说明（面板内的 ? 提示） */
  help?: string
  /** 是否必填（仅用于 UI 标记；权威判定在 dsl-core 的 validate） */
  required?: boolean
  min?: number
  max?: number
  /** 数字单位后缀（如「小时」「%」） */
  suffix?: string
  /** 分组标题（同组归入一个折叠区） */
  group?: string
  /** 可见性条件；未提供即始终可见 */
  visibleIf?: (target: Record<string, unknown>, ctx: SchemaContext) => boolean
  /**
   * 隐藏时是否清除取值。
   *
   * <p>用于「切换策略后清掉不相关字段」——例如把审批人从「指定用户」改成「指定角色」时，
   * 残留的 userOids 会让 DSL 变脏（编译虽只取当前策略分支，但后端防御校验与后续
   * 人工阅读都会困惑）。默认 false，避免误删用户已填内容。
   */
  clearOnHide?: boolean
  /**
   * 只读：控件可见但不可改。
   *
   * <p>用于「值由系统给定」的字段 —— 例如「设置审批人」活动的节点表单是内置固定表单，
   * 使用者没有选择的余地（让它可改只可能改错）。刻意<b>不隐藏</b>：这类字段需要让人
   * 看见「本节点用的是什么表单」。
   */
  disabled?: boolean
  /**
   * 只读字段在**取值为空时**显示的默认值（仅用于显示，不写入 DSL）。
   *
   * <p>用于「值由类型决定」的字段：老模板里的节点可能没写过该字段，
   * 若不兜底就会呈现一个「灰掉的空框，谁也改不了」。真正落库的取值仍由
   * 编译层按类型派生（见 `dsl-core` 的 `fixedFormCodeOf`）。
   */
  defaultWhenEmpty?: (ctx: SchemaContext) => unknown
  /** `object-toggle` 启用时写入的模板对象 */
  objectTemplate?: () => Record<string, unknown>
  /** `object-list` 的元素字段声明与新建模板 */
  itemFields?: FieldSpec[]
  newItem?: () => Record<string, unknown>
  /** 列表项的标题字段（用于列表头显示） */
  itemTitleKey?: string
}

// ==================== 路径读写（纯函数） ====================

/** 读取点号路径；任一层缺失返回 undefined */
export function getByPath(target: unknown, path: string): unknown {
  if (!path) {
    return target
  }
  let cursor: unknown = target
  for (const segment of path.split('.')) {
    if (cursor === null || cursor === undefined || typeof cursor !== 'object') {
      return undefined
    }
    cursor = (cursor as Record<string, unknown>)[segment]
  }
  return cursor
}

/**
 * 写入点号路径，返回<b>新对象</b>（不可变更新）。
 *
 * <p>空值（undefined / '' / null）会连带清理空壳父对象：例如把 `passRule.percent` 置空后
 * `passRule` 变成 `{}`，此时整个 `passRule` 会被删除 —— 避免 DSL 里出现
 * `passRule: {}` 这种「看着配了、其实没配」的脏数据（它会绕过「未配置」判定）。
 */
export function assignByPath<T extends Record<string, unknown>>(
  target: T,
  path: string,
  value: unknown,
): T {
  const segments = path.split('.')
  const next = { ...target }
  let cursor: Record<string, unknown> = next
  for (let i = 0; i < segments.length - 1; i += 1) {
    const segment = segments[i]
    const child = cursor[segment]
    const clone = child && typeof child === 'object' ? { ...(child as Record<string, unknown>) } : {}
    cursor[segment] = clone
    cursor = clone
  }
  const last = segments[segments.length - 1]
  if (value === undefined || value === null || value === '') {
    delete cursor[last]
  } else {
    cursor[last] = value
  }
  return pruneEmpty(next) as T
}

/** 递归清理空对象（但不清理数组下标，避免破坏顺序） */
export function pruneEmpty<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map((item) => pruneEmpty(item)) as unknown as T
  }
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
      const pruned = pruneEmpty(child)
      if (pruned === undefined || pruned === null || pruned === '') {
        continue
      }
      if (
        typeof pruned === 'object' &&
        !Array.isArray(pruned) &&
        Object.keys(pruned as Record<string, unknown>).length === 0
      ) {
        continue
      }
      result[key] = pruned
    }
    return result as unknown as T
  }
  return value
}

/** 字段当前是否可见（无 visibleIf 即可见） */
export function isFieldVisible(field: FieldSpec, ctx: SchemaContext): boolean {
  return field.visibleIf ? field.visibleIf(ctx.target, ctx) : true
}

export function visibleFields(fields: FieldSpec[], ctx: SchemaContext): FieldSpec[] {
  return fields.filter((f) => isFieldVisible(f, ctx))
}

/**
 * 清理「因可见性变化而失效」的字段，返回新目标对象。
 *
 * <p>只在 `clearOnHide` 为真时清理，避免误删用户已填内容。
 * 在每次字段写入后调用一次即可保持 DSL 干净。
 */
export function pruneHidden<T extends Record<string, unknown>>(
  fields: FieldSpec[],
  ctx: SchemaContext,
  target: T,
): T {
  let next = target
  for (const field of fields) {
    if (!field.clearOnHide) {
      continue
    }
    if (isFieldVisible(field, { ...ctx, target: next }) === false) {
      next = assignByPath(next, field.key, undefined)
    }
  }
  return next
}

/** 按 `group` 归组，保持声明顺序（便于渲染折叠区） */
export function groupFields(fields: FieldSpec[]): Array<{ group: string; fields: FieldSpec[] }> {
  const groups: Array<{ group: string; fields: FieldSpec[] }> = []
  for (const field of fields) {
    const name = field.group ?? ''
    const found = groups.find((g) => g.group === name)
    if (found) {
      found.fields.push(field)
    } else {
      groups.push({ group: name, fields: [field] })
    }
  }
  return groups
}

/** 把数组型值安全转为字符串数组（面板控件用） */
export function toStringArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => String(item))
  }
  if (typeof value === 'string' && value) {
    return [value]
  }
  return []
}

/** 把键值对象安全转为可编辑的条目数组 */
export function toKeyValueEntries(value: unknown): Array<{ key: string; value: string }> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return []
  }
  return Object.entries(value as Record<string, unknown>).map(([key, v]) => ({
    key,
    value: v === null || v === undefined ? '' : String(v),
  }))
}

export function fromKeyValueEntries(entries: Array<{ key: string; value: string }>): Record<string, string> {
  const result: Record<string, string> = {}
  for (const entry of entries) {
    const key = entry.key?.trim()
    if (key) {
      result[key] = entry.value ?? ''
    }
  }
  return result
}
