import { describe, expect, it } from 'vitest'
import {
  ApprovalMode,
  AssigneeStrategy,
  ConditionMode,
  ConditionOperator,
  EdgeKind,
  EdgeRoute,
  IssueCode,
  IssueLevel,
  NodeType,
  PassRuleMode,
  ValueType,
  addEdge,
  addNode,
  autoLayout,
  canConnect,
  createEmptyDsl,
  hasAssignee,
  illegalFunctions,
  parseFlowDsl,
  reachableNodeIds,
  rebindEdge,
  removeEdge,
  removeNode,
  setDefaultEdge,
  setEdgeAnchor,
  updateNode,
  validateDsl,
  type ApprovalNode,
  type FlowDsl,
  type FlowMeta,
  type TaskNode,
} from '../index'

const meta: FlowMeta = { key: 'demo_flow', name: '演示流程' }
const START_ID = 'start_1'
const END_ID = 'end_1'

/** 删掉 createEmptyDsl 自带的 start→end 直连，便于插入中间节点 */
function detachDefaultEdge(dsl: FlowDsl): FlowDsl {
  const direct = dsl.edges.find((e) => e.source === START_ID && e.target === END_ID)
  return direct ? removeEdge(dsl, direct.id) : dsl
}

function taskNode(id: string, name: string): TaskNode {
  return { id, type: NodeType.TASK, name, assignee: { strategy: AssigneeStrategy.INITIATOR } }
}

/** start → approval_1 → end 的最小审批流程 */
function withApproval(patch?: Partial<ApprovalNode>): FlowDsl {
  let dsl = detachDefaultEdge(createEmptyDsl(meta))
  dsl = addNode(
    dsl,
    {
      id: 'approval_1',
      type: NodeType.APPROVAL,
      name: '技术评审',
      assignee: { strategy: AssigneeStrategy.INITIATOR },
      approvalMode: ApprovalMode.SINGLE,
      ...patch,
    },
    { x: 200, y: 120 },
  )
  dsl = addEdge(dsl, START_ID, 'approval_1')
  dsl = addEdge(dsl, 'approval_1', END_ID)
  return dsl
}

/** start → gw_1 → {task_x, end}，task_x → end */
function withExclusiveGateway(): FlowDsl {
  let dsl = detachDefaultEdge(createEmptyDsl(meta))
  dsl = addNode(dsl, { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '评审结果' }, { x: 200, y: 120 })
  dsl = addNode(dsl, taskNode('task_x', '实施变更'), { x: 420, y: 200 })
  dsl = addEdge(dsl, START_ID, 'gw_1')
  dsl = addEdge(dsl, 'gw_1', END_ID)
  dsl = addEdge(dsl, 'gw_1', 'task_x')
  dsl = addEdge(dsl, 'task_x', END_ID)
  return dsl
}

describe('DSL 结构校验', () => {
  it('空白模板（开始→结束）合法', () => {
    const report = validateDsl(createEmptyDsl(meta))
    expect(report.errorCount).toBe(0)
    expect(report.ok).toBe(true)
  })

  it('缺少开始节点被拦截', () => {
    const report = validateDsl(removeNode(createEmptyDsl(meta), START_ID))
    expect(report.issues.map((i) => i.code)).toContain(IssueCode.NO_START)
    expect(report.ok).toBe(false)
  })

  it('孤立节点被拦截', () => {
    const dsl = addNode(createEmptyDsl(meta), taskNode('task_9', '游离任务'), { x: 0, y: 0 })
    const report = validateDsl(dsl)
    expect(report.byNode.task_9?.map((i) => i.code)).toContain(IssueCode.ISOLATED_NODE)
  })

  it('不可达节点被拦截', () => {
    let dsl = addNode(createEmptyDsl(meta), taskNode('task_a', 'A'), { x: 0, y: 0 })
    dsl = addNode(dsl, taskNode('task_b', 'B'), { x: 0, y: 0 })
    dsl = addEdge(dsl, 'task_a', 'task_b')
    const report = validateDsl(dsl)
    expect(report.byNode.task_a?.map((i) => i.code)).toContain(IssueCode.UNREACHABLE_NODE)
  })

  it('可达集合从 START 推导', () => {
    const reachable = reachableNodeIds(withApproval())
    expect(reachable.has('approval_1')).toBe(true)
    expect(reachable.has(END_ID)).toBe(true)
    expect(reachable.size).toBe(3)
  })
})

describe('连线合法性矩阵', () => {
  it('拒绝自环 / 重复连线 / 结束出边 / 开始入边', () => {
    const dsl = withApproval()
    expect(canConnect(dsl, 'approval_1', 'approval_1').ok).toBe(false)
    expect(canConnect(dsl, START_ID, 'approval_1').ok).toBe(false) // 重复连线
    expect(canConnect(dsl, END_ID, 'approval_1').ok).toBe(false) // 结束无出边
    expect(canConnect(dsl, 'approval_1', START_ID).ok).toBe(false) // 开始无入边
    expect(canConnect(dsl, START_ID, END_ID).ok).toBe(false) // 开始节点已有出边
  })

  it('移除直连后不可重建（开始节点出边上限 1）', () => {
    const bare = detachDefaultEdge(createEmptyDsl(meta))
    // 直连已删除 → 开始节点出度为 0，可以连
    expect(canConnect(bare, START_ID, END_ID).ok).toBe(true)
    // 连上之后就不能再连第二条
    const linked = addEdge(bare, START_ID, END_ID)
    expect(canConnect(linked, START_ID, 'approval_1').ok).toBe(false)
  })

  it('开始节点只能有一条出边', () => {
    const dsl = createEmptyDsl(meta)
    expect(canConnect(dsl, START_ID, 'approval_x').ok).toBe(false)
  })
})

describe('审批业务规则', () => {
  it('会签未配通过规则 → 拦截', () => {
    const report = validateDsl(withApproval({ approvalMode: ApprovalMode.COUNTERSIGN }))
    expect(report.byNode.approval_1?.map((i) => i.code)).toContain(
      IssueCode.COUNTERSIGN_NO_PASS_RULE,
    )
  })

  it('会签通过比例低于 50% → 拦截', () => {
    const report = validateDsl(
      withApproval({
        approvalMode: ApprovalMode.COUNTERSIGN,
        passRule: { mode: PassRuleMode.PERCENT, percent: 30 },
      }),
    )
    expect(report.byNode.approval_1?.map((i) => i.code)).toContain(
      IssueCode.PASS_PERCENT_TOO_LOW,
    )
  })

  it('会签配齐比例 → 通过', () => {
    const report = validateDsl(
      withApproval({
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG_MANAGER'] },
        approvalMode: ApprovalMode.COUNTERSIGN,
        passRule: { mode: PassRuleMode.PERCENT, percent: 80 },
      }),
    )
    expect(report.errorCount).toBe(0)
  })

  it('审批人策略所需取值缺失 → 拦截', () => {
    const report = validateDsl(withApproval({ assignee: { strategy: AssigneeStrategy.USER } }))
    expect(report.byNode.approval_1?.map((i) => i.code)).toContain(IssueCode.MISSING_ASSIGNEE)
    expect(hasAssignee({ strategy: AssigneeStrategy.INITIATOR })).toBe(true)
    expect(hasAssignee({ strategy: AssigneeStrategy.USER, userOids: [] })).toBe(false)
    expect(hasAssignee(undefined)).toBe(false)
  })

  it('驳回目标节点不存在 → 拦截', () => {
    const report = validateDsl(
      withApproval({ reject: { enabled: true, target: 'NODE', targetNodeId: 'ghost' } }),
    )
    expect(report.byNode.approval_1?.map((i) => i.code)).toContain(IssueCode.REJECT_NODE_NOT_FOUND)
  })

  it('删除节点会清理指向它的驳回引用', () => {
    let dsl = withApproval()
    dsl = addNode(
      dsl,
      {
        id: 'approval_2',
        type: NodeType.APPROVAL,
        name: '主管审批',
        assignee: { strategy: AssigneeStrategy.INITIATOR },
        approvalMode: ApprovalMode.SINGLE,
        reject: { enabled: true, target: 'NODE', targetNodeId: 'approval_1' },
      },
      { x: 0, y: 0 },
    )
    const cleaned = removeNode(dsl, 'approval_1')
    const node = cleaned.nodes.find((n) => n.id === 'approval_2') as ApprovalNode
    expect(node.reject?.targetNodeId).toBeUndefined()
    expect(node.reject?.target).toBe('INITIATOR')
  })
})

describe('网关规则', () => {
  it('排他网关缺默认分支 → 拦截', () => {
    const report = validateDsl(withExclusiveGateway())
    expect(report.byNode.gw_1?.map((i) => i.code)).toContain(IssueCode.GATEWAY_NO_DEFAULT)
    expect(report.ok).toBe(false)
  })

  it('setDefaultEdge 保证默认分支唯一', () => {
    let dsl = withExclusiveGateway()
    const gwEdges = dsl.edges.filter((e) => e.source === 'gw_1')
    expect(gwEdges).toHaveLength(2)
    dsl = setDefaultEdge(dsl, 'gw_1', gwEdges[0].id)
    dsl = setDefaultEdge(dsl, 'gw_1', gwEdges[1].id)
    const defaults = dsl.edges.filter((e) => e.source === 'gw_1' && e.kind === EdgeKind.DEFAULT)
    expect(defaults).toHaveLength(1)
    expect(defaults[0].id).toBe(gwEdges[1].id)
  })

  it('并行网关不允许挂条件', () => {
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(dsl, { id: 'gw_p', type: NodeType.PARALLEL_GATEWAY, name: '并行' }, { x: 0, y: 0 })
    dsl = addNode(dsl, taskNode('task_a', 'A'), { x: 0, y: 0 })
    dsl = addNode(dsl, taskNode('task_b', 'B'), { x: 0, y: 0 })
    dsl = addEdge(dsl, START_ID, 'gw_p')
    dsl = addEdge(dsl, 'gw_p', 'task_a')
    dsl = addEdge(dsl, 'gw_p', 'task_b', {
      kind: EdgeKind.CONDITION,
      condition: { mode: ConditionMode.EXPRESSION, expression: 'approved' },
    })
    dsl = addEdge(dsl, 'task_a', END_ID)
    dsl = addEdge(dsl, 'task_b', END_ID)
    const report = validateDsl(dsl)
    expect(report.byNode.gw_p?.map((i) => i.code)).toContain(IssueCode.PARALLEL_HAS_CONDITION)
  })

  it('并行分支的新出边默认是普通流转 —— 不许生下来就非法', () => {
    // 「网关出边默认条件流转」对并行分支是错的：并行分支的出边一律无条件
    // （PARALLEL_HAS_CONDITION），而面板在并行分支上只提供「普通流转」。
    // 两边不一致的后果：用户从并行分支拉一条线的瞬间就报错，且面板显示的
    // 类型（普通流转）与 DSL 里存的值（条件流转）不是一回事。
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(dsl, { id: 'gw_p', type: NodeType.PARALLEL_GATEWAY, name: '并行' }, { x: 0, y: 0 })
    dsl = addNode(dsl, taskNode('task_a', 'A'), { x: 0, y: 120 })
    dsl = addEdge(dsl, START_ID, 'gw_p')
    dsl = addEdge(dsl, 'gw_p', 'task_a')
    dsl = addEdge(dsl, 'task_a', END_ID)

    const created = dsl.edges.find((e) => e.source === 'gw_p' && e.target === 'task_a')!
    expect(created.kind).toBe(EdgeKind.NORMAL)
    expect(validateDsl(dsl).byNode.gw_p?.map((i) => i.code) ?? []).not.toContain(
      IssueCode.PARALLEL_HAS_CONDITION,
    )

    // 另一半不变：条件分支的出边仍默认条件流转（它们必须配条件或当默认分支，
    // 默认成普通流转会被 MISSING_CONDITION 拦下）
    let ex = detachDefaultEdge(createEmptyDsl(meta))
    ex = addNode(ex, { id: 'gw_x', type: NodeType.EXCLUSIVE_GATEWAY, name: '条件' }, { x: 0, y: 0 })
    ex = addNode(ex, taskNode('task_b', 'B'), { x: 0, y: 120 })
    ex = addEdge(ex, START_ID, 'gw_x')
    ex = addEdge(ex, 'gw_x', 'task_b')
    expect(ex.edges.find((e) => e.source === 'gw_x')!.kind).toBe(EdgeKind.CONDITION)
  })
})

describe('条件与表达式', () => {
  it('表达式函数白名单校验', () => {
    expect(illegalFunctions('len(material) > 3')).toEqual([])
    expect(illegalFunctions('Runtime.exec("whoami")')).toEqual(['exec'])
    expect(illegalFunctions('system("rm -rf /")')).toEqual(['system'])
  })

  it('非网关多出边且无默认分支 → 每条都必须有条件', () => {
    let dsl = withApproval()
    dsl = addNode(dsl, taskNode('task_x', '实施'), { x: 400, y: 200 })
    dsl = addEdge(dsl, 'approval_1', 'task_x', { kind: EdgeKind.CONDITION })
    dsl = addEdge(dsl, 'task_x', END_ID)
    const report = validateDsl(dsl)
    const conditional = dsl.edges.find((e) => e.source === 'approval_1' && e.target === 'task_x')!
    expect(report.byEdge[conditional.id]?.map((i) => i.code)).toContain(IssueCode.MISSING_CONDITION)
  })

  it('指定默认分支 + 补齐字段条件 → 通过', () => {
    let dsl = withApproval()
    const mainEdge = dsl.edges.find((e) => e.source === 'approval_1' && e.target === END_ID)!
    dsl = addNode(dsl, taskNode('task_x', '实施'), { x: 400, y: 200 })
    dsl = setDefaultEdge(dsl, 'approval_1', mainEdge.id)
    dsl = addEdge(dsl, 'approval_1', 'task_x', {
      kind: EdgeKind.CONDITION,
      condition: {
        mode: ConditionMode.FIELD,
        objectType: 'ECR',
        fieldKey: 'changeLevel',
        operator: ConditionOperator.EQ,
        value: 'A',
      },
    })
    dsl = addEdge(dsl, 'task_x', END_ID)
    const report = validateDsl(dsl)
    expect(report.errorCount).toBe(0)
  })

  it('字段条件缺少比较值 → 拦截', () => {
    let dsl = withApproval()
    const mainEdge = dsl.edges.find((e) => e.source === 'approval_1' && e.target === END_ID)!
    dsl = addNode(dsl, taskNode('task_x', '实施'), { x: 400, y: 200 })
    dsl = setDefaultEdge(dsl, 'approval_1', mainEdge.id)
    dsl = addEdge(dsl, 'approval_1', 'task_x', {
      kind: EdgeKind.CONDITION,
      condition: {
        mode: ConditionMode.FIELD,
        objectType: 'ECR',
        fieldKey: 'changeLevel',
        operator: ConditionOperator.EQ,
      },
    })
    dsl = addEdge(dsl, 'task_x', END_ID)
    const report = validateDsl(dsl)
    const conditional = dsl.edges.find((e) => e.source === 'approval_1' && e.target === 'task_x')!
    expect(report.byEdge[conditional.id]?.map((i) => i.code)).toContain(IssueCode.MISSING_CONDITION)
  })

  it('IS_EMPTY 类操作符无需比较值', () => {
    let dsl = withApproval()
    const mainEdge = dsl.edges.find((e) => e.source === 'approval_1' && e.target === END_ID)!
    dsl = addNode(dsl, taskNode('task_x', '实施'), { x: 400, y: 200 })
    dsl = setDefaultEdge(dsl, 'approval_1', mainEdge.id)
    dsl = addEdge(dsl, 'approval_1', 'task_x', {
      kind: EdgeKind.CONDITION,
      condition: {
        mode: ConditionMode.FIELD,
        objectType: 'ECR',
        fieldKey: 'reviewer',
        operator: ConditionOperator.IS_EMPTY,
      },
    })
    dsl = addEdge(dsl, 'task_x', END_ID)
    expect(validateDsl(dsl).errorCount).toBe(0)
  })
})

describe('服务节点与变量', () => {
  it('服务节点未选服务且无表达式 → 拦截', () => {
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(dsl, { id: 'svc_1', type: NodeType.SERVICE, name: '自动升版' }, { x: 0, y: 0 })
    dsl = addEdge(dsl, START_ID, 'svc_1')
    dsl = addEdge(dsl, 'svc_1', END_ID)
    const report = validateDsl(dsl)
    expect(report.byNode.svc_1?.map((i) => i.code)).toContain(IssueCode.SERVICE_NOT_SET)
  })

  it('引用未声明变量 → 告警（不阻断）', () => {
    const report = validateDsl(
      withApproval({ assignee: { strategy: AssigneeStrategy.VARIABLE, variableName: 'approver' } }),
    )
    expect(report.byNode.approval_1?.map((i) => i.code)).toContain(IssueCode.VARIABLE_NOT_DEFINED)
    expect(report.ok).toBe(true)
  })

  it('引用内置变量 → 不算"未声明"（发起人 / 审批结论 / 按活动拼名的设置审批人变量）', () => {
    for (const name of ['initiator', 'approved', 'ckplmSetupAssignees_task_p7anuf']) {
      const report = validateDsl(
        withApproval({ assignee: { strategy: AssigneeStrategy.VARIABLE, variableName: name } }),
      )
      expect(report.byNode.approval_1?.map((i) => i.code) ?? [], name).not.toContain(
        IssueCode.VARIABLE_NOT_DEFINED,
      )
    }
  })

  it('声明与内置变量同名的流程变量 → 告警（运行期会被平台值覆盖），但不阻断保存', () => {
    const dsl: FlowDsl = {
      ...withApproval(),
      variables: [{ name: 'approved', type: ValueType.BOOLEAN }],
    }
    const report = validateDsl(dsl)
    const issue = report.issues.find((i) => i.code === IssueCode.VARIABLE_NAME_BUILTIN)
    expect(issue?.level).toBe(IssueLevel.WARNING)
    expect(issue?.message).toContain('approved')
    expect(report.ok).toBe(true)
  })

  it('声明普通变量 → 不误报内置同名', () => {
    const dsl: FlowDsl = { ...withApproval(), variables: [{ name: 'ecrNo', type: ValueType.STRING }] }
    expect(validateDsl(dsl).issues.some((i) => i.code === IssueCode.VARIABLE_NAME_BUILTIN)).toBe(false)
  })
})

describe('DSL 序列化', () => {
  it('合法 JSON 可解析', () => {
    const result = parseFlowDsl(JSON.parse(JSON.stringify(createEmptyDsl(meta))))
    expect(result.ok).toBe(true)
  })

  it('结构不合法时给出可读错误', () => {
    const result = parseFlowDsl({ meta: { name: '缺 key' }, nodes: [], edges: [] })
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain('DSL 结构不合法')
    }
  })

  it('非法 key 被 schema 拦截', () => {
    expect(parseFlowDsl(createEmptyDsl({ key: '1-invalid key', name: 'x' })).ok).toBe(false)
  })

  it('存量 DSL 里残留的 primaryObjectType（字段已移除）被剥掉，老模板仍能解析', () => {
    const dsl = createEmptyDsl({ key: 'legacy_flow', name: '老模板' })
    // 模拟旧版本存下来的 DSL：meta 里还带着已移除的「主业务对象」
    const legacy = { ...dsl, meta: { ...dsl.meta, primaryObjectType: 'PART' } }
    const result = parseFlowDsl(legacy)
    expect(result.ok).toBe(true)
    if (result.ok) {
      // z.object 默认剥掉未声明的键 → 老模板可继续加载与保存，不会报"结构不合法"
      expect('primaryObjectType' in result.dsl.meta).toBe(false)
    }
  })

  it('坐标只存 layout，节点不含 x/y', () => {
    const dsl = withApproval()
    expect(JSON.stringify(dsl.nodes)).not.toContain('"x"')
    expect(dsl.layout.nodes.approval_1.x).toBe(200)
  })
})

describe('自动排版与节点更新', () => {
  it('按层级纵向排布且不改变语义', () => {
    const dsl = withApproval()
    const laid = autoLayout(dsl)
    expect(laid.layout.nodes.approval_1.y).toBeGreaterThan(laid.layout.nodes[START_ID].y)
    expect(laid.edges).toEqual(dsl.edges)
    expect(laid.nodes).toEqual(dsl.nodes)
  })

  it('同层内保持原有左右顺序：钉在左/右的锚点不会因为排版而"穿帮"', () => {
    // 两个分支节点在 DSL 数组里是 A（原本在右）在前、B（原本在左）在后。
    // 按数组顺序排版会把它们对调 —— 而用户的出/入边锚点常钉在左/右，一调换线就交叉重叠。
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    // 开始只允许一条出边，故用网关分叉（与真实流程同形）
    dsl = addNode(dsl, { id: 'gw_1', type: NodeType.PARALLEL_GATEWAY, name: '并行' }, { x: 400, y: 100 })
    dsl = addNode(dsl, taskNode('task_a', 'A'), { x: 600, y: 200 })
    dsl = addNode(dsl, taskNode('task_b', 'B'), { x: 100, y: 200 })
    dsl = addEdge(dsl, START_ID, 'gw_1')
    dsl = addEdge(dsl, 'gw_1', 'task_a')
    dsl = addEdge(dsl, 'gw_1', 'task_b')
    dsl = addEdge(dsl, 'task_a', END_ID)
    dsl = addEdge(dsl, 'task_b', END_ID)

    const laid = autoLayout(dsl)
    expect(laid.layout.nodes.task_b.x).toBeLessThan(laid.layout.nodes.task_a.x)
  })

  it('带回边（驳回/退回）的流程也能排版 —— 沿环无限抬升层级会让「自动排版」卡死', () => {
    // 回归：布局的层级松弛没剔除回边时，环上的节点每绕一圈层级 +1、反复入队，
    // 队列永远排不空 → 点一下「自动排版」整个画布卡死（主线程被占满）。
    // 驳回画成回边是常态（会签 → 设置对象状态 → 校对 → 回到会签），必须能排。
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(dsl, taskNode('task_a', '审批'), { x: 0, y: 0 })
    dsl = addNode(dsl, taskNode('task_b', '会签审批'), { x: 0, y: 120 })
    dsl = addNode(dsl, taskNode('task_c', '设置对象状态'), { x: 240, y: 120 })
    dsl = addEdge(dsl, START_ID, 'task_a')
    dsl = addEdge(dsl, 'task_a', 'task_b')
    dsl = addEdge(dsl, 'task_b', END_ID)
    // 驳回：会签审批 →（回边）审批
    dsl = addEdge(dsl, 'task_b', 'task_a')

    const laid = autoLayout(dsl)

    // 能跑到这里就说明收敛了（真死循环会被 vitest 超时判失败）；再钉住几条语义：
    for (const id of ['task_a', 'task_b', 'task_c']) {
      expect(Number.isFinite(laid.layout.nodes[id].x)).toBe(true)
      expect(Number.isFinite(laid.layout.nodes[id].y)).toBe(true)
    }
    // 分层仍按主流向：开始 < 审批 < 会签（回边不把上游"抬"到下面去）
    expect(laid.layout.nodes[START_ID].y).toBeLessThan(laid.layout.nodes.task_a.y)
    expect(laid.layout.nodes.task_a.y).toBeLessThan(laid.layout.nodes.task_b.y)
    // 回边仍在（只是不参与分层），语义零改动
    expect(laid.edges).toEqual(dsl.edges)
    expect(laid.nodes).toEqual(dsl.nodes)
  })

  it('updateNode 不可改 id 与 type', () => {
    const updated = updateNode<ApprovalNode>(
      withApproval(),
      'approval_1',
      { id: 'hacked', type: NodeType.TASK, name: '改名' } as unknown as Partial<ApprovalNode>,
    )
    const node = updated.nodes.find((n) => n.name === '改名')!
    expect(node.id).toBe('approval_1')
    expect(node.type).toBe(NodeType.APPROVAL)
  })
})

describe('并行分支：重复实例的提示（来自真实事故）', () => {
  /** start → 并行分支 → {task_a, task_b} → …后续由参数决定 */
  function parallelFixture(options: { merge: boolean; rejectOnA?: boolean }): FlowDsl {
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(dsl, { id: 'gw_fork', type: NodeType.PARALLEL_GATEWAY, name: '并行分支' }, { x: 200, y: 120 })
    dsl = addNode(dsl, taskNode('task_a', '分支A'), { x: 100, y: 240 })
    dsl = addNode(dsl, taskNode('task_b', '分支B'), { x: 340, y: 240 })
    dsl = addEdge(dsl, START_ID, 'gw_fork')
    dsl = addEdge(dsl, 'gw_fork', 'task_a')
    dsl = addEdge(dsl, 'gw_fork', 'task_b')
    if (options.merge) {
      // 正确画法：两条支路先汇到一个并行汇聚网关，再一起往下
      dsl = addNode(dsl, { id: 'gw_join', type: NodeType.PARALLEL_GATEWAY, name: '并行汇聚' }, { x: 200, y: 360 })
      dsl = addEdge(dsl, 'task_a', 'gw_join')
      dsl = addEdge(dsl, 'task_b', 'gw_join')
      dsl = addEdge(dsl, 'gw_join', END_ID)
    } else {
      // 事故画法：两条支路各自走到同一个下游活动，没有汇聚点
      dsl = addNode(dsl, taskNode('task_c', '共同下游'), { x: 200, y: 360 })
      dsl = addEdge(dsl, 'task_a', 'task_c')
      dsl = addEdge(dsl, 'task_b', 'task_c')
      dsl = addEdge(dsl, 'task_c', END_ID)
    }
    if (options.rejectOnA) {
      dsl = addEdge(dsl, 'task_a', 'task_b', { name: '驳回', route: EdgeRoute.REJECT })
    }
    return dsl
  }

  it('并行分支没有汇聚点 → 报错（重复创建活动 + 在办任务会消失，拦保存）', () => {
    const report = validateDsl(parallelFixture({ merge: false }))
    const issue = report.issues.find((i) => i.code === IssueCode.PARALLEL_BRANCH_NOT_MERGED)
    expect(issue).toBeTruthy()
    // 指向真正被两条支路同时走到的那个节点，而不是随便一个
    expect(issue?.nodeId).toBe('task_c')
    // 会重复创建活动、并让另一条支路在办的任务凭空消失 —— 必须拦保存，不是"提醒"
    expect(issue?.level).toBe(IssueLevel.ERROR)
    expect(report.ok).toBe(false)
  })

  it('并行分支有汇聚网关 → 不误报（画对了不能挨骂）', () => {
    const report = validateDsl(parallelFixture({ merge: true }))
    expect(report.issues.some((i) => i.code === IssueCode.PARALLEL_BRANCH_NOT_MERGED)).toBe(false)
  })

  it('并行分支上配了退回 → 提示会重复创建分支上的活动', () => {
    const report = validateDsl(parallelFixture({ merge: true, rejectOnA: true }))
    const issue = report.issues.find((i) => i.code === IssueCode.PARALLEL_BRANCH_HAS_REJECT)
    expect(issue).toBeTruthy()
    expect(issue?.nodeId).toBe('task_a')
    // 同一节点只报一次（分支集合会因"退回绕回来"而互相重叠）
    expect(report.issues.filter((i) => i.code === IssueCode.PARALLEL_BRANCH_HAS_REJECT)).toHaveLength(1)
  })
})

describe('网关角色：分叉 vs 汇聚', () => {
  it('汇聚网关（2 入 1 出）不算"分叉太少"，也不要求默认分支', () => {
    // 回归：这是最标准的并行画法（并行分支 → 审批/审核 → 汇聚网关 → 会签审批），
    // 曾经因为"只数出边"被判成「至少需要 2 条出边」—— 把画对的流程标红
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(dsl, { id: 'gw_fork', type: NodeType.PARALLEL_GATEWAY, name: '并行分支' }, { x: 200, y: 120 })
    dsl = addNode(dsl, taskNode('task_a', '审批'), { x: 100, y: 200 })
    dsl = addNode(dsl, taskNode('task_b', '审核'), { x: 300, y: 200 })
    dsl = addNode(dsl, { id: 'gw_join', type: NodeType.PARALLEL_GATEWAY, name: '汇聚' }, { x: 200, y: 280 })
    dsl = addNode(dsl, taskNode('task_c', '会签审批'), { x: 200, y: 360 })
    dsl = addEdge(dsl, START_ID, 'gw_fork')
    dsl = addEdge(dsl, 'gw_fork', 'task_a')
    dsl = addEdge(dsl, 'gw_fork', 'task_b')
    dsl = addEdge(dsl, 'task_a', 'gw_join')
    dsl = addEdge(dsl, 'task_b', 'gw_join')
    dsl = addEdge(dsl, 'gw_join', 'task_c')
    dsl = addEdge(dsl, 'task_c', END_ID)

    const codes = validateDsl(dsl).issues.map((i) => i.code)
    expect(codes).not.toContain(IssueCode.GATEWAY_TOO_FEW_BRANCHES)
    // 顺带钉住：正确汇聚的画法也不该被判成"分支没有汇聚点"
    expect(codes).not.toContain(IssueCode.PARALLEL_BRANCH_NOT_MERGED)
  })

  it('既不分叉也不汇聚（1 进 1 出）的网关仍然报错 —— 这种网关没有流转意义', () => {
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(dsl, { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '多余网关' }, { x: 200, y: 120 })
    dsl = addNode(dsl, taskNode('task_a', '审批'), { x: 200, y: 240 })
    dsl = addEdge(dsl, START_ID, 'gw_1')
    dsl = addEdge(dsl, 'gw_1', 'task_a')
    dsl = addEdge(dsl, 'task_a', END_ID)

    const codes = validateDsl(dsl).issues.map((i) => i.code)
    expect(codes).toContain(IssueCode.GATEWAY_TOO_FEW_BRANCHES)
  })
})

describe('连线重连与手动锚点', () => {
  /** 审批 → 结束 的一条已配好的连线（分支名 + 路由 + 手动锚点），用于验证"重连不掉配置" */
  function withConfiguredEdge(): FlowDsl {
    let dsl = detachDefaultEdge(createEmptyDsl(meta))
    dsl = addNode(
      dsl,
      {
        id: 'approval_1',
        type: NodeType.APPROVAL,
        name: '技术评审',
        assignee: { strategy: AssigneeStrategy.INITIATOR },
        approvalMode: ApprovalMode.SINGLE,
      },
      { x: 200, y: 120 },
    )
    dsl = addNode(dsl, taskNode('task_x', '实施变更'), { x: 200, y: 260 })
    dsl = addEdge(dsl, START_ID, 'approval_1')
    dsl = addEdge(dsl, 'approval_1', END_ID, {
      name: '同意',
      kind: EdgeKind.CONDITION,
      route: EdgeRoute.PASS,
    })
    return dsl
  }

  const edgeOf = (dsl: FlowDsl, id: string) => dsl.edges.find((e) => e.id === id)!

  it('重连只换两端，分支名/类型/条件/路由全部保留（"改接"而不是"重建"）', () => {
    const dsl = withConfiguredEdge()
    const target = dsl.edges.find((e) => e.source === 'approval_1')!
    const rebound = rebindEdge(dsl, target.id, 'approval_1', 'task_x')
    const edge = edgeOf(rebound, target.id)
    expect(edge.target).toBe('task_x')
    expect(edge.source).toBe('approval_1')
    // 这三样正是"删了重画"会丢掉的东西
    expect(edge.name).toBe('同意')
    expect(edge.kind).toBe(EdgeKind.CONDITION)
    expect(edge.route).toBe(EdgeRoute.PASS)
  })

  it('重连不合法时抛错且不改动原 DSL（画布据此提示并回滚视图）', () => {
    const dsl = withConfiguredEdge()
    const target = dsl.edges.find((e) => e.source === 'approval_1')!
    expect(() => rebindEdge(dsl, target.id, 'approval_1', 'approval_1')).toThrow()
    // 自己连自己（自环）被 canConnect 拦下，原 DSL 不变
    expect(edgeOf(dsl, target.id).target).toBe(END_ID)
  })

  it('换过的那一端锚点被清掉，另一端保留（旧节点的锚点 id 对新节点无意义）', () => {
    let dsl = withConfiguredEdge()
    const target = dsl.edges.find((e) => e.source === 'approval_1')!
    dsl = setEdgeAnchor(dsl, target.id, 'source', 'bottom-2')
    dsl = setEdgeAnchor(dsl, target.id, 'target', 'top-3')
    // 只改起点 → 起点的锚点必须清掉，终点的留着
    const reboundSource = rebindEdge(dsl, target.id, 'task_x', END_ID)
    const afterSourceChange = edgeOf(reboundSource, target.id)
    expect(afterSourceChange.anchor?.source).toBeUndefined()
    expect(afterSourceChange.anchor?.target).toBe('top-3')
    // 只改终点 → 终点的锚点清掉，起点的留着
    const reboundTarget = rebindEdge(dsl, target.id, 'approval_1', 'task_x')
    const afterTargetChange = edgeOf(reboundTarget, target.id)
    expect(afterTargetChange.anchor?.source).toBe('bottom-2')
    expect(afterTargetChange.anchor?.target).toBeUndefined()
  })

  it('锚点可设可清：两端都清空时整块 anchor 被删掉（不留空壳脏数据）', () => {
    let dsl = withConfiguredEdge()
    const target = dsl.edges.find((e) => e.source === 'approval_1')!
    dsl = setEdgeAnchor(dsl, target.id, 'source', 'bottom-2')
    expect(edgeOf(dsl, target.id).anchor).toEqual({ source: 'bottom-2' })
    dsl = setEdgeAnchor(dsl, target.id, 'source', undefined)
    expect(edgeOf(dsl, target.id).anchor).toBeUndefined()
  })

  it('anchor 能保存与往返（zod 未声明就会被剥掉 —— 这条防的是"存一次锚点就丢"）', () => {
    let dsl = withConfiguredEdge()
    const target = dsl.edges.find((e) => e.source === 'approval_1')!
    dsl = setEdgeAnchor(dsl, target.id, 'source', 'bottom-2')
    const parsed = parseFlowDsl(JSON.parse(JSON.stringify(dsl)))
    expect(parsed.ok).toBe(true)
    if (parsed.ok) {
      expect(edgeOf(parsed.dsl, target.id).anchor).toEqual({ source: 'bottom-2' })
    }
  })
})
