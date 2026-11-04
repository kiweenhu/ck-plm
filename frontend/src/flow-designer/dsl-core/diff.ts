/**
 * CK-PLM 流程 DSL · 版本结构对比（M3「版本 diff」）。
 *
 * <p><b>为什么自己写而不是引 diff 库</b>：通用的文本 / JSON diff 只能告诉你
 * "第 37 行从 A 变成 B"，而流程模板的读者是业务设计者 —— 他们要的是
 * 「删了 1 个节点」「审批人从『指定角色』改成『部门主管』」。树 diff 库（如 jsdiff 的
 * JSON 模式）同样给不出领域词汇，还得再写一层映射；不如直接按 <b>id 配对 + 字段展开</b>
 * 做一次语义对比，代码量与依赖都更少。
 *
 * <p><b>三条口径</b>：
 * <ol>
 *   <li><b>按身份配对</b>：节点按 {@code id}、连线按 {@code id}、流程变量按 {@code name}
 *       配对 —— 改名是"修改"，不是"删一个再建一个"（后者会把一次改名报成两条差异）。</li>
 *   <li><b>只比语义，不比表现</b>：{@code layout}（坐标 / 尺寸）、连线的 {@code anchor} /
 *       {@code waypoints} 都是画布表现，挪一下节点不该出现在"这个版本改了什么"里。</li>
 *   <li><b>嵌套对象展开到字段级</b>：{@code assignee} / {@code passRule} / {@code deadline} /
 *       {@code condition} / {@code notifications} 逐字段比，输出
 *       {@code 审批人·策略：指定角色 → 部门主管} 这种能直接读的结论，而不是一坨 JSON。</li>
 * </ol>
 *
 * <p>纯函数、零 DOM / 零运行时依赖，可直接单测。
 */

import {
  ABSTAIN_POLICY_LABEL,
  APPROVAL_MODE_LABEL,
  ASSIGNEE_STRATEGY_LABEL,
  CONDITION_OPERATOR_LABEL,
  DEADLINE_ANCHOR_LABEL,
  EDGE_ROUTE_LABEL,
  END_CALLBACK_KIND_LABEL,
  NODE_TYPE_LABEL,
  NOTIFY_CHANNEL_LABEL,
  OA_AUTH_TYPE_LABEL,
  OVERDUE_ACTION_LABEL,
  PASS_RULE_MODE_LABEL,
  REJECT_TARGET_LABEL,
  TIMER_MODE_LABEL,
} from './constants'
import type { FlowDsl, FlowEdge, FlowNode, VariableDef } from './schema'

// ==================== 类型 ====================

/** 差异种类：新增 / 删除 / 修改 */
export const DiffKind = {
  ADDED: 'ADDED',
  REMOVED: 'REMOVED',
  CHANGED: 'CHANGED',
} as const
export type DiffKind = (typeof DiffKind)[keyof typeof DiffKind]

export const DIFF_KIND_LABEL: Record<DiffKind, string> = {
  ADDED: '新增',
  REMOVED: '删除',
  CHANGED: '修改',
}

/** 字段级变化（{@code path} 为点分路径，如 {@code assignee.strategy}） */
export interface FieldChange {
  path: string
  /** 人类可读的字段名（如「审批人·策略」） */
  label: string
  before: unknown
  after: unknown
}

/** 节点差异 */
export interface NodeDiff {
  id: string
  /** 差异的显示名：新增/删除取对应版本的名称，修改取改动后的名称 */
  name: string
  type: string
  kind: DiffKind
  changes: FieldChange[]
}

/** 连线差异 */
export interface EdgeDiff {
  id: string
  /** 显示名，形如「审批 → 会签」；有自定义连线名时附在后面 */
  label: string
  kind: DiffKind
  changes: FieldChange[]
}

/** 单值差异（流程属性 / dslVersion） */
export interface PropertyDiff {
  key: string
  label: string
  before: unknown
  after: unknown
}

/** 流程变量差异 */
export interface VariableDiff {
  name: string
  kind: DiffKind
  changes: FieldChange[]
}

/** 差异汇总（{@code empty} 为真表示两个版本语义完全一致） */
export interface DslDiff {
  /** DSL 结构代际变化（极少出现，但出现了必须让用户看见） */
  dslVersion?: PropertyDiff
  meta: PropertyDiff[]
  variables: VariableDiff[]
  nodes: NodeDiff[]
  edges: EdgeDiff[]
  summary: { added: number; removed: number; changed: number }
  empty: boolean
}

// ==================== 字段词典 ====================

/**
 * 字段名 → 中文。覆盖面板上会出现的字段；未登记的字段原样显示 key
 * （宁可露出一个英文 key，也不要为了好看去猜它的业务含义）。
 */
const FIELD_LABEL: Record<string, string> = {
  // —— 通用 ——
  id: '标识',
  key: '流程 key',
  name: '名称',
  displayName: '显示名',
  category: '分组',
  description: '说明',
  type: '类型',
  // —— 人员 ——
  initiator: '发起人',
  assignee: '审批人',
  recipients: '接收人',
  reassignTo: '升级对象',
  strategy: '策略',
  userOids: '指定用户',
  roleCodes: '角色',
  variableName: '流程变量',
  expression: '表达式',
  deptFieldKey: '部门字段',
  fallback: '兜底策略',
  // —— 审批 ——
  approvalMode: '审批模式',
  passRule: '会签规则',
  mode: '方式',
  percent: '通过比例',
  count: '必须票数',
  veto: '一票否决',
  abstain: '弃权处理',
  commentRequired: '意见必填',
  deadline: '截止时间',
  anchor: '计时起点',
  durationHours: '时长（小时）',
  action: '逾期后果',
  remindBeforeHours: '截止前提醒（小时）',
  remindAfterHours: '截止后催办（小时）',
  reject: '驳回策略',
  enabled: '允许驳回',
  targetNodeId: '目标节点',
  // —— 绑定与表单 ——
  binding: '业务对象绑定',
  objectType: '对象类型',
  requiredFields: '必填字段',
  formRef: '节点表单',
  autoStart: '允许自动发起',
  // —— 任务 / 交付件 ——
  needDeliverables: '需要交付件',
  deliverableObjectTypes: '交付件对象类型',
  // —— 服务 / 子流程 / 定时 ——
  serviceRef: '服务',
  params: '服务参数',
  processKey: '子流程 key',
  variableMap: '变量映射',
  duration: '等待时长',
  at: '等待至',
  // —— 通知 ——
  notifications: '通知方式',
  channel: '通知渠道',
  channels: '通知渠道（旧）',
  templateCode: '通知模板',
  attachPrimaryObject: '附带对象链接',
  // —— 结束回调 ——
  callbacks: '结束回调',
  kind: '种类',
  targetState: '目标状态',
  // —— 连线 ——
  source: '起点',
  target: '目标',
  condition: '条件',
  fieldKey: '字段',
  operator: '操作符',
  value: '取值',
  valueType: '取值类型',
  route: '路由',
  terminateSiblings: '终止同层分支',
  advanced: '高级表达式',
  // —— 流程变量 ——
  label: '显示名',
  defaultValue: '默认值',
  visible: '任务页可见',
  readonly: '只读',
  writable: '可改写',
}

/** 点分路径 → 中文（未登记的段原样保留） */
function labelOf(path: string): string {
  return path
    .split('.')
    .map((segment) => FIELD_LABEL[segment] ?? segment)
    .join('·')
}

/**
 * 枚举取值 → 中文。按<b>路径</b>选表，而不是按值猜 ——
 * 同一个值 {@code mode} 在会签规则与定时等待里是两个意思。
 */
function enumLabelsOf(path: string): Record<string, string> | undefined {
  if (/(^|\.)strategy$/.test(path)) return ASSIGNEE_STRATEGY_LABEL
  if (/(^|\.)approvalMode$/.test(path)) return APPROVAL_MODE_LABEL
  if (/(^|\.)passRule\.mode$/.test(path)) return PASS_RULE_MODE_LABEL
  if (/(^|\.)passRule\.abstain$/.test(path)) return ABSTAIN_POLICY_LABEL
  if (/(^|\.)deadline\.anchor$/.test(path)) return DEADLINE_ANCHOR_LABEL
  if (/(^|\.)deadline\.action$/.test(path)) return OVERDUE_ACTION_LABEL
  if (/(^|\.)reject\.target$/.test(path)) return REJECT_TARGET_LABEL
  if (/(^|\.)condition\.operator$/.test(path)) return CONDITION_OPERATOR_LABEL
  if (/(^|\.)condition\.mode$/.test(path)) return CONDITION_MODE_LABEL
  if (/(^|\.)callbacks\.kind$/.test(path)) return END_CALLBACK_KIND_LABEL
  if (/(^|\.)notifications\.channel$/.test(path)) return NOTIFY_CHANNEL_LABEL
  if (/(^|\.)notifications\.(email|oa)\.authType$/.test(path)) return OA_AUTH_TYPE_LABEL
  // TimerNode 的 mode 是节点级（顶层），会签规则的 mode 已由上面那条吃掉
  if (path === 'mode') return TIMER_MODE_LABEL
  return undefined
}

/** 没有专用 label 常量的两个小枚举（只在这份展示里用） */
const CONDITION_MODE_LABEL: Record<string, string> = { FIELD: '字段选择器', EXPRESSION: '表达式' }
const VALUE_TYPE_LABEL: Record<string, string> = {
  STRING: '文本',
  NUMBER: '数值',
  BOOLEAN: '布尔',
  DATE: '日期',
}
const EDGE_KIND_LABEL: Record<string, string> = {
  NORMAL: '普通流转',
  CONDITION: '条件流转',
  DEFAULT: '默认分支',
}

// ==================== 值格式化 ====================

/** 差异值的展示文本（供 UI 直接渲染；纯函数，便于单测） */
export function formatDiffValue(value: unknown, path = ''): string {
  if (value === undefined || value === null || value === '') {
    return '（空）'
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  const enumLabels = enumLabelsOf(path)
  if (typeof value === 'string' && enumLabels && enumLabels[value]) {
    return enumLabels[value]
  }
  if (typeof value === 'string' && /(^|\.)type$/.test(path) && VALUE_TYPE_LABEL[value]) {
    return VALUE_TYPE_LABEL[value]
  }
  if (typeof value === 'string' && /(^|\.)kind$/.test(path)) {
    // 节点差异里的 kind 是 DiffKind、连线的是 EdgeKind；两套展示都从同一处取
    const labels: Record<string, string> = DIFF_KIND_LABEL
    return labels[value] ?? EDGE_KIND_LABEL[value] ?? value
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? '（空）' : value.map((item) => formatDiffValue(item, path)).join('、')
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

/** 节点类型的展示名（UI 里给差异项打标签用） */
export function nodeTypeLabel(type: string): string {
  return NODE_TYPE_LABEL[type as keyof typeof NODE_TYPE_LABEL] ?? type
}

// ==================== 比较底座 ====================

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * 把对象摊平成「路径 → 叶子值」。
 *
 * <p>数组整体当叶子 —— 元素级 diff 需要"按什么配对"的领域知识（用户列表？角色列表？），
 * 这里刻意不比：{@code ['a','b'] → ['b','a']} 报成一次改动，比报成两条"增删"更不容易误读。
 */
function flatten(value: unknown, prefix: string, out: Map<string, unknown>): void {
  if (isPlainObject(value)) {
    for (const [key, child] of Object.entries(value)) {
      if (child === undefined) {
        continue
      }
      flatten(child, prefix ? `${prefix}.${key}` : key, out)
    }
    return
  }
  if (prefix) {
    out.set(prefix, value)
  }
}

function sameValue(a: unknown, b: unknown): boolean {
  if (Array.isArray(a) || Array.isArray(b) || isPlainObject(a) || isPlainObject(b)) {
    return JSON.stringify(a) === JSON.stringify(b)
  }
  return a === b
}

/** 两个对象之间的字段级差异（{@code ignore} 里的顶层键不参与，如连线的 anchor / waypoints） */
function fieldChanges(before: unknown, after: unknown, ignore: string[] = []): FieldChange[] {
  const beforeFlat = new Map<string, unknown>()
  const afterFlat = new Map<string, unknown>()
  flatten(before, '', beforeFlat)
  flatten(after, '', afterFlat)

  const ignored = (path: string): boolean => ignore.some((key) => path === key || path.startsWith(`${key}.`))

  const changes: FieldChange[] = []
  for (const path of new Set([...beforeFlat.keys(), ...afterFlat.keys()])) {
    if (ignored(path)) {
      continue
    }
    const from = beforeFlat.get(path)
    const to = afterFlat.get(path)
    if (sameValue(from, to)) {
      continue
    }
    changes.push({ path, label: labelOf(path), before: from, after: to })
  }
  return changes.sort((a, b) => a.path.localeCompare(b.path))
}

// ==================== 主导出 ====================

/** 连线显示名：「源节点名 → 目标节点名」，有自定义名时补在后面 */
function edgeLabel(edge: FlowEdge, nodes: Map<string, FlowNode>): string {
  const from = nodes.get(edge.source)?.name ?? edge.source
  const to = nodes.get(edge.target)?.name ?? edge.target
  const base = `${from} → ${to}`
  return edge.name ? `${base}（${edge.name}）` : base
}

function indexBy<T>(items: T[], keyOf: (item: T) => string): Map<string, T> {
  const map = new Map<string, T>()
  for (const item of items) {
    map.set(keyOf(item), item)
  }
  return map
}

/**
 * 对比两个版本的 DSL。
 *
 * @param before 旧版本（基線）
 * @param after  新版本
 */
export function diffDsl(before: FlowDsl, after: FlowDsl): DslDiff {
  const result: DslDiff = {
    meta: [],
    variables: [],
    nodes: [],
    edges: [],
    summary: { added: 0, removed: 0, changed: 0 },
    empty: true,
  }

  // —— DSL 结构代际 ——
  if ((before.dslVersion ?? '') !== (after.dslVersion ?? '')) {
    result.dslVersion = {
      key: 'dslVersion',
      label: 'DSL 版本',
      before: before.dslVersion,
      after: after.dslVersion,
    }
  }

  // —— 流程属性（meta）——
  result.meta = fieldChanges(before.meta, after.meta).map((change) => ({
    key: change.path,
    label: change.label,
    before: change.before,
    after: change.after,
  }))

  // —— 流程变量（按 name 配对）——
  const beforeVars = indexBy<VariableDef>(before.variables ?? [], (v) => v.name)
  const afterVars = indexBy<VariableDef>(after.variables ?? [], (v) => v.name)
  for (const [name, variable] of afterVars) {
    const previous = beforeVars.get(name)
    if (!previous) {
      result.variables.push({ name, kind: DiffKind.ADDED, changes: fieldChanges({}, variable, ['name']) })
      continue
    }
    const changes = fieldChanges(previous, variable, ['name'])
    if (changes.length > 0) {
      result.variables.push({ name, kind: DiffKind.CHANGED, changes })
    }
  }
  for (const name of beforeVars.keys()) {
    if (!afterVars.has(name)) {
      result.variables.push({ name, kind: DiffKind.REMOVED, changes: [] })
    }
  }

  // —— 节点（按 id 配对）——
  const beforeNodes = indexBy(before.nodes, (n) => n.id)
  const afterNodes = indexBy(after.nodes, (n) => n.id)
  for (const [id, node] of afterNodes) {
    const previous = beforeNodes.get(id)
    if (!previous) {
      result.nodes.push({ id, name: node.name, type: node.type, kind: DiffKind.ADDED, changes: [] })
      continue
    }
    const changes = fieldChanges(previous, node, ['id'])
    if (changes.length > 0) {
      result.nodes.push({ id, name: node.name, type: node.type, kind: DiffKind.CHANGED, changes })
    }
  }
  for (const [id, node] of beforeNodes) {
    if (!afterNodes.has(id)) {
      result.nodes.push({ id, name: node.name, type: node.type, kind: DiffKind.REMOVED, changes: [] })
    }
  }

  // —— 连线（按 id 配对）——
  const beforeEdges = indexBy(before.edges, (e) => e.id)
  const afterEdges = indexBy(after.edges, (e) => e.id)
  const nameOf = new Map<string, FlowNode>([...beforeNodes, ...afterNodes])
  for (const [id, edge] of afterEdges) {
    const previous = beforeEdges.get(id)
    if (!previous) {
      result.edges.push({
        id,
        label: edgeLabel(edge, nameOf),
        kind: DiffKind.ADDED,
        changes: [],
      })
      continue
    }
    // 锚点与折点是画布表现（见文件头口径 2），不参与语义对比
    const changes = fieldChanges(previous, edge, ['id', 'anchor', 'waypoints'])
    if (changes.length > 0) {
      result.edges.push({ id, label: edgeLabel(edge, nameOf), kind: DiffKind.CHANGED, changes })
    }
  }
  for (const [id, edge] of beforeEdges) {
    if (!afterEdges.has(id)) {
      result.edges.push({ id, label: edgeLabel(edge, nameOf), kind: DiffKind.REMOVED, changes: [] })
    }
  }

  // —— 汇总 ——
  const count = (kind: DiffKind): number =>
    result.nodes.filter((item) => item.kind === kind).length +
    result.edges.filter((item) => item.kind === kind).length +
    result.variables.filter((item) => item.kind === kind).length

  result.summary = {
    added: count(DiffKind.ADDED),
    removed: count(DiffKind.REMOVED),
    changed: count(DiffKind.CHANGED) + result.meta.length + (result.dslVersion ? 1 : 0),
  }
  result.empty =
    result.nodes.length === 0 &&
    result.edges.length === 0 &&
    result.variables.length === 0 &&
    result.meta.length === 0 &&
    !result.dslVersion

  return result
}
