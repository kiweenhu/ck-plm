/**
 * 出边上的「路由」（通过 / 驳回）—— 让"驳回后还要对业务对象做事"能画出来。
 *
 * <p>背景：节点上的「驳回目标」只能"一步跳回某处"，表达不了
 * "驳回 → 设置状态（PES 集合）→ 撤回电子签名 → 再回到某处"。
 * 所以驳回也要是一条<b>正常的出边</b>，后面接什么节点由画布决定。
 *
 * <p>本文件钉住：
 * <ol>
 *   <li>编译：路由生成互斥条件（通过 {@code ${approved}}、驳回 {@code ${!approved}}）并写进属性；</li>
 *   <li>往返：编译再解析，路由不丢（导入的手写 BPMN 也能按条件反推出来）；</li>
 *   <li>校验：部分声明、重复路由、没有通过路由、与「驳回目标」冲突、未开允许驳回 —— 都要拦住。</li>
 * </ol>
 */

import { describe, expect, it } from 'vitest'
import { compileToBpmnXml, parseBpmnXml } from '@flow-compiler'
import {
  AssigneeStrategy,
  EdgeKind,
  EdgeRoute,
  IssueCode,
  IssueLevel,
  NodeType,
  RejectTarget,
  createEmptyDsl,
  parseFlowDsl,
  setupControlledIds,
  validateDsl,
  type FlowDsl,
  type FlowEdge,
  type FlowNode,
} from '../index'

/**
 * 一条审批流程：开始 → 审批 → 结束。
 *
 * <p>默认两条出边（通过 + 驳回），可分别覆盖：路由、节点是否允许驳回、节点是否配驳回目标。
 */
function flow(options: {
  routes?: (EdgeRoute | undefined)[]
  rejectEnabled?: boolean
  rejectTarget?: boolean
  /** 驳回边连到哪（默认直连结束：驳回即终止） */
  rejectTarget2?: 'end' | 'service'
} = {}): FlowDsl {
  const base = createEmptyDsl({ key: 'edge_route', name: '出边路由测试' })
  const routes = options.routes ?? [EdgeRoute.PASS, EdgeRoute.REJECT]
  const hasRejectRoute = routes.includes(EdgeRoute.REJECT)
  // 「允许驳回」开关与「退回到哪」是两件事：
  // 画了驳回边就不再配 target（二选一），没画就必须配 target
  const reject = options.rejectEnabled === false
    ? undefined
    : { enabled: hasRejectRoute, ...(options.rejectTarget ? { target: RejectTarget.PREVIOUS } : {}) }
  const nodes: FlowDsl['nodes'] = [
    { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
    {
      id: 'appr_1',
      type: NodeType.APPROVAL,
      name: '审批',
      assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      approvalMode: 'SINGLE',
      ...(reject ? { reject } : {}),
    },
    { id: 'end_1', type: NodeType.END, name: '结束' },
  ]
  if (options.rejectTarget2 === 'service') {
    nodes.push({
      id: 'svc_1',
      type: NodeType.SERVICE,
      name: '设置PES状态',
      serviceRef: 'object.setLifecycleState',
    })
  }
  const edges: FlowEdge[] = [
    { id: 'e_in', kind: EdgeKind.NORMAL, source: 'start_1', target: 'appr_1' },
    { id: 'e_pass', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'end_1', route: routes[0] },
    {
      id: 'e_reject',
      kind: EdgeKind.NORMAL,
      source: 'appr_1',
      target: options.rejectTarget2 === 'service' ? 'svc_1' : 'end_1',
      route: routes[1],
    },
  ]
  if (options.rejectTarget2 === 'service') {
    edges.push({ id: 'e_svc_end', kind: EdgeKind.NORMAL, source: 'svc_1', target: 'end_1' })
  }
  return { ...base, nodes, edges }
}

function codesOf(dsl: FlowDsl): string[] {
  return validateDsl(dsl).issues.map((issue) => issue.code)
}

function issueOf(dsl: FlowDsl, code: string) {
  return validateDsl(dsl).issues.find((issue) => issue.code === code)
}

describe('出边路由 · 编译', () => {
  it('通过 / 驳回各生成互斥条件，并把路由写进属性', () => {
    const { xml } = compileToBpmnXml(flow())
    expect(xml).toContain('ckplm:route="PASS"')
    expect(xml).toContain('ckplm:route="REJECT"')
    expect(xml).toContain('${approved}')
    expect(xml).toContain('${!approved}')
  })

  it('驳回那条边可以接到自动化节点（设置状态 / 撤回签名这类动作就有地方挂了）', () => {
    const { xml } = compileToBpmnXml(flow({ rejectTarget2: 'service' }))
    // 驳回边指向服务节点，再由它接到结束
    expect(xml).toContain('id="e_reject" sourceRef="appr_1" targetRef="svc_1"')
    expect(xml).toContain('id="e_svc_end" sourceRef="svc_1" targetRef="end_1"')
    expect(xml).toContain('${!approved}')
  })

  it('用户手填的条件被路由覆盖（不让他写 UEL 也能表达结论）', () => {
    const dsl = flow()
    const withCondition: FlowDsl = {
      ...dsl,
      edges: dsl.edges.map((edge) => (edge.id === 'e_reject'
        ? { ...edge, kind: EdgeKind.CONDITION, condition: { mode: 'EXPRESSION' as never, expression: 'x=1' } }
        : edge)),
    }
    const result = compileToBpmnXml(withCondition)
    expect(result.xml).toContain('${!approved}')
    expect(result.warnings.join()).toContain('手写条件被忽略')
  })
})

describe('出边路由 · 加载归一', () => {
  it('老模板残留的「驳回目标」在加载时被清掉（统一为画线，不留看不见却生效的配置）', () => {
    // 驳回已统一为"画线"（属性面板不再有「允许驳回 / 驳回目标」），所以加载时把老数据里的
    // reject 一并清掉 —— 否则它会继续被编译进 BPMN、运行期继续一键跳回，而界面上无处可改
    const dsl = flow({ rejectTarget: true })
    expect(dsl.nodes.find((item) => item.id === 'appr_1')).toHaveProperty('reject')
    const result = parseFlowDsl(JSON.parse(JSON.stringify(dsl)))
    expect(result.ok).toBe(true)
    if (result.ok) {
      const node = result.dsl.nodes.find((item: FlowNode) => item.id === 'appr_1') as
        | { reject?: unknown }
        | undefined
      expect(node?.reject).toBeUndefined()
    }
  })
})

describe('加载归一 · 节点字段', () => {
  it('审批 / 办理节点的「绑定业务对象」在加载时被清掉，开始节点保留', () => {
    const base = createEmptyDsl({ key: 'binding_strip', name: '绑定清理' })
    const dsl = {
      ...base,
      nodes: [
        {
          id: 'start_1',
          type: NodeType.START,
          name: '开始',
          initiator: { strategy: AssigneeStrategy.INITIATOR },
          binding: { objectType: 'PART' },
        },
        {
          id: 'appr_1',
          type: NodeType.APPROVAL,
          name: '审批',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: 'SINGLE',
          binding: { objectType: 'PART' },
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'appr_1' },
        { id: 'e2', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'end_1' },
      ],
    } as FlowDsl

    const result = parseFlowDsl(JSON.parse(JSON.stringify(dsl)))
    expect(result.ok).toBe(true)
    if (result.ok) {
      const nodes = result.dsl.nodes as unknown as { id: string; binding?: unknown }[]
      expect(nodes.find((node) => node.id === 'start_1')?.binding).toEqual({ objectType: 'PART' })
      expect(nodes.find((node) => node.id === 'appr_1')?.binding).toBeUndefined()
    }
  })
})

describe('出边路由 · 驳回回环不算"顺序"', () => {
  /** 开始 → 申请填报(TASK) → 设置审批人 → 初审(审批) -驳回→ 申请填报（回环） */
  function loopedFlow(): FlowDsl {
    const base = createEmptyDsl({ key: 'reject_loop', name: '驳回回环' })
    return {
      ...base,
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'task_apply',
          type: NodeType.TASK,
          name: '申请填报',
          assignee: { strategy: AssigneeStrategy.INITIATOR },
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
          name: '初审',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: 'SINGLE',
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'task_apply' },
        { id: 'e2', kind: EdgeKind.NORMAL, source: 'task_apply', target: 'set_1' },
        { id: 'e3', kind: EdgeKind.NORMAL, source: 'set_1', target: 'appr_1' },
        { id: 'e4', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'end_1', route: EdgeRoute.PASS },
        // 驳回回到上游的填报节点：形成回环 —— 但它是"回退"，不代表执行顺序
        { id: 'e5', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'task_apply', route: EdgeRoute.REJECT },
      ],
    } as FlowDsl
  }

  it('下游审批不会被误报成「设置审批人之前的活动」（否则用户画完驳回边存不了盘）', () => {
    expect(codesOf(loopedFlow())).not.toContain(IssueCode.SETUP_NOT_FIRST)
  })

  it('上游填报节点不会被算成「设置审批人的下游」（否则它的 ${initiator} 会变成候选人）', () => {
    const controlled = setupControlledIds(loopedFlow())
    expect(controlled.has('task_apply')).toBe(false)
    expect(controlled.has('appr_1')).toBe(true)
    const { xml } = compileToBpmnXml(loopedFlow())
    expect(xml).toMatch(/id="task_apply"[^>]*flowable:assignee="\$\{initiator\}"/)
    expect(xml).not.toContain('candidateUsers="${ckplmSetupAssignees_task_apply}"')
  })
})

describe('出边路由 · 往返', () => {
  it('编译后再解析，路由不丢', () => {
    const { xml } = compileToBpmnXml(flow())
    const imported = parseBpmnXml(xml) as unknown as { dsl?: FlowDsl }
    const edges = imported.dsl?.edges ?? []
    expect(edges.find((edge) => edge.id === 'e_pass')?.route).toBe(EdgeRoute.PASS)
    expect(edges.find((edge) => edge.id === 'e_reject')?.route).toBe(EdgeRoute.REJECT)
  })

  it('手写 BPMN 用 ${approved} / ${!approved} 表达结论时，导入后也认成路由', () => {
    // 别人的 BPMN：有结论条件，但没有我们自产的 ckplm:route 属性
    const { xml } = compileToBpmnXml(flow())
    const handWritten = xml.replace(/ ckplm:route="(PASS|REJECT)"/g, '')
    const imported = parseBpmnXml(handWritten) as unknown as { dsl?: FlowDsl }
    const edges = imported.dsl?.edges ?? []
    expect(edges.find((edge) => edge.id === 'e_pass')?.route).toBe(EdgeRoute.PASS)
    expect(edges.find((edge) => edge.id === 'e_reject')?.route).toBe(EdgeRoute.REJECT)
  })
})

describe('出边路由 · 校验', () => {
  it('通过 + 驳回各一条 → 路由相关的问题一个都没有', () => {
    // 只断言本文件管的规则：这条夹具没有「设置审批人」等别的配置，
    // 断言"整份流程零错误"会把无关规则的问题也算进来，反而盖住这里的回归
    const codes = codesOf(flow())
    expect(codes.filter((code) => code.startsWith('EDGE_ROUTE_') || code === 'REJECT_NO_TARGET')).toEqual([])
  })

  it('只有一部分出边声明了路由 → 错误（未声明那条会被无条件走掉）', () => {
    const issue = issueOf(flow({ routes: [EdgeRoute.PASS, undefined] }), IssueCode.EDGE_ROUTE_PARTIAL)
    expect(issue?.level).toBe(IssueLevel.ERROR)
    expect(issue?.message).toContain('未声明')
  })

  it('同一种路由声明了两次 → 错误', () => {
    expect(codesOf(flow({ routes: [EdgeRoute.PASS, EdgeRoute.PASS] })))
      .toContain(IssueCode.EDGE_ROUTE_DUPLICATED)
  })

  it('声明了路由却没有「通过」路由 → 错误（同意后无路可走）', () => {
    expect(codesOf(flow({ routes: [undefined, EdgeRoute.REJECT] })))
      .toContain(IssueCode.EDGE_ROUTE_NO_PASS)
  })

  it('审批节点多条出边却都没声明路由 → 提示（会被当成并行，两条都走）', () => {
    const issue = issueOf(flow({ routes: [undefined, undefined] }), IssueCode.EDGE_ROUTE_UNSPECIFIED)
    expect(issue?.level).toBe(IssueLevel.WARNING)
  })

  it('有多条出边但每条都声明了路由 → 不再报"缺少条件"（条件由编译层生成）', () => {
    // 这条规则是"非网关多出边必须配条件"的老规则；路由就是条件，不认它会挡住驳回边
    expect(codesOf(flow())).not.toContain(IssueCode.MISSING_CONDITION)
  })

  it('非审批/会签节点声明路由 → 错误（它没有"结论"可分）', () => {
    const dsl = flow()
    const withService: FlowDsl = {
      ...dsl,
      nodes: dsl.nodes.map((node) => (node.id === 'appr_1'
        ? { id: 'appr_1', type: NodeType.SERVICE, name: '服务', serviceRef: 'object.promote' }
        : node)),
    }
    expect(codesOf(withService)).toContain(IssueCode.EDGE_ROUTE_NOT_APPLICABLE)
  })
})
