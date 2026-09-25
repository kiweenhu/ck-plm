/**
 * BPMN 2.0 XML → DSL 导入解析（spec §2.2 的反向编译）
 *
 * <h3>原则（spec §1 第 5 条：不支持即明示）</h3>
 * 存量 BPMN 可以导入，但<b>无法映射的元素一律降级为「只读展示 + 明确提示」，
 * 绝不静默丢弃语义</b>。所有无法映射的元素会出现在 {@link ImportResult.unsupported} 中。
 *
 * <h3>为何不用 bpmn-moddle</h3>
 * 与 {@link ./compile} 同理：本层只需识别 M0 的 11 类节点与迁移到 DSL 所需属性，
 * 手写扫描器在浏览器与 Node（单测）中行为一致且零额外依赖；
 * 面对 Camunda/Activiti 等第三方产物的完整兼容，留待接入 moddle 后增强。
 * 当前实现<b>面向本编译层产物 + 常见 Flowable 子集</b>，其余明确上报。
 */

import {
  ApprovalMode,
  AssigneeStrategy,
  ConditionMode,
  DeadlineAnchor,
  DSL_VERSION,
  EdgeKind,
  EdgeRoute,
  NodeType,
  OverdueAction,
  PassRuleMode,
  RejectTarget,
  SETUP_ASSIGNEE_FORM_CODE,
  TimerMode,
  defaultNodeSize,
  type ApprovalNode,
  type CountersignNode,
  type AssigneeSpec,
  type Condition,
  type FlowDsl,
  type FlowEdge,
  type FlowNode,
} from '@flow-dsl-core'
import {
  CKPLM_ATTR,
  CKPLM_PREFIX,
  FLOWABLE_PREFIX,
  RUNTIME_VARIABLES,
} from './namespaces'

export interface UnsupportedElement {
  tag: string
  id?: string
  name?: string
  reason: string
}

export interface ImportResult {
  dsl: FlowDsl
  /** 无法映射的元素（只读展示 + 提示），语义未丢失 */
  unsupported: UnsupportedElement[]
  warnings: string[]
}

interface RawTag {
  /** 本地名（去掉命名空间前缀），用于类型识别 */
  name: string
  /** 原始标签名（含命名空间前缀），用于识别本层自产的 ckplm 扩展元素 */
  rawName: string
  attrs: Record<string, string>
  selfClosing: boolean
  start: number
  end: number
  inner: string
}

/** process 内容内允许出现的「节点」标签 → DSL 节点类型 */
const NODE_TAG_TO_TYPE: Record<string, NodeType | 'AUTO'> = {
  startEvent: NodeType.START,
  endEvent: NodeType.END,
  userTask: 'AUTO', // 由 ckplm:nodeType 区分 APPROVAL / TASK
  serviceTask: 'AUTO', // 由 ckplm:serviceRef / ckplm:recipients 区分 SERVICE / NOTIFY
  callActivity: NodeType.SUB_PROCESS,
  intermediateCatchEvent: NodeType.TIMER,
  exclusiveGateway: NodeType.EXCLUSIVE_GATEWAY,
  parallelGateway: NodeType.PARALLEL_GATEWAY,
  inclusiveGateway: NodeType.INCLUSIVE_GATEWAY,
}

/** 结构化子元素（由父元素解析时消费，不作为独立节点） */
const CHILD_TAGS = new Set([
  'multiInstanceLoopCharacteristics',
  'completionCondition',
  'conditionExpression',
  'timerEventDefinition',
  'timeDuration',
  'timeDate',
  'extensionElements',
  'documentation',
  'incoming',
  'outgoing',
])

export interface ParseOptions {
  /** 覆盖流程 key（默认取 process id） */
  key?: string
  /** 覆盖流程名称（默认取 process name） */
  name?: string
}

export function parseBpmnXml(xml: string, options: ParseOptions = {}): ImportResult {
  const unsupported: UnsupportedElement[] = []
  const warnings: string[] = []
  const tags = scanTags(xml)

  const processTag = tags.find((t) => t.name === 'process')
  if (!processTag) {
    throw new Error('BPMN 中未找到 <process> 元素')
  }
  const processInnerStart = processTag.end
  const processClose = xml.indexOf('</process>', processInnerStart)
  const innerEnd = processClose >= 0 ? processClose : xml.length
  // extensionElements 内部的一切（含本层自产的 ckplm 扩展元素、flowable:field 委托字段、
  // 第三方扩展）都不是流程节点，必须排除 —— 否则会把自己的扩展元素误报成「不支持元素」。
  // 注意区间要覆盖到闭标签，只取开标签的话其子元素仍在区间之外。
  const extensionSpans = tags
    .filter((t) => t.name === 'extensionElements')
    .map((t) => {
      const closeTag = `</${t.rawName}>`
      const closeIndex = xml.indexOf(closeTag, t.end)
      const end = closeIndex >= 0 ? closeIndex + closeTag.length : t.end
      return [t.start, end] as const
    })
  const innerTags = tags.filter(
    (t) =>
      t.start >= processInnerStart &&
      t.end <= innerEnd &&
      !CHILD_TAGS.has(t.name) &&
      !t.rawName.startsWith(`${CKPLM_PREFIX}:`) &&
      !extensionSpans.some(([start, end]) => t.start >= start && t.end <= end),
  )

  const nodes: FlowNode[] = []
  const edges: FlowEdge[] = []
  const gatewayDefaults = new Map<string, string>()

  for (const tag of innerTags) {
    if (tag.name === 'sequenceFlow') {
      continue
    }
    const mapped = NODE_TAG_TO_TYPE[tag.name]
    if (mapped === undefined) {
      unsupported.push({
        tag: tag.name,
        id: tag.attrs.id,
        name: tag.attrs.name,
        reason: 'M0 未支持的元素类型，导入后不参与编辑（只读提示）',
      })
      continue
    }
    const node = toNode(tag, mapped, unsupported, warnings)
    if (node) {
      nodes.push(node)
      const defaultFlow = tag.attrs.default
      if (defaultFlow) {
        gatewayDefaults.set(tag.attrs.id, defaultFlow)
      }
    }
  }

  for (const tag of innerTags.filter((t) => t.name === 'sequenceFlow')) {
    edges.push(toEdge(tag, gatewayDefaults, unsupported))
  }

  const layout = buildLayout(xml, nodes, edges)

  const dsl: FlowDsl = {
    dslVersion: DSL_VERSION,
    meta: {
      key: options.key ?? processTag.attrs.id ?? 'imported_flow',
      name: options.name ?? processTag.attrs.name ?? '导入的流程',
    },
    variables: [],
    nodes,
    edges,
    layout,
  }
  return { dsl, unsupported, warnings }
}

// ==================== 节点映射 ====================

function toNode(
  tag: RawTag,
  mapped: NodeType | 'AUTO',
  unsupported: UnsupportedElement[],
  warnings: string[],
): FlowNode | null {
  const id = tag.attrs.id
  const name = tag.attrs.name ?? id
  if (!id) {
    unsupported.push({ tag: tag.name, reason: '元素缺少 id，无法导入' })
    return null
  }
  // 优先采信 ckplm:nodeType（本编译层产物 → 无损往返）
  const declared = readCkplm(tag, CKPLM_ATTR.NODE_TYPE) as NodeType | undefined
  const type = declared ?? (mapped === 'AUTO' ? inferAutoType(tag) : mapped)

  switch (type) {
    case NodeType.START: {
      const initiator = (readCkplmJson(tag, CKPLM_ATTR.INITIATOR) as AssigneeSpec | undefined) ?? {
        strategy: AssigneeStrategy.INITIATOR,
      }
      return {
        id,
        type: NodeType.START,
        name,
        initiator,
        formRef: readCkplm(tag, CKPLM_ATTR.FORM_REF) ?? tag.attrs[`${FLOWABLE_PREFIX}:formKey`],
        autoStart: readCkplmJson(tag, CKPLM_ATTR.AUTO_START) === true ? true : undefined,
        binding: readCkplmJson(tag, CKPLM_ATTR.BINDING),
      }
    }
    case NodeType.END:
      return {
        id,
        type: NodeType.END,
        name,
        callbacks: readCkplmJson(tag, CKPLM_ATTR.END_CALLBACKS),
      }
    case NodeType.APPROVAL: {
      const approvalMode = (readCkplmJson(tag, CKPLM_ATTR.APPROVAL_MODE) as ApprovalMode | undefined)
        ?? inferApprovalMode(tag)
      const node: ApprovalNode = {
        id,
        type: NodeType.APPROVAL,
        name,
        assignee: readAssignee(tag),
        approvalMode,
        passRule: readCkplmJson(tag, CKPLM_ATTR.PASS_RULE),
        commentRequired: readCkplmJson(tag, CKPLM_ATTR.COMMENT_REQUIRED) === true ? true : undefined,
        deadline: readCkplmJson(tag, CKPLM_ATTR.DEADLINE),
        reject: readCkplmJson(tag, CKPLM_ATTR.REJECT),
        binding: readCkplmJson(tag, CKPLM_ATTR.BINDING),
        formRef: readCkplm(tag, CKPLM_ATTR.FORM_REF) ?? tag.attrs[`${FLOWABLE_PREFIX}:formKey`],
      }
      if (!declared) {
        warnings.push(`节点「${name}」无 ckplm 标记，审批模式由 multiInstance 推断：${approvalMode}`)
        // 无标记且推断为会签 → 归入「会签审批」类型：业务类型优先于"用模式表达"。
        // 有 ckplm 标记的存量产物保持 APPROVAL + COUNTERSIGN（照样能编译、能运行）。
        if (approvalMode === ApprovalMode.COUNTERSIGN && node.passRule) {
          return {
            id: node.id,
            type: NodeType.COUNTERSIGN_APPROVAL,
            name: node.name,
            description: node.description,
            assignee: node.assignee,
            passRule: node.passRule,
            commentRequired: node.commentRequired,
            deadline: node.deadline,
            reject: node.reject,
            binding: node.binding,
            formRef: node.formRef,
          }
        }
      }
      return node
    }
    case NodeType.COUNTERSIGN_APPROVAL: {
      // 会签审批：passRule 在 DSL 里必需；产物缺规则时给保守默认（全员通过、弃权不计入）
      const declaredRule = readCkplmJson(tag, CKPLM_ATTR.PASS_RULE) as CountersignNode['passRule'] | undefined
      const node: CountersignNode = {
        id,
        type: NodeType.COUNTERSIGN_APPROVAL,
        name,
        assignee: readAssignee(tag),
        passRule: declaredRule ?? { mode: PassRuleMode.PERCENT, percent: 100, abstain: 'IGNORE' },
        commentRequired: readCkplmJson(tag, CKPLM_ATTR.COMMENT_REQUIRED) === true ? true : undefined,
        deadline: readCkplmJson(tag, CKPLM_ATTR.DEADLINE),
        reject: readCkplmJson(tag, CKPLM_ATTR.REJECT),
        binding: readCkplmJson(tag, CKPLM_ATTR.BINDING),
        formRef: readCkplm(tag, CKPLM_ATTR.FORM_REF) ?? tag.attrs[`${FLOWABLE_PREFIX}:formKey`],
      }
      return node
    }
    case NodeType.SET_ASSIGNEE:
      // 设置审批人：人员清单不入 DSL（由下游活动的 SETUP 策略表达），故只读自身属性
      return {
        id,
        type: NodeType.SET_ASSIGNEE,
        name,
        assignee: readAssignee(tag),
        commentRequired: readCkplmJson(tag, CKPLM_ATTR.COMMENT_REQUIRED) === true ? true : undefined,
        deadline: readCkplmJson(tag, CKPLM_ATTR.DEADLINE),
        binding: readCkplmJson(tag, CKPLM_ATTR.BINDING),
        // 内置固定表单：产物里没写 formKey 也要补上（否则往返一次就丢了这个契约）
        formRef:
          readCkplm(tag, CKPLM_ATTR.FORM_REF) ??
          tag.attrs[`${FLOWABLE_PREFIX}:formKey`] ??
          SETUP_ASSIGNEE_FORM_CODE,
      }
    case NodeType.TASK:
      return {
        id,
        type: NodeType.TASK,
        name,
        assignee: readAssignee(tag),
        formRef: readCkplm(tag, CKPLM_ATTR.FORM_REF) ?? tag.attrs[`${FLOWABLE_PREFIX}:formKey`],
        deliverables: readCkplmJson(tag, CKPLM_ATTR.DELIVERABLES),
        deadline: readCkplmJson(tag, CKPLM_ATTR.DEADLINE),
        binding: readCkplmJson(tag, CKPLM_ATTR.BINDING),
      }
    case NodeType.SERVICE:
      return {
        id,
        type: NodeType.SERVICE,
        name,
        serviceRef: readCkplm(tag, CKPLM_ATTR.SERVICE_REF) ?? extractServiceId(tag),
        params: readCkplmJson(tag, CKPLM_ATTR.SERVICE_PARAMS),
        expression: readCkplm(tag, CKPLM_ATTR.SERVICE_REF)
          ? undefined
          : stripUel(tag.attrs[`${FLOWABLE_PREFIX}:expression`]),
      }
    case NodeType.NOTIFY:
      return {
        id,
        type: NodeType.NOTIFY,
        name,
        recipients: (readCkplmJson(tag, CKPLM_ATTR.RECIPIENTS) as AssigneeSpec | undefined)
          ?? { strategy: AssigneeStrategy.ROLE },
        templateCode: readCkplm(tag, CKPLM_ATTR.TEMPLATE_CODE),
        attachPrimaryObject: readCkplmJson(tag, CKPLM_ATTR.ATTACH_PRIMARY_OBJECT) === true ? true : undefined,
      }
    case NodeType.SUB_PROCESS:
      return {
        id,
        type: NodeType.SUB_PROCESS,
        name,
        processKey: tag.attrs.calledElement ?? '',
        variableMap: readCkplmJson(tag, CKPLM_ATTR.VARIABLE_MAP),
      }
    case NodeType.TIMER: {
      const mode = (readCkplmJson(tag, CKPLM_ATTR.TIMER_MODE) as TimerMode | undefined)
        ?? (tag.inner.includes('timeDuration') ? TimerMode.DURATION : TimerMode.AT)
      return {
        id,
        type: NodeType.TIMER,
        name,
        mode,
        duration: mode === TimerMode.DURATION ? textOf(tag.inner, 'timeDuration') : undefined,
        at: mode === TimerMode.AT ? textOf(tag.inner, 'timeDate') : undefined,
      }
    }
    case NodeType.EXCLUSIVE_GATEWAY:
      return { id, type: NodeType.EXCLUSIVE_GATEWAY, name, advanced: readCkplmJson(tag, CKPLM_ATTR.ADVANCED) === true ? true : undefined }
    case NodeType.PARALLEL_GATEWAY:
      return { id, type: NodeType.PARALLEL_GATEWAY, name }
    case NodeType.INCLUSIVE_GATEWAY:
      return { id, type: NodeType.INCLUSIVE_GATEWAY, name, advanced: readCkplmJson(tag, CKPLM_ATTR.ADVANCED) === true ? true : undefined }
    default:
      unsupported.push({ tag: tag.name, id, name, reason: `无法映射为 DSL 节点类型: ${type}` })
      return null
  }
}

/** 无 ckplm 标记时，从标准元素推断类型（第三方 BPMN 导入路径） */
function inferAutoType(tag: RawTag): NodeType {
  if (tag.name === 'serviceTask') {
    if (readCkplm(tag, CKPLM_ATTR.TEMPLATE_CODE) || readCkplm(tag, CKPLM_ATTR.RECIPIENTS)) {
      return NodeType.NOTIFY
    }
    return NodeType.SERVICE
  }
  if (tag.name === 'userTask') {
    // 带多实例（会签/并行/或签）或显式审批语义的 userTask 视为审批节点 ——
    // 这是导入存量 BPMN 时最有价值的推断：业务用户看到的是「审批」而不是「用户任务」
    if (tag.inner.includes('multiInstanceLoopCharacteristics')) {
      return NodeType.APPROVAL
    }
    return NodeType.TASK
  }
  return NodeType.TASK
}

function inferApprovalMode(tag: RawTag): ApprovalMode {
  const multi = /<multiInstanceLoopCharacteristics([^>]*)/.exec(tag.inner)
  if (!multi) {
    return ApprovalMode.SINGLE
  }
  const sequential = /isSequential\s*=\s*"true"/.test(multi[1])
  if (sequential) {
    return ApprovalMode.SERIAL
  }
  const condition = /<completionCondition>([\s\S]*?)<\/completionCondition>/.exec(tag.inner)
  if (condition && /nrOfCompletedInstances\s*>=\s*1\b/.test(condition[1])) {
    return ApprovalMode.OR_SIGN
  }
  if (condition && /nrOfCompletedInstances\s*\*\s*100/.test(condition[1])) {
    return ApprovalMode.COUNTERSIGN
  }
  if (condition && /nrOfCompletedInstances\s*>=/.test(condition[1])) {
    return ApprovalMode.COUNTERSIGN
  }
  return ApprovalMode.PARALLEL_ALL
}

/** 从标准属性 + 运行期变量约定反推审批人策略（无 ckplm 标记时） */
function readAssignee(tag: RawTag): AssigneeSpec {
  const declared = readCkplmJson(tag, CKPLM_ATTR.ASSIGNEE) as AssigneeSpec | undefined
  if (declared) {
    return declared
  }
  const users = tag.attrs[`${FLOWABLE_PREFIX}:candidateUsers`]
  if (users) {
    return { strategy: AssigneeStrategy.USER, userOids: users.split(',').map((s) => s.trim()) }
  }
  const groups = tag.attrs[`${FLOWABLE_PREFIX}:candidateGroups`]
  if (groups) {
    return { strategy: AssigneeStrategy.ROLE, roleCodes: groups.split(',').map((s) => s.trim()) }
  }
  const assignee = stripUel(tag.attrs[`${FLOWABLE_PREFIX}:assignee`])
  if (!assignee) {
    return { strategy: AssigneeStrategy.INITIATOR }
  }
  if (assignee === RUNTIME_VARIABLES.INITIATOR) {
    return { strategy: AssigneeStrategy.INITIATOR }
  }
  if (assignee === RUNTIME_VARIABLES.DEPT_LEADER) {
    return { strategy: AssigneeStrategy.DEPT_LEADER }
  }
  if (assignee === RUNTIME_VARIABLES.INITIATOR_LEADER) {
    return { strategy: AssigneeStrategy.INITIATOR_LEADER }
  }
  return { strategy: AssigneeStrategy.VARIABLE, variableName: assignee }
}

function toEdge(
  tag: RawTag,
  gatewayDefaults: Map<string, string>,
  unsupported: UnsupportedElement[],
): FlowEdge {
  const id = tag.attrs.id ?? `${tag.attrs.sourceRef}_${tag.attrs.targetRef}`
  const condition = readCondition(tag)
  const route = readRoute(tag, condition)
  const isDefault = gatewayDefaults.get(tag.attrs.sourceRef ?? '') === tag.attrs.id
  const kind = isDefault
    ? EdgeKind.DEFAULT
    : condition
      ? EdgeKind.CONDITION
      : EdgeKind.NORMAL
  if (!tag.attrs.sourceRef || !tag.attrs.targetRef) {
    unsupported.push({ tag: 'sequenceFlow', id, reason: '连线缺少 sourceRef/targetRef' })
  }
  return {
    id,
    source: tag.attrs.sourceRef ?? '',
    target: tag.attrs.targetRef ?? '',
    kind,
    name: tag.attrs.name,
    route,
    condition,
  }
}

/**
 * 读回路由。
 *
 * <p>优先认我们自产的 {@code ckplm:route}；没有该属性时，再按条件表达式
 * 反推：{@code ${approved}} / {@code ${!approved}} 就是通过 / 驳回 ——
 * 手写 BPMN（或更早版本导出的）用这两个表达式表达结论时，
 * 导入后在面板上应当显示成「通过 / 驳回」路由，而不是让人看到一段 UEL。
 */
function readRoute(tag: RawTag, condition?: Condition): EdgeRoute | undefined {
  const declared = tag.attrs[`${CKPLM_PREFIX}:${CKPLM_ATTR.ROUTE}`]
  if (declared === EdgeRoute.PASS || declared === EdgeRoute.REJECT) {
    return declared
  }
  // readCondition 已把 ${...} 剥掉了，两种形态都认一下（手写 BPMN 可能带或不带包裹）
  const expression = condition?.expression?.replace(/\s+/g, '')
  if (expression === '!approved' || expression === '${!approved}') {
    return EdgeRoute.REJECT
  }
  if (expression === 'approved' || expression === '${approved}') {
    return EdgeRoute.PASS
  }
  return undefined
}

function readCondition(tag: RawTag): Condition | undefined {
  const attr = tag.attrs[`${FLOWABLE_PREFIX}:conditionExpression`]
  const child = textOf(tag.inner, 'conditionExpression')
  const raw = attr ?? child
  if (!raw) {
    return undefined
  }
  const expression = stripUel(raw)
  // 逆向解析我们生成的简单字段条件，失败则保留为表达式模式
  const simple = /^(\w+)\s*(==|!=|>=|<=|>|<)\s*'([^']*)'$/.exec(expression ?? '')
  if (simple) {
    return {
      mode: ConditionMode.FIELD,
      fieldKey: simple[1],
      operator: mapOperator(simple[2]),
      value: simple[3],
    }
  }
  return { mode: ConditionMode.EXPRESSION, expression: expression ?? raw }
}

function mapOperator(symbol: string): Condition['operator'] {
  switch (symbol) {
    case '==':
      return 'EQ'
    case '!=':
      return 'NE'
    case '>':
      return 'GT'
    case '>=':
      return 'GE'
    case '<':
      return 'LT'
    case '<=':
      return 'LE'
    default:
      return 'EQ'
  }
}

// ==================== 布局（BPMNDI）====================

function buildLayout(xml: string, nodes: FlowNode[], edges: FlowEdge[]): FlowDsl['layout'] {
  const layout: FlowDsl['layout'] = { nodes: {} }
  const shapeRe = /<bpmndi:BPMNShape[^>]*bpmnElement="([^"]+)"[^>]*>([\s\S]*?)<\/bpmndi:BPMNShape>/g
  let match: RegExpExecArray | null
  while ((match = shapeRe.exec(xml)) !== null) {
    const id = match[1]
    const bounds = /<omgdc:Bounds([^>]*)\/>/.exec(match[2])
    if (!bounds) {
      continue
    }
    layout.nodes[id] = {
      x: numberAttr(bounds[1], 'x'),
      y: numberAttr(bounds[1], 'y'),
      width: numberAttr(bounds[1], 'width'),
      height: numberAttr(bounds[1], 'height'),
    }
  }
  // 无图形信息的 BPMN：按声明顺序纵向排布，保证节点可见可编辑
  let index = 0
  for (const node of nodes) {
    if (!layout.nodes[node.id]) {
      const size = defaultNodeSize(node.type)
      layout.nodes[node.id] = { x: 240, y: 40 + index * 110, ...size }
    }
    index += 1
  }
  return layout
}

function numberAttr(attrText: string, name: string): number {
  const found = new RegExp(`${name}\\s*=\\s*"([^"]+)"`).exec(attrText)
  return found ? Number(found[1]) : 0
}

// ==================== 扫描与工具 ====================

function scanTags(xml: string): RawTag[] {
  const tags: RawTag[] = []
  const re = /<([a-zA-Z][\w:.-]*)\b([^>]*?)(\/?)>/g
  let match: RegExpExecArray | null
  while ((match = re.exec(xml)) !== null) {
    const name = match[1].replace(/^.*:/, '') // 去掉命名空间前缀，只保留本地名
    const attrs = parseAttrs(match[2])
    const selfClosing = match[3] === '/'
    const start = match.index
    const end = start + match[0].length
    let inner = ''
    if (!selfClosing) {
      const closeIndex = xml.indexOf(`</${match[1]}>`, end)
      if (closeIndex > 0) {
        inner = xml.slice(end, closeIndex)
      }
    }
    tags.push({ name, rawName: match[1], attrs, selfClosing, start, end, inner })
  }
  return tags
}

function parseAttrs(attrText: string): Record<string, string> {
  const attrs: Record<string, string> = {}
  const re = /([\w:.-]+)\s*=\s*"([^"]*)"/g
  let match: RegExpExecArray | null
  while ((match = re.exec(attrText)) !== null) {
    attrs[decode(match[1])] = decode(match[2])
  }
  return attrs
}

/** 读取 ckplm 扩展元素（{@code <ckplm:endCallbacks>} 形态）的文本内容 */
function readCkplm(tag: RawTag, name: string): string | undefined {
  const re = new RegExp(`<${CKPLM_PREFIX}:${name}[^>]*>([\\s\\S]*?)</${CKPLM_PREFIX}:${name}>`)
  const found = re.exec(tag.inner)
  if (found) {
    return decode(found[1])
  }
  return tag.attrs[`${CKPLM_PREFIX}:${name}`]
}

/** 读取 ckplm 扩展属性并反序列化为对象（失败返回 undefined） */
function readCkplmJson<T = unknown>(tag: RawTag, name: string): T | undefined {
  const raw = readCkplm(tag, name)
  if (raw === undefined) {
    return undefined
  }
  try {
    return JSON.parse(raw) as T
  } catch {
    return undefined
  }
}

function textOf(inner: string, tag: string): string | undefined {
  const found = new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`).exec(inner)
  return found ? decode(found[1].trim()) : undefined
}

/** 去掉 UEL 包裹 {@code ${...}} */
function stripUel(value?: string): string | undefined {
  if (!value) {
    return undefined
  }
  const trimmed = value.trim()
  const wrapped = /^\$\{([\s\S]*)\}$/.exec(trimmed)
  return wrapped ? wrapped[1].trim() : trimmed
}

/** 从 {@code flowable:field name="serviceId"} 提取服务 id（无 ckplm 标记时的兜底推断） */
function extractServiceId(tag: RawTag): string | undefined {
  const found = new RegExp(
    `<${FLOWABLE_PREFIX}:field[^>]*name="serviceId"[^>]*>\\s*` +
      `<${FLOWABLE_PREFIX}:string>([\\s\\S]*?)</${FLOWABLE_PREFIX}:string>`,
  ).exec(tag.inner)
  return found ? decode(found[1].trim()) : undefined
}

function decode(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
}
