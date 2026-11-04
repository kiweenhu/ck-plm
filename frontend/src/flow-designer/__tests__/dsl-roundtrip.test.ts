/**
 * 设计器主链路回归：DSL → BPMN → DSL 的往返一致性（M3「e2e 回归」）。
 *
 * <h3>为什么是这一层，而不是浏览器 e2e</h3>
 * <p>设计器真正的破绽不在"按钮能不能点"，而在<b>三个独立模块的约定是否对得上</b>：
 * {@code compile}（写产物）、{@code parse}（读产物）、{@code validate}（拦保存）。
 * 这三处各自有单测，但单测可以"自己跟自己对"，一起改错方向也不会红 ——
 * 用一条真实链路把它们串起来做往返，才能守住"改一处、另一处没跟上"这类回归
 * （`DEPT_LEADER` 的声明、`terminateOn` 的兜底都是这么长出来的）。
 *
 * <h3>口径</h3>
 * <ul>
 *   <li>跑一遍 {@code compile → parse}，再用 {@code diffDsl} 对比"原始 DSL"与"读回来的 DSL"。
 *       {@code diffDsl} 本来就忽略 layout / 折点 / 锚点 —— 这些是画布表现，丢了不算回归。</li>
 *   <li>失败时输出<b>可读的差异清单</b>（而不是一句 true/false），否则回归报告等于没写。</li>
 * </ul>
 */

import { describe, expect, it } from 'vitest'
import { compileToBpmnXml, parseBpmnXml } from '../bpmn-compiler'
import {
  ApprovalMode,
  AssigneeStrategy,
  ConditionMode,
  ConditionOperator,
  DeadlineAnchor,
  DiffKind,
  EdgeKind,
  EdgeRoute,
  NodeType,
  OverdueAction,
  PassRuleMode,
  RejectTarget,
  SERVICE_SET_STATE,
  SETUP_ASSIGNEE_FORM_CODE,
  TimerMode,
  ValueType,
  diffDsl,
  formatDiffValue,
  type FlowDsl,
  type FlowMeta,
} from '../dsl-core'

const META: FlowMeta = {
  key: 'roundtrip_flow',
  name: '往返回归流程',
  description: '覆盖人工 / 自动化 / 分支三类节点',
}

/** 给一组节点 id 生成整齐的布局（DI 需要坐标，缺了会告警） */
function layoutOf(ids: string[]): FlowDsl['layout'] {
  const nodes: Record<string, { x: number; y: number }> = {}
  ids.forEach((id, index) => {
    nodes[id] = { x: 80 + (index % 4) * 180, y: 80 + Math.floor(index / 4) * 140 }
  })
  return { nodes }
}

function flowOf(nodes: FlowDsl['nodes'], edges: FlowDsl['edges']): FlowDsl {
  return {
    dslVersion: '1.0',
    meta: META,
    variables: [
      { name: 'amount', type: ValueType.NUMBER, label: '金额' },
      { name: 'approved', type: ValueType.BOOLEAN, defaultValue: false },
    ],
    nodes,
    edges,
    layout: layoutOf(nodes.map((node) => node.id)),
  }
}

/** 把 diff 摊成多行文本 —— 断言失败时直接看到"哪个字段丢了"，不用再点开对象 */
function describeDiff(before: FlowDsl, after: FlowDsl): string {
  const diff = diffDsl(before, after)
  const lines: string[] = []
  for (const item of diff.meta) {
    lines.push(`meta ${item.key}: ${formatDiffValue(item.before)} → ${formatDiffValue(item.after)}`)
  }
  for (const item of diff.variables) {
    lines.push(`变量 ${item.name} [${item.kind}]: ${item.changes.map((c) => c.path).join(', ')}`)
  }
  for (const item of diff.nodes) {
    lines.push(
      `节点 ${item.id}（${item.name}）[${item.kind}]: ` +
        item.changes.map((c) => `${c.path} ${formatDiffValue(c.before)} → ${formatDiffValue(c.after)}`).join('; '),
    )
  }
  for (const item of diff.edges) {
    lines.push(
      `连线 ${item.label} [${item.kind}]: ` +
        item.changes.map((c) => `${c.path} ${formatDiffValue(c.before)} → ${formatDiffValue(c.after)}`).join('; '),
    )
  }
  return lines.join('\n')
}

/**
 * 原稿 → 与"往返后"同一口径。
 *
 * <p>「设置审批人」活动没写 `formRef` 时，编译层会写内置表单、解析层会把它读回来
 * （`CKPLM_SETUP_ASSIGNEE`，运行期按它派发"设置流程参与者"界面）—— 这是设计上的
 * <b>补全</b>而不是丢失。比较前把原稿也补上，否则每次都多一条假差异。
 */
function normalizeForCompare(dsl: FlowDsl): FlowDsl {
  return {
    ...dsl,
    nodes: dsl.nodes.map((node) =>
      node.type === NodeType.SET_ASSIGNEE
        ? { ...node, formRef: node.formRef ?? SETUP_ASSIGNEE_FORM_CODE }
        : node,
    ),
  }
}

/** 往返一次并断言无损 */
function expectLossless(dsl: FlowDsl): void {
  const { xml } = compileToBpmnXml(dsl)
  const parsed = parseBpmnXml(xml, { key: dsl.meta.key })
  expect(parsed.unsupported, '产物里不应出现设计器不支持的元素').toEqual([])
  expect(describeDiff(normalizeForCompare(dsl), parsed.dsl), '往返后 DSL 应与原稿一致（layout 除外）').toBe('')
  expect(diffDsl(normalizeForCompare(dsl), parsed.dsl).empty).toBe(true)
}

describe('DSL 往返：人工类节点', () => {
  const dsl = flowOf(
    [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'setup_1', type: NodeType.SET_ASSIGNEE, name: '设置审批人', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      {
        id: 'approval_1',
        type: NodeType.APPROVAL,
        name: '技术评审',
        assignee: { strategy: AssigneeStrategy.DEPT_LEADER, deptFieldKey: 'dept' },
        approvalMode: ApprovalMode.SINGLE,
        commentRequired: true,
        formRef: 'FORM_REVIEW',
        deadline: {
          anchor: DeadlineAnchor.NODE_START,
          durationHours: 24,
          action: OverdueAction.REASSIGN,
          reassignTo: { strategy: AssigneeStrategy.INITIATOR_LEADER },
        },
        reject: { enabled: true, target: RejectTarget.INITIATOR, commentRequired: true },
      },
      {
        id: 'cs_1',
        type: NodeType.COUNTERSIGN_APPROVAL,
        name: '集体会签',
        assignee: { strategy: AssigneeStrategy.DEPT_LEADER, deptFieldKey: 'dept' },
        passRule: { mode: PassRuleMode.PERCENT, percent: 60, abstain: 'COUNT_AS_REJECT' },
        deadline: { anchor: DeadlineAnchor.PROCESS_START, durationHours: 48, action: OverdueAction.SKIP },
      },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    [
      { id: 'flow_1', source: 'start_1', target: 'setup_1', kind: EdgeKind.NORMAL },
      { id: 'flow_2', source: 'setup_1', target: 'approval_1', kind: EdgeKind.NORMAL },
      { id: 'flow_3', source: 'approval_1', target: 'cs_1', kind: EdgeKind.NORMAL },
      { id: 'flow_4', source: 'cs_1', target: 'end_1', kind: EdgeKind.NORMAL },
    ],
  )

  it('编译 → 解析后不丢任何人工配置', () => {
    expectLossless(dsl)
  })
})

describe('DSL 往返：自动化类节点', () => {
  const dsl = flowOf(
    [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      {
        id: 'svc_1',
        type: NodeType.SERVICE,
        name: '设置对象状态',
        serviceRef: SERVICE_SET_STATE,
        params: { state: 'RELEASED', note: '${reason}' },
      },
      {
        id: 'notify_1',
        type: NodeType.NOTIFY,
        name: '通知设计者',
        recipients: { strategy: AssigneeStrategy.INITIATOR },
        templateCode: 'TPL_DONE',
        attachPrimaryObject: true,
      },
      { id: 'timer_1', type: NodeType.TIMER, name: '等待两小时', mode: TimerMode.DURATION, duration: 'PT2H' },
      {
        id: 'sub_1',
        type: NodeType.SUB_PROCESS,
        name: '调用子流程',
        processKey: 'eco_review',
        variableMap: { parentAmount: '${amount}' },
        // 1:M 子流程：往返后集合 / 元素变量 / 业务 key 都不能丢
        multiInstance: { collection: 'changeTaskList', elementVariable: 'task', businessKey: 'oid' },
      },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    [
      { id: 'flow_1', source: 'start_1', target: 'svc_1', kind: EdgeKind.NORMAL },
      { id: 'flow_2', source: 'svc_1', target: 'notify_1', kind: EdgeKind.NORMAL },
      { id: 'flow_3', source: 'notify_1', target: 'timer_1', kind: EdgeKind.NORMAL },
      { id: 'flow_4', source: 'timer_1', target: 'sub_1', kind: EdgeKind.NORMAL },
      { id: 'flow_5', source: 'sub_1', target: 'end_1', kind: EdgeKind.NORMAL },
    ],
  )

  it('编译 → 解析后不丢服务参数 / 通知 / 定时 / 子流程映射', () => {
    expectLossless(dsl)
  })
})

describe('DSL 往返：分支与三种连线', () => {
  const dsl = flowOf(
    [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'gw_ex', type: NodeType.EXCLUSIVE_GATEWAY, name: '按金额分流' },
      { id: 'task_a', type: NodeType.TASK, name: '大额审批', assignee: { strategy: AssigneeStrategy.DEPT_LEADER, deptFieldKey: 'dept' } },
      { id: 'task_b', type: NodeType.TASK, name: '常规审批', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'gw_par', type: NodeType.PARALLEL_GATEWAY, name: '并行' },
      { id: 'task_c', type: NodeType.TASK, name: '并行A', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'task_d', type: NodeType.TASK, name: '并行B', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'gw_in', type: NodeType.INCLUSIVE_GATEWAY, name: '包容' },
      { id: 'task_e', type: NodeType.TASK, name: '包容A', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    [
      { id: 'flow_1', source: 'start_1', target: 'gw_ex', kind: EdgeKind.NORMAL },
      {
        id: 'flow_2',
        source: 'gw_ex',
        target: 'task_a',
        kind: EdgeKind.CONDITION,
        condition: {
          mode: ConditionMode.FIELD,
          objectType: 'Part',
          fieldKey: 'amount',
          operator: ConditionOperator.GT,
          value: 10000,
          valueType: ValueType.NUMBER,
        },
      },
      { id: 'flow_3', source: 'gw_ex', target: 'task_b', kind: EdgeKind.DEFAULT },
      { id: 'flow_4', source: 'task_a', target: 'gw_par', kind: EdgeKind.NORMAL },
      { id: 'flow_5', source: 'task_b', target: 'gw_par', kind: EdgeKind.NORMAL },
      { id: 'flow_6', source: 'gw_par', target: 'task_c', kind: EdgeKind.NORMAL },
      { id: 'flow_7', source: 'gw_par', target: 'task_d', kind: EdgeKind.NORMAL },
      { id: 'flow_8', source: 'task_c', target: 'gw_in', kind: EdgeKind.NORMAL },
      { id: 'flow_9', source: 'task_d', target: 'gw_in', kind: EdgeKind.NORMAL },
      { id: 'flow_10', source: 'gw_in', target: 'task_e', kind: EdgeKind.NORMAL },
      { id: 'flow_11', source: 'task_e', target: 'end_1', kind: EdgeKind.NORMAL },
    ],
  )

  it('编译 → 解析后不丢条件 / 默认分支 / 网关类型', () => {
    expectLossless(dsl)
  })

  it('往返后节点类型与连线种类逐条核对（不依赖 diffDsl 的实现）', () => {
    const { xml } = compileToBpmnXml(dsl)
    const parsed = parseBpmnXml(xml, { key: dsl.meta.key })

    const typeOf = new Map(parsed.dsl.nodes.map((node) => [node.id, node.type]))
    expect(typeOf.get('gw_ex')).toBe(NodeType.EXCLUSIVE_GATEWAY)
    expect(typeOf.get('gw_par')).toBe(NodeType.PARALLEL_GATEWAY)
    expect(typeOf.get('gw_in')).toBe(NodeType.INCLUSIVE_GATEWAY)

    const kindOf = new Map(parsed.dsl.edges.map((edge) => [edge.id, edge.kind]))
    expect(kindOf.get('flow_2')).toBe(EdgeKind.CONDITION)
    expect(kindOf.get('flow_3')).toBe(EdgeKind.DEFAULT)
    expect(kindOf.get('flow_1')).toBe(EdgeKind.NORMAL)
  })
})

describe('DSL 往返：通过 / 驳回路由边', () => {
  const dsl = flowOf(
    [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      {
        id: 'approval_1',
        type: NodeType.APPROVAL,
        name: '评审',
        assignee: { strategy: AssigneeStrategy.INITIATOR },
        approvalMode: ApprovalMode.SINGLE,
      },
      { id: 'task_1', type: NodeType.TASK, name: '整改', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    [
      { id: 'flow_1', source: 'start_1', target: 'approval_1', kind: EdgeKind.NORMAL },
      { id: 'flow_pass', source: 'approval_1', target: 'end_1', kind: EdgeKind.NORMAL, route: EdgeRoute.PASS },
      { id: 'flow_reject', source: 'approval_1', target: 'task_1', kind: EdgeKind.NORMAL, route: EdgeRoute.REJECT },
      { id: 'flow_3', source: 'task_1', target: 'end_1', kind: EdgeKind.NORMAL },
    ],
  )

  it('路由边往返后仍是「普通流转 + 路由」，不会退化成条件流转', () => {
    expectLossless(dsl)

    const { xml } = compileToBpmnXml(dsl)
    const parsed = parseBpmnXml(xml, { key: dsl.meta.key })
    const passEdge = parsed.dsl.edges.find((edge) => edge.id === 'flow_pass')
    const rejectEdge = parsed.dsl.edges.find((edge) => edge.id === 'flow_reject')

    expect(passEdge?.route).toBe(EdgeRoute.PASS)
    expect(rejectEdge?.route).toBe(EdgeRoute.REJECT)
    // 条件由编译层按路由生成，不属于用户配置 —— 读回时不该出现在 DSL 里
    expect(passEdge?.kind).toBe(EdgeKind.NORMAL)
    expect(passEdge?.condition).toBeUndefined()
  })
})

describe('往返不会凭空产生差异', () => {
  it('两次编译同一份 DSL：产物逐字节一致（编译是确定性的）', () => {
    const dsl = flowOf(
      [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      [{ id: 'flow_1', source: 'start_1', target: 'end_1', kind: EdgeKind.NORMAL }],
    )

    expect(compileToBpmnXml(dsl).xml).toBe(compileToBpmnXml(dsl).xml)
  })

  it('往返后再次编译：产物与原产物一致（幂等）', () => {
    const dsl = flowOf(
      [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'approval_1',
          type: NodeType.APPROVAL,
          name: '评审',
          assignee: { strategy: AssigneeStrategy.DEPT_LEADER, deptFieldKey: 'dept' },
          approvalMode: ApprovalMode.SINGLE,
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      [
        { id: 'flow_1', source: 'start_1', target: 'approval_1', kind: EdgeKind.NORMAL },
        { id: 'flow_2', source: 'approval_1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
    )

    const first = compileToBpmnXml(dsl).xml
    const parsed = parseBpmnXml(first, { key: dsl.meta.key })
    const second = compileToBpmnXml(parsed.dsl).xml

    expect(second).toBe(first)
  })
})

describe('diffDsl 在回归里的作用', () => {
  it('缺失字段会被报成修改（不是"看起来一样"）', () => {
    const dsl = flowOf(
      [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'approval_1',
          type: NodeType.APPROVAL,
          name: '评审',
          assignee: { strategy: AssigneeStrategy.DEPT_LEADER, deptFieldKey: 'dept' },
          approvalMode: ApprovalMode.SINGLE,
          formRef: 'FORM_X',
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      [
        { id: 'flow_1', source: 'start_1', target: 'approval_1', kind: EdgeKind.NORMAL },
        { id: 'flow_2', source: 'approval_1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
    )

    // 模拟"解析把 formRef 丢了"
    const broken: FlowDsl = {
      ...dsl,
      nodes: dsl.nodes.map((node) => (node.id === 'approval_1' ? { ...node, formRef: undefined } : node)),
    }
    const diff = diffDsl(dsl, broken)

    expect(diff.empty).toBe(false)
    expect(diff.nodes[0].kind).toBe(DiffKind.CHANGED)
    expect(diff.nodes[0].changes.map((change) => change.path)).toContain('formRef')
  })
})
