/**
 * 「设置审批人」活动 —— 跨层测试。
 *
 * <p>四件事必须同时成立：
 * <ol>
 *   <li><b>表单随流程自动派生</b>：下游的审批 / 会签 / 办理活动**自动入表**（不需要谁去声明），
 *       且每项带出该活动的角色作为候选范围；</li>
 *   <li><b>通知不纳入</b>：它的接收人由自身配置表达，不属于"由发起人指派"的岗位；</li>
 *   <li><b>设计期校验</b>拦住"设置活动没放在第一步""有活动却没人指派"这类组合；</li>
 *   <li><b>编译契约</b>：被覆盖的活动绑定到按活动的运行期变量，且类型无损往返。</li>
 * </ol>
 */

import { describe, expect, it } from 'vitest'
import {
  APPROVAL_OPINION_FORM_CODE,
  ApprovalMode,
  AssigneeStrategy,
  EdgeKind,
  IssueCode,
  IssueLevel,
  NodeType,
  SETUP_ASSIGNEE_FORM_CODE,
  SETUP_COVERED_TYPES,
  allTaskFormTemplates,
  canReach,
  createEmptyDsl,
  defaultFormCodeOfNode,
  findFormTemplate,
  fixedFormCodeOf,
  formTemplatesFor,
  registerTaskFormTemplates,
  setupControlledIds,
  setupNodes,
  validateDsl,
  type FlowDsl,
} from '@flow-dsl-core'
import { compileToBpmnXml, parseBpmnXml } from '@flow-compiler'
import {
  buildAssigneeSetupModel,
  toSetupPayload,
  toSetupVariables,
  validateAssigneeSetup,
} from '../assignee-setup'

/**
 * 主流程：
 * ```
 * 开始 → 前置评审（角色 ENG） → 设置审批人 → 技术评审（角色 ENG，单签）
 *      → 质量会签（角色 QA，会签） → 通知 → 结束
 * ```
 *
 * <p>刻意覆盖四种情形：设置活动**之前**的审批、下游的审批 / 会签（都应自动入表）、
 * 下游的通知（不应入表）。
 */
function flow(): FlowDsl {
  const base = createEmptyDsl({ key: 'setup_flow', name: '设置审批人测试' })
  return {
    ...base,
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      {
        id: 'pre_1',
        type: NodeType.APPROVAL,
        name: '前置评审',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        approvalMode: ApprovalMode.SINGLE,
      },
      {
        id: 'set_1',
        type: NodeType.SET_ASSIGNEE,
        name: '设置审批人',
        assignee: { strategy: AssigneeStrategy.INITIATOR },
      },
      {
        id: 'appr_1',
        type: NodeType.APPROVAL,
        name: '技术评审',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        approvalMode: ApprovalMode.SINGLE,
      },
      {
        id: 'cs_1',
        type: NodeType.COUNTERSIGN_APPROVAL,
        name: '质量会签',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['QA'] },
        passRule: { mode: 'PERCENT', percent: 100 },
      },
      {
        id: 'notify_1',
        type: NodeType.NOTIFY,
        name: '结果通知',
        kind: 'NOTIFY',
        recipients: { strategy: AssigneeStrategy.ROLE, roleCodes: ['QA'] },
      },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    edges: [
      { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'pre_1' },
      { id: 'e2', kind: EdgeKind.NORMAL, source: 'pre_1', target: 'set_1' },
      { id: 'e3', kind: EdgeKind.NORMAL, source: 'set_1', target: 'appr_1' },
      { id: 'e4', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'cs_1' },
      { id: 'e5', kind: EdgeKind.NORMAL, source: 'cs_1', target: 'notify_1' },
      { id: 'e6', kind: EdgeKind.NORMAL, source: 'notify_1', target: 'end_1' },
    ],
  } as FlowDsl
}

describe('任务表单：结构随流程自动派生', () => {
  it('下游的审批 / 会签活动自动入表，按流程顺序排列', () => {
    const model = buildAssigneeSetupModel(flow(), 'set_1')
    expect(model.slots.map((s) => s.nodeId)).toEqual(['appr_1', 'cs_1'])
    expect(model.slots.map((s) => s.kind)).toEqual(['审批人', '会签人'])
    // 会签在业务上至少两人
    expect(model.slots[1].minPeople).toBe(2)
  })

  it('每项带出该活动的角色，作为发起人挑人的候选范围', () => {
    const model = buildAssigneeSetupModel(flow(), 'set_1')
    expect(model.slots[0].roleCodes).toEqual(['ENG'])
    expect(model.slots[1].roleCodes).toEqual(['QA'])
    // 有角色约束 → 不应出现"候选为全部用户"的提示
    expect(model.warnings.join('')).not.toContain('未指定角色')
  })

  it('设置活动之前的活动不入表（不归它管），且不需要任何"声明"', () => {
    const model = buildAssigneeSetupModel(flow(), 'set_1')
    expect(model.slots.some((s) => s.nodeId === 'pre_1')).toBe(false)
    expect(model.slots.some((s) => s.nodeId === 'notify_1')).toBe(false)
  })

  it('活动没指定角色时，候选退化为全体用户并如实提示（不静默给空列表）', () => {
    const dsl = flow()
    const noRole: FlowDsl = {
      ...dsl,
      nodes: dsl.nodes.map((n) =>
        n.id === 'appr_1'
          ? ({ ...n, assignee: { strategy: AssigneeStrategy.USER, userOids: ['u1'] } } as never)
          : n,
      ),
    }
    const model = buildAssigneeSetupModel(noRole, 'set_1')
    const slot = model.slots.find((s) => s.nodeId === 'appr_1')
    expect(slot?.roleCodes).toEqual([])
    expect(model.warnings.join('')).toContain('技术评审')
  })

  it('会签未填两人时报错，填两人后通过', () => {
    const model = buildAssigneeSetupModel(flow(), 'set_1')
    expect(validateAssigneeSetup(model, { appr_1: ['u1'] }).map((i) => i.nodeId)).toEqual(['cs_1'])

    const ok = validateAssigneeSetup(model, { appr_1: ['u1'], cs_1: ['u2', 'u3'] })
    expect(ok).toEqual([])
  })

  it('提交载荷去重，且不含未填的槽位', () => {
    const model = buildAssigneeSetupModel(flow(), 'set_1')
    const payload = toSetupPayload(model, { appr_1: ['u1', 'u1'], cs_1: [] })
    expect(payload).toEqual([{ nodeId: 'appr_1', userOids: ['u1'] }])
  })

  it('槽位带出「变量取单个还是列表」的形态：只有单签审批是单个', () => {
    const model = buildAssigneeSetupModel(flow(), 'set_1')
    expect(model.slots.map((s) => [s.nodeId, s.singleValue])).toEqual([
      ['appr_1', true],
      ['cs_1', false],
    ])
  })

  it('流程变量按活动拼名，形态与编译产物一致：单签取单个 oid、会签取列表', () => {
    const model = buildAssigneeSetupModel(flow(), 'set_1')
    const variables = toSetupVariables(model, { appr_1: ['u1'], cs_1: ['u2', 'u3'] })
    expect(variables).toEqual({
      ckplmSetupAssignees_appr_1: 'u1',
      ckplmSetupAssignees_cs_1: ['u2', 'u3'],
    })
  })

  it('覆盖范围由 dsl-core 唯一定义：只有审批 / 会签 / 办理三类', () => {
    expect(SETUP_COVERED_TYPES).toEqual([
      NodeType.APPROVAL,
      NodeType.COUNTERSIGN_APPROVAL,
      NodeType.TASK,
    ])
    const dsl = flow()
    expect(setupNodes(dsl).map((n) => n.id)).toEqual(['set_1'])
    // 下游可达的审批 / 会签被覆盖；通知不在其列
    expect(Array.from(setupControlledIds(dsl)).sort()).toEqual(['appr_1', 'cs_1'])
    expect(canReach(dsl, 'set_1', 'cs_1')).toBe(true)
    expect(canReach(dsl, 'set_1', 'pre_1')).toBe(false)
  })
})

/**
 * 四种"人数口径"都在这条链上：
 * ```
 * 开始 → 设置审批人 → 单人审批（单签） → 并行审批（并行全员） → 会签审批 → 资料补齐（办理） → 结束
 * ```
 */
function flowWithKinds(): FlowDsl {
  const base = createEmptyDsl({ key: 'setup_kinds', name: '人员数量口径' })
  return {
    ...base,
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      {
        id: 'set_1',
        type: NodeType.SET_ASSIGNEE,
        name: '设置审批人',
        assignee: { strategy: AssigneeStrategy.INITIATOR },
      },
      {
        id: 'appr_single',
        type: NodeType.APPROVAL,
        name: '单人审批',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        approvalMode: ApprovalMode.SINGLE,
      },
      {
        id: 'appr_multi',
        type: NodeType.APPROVAL,
        name: '并行审批',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        approvalMode: ApprovalMode.PARALLEL_ALL,
      },
      {
        id: 'cs_1',
        type: NodeType.COUNTERSIGN_APPROVAL,
        name: '会签审批',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['QA'] },
        passRule: { mode: 'PERCENT', percent: 100 },
      },
      {
        id: 'task_1',
        type: NodeType.TASK,
        name: '资料补齐',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    edges: [
      { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'set_1' },
      { id: 'e2', kind: EdgeKind.NORMAL, source: 'set_1', target: 'appr_single' },
      { id: 'e3', kind: EdgeKind.NORMAL, source: 'appr_single', target: 'appr_multi' },
      { id: 'e4', kind: EdgeKind.NORMAL, source: 'appr_multi', target: 'cs_1' },
      { id: 'e5', kind: EdgeKind.NORMAL, source: 'cs_1', target: 'task_1' },
      { id: 'e6', kind: EdgeKind.NORMAL, source: 'task_1', target: 'end_1' },
    ],
  } as FlowDsl
}

describe('人员数量口径：谁的活该几个人办', () => {
  it('审批（单签）与办理只给单选，会签与多签审批才给多选', () => {
    const model = buildAssigneeSetupModel(flowWithKinds(), 'set_1')
    expect(model.slots.map((s) => [s.nodeId, s.kind, s.allowMultiple])).toEqual([
      ['appr_single', '审批人', false],
      ['appr_multi', '审批人', true],
      ['cs_1', '会签人', true],
      ['task_1', '办理人', false],
    ])
  })

  it('单选的活动被填了两个人 → 拦下（多出来的会被运行期丢掉，与其静默丢弃不如说清楚）', () => {
    const model = buildAssigneeSetupModel(flowWithKinds(), 'set_1')
    // 其余槽位都按口径填好，让问题只剩"单选却给了两个"
    const issues = validateAssigneeSetup(model, {
      appr_single: ['u1', 'u2'],
      appr_multi: ['u3'],
      cs_1: ['u4', 'u5'],
      task_1: ['u6'],
    })
    expect(issues.map((i) => i.nodeId)).toEqual(['appr_single'])
    expect(issues[0].message).toContain('只能指定 1 人')
  })

  it('按口径填满 → 通过', () => {
    const model = buildAssigneeSetupModel(flowWithKinds(), 'set_1')
    const ok = validateAssigneeSetup(model, {
      appr_single: ['u1'],
      appr_multi: ['u2', 'u3'],
      cs_1: ['u4', 'u5'],
      task_1: ['u6'],
    })
    expect(ok).toEqual([])
  })

  it('单选 / 多选只改控件，不改变量形态（单签取单个 oid，其余取列表）', () => {
    const model = buildAssigneeSetupModel(flowWithKinds(), 'set_1')
    const variables = toSetupVariables(model, {
      appr_single: ['u1'],
      appr_multi: ['u2', 'u3'],
      cs_1: ['u4', 'u5'],
      task_1: ['u6'],
    })
    expect(variables).toEqual({
      ckplmSetupAssignees_appr_single: 'u1',
      ckplmSetupAssignees_appr_multi: ['u2', 'u3'],
      ckplmSetupAssignees_cs_1: ['u4', 'u5'],
      ckplmSetupAssignees_task_1: ['u6'],
    })
  })
})

describe('设计期校验', () => {
  it('有审批 / 会签 / 办理活动，却没有设置活动 → 提醒（这些人不会被发起人指派）', () => {
    const dsl = flow()
    const withoutSetter: FlowDsl = {
      ...dsl,
      nodes: dsl.nodes.filter((n) => n.type !== NodeType.SET_ASSIGNEE),
      edges: dsl.edges
        .filter((e) => e.source !== 'set_1' && e.target !== 'set_1')
        .map((e) => (e.source === 'pre_1' ? { ...e, target: 'appr_1' } : e)),
    }
    const issue = validateDsl(withoutSetter).issues.find((i) => i.code === IssueCode.SETUP_MISSING)
    expect(issue).toBeDefined()
    expect(issue?.level).toBe(IssueLevel.WARNING)
  })

  it('设置活动没有直接跟在「开始」之后 → 提醒（它之前的活动人员不会被指派）', () => {
    const issue = validateDsl(flow()).issues.find((i) => i.code === IssueCode.SETUP_NOT_FIRST)
    expect(issue).toBeDefined()
    expect(issue?.nodeId).toBe('set_1')
    expect(issue?.level).toBe(IssueLevel.WARNING)
  })

  it('多个设置活动且互相不重叠 → 提醒（只是多设了几个，不构成冲突）', () => {
    const dsl = flow()
    const extra = {
      id: 'set_2',
      type: NodeType.SET_ASSIGNEE,
      name: '再设置一次',
      assignee: { strategy: AssigneeStrategy.INITIATOR },
    }
    // 既没有任何出边，也就覆盖不到任何活动 —— 与 set_1 不存在冲突
    const withTwo: FlowDsl = { ...dsl, nodes: [...dsl.nodes, extra] } as FlowDsl
    const issue = validateDsl(withTwo).issues.find((i) => i.code === IssueCode.SETUP_MULTIPLE)
    expect(issue?.level).toBe(IssueLevel.WARNING)
  })

  it('两个设置活动覆盖同一个活动 → 错误（后执行者会静默覆盖先执行者，且拦保存）', () => {
    const dsl = flow()
    // 第二个设置活动也接在「开始」之后，并同样流到「技术评审」——
    // 于是 appr_1（以及它下游的 cs_1）同时被两个设置活动覆盖
    const withTwo: FlowDsl = {
      ...dsl,
      nodes: [
        ...dsl.nodes,
        {
          id: 'set_2',
          type: NodeType.SET_ASSIGNEE,
          name: '再设置一次',
          assignee: { strategy: AssigneeStrategy.INITIATOR },
        },
      ],
      edges: [
        ...dsl.edges,
        { id: 'e7', kind: EdgeKind.NORMAL, source: 'start_1', target: 'set_2' },
        { id: 'e8', kind: EdgeKind.NORMAL, source: 'set_2', target: 'appr_1' },
      ],
    } as FlowDsl

    const report = validateDsl(withTwo)
    const issue = report.issues.find(
      (i) => i.code === IssueCode.SETUP_MULTIPLE && i.nodeId === 'appr_1',
    )
    expect(issue).toBeDefined()
    // 会丢数据的问题不是"建议"：必须是错误，且能被保存拦截
    expect(issue?.level).toBe(IssueLevel.ERROR)
    expect(issue?.message).toContain('技术评审')
    expect(report.ok).toBe(false)
  })

  it('设置活动下游没有任何审批 / 会签 / 办理活动 → 提醒（表单会是空的）', () => {
    const dsl = flow()
    // 让设置活动直接连到结束：下游再无人工活动
    const empty: FlowDsl = {
      ...dsl,
      nodes: dsl.nodes.filter((n) => ['start_1', 'set_1', 'end_1'].includes(n.id)),
      edges: [
        { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'set_1' },
        { id: 'e2', kind: EdgeKind.NORMAL, source: 'set_1', target: 'end_1' },
      ],
    } as FlowDsl
    const issue = validateDsl(empty).issues.find((i) => i.code === IssueCode.SETUP_NO_TARGET)
    expect(issue).toBeDefined()
    expect(issue?.nodeId).toBe('set_1')
    expect(issue?.level).toBe(IssueLevel.WARNING)
  })
})

describe('表单模板注册表', () => {
  it('「设置审批人」固定使用内置的「设置流程参与者」；审批意见是默认而非固定', () => {
    expect(fixedFormCodeOf(NodeType.SET_ASSIGNEE)).toBe(SETUP_ASSIGNEE_FORM_CODE)
    expect(formTemplatesFor(NodeType.SET_ASSIGNEE).map((t) => t.code)).toEqual([
      SETUP_ASSIGNEE_FORM_CODE,
    ])
    // 审批活动有内置的默认表单（审批意见），不再是"无模板、落通用兜底"
    expect(formTemplatesFor(NodeType.APPROVAL).map((t) => t.code)).toEqual([
      APPROVAL_OPINION_FORM_CODE,
    ])
    expect(defaultFormCodeOfNode({ type: NodeType.APPROVAL })).toBe(APPROVAL_OPINION_FORM_CODE)
    // 固定 vs 默认的分界：设置审批人不可替换，审批没有"固定表单"（企业可以换自己的）
    expect(fixedFormCodeOf(NodeType.APPROVAL)).toBeUndefined()
  })

  it('同一节点类型可以挂多张模板（含企业自定义），下拉里都能选到', () => {
    // 模拟「业务配置 → 流程表单」里新建的两张审批表单
    registerTaskFormTemplates([
      {
        code: 'TECH_REVIEW_FORM', label: '技术评审单', description: '',
        builtin: false, fixed: false, nodeTypes: [NodeType.APPROVAL],
      },
      {
        code: 'BIZ_REVIEW_FORM', label: '商务评审单', description: '',
        builtin: false, fixed: false, nodeTypes: [NodeType.APPROVAL, NodeType.TASK],
      },
    ])
    try {
      expect(formTemplatesFor(NodeType.APPROVAL).map((t) => t.code)).toEqual([
        APPROVAL_OPINION_FORM_CODE, 'TECH_REVIEW_FORM', 'BIZ_REVIEW_FORM',
      ])
      // 一张模板也能适用多种节点类型
      expect(formTemplatesFor(NodeType.TASK).map((t) => t.code)).toEqual(['BIZ_REVIEW_FORM'])
      // 同 code 以内置注册表为准：后端的"同名"盖不过内置的渲染契约
      registerTaskFormTemplates([
        {
          code: SETUP_ASSIGNEE_FORM_CODE, label: '冒名的设置表单', description: '',
          builtin: true, fixed: false, nodeTypes: [NodeType.SET_ASSIGNEE],
        },
      ])
      expect(allTaskFormTemplates().filter((t) => t.code === SETUP_ASSIGNEE_FORM_CODE)).toHaveLength(1)
      expect(findFormTemplate(SETUP_ASSIGNEE_FORM_CODE)?.label).toBe('设置流程参与者')
    } finally {
      // 注册表是模块级状态：用完清空，免得污染其它用例
      registerTaskFormTemplates([])
    }
  })

  it('编译产物带上 formKey（DSL 未写也按类型派生），运行期据此派发表单', () => {
    const dsl = flow()
    // 刻意清掉 formRef，验证编译层的兜底
    const withoutForm: FlowDsl = {
      ...dsl,
      nodes: dsl.nodes.map((n) => (n.id === 'set_1' ? ({ ...n, formRef: undefined } as never) : n)),
    }
    expect(compileToBpmnXml(withoutForm).xml).toContain(`formKey="${SETUP_ASSIGNEE_FORM_CODE}"`)
  })
})

describe('编译契约：人员绑定到按活动的运行期变量', () => {
  it('被覆盖的每个活动用自己的变量（避免互相覆盖）', () => {
    const xml = compileToBpmnXml(flow()).xml
    // 单人审批：直接取该活动的变量
    expect(xml).toContain('ckplmSetupAssignees_appr_1')
    // 会签是多实例：走 collection
    expect(xml).toContain('ckplmSetupAssignees_cs_1')
    expect(xml).toContain('multiInstanceLoopCharacteristics')
  })

  it('运行期指定人员时，不再输出设计期的静态候选组（否则两者并存）', () => {
    const xml = compileToBpmnXml(flow()).xml
    // cs_1 的策略是「角色 QA」，但它被设置活动覆盖 → 不应再写死 candidateGroups="QA"
    expect(xml).not.toContain('candidateGroups="QA"')
    // 未被覆盖的 pre_1 仍按设计期配置输出（它的角色 ENG）
    expect(xml).toContain('candidateGroups="ENG"')
  })

  it('类型与变量绑定无损往返（BPMN → DSL → 再编译）', () => {
    const xml = compileToBpmnXml(flow()).xml
    const imported = parseBpmnXml(xml) as unknown as { dsl?: FlowDsl }
    expect(imported.dsl, 'ImportResult 应包含 dsl').toBeDefined()

    const setter = imported.dsl!.nodes.find((n) => n.id === 'set_1')
    expect(setter?.type).toBe(NodeType.SET_ASSIGNEE)
    // 覆盖关系由类型 + 可达性推导，重新编译后变量绑定仍在（无需任何"声明"存活）
    const recompiled = compileToBpmnXml(imported.dsl!).xml
    expect(recompiled).toContain('ckplmSetupAssignees_appr_1')
    expect(recompiled).toContain('ckplmSetupAssignees_cs_1')
  })
})
