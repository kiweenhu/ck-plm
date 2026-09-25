import { describe, expect, it } from 'vitest'
import {
  APPROVAL_OPINION_FORM_CODE,
  COUNTERSIGN_FORM_CODE,
  ApprovalMode,
  AssigneeStrategy,
  BUILTIN_SERVICES,
  EdgeKind,
  SETUP_ASSIGNEE_FORM_CODE,
  NodeType,
  addNode,
  createEmptyDsl,
  parseFlowDsl,
  replaceNode,
  updateNode,
  type FlowDsl,
  type FlowNode,
} from '@flow-dsl-core'
import {
  assignByPath,
  getByPath,
  pruneHidden,
  visibleFields,
  type FieldSpec,
  type SchemaContext,
} from '../field-schema'
import {
  assigneeFields,
  duplicateKeys,
  edgeFields,
  metaFields,
  missingNodeSchemaTypes,
  nodeFields,
  variableFields,
} from '../node-schemas'

function ctxOf(dsl: FlowDsl, target: Record<string, unknown>, kind: 'node' | 'edge' | 'meta' = 'node'): SchemaContext {
  return { dsl, target, kind }
}

const emptyDsl = () => createEmptyDsl({ key: 'panel_flow', name: '面板测试' })

/** 取字段并断言存在（避免非空断言噪声） */
function fieldOf(fields: FieldSpec[], key: string): FieldSpec {
  const found = fields.find((f) => f.key === key)
  expect(found, `缺少字段声明: ${key}`).toBeDefined()
  return found as FieldSpec
}

const visible = (field: FieldSpec, dsl: FlowDsl, target: Record<string, unknown>): boolean =>
  field.visibleIf ? field.visibleIf(target, ctxOf(dsl, target)) : true

/**
 * 按 id 取节点。
 *
 * <p>不能用 `nodes[0]`：`createEmptyDsl` 自带 START/END，`addNode` 是<b>追加</b>，
 * 所以新增节点不在下标 0。
 */
function nodeById(dsl: FlowDsl, id: string): Record<string, unknown> {
  const found = dsl.nodes.find((n) => n.id === id)
  expect(found, `节点不存在: ${id}`).toBeDefined()
  return found as unknown as Record<string, unknown>
}

describe('字段声明覆盖率（与 dsl-core schema 对齐）', () => {
  it('每个节点类型都有字段声明', () => {
    expect(missingNodeSchemaTypes()).toEqual([])
  })

  it('每类声明内无重复 key', () => {
    for (const type of Object.values(NodeType)) {
      const fields = nodeFields(type)
      expect(duplicateKeys(fields), `${type} 存在重复字段 key`).toEqual([])
    }
    expect(duplicateKeys(edgeFields())).toEqual([])
    expect(duplicateKeys(metaFields())).toEqual([])
    expect(duplicateKeys(variableFields())).toEqual([])
  })

  it('每个节点类型都声明了 名称（必填）与 说明', () => {
    for (const type of Object.values(NodeType)) {
      const fields = nodeFields(type)
      expect(fieldOf(fields, 'name').required).toBe(true)
      expect(fields.some((f) => f.key === 'description')).toBe(true)
    }
  })

  it('服务下拉来自内置服务白名单（不开放任意 Bean 名）', () => {
    const serviceFields = nodeFields(NodeType.SERVICE)
    const ref = fieldOf(serviceFields, 'serviceRef')
    const whitelist = BUILTIN_SERVICES.map((s) => s.id)
    const options = ref.options?.map((o) => o.value) ?? []

    // 候选只能是白名单的子集 —— 这是"不开放任意 Bean 名"的护栏本身
    expect(options.length).toBeGreaterThan(0)
    for (const id of options) {
      expect(whitelist, `${id} 不在内置服务白名单里`).toContain(id)
    }
    // 已独立成节点库入口的服务不在下拉里重复出现（否则同一个动作两处入口）。
    // 不在这里锁死"具体剩哪几项"：每次加/挪入口都得改测试就成了维护负担，
    // 那条规则由 node-presets 的「对象动作四件套」用例锁定。
    expect(options).not.toContain('object.checkout')
    expect(options).not.toContain('object.setAttribute')
  })

  it('参数下拉与编译层读参数的位置一致（路径都写在 params.<参数名>）', () => {
    for (const service of BUILTIN_SERVICES) {
      for (const param of service.params) {
        if (!param.optionsSource) continue
        const field = fieldOf(nodeFields(NodeType.SERVICE), `params.${param.name}`)
        expect(field.control, `params.${param.name}`).toBe('select')
        expect(field.optionsSource, `params.${param.name}`).toBe(param.optionsSource)
      }
    }
  })
})

describe('自动服务 · 设置状态（目标状态下拉）', () => {
  /** 造一个选好服务的自动服务节点 */
  function serviceNode(serviceRef: string): { dsl: FlowDsl; node: Record<string, unknown> } {
    const dsl = addNode(
      emptyDsl(),
      { id: 'svc_1', type: NodeType.SERVICE, name: '设置状态', serviceRef } as FlowNode,
      { x: 0, y: 0 },
    )
    return { dsl, node: nodeById(dsl, 'svc_1') }
  }

  it('「设置状态」是独立服务：id 不变（已部署模板仍能引用），名称已改', () => {
    const service = BUILTIN_SERVICES.find((s) => s.label === '设置状态')
    expect(service?.id).toBe('object.setLifecycleState')
    const ref = fieldOf(nodeFields(NodeType.SERVICE), 'serviceRef')
    // 参数已由下拉承载 → 下拉标签不再挤参数名（原来是「设置生命周期状态（state*）」）
    expect(ref.options?.find((o) => o.value === 'object.setLifecycleState')?.label).toBe('设置状态')
    // 没有下拉可选的服务的参数名提示保留（否则用户不知道要填哪些参数）
    expect(ref.options?.find((o) => o.value === 'notification.send')?.label)
      .toBe('发送通知（templateCode*、recipients*）')
  })

  it('目标状态下拉的候选来自生命周期状态清单', () => {
    const field = fieldOf(nodeFields(NodeType.SERVICE), 'params.state')
    expect(field.label).toBe('目标状态')
    expect(field.control).toBe('select')
    expect(field.optionsSource).toBe('LIFECYCLE_STATES')
  })

  it('下拉只在选中「设置状态」时出现；此时通用键值编辑器让位', () => {
    const fields = nodeFields(NodeType.SERVICE)
    const stateField = fieldOf(fields, 'params.state')
    const kvField = fieldOf(fields, 'params')

    const setState = serviceNode('object.setLifecycleState')
    expect(visible(stateField, setState.dsl, setState.node)).toBe(true)
    expect(visible(kvField, setState.dsl, setState.node)).toBe(false)

    const notify = serviceNode('notification.send')
    expect(visible(stateField, notify.dsl, notify.node)).toBe(false)
    expect(visible(kvField, notify.dsl, notify.node)).toBe(true)
  })

  it('在「设置状态」上选完状态后不会被清理动作误删', () => {
    // 通用键值编辑器刻意没有 clearOnHide：若给它加上，每次写值都会把 params 整个删掉
    //（含刚选中的目标状态）—— 这正是"清理由 PropertyPanel 统一负责"要防的坑
    const fields = nodeFields(NodeType.SERVICE)
    const { dsl, node } = serviceNode('object.setLifecycleState')
    const written = assignByPath(node, 'params.state', 'RELEASED')
    const kept = pruneHidden(fields, ctxOf(dsl, written), written)
    expect(getByPath(kept, 'params.state')).toBe('RELEASED')
  })

  it('切换到别的服务后目标状态被清掉（DSL 不留别的服务的参数）', () => {
    const fields = nodeFields(NodeType.SERVICE)
    const { dsl, node } = serviceNode('object.setLifecycleState')
    const withState = assignByPath(node, 'params.state', 'RELEASED')
    const switched = { ...withState, serviceRef: 'notification.send' }
    const pruned = pruneHidden(fields, ctxOf(dsl, switched), switched)
    expect(getByPath(pruned, 'params')).toBeUndefined()
  })
})

describe('节点表单：注册表驱动的下拉 + 内置固定表单只读', () => {
  it('设置审批人：只读下拉，且默认指向内置的「设置流程参与者」', () => {
    const dsl = emptyDsl()
    const target = { type: NodeType.SET_ASSIGNEE }
    const field = fieldOf(nodeFields(NodeType.SET_ASSIGNEE), 'formRef')

    expect(field.control).toBe('select')
    expect(field.disabled).toBe(true)
    // 候选项来自注册表（不能手填 code）
    const options = field.optionsFromDsl?.(ctxOf(dsl, target)) ?? []
    expect(options.map((o) => o.value)).toEqual([SETUP_ASSIGNEE_FORM_CODE])
    // 老模板的节点没写过 formRef 时，显示内置值 —— 而不是一个灰掉的空框
    expect(field.defaultWhenEmpty?.(ctxOf(dsl, target))).toBe(SETUP_ASSIGNEE_FORM_CODE)
  })

  it('审批节点：可选用哪张表单，默认指向内置的「审批意见」', () => {
    const dsl = emptyDsl()
    const target = { type: NodeType.APPROVAL }
    const field = fieldOf(nodeFields(NodeType.APPROVAL), 'formRef')

    expect(field.control).toBe('select')
    // 审批表单不是"固定"的：企业可以在「业务配置 → 流程表单」里加自己的审批表单再来选
    expect(field.disabled).toBe(false)
    const options = field.optionsFromDsl?.(ctxOf(dsl, target)) ?? []
    expect(options.map((o) => o.value)).toEqual([APPROVAL_OPINION_FORM_CODE])
    // 老模板（创建时还没登记这个表单）不给"空框"：显示实际会用的那张
    expect(field.defaultWhenEmpty?.(ctxOf(dsl, target))).toBe(APPROVAL_OPINION_FORM_CODE)
  })

  it('审批 + 会签模式：下拉收窄到会签表单（编译层同样以它为准，给别的选项就是误导）', () => {
    const dsl = emptyDsl()
    const target = { type: NodeType.APPROVAL, approvalMode: 'COUNTERSIGN' }
    const field = fieldOf(nodeFields(NodeType.APPROVAL), 'formRef')

    const options = field.optionsFromDsl?.(ctxOf(dsl, target)) ?? []
    expect(options.map((o) => o.value)).toEqual([COUNTERSIGN_FORM_CODE])
    expect(field.defaultWhenEmpty?.(ctxOf(dsl, target))).toBe(COUNTERSIGN_FORM_CODE)
  })

  it('会签节点：只读下拉，且默认指向内置的「会签」', () => {
    const dsl = emptyDsl()
    const target = { type: NodeType.COUNTERSIGN_APPROVAL }
    const field = fieldOf(nodeFields(NodeType.COUNTERSIGN_APPROVAL), 'formRef')

    expect(field.control).toBe('select')
    expect(field.disabled).toBe(true)
    const options = field.optionsFromDsl?.(ctxOf(dsl, target)) ?? []
    expect(options.map((o) => o.value)).toEqual([COUNTERSIGN_FORM_CODE])
    expect(field.defaultWhenEmpty?.(ctxOf(dsl, target))).toBe(COUNTERSIGN_FORM_CODE)
  })

  it('没有专属模板的节点（办理节点）仍是可选下拉，不做固定、也没有默认表单', () => {
    const dsl = emptyDsl()
    const field = fieldOf(nodeFields(NodeType.TASK), 'formRef')
    expect(field.control).toBe('select')
    expect(field.disabled).toBeFalsy()
    // 办理节点没有内置默认表单：兜底由运行期的通用表单承担，不在设计期假装有一张
    expect(field.defaultWhenEmpty?.(ctxOf(dsl, { type: NodeType.TASK }))).toBeUndefined()
  })
})

describe('路径读写', () => {
  it('getByPath 支持嵌套路径，缺失时返回 undefined', () => {
    const target = { a: { b: { c: 1 } }, flat: 2 }
    expect(getByPath(target, 'a.b.c')).toBe(1)
    expect(getByPath(target, 'flat')).toBe(2)
    expect(getByPath(target, 'a.x.y')).toBeUndefined()
  })

  it('assignByPath 不可变写入：不改动原对象', () => {
    const target = { assignee: { strategy: 'USER' } }
    const next = assignByPath(target, 'assignee.strategy', 'ROLE')
    expect(next.assignee).toEqual({ strategy: 'ROLE' })
    expect(target.assignee).toEqual({ strategy: 'USER' })
  })

  it('assignByPath 置空时清理空壳父对象（避免 passRule:{} 绕过未配置判定）', () => {
    const next = assignByPath({ passRule: { percent: 60 } }, 'passRule.percent', undefined)
    expect(next.passRule).toBeUndefined()
  })

  it('assignByPath 清空多字段子对象时保留仍有值的兄弟', () => {
    const next = assignByPath({ passRule: { mode: 'PERCENT', percent: 60 } }, 'passRule.percent', '')
    expect(next.passRule).toEqual({ mode: 'PERCENT' })
  })
})

describe('可见性：审批人策略', () => {
  it('USER 策略只显示指定用户', () => {
    const dsl = emptyDsl()
    const assignee = assigneeFields('assignee')
    const target = { assignee: { strategy: AssigneeStrategy.USER } }
    expect(visible(fieldOf(assignee, 'assignee.userOids'), dsl, target)).toBe(true)
    expect(visible(fieldOf(assignee, 'assignee.roleCodes'), dsl, target)).toBe(false)
    expect(visible(fieldOf(assignee, 'assignee.variableName'), dsl, target)).toBe(false)
    expect(visible(fieldOf(assignee, 'assignee.deptFieldKey'), dsl, target)).toBe(false)
  })

  it('ROLE / PROJECT_ROLE 策略只显示角色', () => {
    const dsl = emptyDsl()
    const assignee = assigneeFields('assignee')
    for (const strategy of [AssigneeStrategy.ROLE, AssigneeStrategy.PROJECT_ROLE]) {
      const target = { assignee: { strategy } }
      expect(visible(fieldOf(assignee, 'assignee.roleCodes'), dsl, target)).toBe(true)
      expect(visible(fieldOf(assignee, 'assignee.userOids'), dsl, target)).toBe(false)
    }
  })

  it('pruneHidden 清掉切换策略后的残留取值', () => {
    const dsl = emptyDsl()
    const fields = assigneeFields('assignee')
    const before = { assignee: { strategy: AssigneeStrategy.USER, userOids: ['u1', 'u2'] } }
    // 用户把策略改成「指定角色」
    const switched = assignByPath({ ...before }, 'assignee.strategy', AssigneeStrategy.ROLE)
    const cleaned = pruneHidden(fields, ctxOf(dsl, switched), switched)
    expect((cleaned.assignee as Record<string, unknown>).userOids).toBeUndefined()
  })
})

describe('可见性：会签规则与截止时间', () => {
  it('仅会签模式显示通过规则', () => {
    const dsl = emptyDsl()
    const fields = nodeFields(NodeType.APPROVAL)
    const single = { approvalMode: ApprovalMode.SINGLE }
    expect(visible(fieldOf(fields, 'passRule.mode'), dsl, single)).toBe(false)
    const countersign = { approvalMode: ApprovalMode.COUNTERSIGN }
    expect(visible(fieldOf(fields, 'passRule.mode'), dsl, countersign)).toBe(true)
    // visibleFields 是渲染器实际使用的入口，此处顺带锁定其行为
    expect(visibleFields(fields, ctxOf(dsl, countersign)).map((f) => f.key)).toContain('passRule.mode')
  })

  it('通过比例 / 票数 互斥显示，一票否决时不显示弃权处理', () => {
    const dsl = emptyDsl()
    const fields = nodeFields(NodeType.APPROVAL)
    const percent = { approvalMode: ApprovalMode.COUNTERSIGN, passRule: { mode: 'PERCENT' } }
    expect(visible(fieldOf(fields, 'passRule.percent'), dsl, percent)).toBe(true)
    expect(visible(fieldOf(fields, 'passRule.count'), dsl, percent)).toBe(false)

    const count = { approvalMode: ApprovalMode.COUNTERSIGN, passRule: { mode: 'COUNT' } }
    expect(visible(fieldOf(fields, 'passRule.count'), dsl, count)).toBe(true)
    expect(visible(fieldOf(fields, 'passRule.percent'), dsl, count)).toBe(false)

    const veto = { approvalMode: ApprovalMode.COUNTERSIGN, passRule: { mode: 'VETO' } }
    expect(visible(fieldOf(fields, 'passRule.abstain'), dsl, veto)).toBe(false)
  })

  it('截止时间未启用时子字段全部隐藏；启用后按逾期后果显示升级对象', () => {
    const dsl = emptyDsl()
    const fields = nodeFields(NodeType.APPROVAL)
    const off = { deadline: undefined }
    expect(visible(fieldOf(fields, 'deadline.durationHours'), dsl, off)).toBe(false)

    const on = { deadline: { anchor: 'NODE_START', durationHours: 24, action: 'SKIP' } }
    expect(visible(fieldOf(fields, 'deadline.durationHours'), dsl, on)).toBe(true)
    expect(visible(fieldOf(fields, 'deadline.reassignTo.strategy'), dsl, on)).toBe(false)

    const reassign = { deadline: { anchor: 'NODE_START', durationHours: 24, action: 'REASSIGN' } }
    expect(visible(fieldOf(fields, 'deadline.reassignTo.strategy'), dsl, reassign)).toBe(true)
  })

  it('驳回目标节点仅在 target=NODE 时显示', () => {
    const dsl = emptyDsl()
    const fields = nodeFields(NodeType.APPROVAL)
    const previous = { reject: { enabled: true, target: 'PREVIOUS' } }
    expect(visible(fieldOf(fields, 'reject.targetNodeId'), dsl, previous)).toBe(false)
    const node = { reject: { enabled: true, target: 'NODE' } }
    expect(visible(fieldOf(fields, 'reject.targetNodeId'), dsl, node)).toBe(true)
  })

  it('object-toggle 模板与 dsl-core 结构一致', () => {
    const fields = nodeFields(NodeType.APPROVAL)
    expect(fieldOf(fields, 'deadline').objectTemplate?.()).toEqual({
      anchor: 'NODE_START',
      durationHours: 24,
      action: 'SKIP',
    })
    // 开启「允许驳回」时**不预填驳回目标**：预填 PREVIOUS 会让"其实什么都没配"的节点
    // 看起来是配好的（目标＝上一步），校验也就永远不报 —— 驳回到运行期才把经办人挡住。
    // 不预填之后它是可见的空目标，由 REJECT_NO_TARGET 拦保存。
    expect(fieldOf(fields, 'reject').objectTemplate?.()).toEqual({
      enabled: true,
      commentRequired: true,
    })
  })
})

describe('可见性：服务与定时', () => {
  it('SERVICE 配了服务就隐藏表达式，反之亦然', () => {
    const dsl = emptyDsl()
    const fields = nodeFields(NodeType.SERVICE)
    expect(visible(fieldOf(fields, 'params'), dsl, { serviceRef: 'object.checkin' })).toBe(true)
    expect(visible(fieldOf(fields, 'expression'), dsl, { serviceRef: 'object.checkin' })).toBe(false)
    expect(visible(fieldOf(fields, 'expression'), dsl, {})).toBe(true)
  })

  it('TIMER 按等待方式互斥显示时长/时点', () => {
    const dsl = emptyDsl()
    const fields = nodeFields(NodeType.TIMER)
    expect(visible(fieldOf(fields, 'duration'), dsl, { mode: 'DURATION' })).toBe(true)
    expect(visible(fieldOf(fields, 'at'), dsl, { mode: 'DURATION' })).toBe(false)
    expect(visible(fieldOf(fields, 'at'), dsl, { mode: 'AT' })).toBe(true)
    expect(visible(fieldOf(fields, 'duration'), dsl, { mode: 'AT' })).toBe(false)
  })
})

describe('可见性：连线条件', () => {
  it('普通流转不显示条件字段，条件流转才显示', () => {
    const dsl = emptyDsl()
    const fields = edgeFields()
    const normal = { kind: EdgeKind.NORMAL }
    expect(visible(fieldOf(fields, 'condition.fieldKey'), dsl, normal)).toBe(false)
    const conditional = { kind: EdgeKind.CONDITION, condition: { mode: 'FIELD', operator: 'EQ' } }
    expect(visible(fieldOf(fields, 'condition.fieldKey'), dsl, conditional)).toBe(true)
    expect(visible(fieldOf(fields, 'terminateSiblings'), dsl, conditional)).toBe(true)
  })

  it('一元操作符不显示取值输入框', () => {
    const dsl = emptyDsl()
    const fields = edgeFields()
    const unary = { kind: EdgeKind.CONDITION, condition: { mode: 'FIELD', operator: 'IS_EMPTY' } }
    expect(visible(fieldOf(fields, 'condition.value'), dsl, unary)).toBe(false)
    const binary = { kind: EdgeKind.CONDITION, condition: { mode: 'FIELD', operator: 'EQ' } }
    expect(visible(fieldOf(fields, 'condition.value'), dsl, binary)).toBe(true)
  })

  it('表达式模式隐藏字段选择器', () => {
    const dsl = emptyDsl()
    const fields = edgeFields()
    const expression = { kind: EdgeKind.CONDITION, condition: { mode: 'EXPRESSION' } }
    expect(visible(fieldOf(fields, 'condition.fieldKey'), dsl, expression)).toBe(false)
    expect(visible(fieldOf(fields, 'condition.expression'), dsl, expression)).toBe(true)
  })
})

describe('自动服务节点的配置说明与样例', () => {
  it('未选服务时给出「配置说明 + 配置样例」，选好服务后收起', () => {
    const fields = nodeFields(NodeType.SERVICE)
    const hint = fieldOf(fields, 'hint-service')
    const sample = fieldOf(fields, 'hint-service-example')

    // 样例要能直接照抄：含服务 id、参数名、流程变量引用三样
    expect(sample.help).toContain('custom.sendMail')
    expect(sample.help).toContain('to → ${initiator}')
    expect(hint.help).toContain('${流程变量}')

    // 只讲函数调用本身：平台自带动作（设置状态）与外部接口（系统集成）
    // 各自有节点库入口，混进这里的样例会让人以为"函数调用"也归它们管
    expect(sample.help).not.toContain('设置状态')
    expect(sample.help).not.toContain('系统集成')

    // 没选服务 = 面板只有空控件（用户最迷茫的状态）→ 两块都必须在
    expect(visible(hint, emptyDsl(), {})).toBe(true)
    expect(visible(sample, emptyDsl(), {})).toBe(true)

    // 选好服务 → 面板已列出该服务的参数，说明收起（不然是噪声）
    expect(visible(hint, emptyDsl(), { serviceRef: 'object.checkout' })).toBe(false)
    expect(visible(sample, emptyDsl(), { serviceRef: 'object.checkout' })).toBe(false)
  })
})

describe('面板产出可被 dsl-core 接受', () => {
  it('按面板顺序填写后，节点结构通过 zod 校验', () => {
    const dsl = emptyDsl()
    // 模拟用户在面板上的操作序列
    let node: Record<string, unknown> = { id: 'a1', type: NodeType.APPROVAL, name: '技术评审' }
    node = assignByPath(node, 'assignee.strategy', AssigneeStrategy.ROLE)
    node = assignByPath(node, 'assignee.roleCodes', ['ENG'])
    node = assignByPath(node, 'approvalMode', ApprovalMode.COUNTERSIGN)
    node = assignByPath(node, 'passRule.mode', 'PERCENT')
    node = assignByPath(node, 'passRule.percent', 60)
    node = assignByPath(node, 'reject', { enabled: true, target: 'PREVIOUS' })

    const withNode = addNode(dsl, node as unknown as FlowNode, { x: 100, y: 100 })
    const parsed = parseFlowDsl(withNode)
    expect(parsed.ok, parsed.ok ? '' : parsed.error).toBe(true)
  })

  it('面板清空会签规则后，节点仍是合法结构（不残留空壳）', () => {
    const dsl = emptyDsl()
    let node: Record<string, unknown> = {
      id: 'a2',
      type: NodeType.APPROVAL,
      name: '评审',
      // assignee 是 APPROVAL 的结构必填项（zod 层）
      assignee: { strategy: AssigneeStrategy.INITIATOR },
      approvalMode: ApprovalMode.SINGLE,
      passRule: { mode: 'PERCENT', percent: 60 },
    }
    // 改回单签：面板会把可见性失效的 passRule.percent 清掉，只留 mode → 再清 mode 即整键删除
    node = assignByPath(node, 'passRule.mode', undefined)
    node = assignByPath(node, 'passRule.percent', undefined)
    expect(node.passRule).toBeUndefined()
    const parsed = parseFlowDsl(addNode(dsl, node as unknown as FlowNode, { x: 0, y: 0 }))
    expect(parsed.ok).toBe(true)
  })
})

describe('replaceNode 与浅合并的差异（面板写入必须用替换）', () => {
  it('浅合并无法删除字段，整对象替换可以', () => {
    const dsl = emptyDsl()
    const node: FlowNode = {
      id: 'a3',
      type: NodeType.APPROVAL,
      name: '审批',
      assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      approvalMode: ApprovalMode.COUNTERSIGN,
      passRule: { mode: 'PERCENT', percent: 60 },
    } as FlowNode
    const withNode = addNode(dsl, node, { x: 0, y: 0 })

    // 模拟面板的<b>真实产出</b>：可见性变化会把 passRule 从对象里删掉（而非置空）
    const panelResult = {
      id: 'a3',
      type: NodeType.APPROVAL,
      name: '审批',
      assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      approvalMode: ApprovalMode.SINGLE,
    } as FlowNode
    expect('passRule' in panelResult).toBe(false)

    const merged = updateNode<FlowNode>(withNode, 'a3', panelResult)
    const replaced = replaceNode(withNode, 'a3', panelResult)

    // 浅合并：旧 passRule 仍然留在节点上（这正是必须用替换的原因）
    expect(nodeById(merged, 'a3').passRule).toEqual({ mode: 'PERCENT', percent: 60 })
    // 整对象替换：如实反映面板产出
    expect(nodeById(replaced, 'a3').passRule).toBeUndefined()
  })

  it('替换不会改变节点 id 与 type', () => {
    const dsl = emptyDsl()
    const node = {
      id: 'a4',
      type: NodeType.TASK,
      name: '办理',
      assignee: { strategy: AssigneeStrategy.INITIATOR },
    } as FlowNode
    const withNode = addNode(dsl, node, { x: 0, y: 0 })
    const replaced = replaceNode(withNode, 'a4', { ...node, id: 'hacked', type: NodeType.SERVICE } as FlowNode)
    // 节点身份不可被面板改写：id 仍是 a4、type 仍是 TASK，且不会多出 hacked
    expect(nodeById(replaced, 'a4').type).toBe(NodeType.TASK)
    expect(replaced.nodes.some((n) => n.id === 'hacked')).toBe(false)
    expect(replaced.nodes.filter((n) => n.id === 'a4')).toHaveLength(1)
  })
})

describe('连线 · 路由（通过 / 驳回）', () => {
  /** 一段含审批节点的流程：路由字段只在"源节点是审批/会签"时才有意义 */
  const withApproval = () => addNode(
    emptyDsl(),
    {
      id: 'appr_1',
      type: NodeType.APPROVAL,
      name: '审批',
      assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      approvalMode: ApprovalMode.SINGLE,
    } as unknown as FlowNode,
    { x: 0, y: 0 },
  )

  it('源节点是审批/会签时才显示「路由」', () => {
    const field = fieldOf(edgeFields(), 'route')
    const dsl = withApproval()
    expect(visible(field, dsl, { id: 'e_out', source: 'appr_1', target: 'end_1' })).toBe(true)
    // 开始 → 审批 这条边没有"结论"可分
    expect(visible(field, dsl, { id: 'e_in', source: 'start_1', target: 'appr_1' })).toBe(false)
  })

  it('路由是单选，含「通过」「驳回」以及"什么都不选"', () => {
    const field = fieldOf(edgeFields(), 'route')
    expect(field.control).toBe('select')
    expect(field.options?.map((option) => option.value)).toEqual(['', 'PASS', 'REJECT'])
  })
})

describe('模板 · 通知方式（流程级）', () => {
  it('通知方式是单值（六种渠道可选）', () => {
    const field = fieldOf(metaFields(), 'notifications.channel')
    // 单选：一个流程一个通知出口（多出口是系统侧的事）
    expect(field.control).toBe('select')
    expect(field.options?.map((o) => o.value))
      .toEqual(['CK_PLM', 'EMAIL', 'OA', 'FEISHU', 'DINGTALK', 'WECOM'])
  })

  it('字段不再挂同名分组 —— 组标题与字段名同为「通知方式」会显示成同一个词两遍', () => {
    expect(fieldOf(metaFields(), 'notifications.channel').group).toBeFalsy()
  })

  it('面板上不再有渠道凭据字段（凭据归服务端配置，模板里只留通知方式选择）', () => {
    // 这条用例钉住收拢后的边界：SMTP 地址、OA 接口、应用密钥都不该出现在流程面板上 ——
    // 出现就意味着"每个流程各填一遍企业集成"，而那是这次特意去掉的
    const notifyKeys = metaFields()
      .map((field) => field.key)
      .filter((key) => key.startsWith('notifications.'))
    expect(notifyKeys).toEqual(['notifications.channel'])
  })

  it('通知方式的说明指向服务端配置（否则用户会满面板找填 SMTP 的地方）', () => {
    const help = fieldOf(metaFields(), 'notifications.channel').help ?? ''
    expect(help).toContain('plm.notification')
    expect(help).toContain('服务端')
  })
})


