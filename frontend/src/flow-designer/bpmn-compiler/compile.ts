/**
 * DSL → BPMN 2.0 XML 编译（spec §2.2 `@ckplm/flow-bpmn-compiler`）
 *
 * <h3>为何用确定性序列化而不是 bpmn-moddle</h3>
 * 规格原文写「bpmn-moddle 序列化」，但 moddle 需要一份 Flowable/CK-PLM 的 moddle 描述符；
 * 仓库现有的 {@code views/workflow/activiti/activiti.json} 是 <b>Activiti 命名空间</b>
 * （{@code http://activiti.org/bpmn}）且只声明了少量属性 —— 直接拿来用会把
 * {@code ckplm:*} 扩展与多实例属性<b>静默丢弃</b>（moddle 对未知属性不报错）。
 * 因此本层改为手写确定性序列化：输出完全可控、可快照测试、无隐式丢字段。
 * 若后续补上完整 moddle 描述符，可平滑替换本文件而不影响调用方。
 *
 * <h3>双编码</h3>
 * 标准 Flowable 属性（引擎可执行）+ {@code ckplm:*} 扩展属性（业务意图无损回读），
 * 映射依据集中在 {@link ./namespaces}。
 *
 * <h3>不做的事</h3>
 * 不校验 DSL 合法性（那是 {@code @flow-dsl-core} 的 {@code validateDsl} 职责）——
 * 但会把「目前无运行期支持」的语义以 {@code warnings} 明示，绝不静默丢弃。
 */

import {
  ApprovalMode,
  AssigneeStrategy,
  ConditionMode,
  ConditionOperator,
  DeadlineAnchor,
  EdgeKind,
  EdgeRoute,
  EDGE_ROUTE_LABEL,
  GATEWAY_TYPES,
  NodeType,
  OverdueAction,
  PassRuleMode,
  RejectTarget,
  TimerMode,
  defaultFormCodeOfNode,
  defaultNodeSize,
  fixedFormCodeOfNode,
  setupControlledIds,
  type ApprovalNode,
  type CountersignNode,
  type SetAssigneeNode,
  type AssigneeSpec,
  type Condition,
  type Deadline,
  type EndNode,
  type ExclusiveGatewayNode,
  type FlowDsl,
  type FlowEdge,
  type FlowNode,
  type InclusiveGatewayNode,
  type NotifyNode,
  type PassRule,
  type ServiceNode,
  type StartNode,
  type SubProcessNode,
  type TaskNode,
  type TimerNode,
} from '@flow-dsl-core'
import {
  BPMN_NS,
  BPMNDI_NS,
  CKPLM_ATTR,
  CKPLM_NS,
  CKPLM_PREFIX,
  DEFAULT_TARGET_NAMESPACE,
  FLOWABLE_NS,
  FLOWABLE_PREFIX,
  NOTIFY_DELEGATE,
  OMGDC_NS,
  OMGDI_NS,
  RUNTIME_VARIABLES,
  RUNTIME_VETO_VARIABLE,
  SERVICE_DELEGATE,
  SERVICE_ID_FIELD,
  ckplmAttr,
  flowableAttr,
} from './namespaces'

export interface CompileOptions {
  /** 是否输出 BPMNDI 图形信息（默认 true；仅做部署校验时可关闭以减小体积） */
  includeDiagram?: boolean
  /** targetNamespace（默认 {@link DEFAULT_TARGET_NAMESPACE}） */
  targetNamespace?: string
}

export interface CompileResult {
  xml: string
  /** 需要人工知晓的降级/待支持项（如「一票否决需运行期监听器」） */
  warnings: string[]
}

interface Ctx {
  dsl: FlowDsl
  warnings: string[]
  /**
   * 被「设置审批人」活动覆盖的活动 id（判定唯一实现在 dsl-core：`setupControlledIds`）。
   *
   * <p>进上下文是为了**只算一次**：每个节点的发射器都要问"我的人员由谁定"，
   * 逐个节点跑可达性会是 O(节点数 × 边数)。
   */
  setupControlled: Set<string>
}

/** 编译 DSL 为可部署的 BPMN 2.0 XML */
export function compileToBpmnXml(dsl: FlowDsl, options: CompileOptions = {}): CompileResult {
  const ctx: Ctx = { dsl, warnings: [], setupControlled: setupControlledIds(dsl) }
  const includeDiagram = options.includeDiagram !== false
  const targetNamespace = options.targetNamespace ?? DEFAULT_TARGET_NAMESPACE

  const body: string[] = []
  for (const node of dsl.nodes) {
    body.push(emitNode(node, ctx))
  }
  for (const edge of dsl.edges) {
    body.push(emitEdge(edge, ctx))
  }

  const xml = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<definitions xmlns="${BPMN_NS}"`,
    `             xmlns:bpmndi="${BPMNDI_NS}"`,
    `             xmlns:omgdc="${OMGDC_NS}"`,
    `             xmlns:omgdi="${OMGDI_NS}"`,
    // xsi 必须声明：conditionExpression 子元素使用 xsi:type="tFormalExpression"，
    // 缺声明会让 Flowable 解析报「prefix xsi is not bound」而拒绝部署
    '             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
    `             xmlns:${FLOWABLE_PREFIX}="${FLOWABLE_NS}"`,
    `             xmlns:${CKPLM_PREFIX}="${CKPLM_NS}"`,
    '             typeLanguage="http://www.w3.org/2001/XMLSchema"',
    '             expressionLanguage="http://www.w3.org/1999/XPath"',
    `             targetNamespace="${escAttr(targetNamespace)}">`,
    `  <process id="${escAttr(dsl.meta.key)}" name="${escAttr(dsl.meta.name)}" isExecutable="true">`,
    dsl.meta.description ? `    <documentation>${esc(dsl.meta.description)}</documentation>` : '',
    ...body.map((line) => indent(line, 4)),
    '  </process>',
    includeDiagram ? emitDiagram(dsl) : '',
    '</definitions>',
    '',
  ]
    .filter((line) => line !== '')
    .join('\n')

  return { xml, warnings: ctx.warnings }
}

// ==================== 节点 ====================

function emitNode(node: FlowNode, ctx: Ctx): string {
  switch (node.type) {
    case NodeType.START:
      return emitStart(node as StartNode, ctx)
    case NodeType.END:
      return emitEnd(node as EndNode, ctx)
    case NodeType.APPROVAL:
      return emitApproval(node as ApprovalNode, ctx)
    case NodeType.COUNTERSIGN_APPROVAL:
      // 业务上是独立类型，技术上与审批节点共用同一发射器（产物仍是 userTask + 多实例）
      return emitApproval(node as CountersignNode, ctx)
    case NodeType.SET_ASSIGNEE:
      return emitSetAssignee(node as SetAssigneeNode, ctx)
    case NodeType.TASK:
      return emitTask(node as TaskNode, ctx)
    case NodeType.SERVICE:
      return emitService(node as ServiceNode, ctx)
    case NodeType.NOTIFY:
      return emitNotify(node as NotifyNode, ctx)
    case NodeType.SUB_PROCESS:
      return emitSubProcess(node as SubProcessNode, ctx)
    case NodeType.TIMER:
      return emitTimer(node as TimerNode, ctx)
    case NodeType.EXCLUSIVE_GATEWAY:
      return `<exclusiveGateway ${baseAttrs(node, ctx)}${defaultBranchAttr(node.id, ctx)}${ctxAttr(CKPLM_ATTR.ADVANCED, (node as ExclusiveGatewayNode).advanced)}/>`
    case NodeType.PARALLEL_GATEWAY:
      // 并行网关是按定义无条件全走，BPMN 不允许 default 属性
      return `<parallelGateway ${baseAttrs(node, ctx)}/>`
    case NodeType.INCLUSIVE_GATEWAY:
      return `<inclusiveGateway ${baseAttrs(node, ctx)}${defaultBranchAttr(node.id, ctx)}${ctxAttr(CKPLM_ATTR.ADVANCED, (node as InclusiveGatewayNode).advanced)}/>`
    default:
      ctx.warnings.push(`未知节点类型，已跳过: ${(node as FlowNode).type}`)
      return ''
  }
}

function emitStart(node: StartNode, ctx: Ctx): string {
  const attrs: string[] = []
  const spec = node.initiator
  if (spec) {
    // 标准映射：Flowable 内置 initiator 变量（与项目既有 plm-change-review.bpmn20.xml 一致）
    const standard = standardAssigneeAttrs(spec, ctx, node.name)
    if (spec.strategy === AssigneeStrategy.INITIATOR) {
      attrs.push(`${flowableAttr('initiator')}="${RUNTIME_VARIABLES.INITIATOR}"`)
    } else if (standard) {
      attrs.push(attrsToXml(standard))
    }
    attrs.push(ctxJson(CKPLM_ATTR.INITIATOR, spec))
  }
  if (node.formRef) {
    attrs.push(`${flowableAttr('formKey')}="${escAttr(node.formRef)}"`)
    attrs.push(ctxAttr(CKPLM_ATTR.FORM_REF, node.formRef))
  }
  if (node.autoStart) {
    attrs.push(ctxJson(CKPLM_ATTR.AUTO_START, node.autoStart))
    ctx.warnings.push('开始节点配置了「自动发起」：需运行期事件触发器支持（本层只输出声明）')
  }
  if (node.binding) {
    attrs.push(ctxJson(CKPLM_ATTR.BINDING, node.binding))
  }
  const joined = attrs.length > 0 ? ` ${attrs.join(' ')}` : ''
  return `<startEvent ${baseAttrs(node, ctx)}${joined}/>`
}

function emitEnd(node: EndNode, ctx: Ctx): string {
  const extension = ctxExtensionElement(emitEndCallbacks(node, ctx))
  return extension
    ? `<endEvent ${baseAttrs(node, ctx)}>${indent(extension, 2)}</endEvent>`
    : `<endEvent ${baseAttrs(node, ctx)}/>`
}

function emitEndCallbacks(node: EndNode, ctx: Ctx): string {
  const callbacks = node.callbacks ?? []
  if (callbacks.length === 0) {
    return ''
  }
  const unsupported = callbacks.filter((c) => c.kind === 'OBJECT_STATE' && !c.targetState)
  if (unsupported.length > 0) {
    ctx.warnings.push(`结束节点「${node.name}」存在未指定目标状态的对象状态回调`)
  }
  return `<${CKPLM_PREFIX}:${CKPLM_ATTR.END_CALLBACKS}>${esc(JSON.stringify(callbacks))}</${CKPLM_PREFIX}:${CKPLM_ATTR.END_CALLBACKS}>`
}

/**
 * 审批类节点发射器（单人审批 / 会签审批共用）。
 *
 * <p>两者<b>业务类型不同但产物同源</b>：都是 `<userTask>`，差异只在是否带
 * `multiInstanceLoopCharacteristics`。共用可避免「同一种多实例产物写两遍」而漂移；
 * 业务类型由 `ckplm:nodeType`（见 {@link baseAttrsArr}）如实写出，往返无损。
 */
/**
 * 「设置审批人」活动发射器。
 *
 * <p>它本身就是个普通 userTask（由发起人办理）。产物里<b>不重复声明"要设置哪些活动"</b>：
 * 那由下游活动的策略（`SETUP`）表达，运行期从模板 DSL 即可推导 ——
 * 若在这里再存一份清单，流程一改就会出现两份不一致的真相。
 *
 * <p>因此本层只负责一件事：把「本活动的人员变量」绑定好，并提示运行期契约。
 */
function emitSetAssignee(node: SetAssigneeNode, ctx: Ctx): string {
  const attrs: string[] = [...baseAttrsArr(node)]
  const standard = standardAssigneeAttrs(node.assignee, ctx, node.name)
  if (standard) {
    attrs.push(attrsToXml(standard))
  }
  attrs.push(ctxJson(CKPLM_ATTR.ASSIGNEE, node.assignee))
  if (node.commentRequired) {
    attrs.push(ctxJson(CKPLM_ATTR.COMMENT_REQUIRED, true))
  }
  if (node.deadline) {
    attrs.push(ctxJson(CKPLM_ATTR.DEADLINE, node.deadline))
    warnDeadline(node.deadline, node.name, ctx)
  }
  if (node.binding) {
    attrs.push(ctxJson(CKPLM_ATTR.BINDING, node.binding))
  }
  // 表单走三档（见 dsl-core 的 forms.ts）：
  //   ① 语义固定的内置表单（设置审批人 / 会签）—— 它"不可被替换"，老模板里残留的历史 formRef
  //      也盖不过它：换了它运行期就取不到那张表单；
  //   ② 节点显式指定的 formRef（企业自定义模板、或从内置里另选一张）；
  //   ③ 类型默认（审批 → 审批意见）。
  const formRef = fixedFormCodeOfNode(node) ?? node.formRef ?? defaultFormCodeOfNode(node)
  if (formRef) {
    attrs.push(`${flowableAttr('formKey')}="${escAttr(formRef)}"`)
    attrs.push(ctxAttr(CKPLM_ATTR.FORM_REF, formRef))
  }
  ctx.warnings.push(
    `「设置审批人」活动「${node.name}」需运行期支持：任务完成后把所填人员写入 ` +
      `ckplmSetupAssignees_<活动id> 变量（一个活动一个变量，避免多活动互相覆盖）`,
  )
  return `<userTask ${attrs.join(' ').trim()}/>`
}

function emitApproval(node: ApprovalNode | CountersignNode, ctx: Ctx): string {
  // 会签审批没有 approvalMode（类型本身就是会签），在此换算为契约里的模式编码
  const approvalMode: ApprovalMode =
    node.type === NodeType.COUNTERSIGN_APPROVAL ? ApprovalMode.COUNTERSIGN : node.approvalMode
  const attrs: string[] = [...baseAttrsArr(node)]
  // 被「设置审批人」覆盖 → 人员由发起人在运行期指定，节点自身的策略退为"候选范围"，
  // 因此不再输出静态的候选组 / 指派（否则两者并存，任务会既属于某人又对某组可见）
  const usesSetup = ctx.setupControlled.has(node.id)
  const standard = standardAssigneeAttrs(node.assignee, ctx, node.name)
  if (standard && !usesSetup) {
    attrs.push(attrsToXml(standard))
  }
  if (usesSetup) {
    if (approvalMode === ApprovalMode.SINGLE) {
      // 单人审批：人员由「设置审批人」活动给出单个 oid，直接取变量
      attrs.push(`${flowableAttr('assignee')}="${escAttr(`\${${setupVariableName(node)}}`)}"`)
    } else {
      // 多实例：集合里的**每个人各得一个任务**，负责人取多实例的元素变量。
      // 缺了这一句，集合再正确也没人接到任务（会签任务会全部"无人办理"）——
      // 人员由发起人指定，就不该再走"候选组认领"，直接指派到人。
      attrs.push(`${flowableAttr('assignee')}="${escAttr(`\${${RUNTIME_VARIABLES.APPROVER}}`)}"`)
    }
    // 多实例模式下 collection 由 emitMultiInstance 输出同一个变量
    ctx.warnings.push(
      `活动「${node.name}」的人员由「设置审批人」指定：` +
        `需运行期在该任务创建前写入变量 ${setupVariableName(node)}` +
        `（${approvalMode === ApprovalMode.SINGLE ? '人员 oid' : '人员 oid 列表'}），否则该任务无人办理`,
    )
  }
  attrs.push(ctxJson(CKPLM_ATTR.ASSIGNEE, node.assignee))
  attrs.push(ctxJson(CKPLM_ATTR.APPROVAL_MODE, approvalMode))
  if (node.passRule) {
    attrs.push(ctxJson(CKPLM_ATTR.PASS_RULE, node.passRule))
  }
  if (node.commentRequired) {
    attrs.push(ctxJson(CKPLM_ATTR.COMMENT_REQUIRED, true))
  }
  if (node.deadline) {
    attrs.push(ctxJson(CKPLM_ATTR.DEADLINE, node.deadline))
    warnDeadline(node.deadline, node.name, ctx)
  }
  if (node.reject) {
    // 有声明就写出来（不限于 enabled=true）。设计器关掉「允许驳回」时会把整个 reject 键删掉，
    // 所以正常产出的 DSL 里 enabled 恒为 true；这条兼容的是「导入/手改」来的 DSL ——
    // 运行期据此明确拒绝驳回，而不是把它当"老流程没配"放行
    attrs.push(ctxJson(CKPLM_ATTR.REJECT, node.reject))
    if (node.reject.enabled && node.reject.target === RejectTarget.NODE && !node.reject.targetNodeId) {
      ctx.warnings.push(`审批节点「${node.name}」驳回目标为指定节点但未给出目标节点`)
    }
  }
  if (node.binding) {
    attrs.push(ctxJson(CKPLM_ATTR.BINDING, node.binding))
  }
  if (node.formRef) {
    attrs.push(`${flowableAttr('formKey')}="${escAttr(node.formRef)}"`)
    attrs.push(ctxAttr(CKPLM_ATTR.FORM_REF, node.formRef))
  }

  const inner = emitMultiInstance(node, approvalMode, ctx)
  return inner
    ? `<userTask ${attrs.join(' ').trim()}>${indent(inner, 2)}</userTask>`
    : `<userTask ${attrs.join(' ').trim()}/>`
}

function emitTask(node: TaskNode, ctx: Ctx): string {
  const attrs: string[] = [...baseAttrsArr(node)]
  const controlled = ctx.setupControlled.has(node.id)
  const standard = standardAssigneeAttrs(node.assignee, ctx, node.name)
  if (standard && !controlled) {
    attrs.push(attrsToXml(standard))
  }
  if (controlled) {
    // 办理节点可能指定多人 → 用**候选人**而非直接指派：
    // flowable:assignee 只接受一个人，而候选人多于一人时要由他们认领
    attrs.push(`${flowableAttr('candidateUsers')}="${escAttr(`\${${setupVariableName(node)}}`)}"`)
    ctx.warnings.push(
      `办理节点「${node.name}」的人员由「设置审批人」指定：需运行期在该任务创建前写入变量 ` +
        `${setupVariableName(node)}（人员 oid 列表），否则该任务无人可办`,
    )
  }
  attrs.push(ctxJson(CKPLM_ATTR.ASSIGNEE, node.assignee))
  if (node.formRef) {
    attrs.push(`${flowableAttr('formKey')}="${escAttr(node.formRef)}"`)
    attrs.push(ctxAttr(CKPLM_ATTR.FORM_REF, node.formRef))
  }
  if (node.deliverables?.length) {
    attrs.push(ctxJson(CKPLM_ATTR.DELIVERABLES, node.deliverables))
    ctx.warnings.push(`办理节点「${node.name}」的交付物需运行期校验（本层只输出声明）`)
  }
  if (node.deadline) {
    attrs.push(ctxJson(CKPLM_ATTR.DEADLINE, node.deadline))
    warnDeadline(node.deadline, node.name, ctx)
  }
  if (node.binding) {
    attrs.push(ctxJson(CKPLM_ATTR.BINDING, node.binding))
  }
  return `<userTask ${attrs.join(' ').trim()}/>`
}

function emitService(node: ServiceNode, ctx: Ctx): string {
  const attrs: string[] = [...baseAttrsArr(node)]
  const fields: string[] = []
  if (node.serviceRef) {
    // 白名单服务统一走「一个委托 Bean + flowable:field 传参」：
    // 既禁止用户直写 Bean 名/类名（spec §4-J），也避免把服务 id 拼进表达式
    // —— `${plmService:object.promote}` 是非法 UEL，会让引擎直接拒绝部署
    attrs.push(`${flowableAttr('delegateExpression')}="\${${SERVICE_DELEGATE}}"`)
    // 字符串标量用字面量编码：JSON 编码会带上引号，解析回来就不是原值了
    attrs.push(ctxAttr(CKPLM_ATTR.SERVICE_REF, node.serviceRef))
    fields.push(delegateField(SERVICE_ID_FIELD, node.serviceRef))
    if (node.params && Object.keys(node.params).length > 0) {
      for (const [name, value] of Object.entries(node.params)) {
        fields.push(delegateField(name, value))
      }
      attrs.push(ctxJson(CKPLM_ATTR.SERVICE_PARAMS, node.params))
    }
  } else if (node.expression) {
    attrs.push(`${flowableAttr('expression')}="${escAttr('${' + node.expression + '}')}"`)
  } else {
    ctx.warnings.push(`自动服务节点「${node.name}」既未选择服务也未填写表达式，编译产物不含任何行为`)
  }
  const inner = fields.length > 0
    ? `<extensionElements>${fields.join('')}</extensionElements>`
    : ''
  return inner
    ? `<serviceTask ${attrs.join(' ').trim()}>${indent(inner, 2)}</serviceTask>`
    : `<serviceTask ${attrs.join(' ').trim()}/>`
}

/** 委托字段：{@code <flowable:field name="x"><flowable:string>v</flowable:string></flowable:field>} */
function delegateField(name: string, value: string): string {
  return (
    `<${FLOWABLE_PREFIX}:field name="${escAttr(name)}">` +
    `<${FLOWABLE_PREFIX}:string>${esc(value)}</${FLOWABLE_PREFIX}:string>` +
    `</${FLOWABLE_PREFIX}:field>`
  )
}

function emitNotify(node: NotifyNode, ctx: Ctx): string {
  const attrs: string[] = [...baseAttrsArr(node)]
  attrs.push(`${flowableAttr('delegateExpression')}="\${${NOTIFY_DELEGATE}}"`)
  if (node.templateCode) {
    attrs.push(ctxAttr(CKPLM_ATTR.TEMPLATE_CODE, node.templateCode))
  }
  attrs.push(ctxJson(CKPLM_ATTR.RECIPIENTS, node.recipients))
  attrs.push(ctxJson(CKPLM_ATTR.ATTACH_PRIMARY_OBJECT, !!node.attachPrimaryObject))
  if (!hasUsableAssignee(node.recipients)) {
    ctx.warnings.push(`通知节点「${node.name}」未配置有效接收人`)
  }
  return `<serviceTask ${attrs.join(' ').trim()}/>`
}

function emitSubProcess(node: SubProcessNode, ctx: Ctx): string {
  const attrs: string[] = [...baseAttrsArr(node)]
  attrs.push(`calledElement="${escAttr(node.processKey)}"`)
  if (node.variableMap && Object.keys(node.variableMap).length > 0) {
    attrs.push(ctxJson(CKPLM_ATTR.VARIABLE_MAP, node.variableMap))
  }
  return `<callActivity ${attrs.join(' ').trim()}/>`
}

function emitTimer(node: TimerNode, ctx: Ctx): string {
  const attrs: string[] = [...baseAttrsArr(node)]
  attrs.push(ctxJson(CKPLM_ATTR.TIMER_MODE, node.mode))
  let definition = ''
  if (node.mode === TimerMode.DURATION) {
    if (!node.duration) {
      ctx.warnings.push(`定时节点「${node.name}」未设置等待时长`)
    } else {
      definition = `<timerEventDefinition><timeDuration>${esc(node.duration)}</timeDuration></timerEventDefinition>`
    }
  } else if (!node.at) {
    ctx.warnings.push(`定时节点「${node.name}」未设置目标时点`)
  } else {
    definition = `<timerEventDefinition><timeDate>${esc(node.at)}</timeDate></timerEventDefinition>`
  }
  return definition
    ? `<intermediateCatchEvent ${attrs.join(' ').trim()}>${definition}</intermediateCatchEvent>`
    : `<intermediateCatchEvent ${attrs.join(' ').trim()}/>`
}

// ==================== 多实例（会签/并行/或签）====================

function emitMultiInstance(node: ApprovalNode | CountersignNode, mode: ApprovalMode, ctx: Ctx): string {
  if (mode === ApprovalMode.SINGLE) {
    return ''
  }
  const sequential = mode === ApprovalMode.SERIAL ? 'true' : 'false'
  const completion = completionCondition(node, mode, ctx)
  // 被「设置审批人」覆盖的活动，人员取它的专属变量（粒度到活动）；其余走默认审批人集合
  const source = ctx.setupControlled.has(node.id)
    ? setupVariableName(node)
    : RUNTIME_VARIABLES.APPROVERS
  const collection = `\${${source}}`
  const attrs =
    `isSequential="${sequential}"` +
    ` ${flowableAttr('collection')}="${escAttr(collection)}"` +
    ` ${flowableAttr('elementVariable')}="${RUNTIME_VARIABLES.APPROVER}"`
  return completion
    ? `<multiInstanceLoopCharacteristics ${attrs}><completionCondition>${esc(completion)}</completionCondition></multiInstanceLoopCharacteristics>`
    : `<multiInstanceLoopCharacteristics ${attrs}/>`
}

/**
 * 由 passRule 派生 Flowable 的 completionCondition（UEL）。
 *
 * <p>`mode` 由调用方传入而不是从节点读取：会签审批类型的节点上没有 `approvalMode`
 * （类型本身就是会签），模式在 {@link emitApproval} 里已统一换算。
 */
function completionCondition(
  node: ApprovalNode | CountersignNode,
  mode: ApprovalMode,
  ctx: Ctx,
): string {
  if (mode === ApprovalMode.OR_SIGN) {
    return '${nrOfCompletedInstances >= 1}'
  }
  if (mode !== ApprovalMode.COUNTERSIGN) {
    // SERIAL / PARALLEL_ALL：全部完成即通过，不需要 completionCondition
    return ''
  }
  const rule = node.passRule
  if (!rule) {
    ctx.warnings.push(`会签节点「${node.name}」缺少通过规则，已退化为「全部完成才通过」`)
    return ''
  }
  return passRuleToUel(rule, node.name, ctx)
}

function passRuleToUel(rule: PassRule, nodeName: string, ctx: Ctx): string {
  switch (rule.mode) {
    case PassRuleMode.PERCENT: {
      const percent = rule.percent ?? 100
      if (rule.abstain && rule.abstain !== 'IGNORE') {
        ctx.warnings.push(
          `会签节点「${nodeName}」配置了弃权处理（${rule.abstain}）：` +
            'Flowable 的 completionCondition 只统计「已完成」实例，' +
            `弃权语义需运行期监听器配合（本层按 IGNORE 语义编译，另以 ckplm:passRule 声明完整意图）`,
        )
      }
      // 用乘 100 比较避免浮点表示误差
      return `\${nrOfCompletedInstances * 100 >= nrOfInstances * ${percent}}`
    }
    case PassRuleMode.COUNT: {
      const count = rule.count ?? 1
      return `\${nrOfCompletedInstances >= ${count}}`
    }
    case PassRuleMode.VETO:
      ctx.warnings.push(
        `会签节点「${nodeName}」使用「一票否决」：该语义无法用 completionCondition 表达` +
          `（完成条件只在实例完成时求值），需运行期任务监听器置 ${RUNTIME_VETO_VARIABLE} 后中断。` +
          '本层只输出 ckplm:passRule 声明，不生成 completionCondition。',
      )
      return ''
    default:
      return ''
  }
}

// ==================== 连线 ====================

function emitEdge(edge: FlowEdge, ctx: Ctx): string {
  const gatewaySource = ctx.dsl.nodes.find((n) => n.id === edge.source)
  const fromGateway = gatewaySource ? GATEWAY_TYPES.includes(gatewaySource.type) : false
  const attrs: string[] = [
    `id="${escAttr(edge.id)}"`,
    `sourceRef="${escAttr(edge.source)}"`,
    `targetRef="${escAttr(edge.target)}"`,
  ]
  if (edge.name) {
    attrs.push(`name="${escAttr(edge.name)}"`)
  }
  if (edge.route) {
    // 路由同时写进属性：运行期据此知道"这个节点已经把驳回画成边了"，
    // 就不会再去按「驳回目标」跳一次（见后端 reject/RejectRouter）
    attrs.push(ctxAttr(CKPLM_ATTR.ROUTE, edge.route))
    if (edge.condition) {
      ctx.warnings.push(
        `连线「${edge.name ?? edge.id}」既声明了路由（${EDGE_ROUTE_LABEL[edge.route]}）又填了条件：` +
          '按路由处理（条件由编译层生成），手写条件被忽略',
      )
    }
  }
  const condition = routeExpression(edge.route) ?? conditionExpression(edge.condition)
  // 注意：条件只写成子元素 <conditionExpression>，不写 flowable:conditionExpression 属性
  // —— BPMN 规范里 sequenceFlow 没有这个属性，多写会被引擎视为非法
  // 非网关多出边（同意/拒绝）：条件由连线自带；网关的默认分支由网关的 default 属性承载
  if (!fromGateway && edge.kind === EdgeKind.DEFAULT) {
    ctx.warnings.push(`连线「${edge.name ?? edge.id}」被标记为默认分支，但源节点不是分支节点，将按普通流转处理`)
  }
  if (edge.terminateSiblings) {
    ctx.warnings.push(
      `连线「${edge.name ?? edge.id}」配置了「分支终止语义」：` +
        'Flowable 无原生对应，需运行期终止其余并行分支（本层只输出声明）',
    )
  }
  const child = condition
    ? `<conditionExpression xsi:type="tFormalExpression">${esc(condition.expression)}</conditionExpression>`
    : ''
  return child
    ? `<sequenceFlow ${attrs.join(' ')}>${child}</sequenceFlow>`
    : `<sequenceFlow ${attrs.join(' ')}/>`
}

/**
 * 路由 → UEL 条件。
 *
 * <p>通过 = {@code ${approved}}、驳回 = {@code ${!approved}}：二者互斥，
 * 引擎（以及 6/7 里"活动多出边按条件选择"的行为）必然只走一条 ——
 * 这就是为什么"驳回"能像通过一样是一条正常的边，而不用把结论塞进连线条件让用户手写。
 */
function routeExpression(route?: EdgeRoute): { expression: string } | null {
  if (route === EdgeRoute.PASS) {
    return { expression: '${approved}' }
  }
  if (route === EdgeRoute.REJECT) {
    return { expression: '${!approved}' }
  }
  return null
}

/** 把 DSL 条件编译为 UEL 表达式 */
function conditionExpression(condition?: Condition): { expression: string } | null {
  if (!condition) {
    return null
  }
  if (condition.mode === ConditionMode.EXPRESSION) {
    return condition.expression ? { expression: '${' + condition.expression + '}' } : null
  }
  const { fieldKey, operator, value, valueType } = condition
  if (!fieldKey || !operator) {
    return null
  }
  const literal = uelLiteral(value, valueType)
  switch (operator) {
    case ConditionOperator.EQ:
      return { expression: `\${${fieldKey} == ${literal}}` }
    case ConditionOperator.NE:
      return { expression: `\${${fieldKey} != ${literal}}` }
    case ConditionOperator.GT:
      return { expression: `\${${fieldKey} > ${literal}}` }
    case ConditionOperator.GE:
      return { expression: `\${${fieldKey} >= ${literal}}` }
    case ConditionOperator.LT:
      return { expression: `\${${fieldKey} < ${literal}}` }
    case ConditionOperator.LE:
      return { expression: `\${${fieldKey} <= ${literal}}` }
    case ConditionOperator.IN:
      return { expression: `\${${fieldKey} != null && [${toLiteralList(value, valueType)}].contains(${fieldKey})}` }
    case ConditionOperator.NOT_IN:
      return { expression: `\${${fieldKey} == null || ![${toLiteralList(value, valueType)}].contains(${fieldKey})}` }
    case ConditionOperator.CONTAINS:
      return { expression: `\${${fieldKey} != null && ${fieldKey}.contains(${literal})}` }
    case ConditionOperator.IS_EMPTY:
      return { expression: `\${${fieldKey} == null || ${fieldKey} == ''}` }
    case ConditionOperator.IS_NOT_EMPTY:
      return { expression: `\${${fieldKey} != null && ${fieldKey} != ''}` }
    default:
      return null
  }
}

function uelLiteral(value: Condition['value'], valueType: Condition['valueType']): string {
  if (value === undefined) {
    return 'null'
  }
  if (typeof value === 'boolean' || typeof value === 'number') {
    return String(value)
  }
  if (Array.isArray(value)) {
    return `[${value.map((v) => quote(v)).join(', ')}]`
  }
  if (valueType === 'NUMBER') {
    return String(Number(value))
  }
  if (valueType === 'BOOLEAN') {
    return String(value) === 'true' ? 'true' : 'false'
  }
  return quote(String(value))
}

function toLiteralList(value: Condition['value'], valueType: Condition['valueType']): string {
  const list = Array.isArray(value) ? value : value === undefined ? [] : [value]
  return list.map((v) => uelLiteral(v, valueType)).join(', ')
}

function quote(value: string): string {
  return `'${value.replace(/'/g, "\\'")}'`
}

// ==================== 「设置审批人」运行期变量 ====================

/**
 * 某活动的人员变量名（运行期由「设置审批人」任务完成后写入）。
 *
 * <p>一个活动一个变量，前缀与 {@link RUNTIME_VARIABLES}.SETUP_ASSIGNEES 一致：
 * 若多个活动共用一个集合，后填的会覆盖先填的，且无法按活动校验人数（会签至少两人）。
 */
function setupVariableName(node: { id: string }): string {
  return `${RUNTIME_VARIABLES.SETUP_ASSIGNEES}${node.id}`
}

// ==================== 审批人标准映射 ====================

/**
 * 把审批人策略映射为 Flowable 可执行的标准属性。
 *
 * <p>无法直接映射的策略（部门主管 / 发起人上级）走<b>运行期变量约定</b>
 * （见 {@link RUNTIME_VARIABLES}），由运行期支持组件在任务创建前填充。
 */
function standardAssigneeAttrs(
  spec: AssigneeSpec | undefined,
  ctx: Ctx,
  nodeName: string,
): Record<string, string> | null {
  if (!spec) {
    ctx.warnings.push(`节点「${nodeName}」未配置审批人，编译产物中该任务无人办理`)
    return null
  }
  if (spec.fallback) {
    ctx.warnings.push(
      `节点「${nodeName}」配置了兜底策略：需运行期在策略解析为空时降级（本层只输出 ckplm:assignee 声明）`,
    )
  }
  switch (spec.strategy) {
    case AssigneeStrategy.USER: {
      const users = spec.userOids ?? []
      if (users.length === 0) {
        ctx.warnings.push(`节点「${nodeName}」审批人策略为「指定用户」但未选择用户`)
        return null
      }
      return users.length === 1
        ? { [flowableAttr('assignee')]: users[0] }
        : { [flowableAttr('candidateUsers')]: users.join(',') }
    }
    case AssigneeStrategy.ROLE:
    case AssigneeStrategy.PROJECT_ROLE: {
      const roles = spec.roleCodes ?? []
      if (roles.length === 0) {
        ctx.warnings.push(`节点「${nodeName}」审批人策略为「${spec.strategy}」但未选择角色`)
        return null
      }
      // 角色与项目角色都映射为候选组：运行期身份来自 ck_role.code（见 ProcessIdentitySupport）
      return { [flowableAttr('candidateGroups')]: roles.join(',') }
    }
    case AssigneeStrategy.INITIATOR:
      return { [flowableAttr('assignee')]: `\${${RUNTIME_VARIABLES.INITIATOR}}` }
    case AssigneeStrategy.DEPT_LEADER:
      return { [flowableAttr('assignee')]: `\${${RUNTIME_VARIABLES.DEPT_LEADER}}` }
    case AssigneeStrategy.INITIATOR_LEADER:
      return { [flowableAttr('assignee')]: `\${${RUNTIME_VARIABLES.INITIATOR_LEADER}}` }
    case AssigneeStrategy.VARIABLE:
      if (!spec.variableName) {
        ctx.warnings.push(`节点「${nodeName}」审批人策略为「流程变量」但未指定变量名`)
        return null
      }
      return { [flowableAttr('assignee')]: `\${${spec.variableName}}` }
    case AssigneeStrategy.EXPRESSION:
      if (!spec.expression) {
        ctx.warnings.push(`节点「${nodeName}」审批人策略为「表达式」但表达式为空`)
        return null
      }
      return { [flowableAttr('assignee')]: `\${${spec.expression}}` }
    default:
      return null
  }
}

function hasUsableAssignee(spec?: AssigneeSpec): boolean {
  if (!spec) {
    return false
  }
  switch (spec.strategy) {
    case AssigneeStrategy.USER:
      return !!spec.userOids?.length
    case AssigneeStrategy.ROLE:
    case AssigneeStrategy.PROJECT_ROLE:
      return !!spec.roleCodes?.length
    case AssigneeStrategy.VARIABLE:
      return !!spec.variableName
    case AssigneeStrategy.EXPRESSION:
      return !!spec.expression
    case AssigneeStrategy.DEPT_LEADER:
      return !!spec.deptFieldKey
    default:
      return true
  }
}

function warnDeadline(deadline: Deadline, nodeName: string, ctx: Ctx): void {
  if (deadline.anchor === DeadlineAnchor.PROCESS_START) {
    ctx.warnings.push(
      `节点「${nodeName}」的截止时间从「流程启动」计时：需运行期换算为绝对时间` +
        '（本层只输出声明，不生成边界定时事件）',
    )
  } else if (deadline.action !== OverdueAction.AUTO_APPROVE && deadline.action !== OverdueAction.SKIP) {
    ctx.warnings.push(
      `节点「${nodeName}」的逾期后果为「${deadline.action}」：需运行期边界定时事件 + 监听器实现` +
        '（本层只输出 ckplm:deadline 声明）',
    )
  }
}

// ==================== BPMNDI 图形信息 ====================

function emitDiagram(dsl: FlowDsl): string {
  const shapes: string[] = []
  for (const node of dsl.nodes) {
    const layout = dsl.layout.nodes[node.id]
    // 尺寸只认 defaultNodeSize，不读 layout 里的历史快照 —— 与画布同一处事实源，
    // 否则设计器画 150×52、导出的 BPMN DI 却是 180×64，两张图对不上
    const size = defaultNodeSize(node.type)
    const x = layout?.x ?? 0
    const y = layout?.y ?? 0
    const width = size.width
    const height = size.height
    shapes.push(
      `    <bpmndi:BPMNShape id="Shape_${node.id}" bpmnElement="${escAttr(node.id)}">\n` +
        `      <omgdc:Bounds x="${x}" y="${y}" width="${width}" height="${height}"/>\n` +
        '    </bpmndi:BPMNShape>',
    )
  }
  for (const edge of dsl.edges) {
    const from = dsl.layout.nodes[edge.source]
    const to = dsl.layout.nodes[edge.target]
    const fromNode = dsl.nodes.find((n) => n.id === edge.source)
    const toNode = dsl.nodes.find((n) => n.id === edge.target)
    const fromSize = defaultNodeSize(fromNode?.type ?? NodeType.TASK)
    const toSize = defaultNodeSize(toNode?.type ?? NodeType.TASK)
    const x1 = (from?.x ?? 0) + fromSize.width / 2
    const y1 = (from?.y ?? 0) + fromSize.height
    const x2 = (to?.x ?? 0) + toSize.width / 2
    const y2 = to?.y ?? 0
    shapes.push(
      `    <bpmndi:BPMNEdge id="Edge_${edge.id}" bpmnElement="${escAttr(edge.id)}">\n` +
        `      <omgdi:waypoint x="${x1}" y="${y1}"/>\n` +
        `      <omgdi:waypoint x="${x2}" y="${y2}"/>\n` +
        '    </bpmndi:BPMNEdge>',
    )
  }
  return (
    '  <bpmndi:BPMNDiagram id="BPMNDiagram_1">\n' +
    `    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${escAttr(dsl.meta.key)}">\n` +
    shapes.join('\n') +
    '\n    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>'
  )
}

// ==================== 工具 ====================

function baseAttrs(node: FlowNode, ctx: Ctx): string {
  return baseAttrsArr(node).join(' ')
}

function baseAttrsArr(node: FlowNode): string[] {
  // ckplm:nodeType 是往返无损的必要标记：BPMN 里 APPROVAL 与 TASK 都是 userTask，
  // 仅靠标准元素无法还原 DSL 节点类型。
  // 必须用 ctxAttr（字面量）而非 ctxJson：JSON.stringify('START') 会带上引号，
  // 反序列化后得到 "START"（含引号）就再也匹配不上任何类型了。
  return [
    `id="${escAttr(node.id)}"`,
    `name="${escAttr(node.name)}"`,
    ctxAttr(CKPLM_ATTR.NODE_TYPE, node.type),
  ]
}

function ctxAttr(name: string, value: unknown): string {
  return value === undefined || value === null ? '' : `${ckplmAttr(name)}="${escAttr(String(value))}"`
}

/**
 * 默认分支在 BPMN 中的正确载体是<b>网关的 {@code default} 属性</b>
 * （指向那条 sequenceFlow 的 id），而不是连线自身的属性。
 * 遗漏它会导致「所有条件都不命中时流程卡死」——这与 validate 的 GATEWAY_NO_DEFAULT 规则呼应。
 */
function defaultBranchAttr(nodeId: string, ctx: Ctx): string {
  const defaultEdge = ctx.dsl.edges.find(
    (e) => e.source === nodeId && e.kind === EdgeKind.DEFAULT,
  )
  return defaultEdge ? ` default="${escAttr(defaultEdge.id)}"` : ''
}

/** 复杂结构统一以 JSON 写入 ckplm 扩展属性（无损往返） */
function ctxJson(name: string, value: unknown): string {
  return `${ckplmAttr(name)}="${escAttr(JSON.stringify(value))}"`
}

function ctxExtensionElement(inner: string): string {
  return inner ? `<extensionElements>${inner}</extensionElements>` : ''
}

function attrsToXml(attrs: Record<string, string>): string {
  return Object.entries(attrs)
    .map(([key, value]) => `${key}="${escAttr(value)}"`)
    .join(' ')
}

function indent(text: string, spaces: number): string {
  if (!text) {
    return text
  }
  const pad = ' '.repeat(spaces)
  return text
    .split('\n')
    .map((line) => (line.trim() ? pad + line : line))
    .join('\n')
}

function esc(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escAttr(text: string): string {
  return esc(text).replace(/"/g, '&quot;')
}
