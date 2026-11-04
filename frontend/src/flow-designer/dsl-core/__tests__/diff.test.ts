import { describe, expect, it } from 'vitest'
import {
  ApprovalMode,
  AssigneeStrategy,
  DiffKind,
  NodeType,
  ValueType,
  addEdge,
  addNode,
  createEmptyDsl,
  diffDsl,
  formatDiffValue,
  removeEdge,
  removeNode,
  updateNode,
  type ApprovalNode,
  type FlowDsl,
  type FlowMeta,
} from '../index'

const meta: FlowMeta = { key: 'demo_flow', name: '演示流程' }
const START_ID = 'start_1'
const END_ID = 'end_1'

/** start → approval_1 → end（与 dsl-core.test 同一个最小流程） */
function withApproval(): FlowDsl {
  let dsl = createEmptyDsl(meta)
  const direct = dsl.edges.find((e) => e.source === START_ID && e.target === END_ID)
  if (direct) {
    dsl = removeEdge(dsl, direct.id)
  }
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
  dsl = addEdge(dsl, START_ID, 'approval_1')
  dsl = addEdge(dsl, 'approval_1', END_ID)
  return dsl
}

describe('diffDsl · 结构对比', () => {
  it('同一份 DSL：没有差异', () => {
    const dsl = withApproval()
    const diff = diffDsl(dsl, dsl)

    expect(diff.empty).toBe(true)
    expect(diff.nodes).toEqual([])
    expect(diff.edges).toEqual([])
    expect(diff.meta).toEqual([])
    expect(diff.summary).toEqual({ added: 0, removed: 0, changed: 0 })
  })

  it('只挪了坐标、只改了折点与锚点：不算差异（表现不进语义对比）', () => {
    const dsl = withApproval()
    const moved: FlowDsl = {
      ...dsl,
      layout: {
        ...dsl.layout,
        nodes: { ...dsl.layout.nodes, approval_1: { ...dsl.layout.nodes.approval_1, x: 999, y: 888 } },
      },
      edges: dsl.edges.map((edge) => ({
        ...edge,
        anchor: { source: 'bottom', target: 'top' },
        waypoints: [{ x: 1, y: 2 }],
      })),
    }

    expect(diffDsl(dsl, moved).empty).toBe(true)
  })

  it('新增节点：报新增，并计入汇总', () => {
    const before = withApproval()
    const after = addNode(
      before,
      { id: 'task_1', type: NodeType.TASK, name: '补充资料', assignee: { strategy: AssigneeStrategy.INITIATOR } },
      { x: 400, y: 120 },
    )
    const diff = diffDsl(before, after)

    expect(diff.empty).toBe(false)
    expect(diff.nodes).toHaveLength(1)
    expect(diff.nodes[0]).toMatchObject({ id: 'task_1', name: '补充资料', kind: DiffKind.ADDED })
    expect(diff.summary.added).toBe(1)
  })

  it('删除节点：报删除（连线一并消失不算额外差异）', () => {
    const before = withApproval()
    const after = removeNode(before, 'approval_1')
    const diff = diffDsl(before, after)

    expect(diff.nodes).toHaveLength(1)
    expect(diff.nodes[0]).toMatchObject({ id: 'approval_1', kind: DiffKind.REMOVED })
    expect(diff.summary.removed).toBe(3) // 节点 + 两条连线
  })

  it('改名：报「修改」，而不是删一个再建一个', () => {
    const before = withApproval()
    const after = updateNode<ApprovalNode>(before, 'approval_1', { name: '技术评审（试运行）' })
    const diff = diffDsl(before, after)

    expect(diff.nodes).toHaveLength(1)
    expect(diff.nodes[0].kind).toBe(DiffKind.CHANGED)
    expect(diff.nodes[0].name).toBe('技术评审（试运行）')
    expect(diff.nodes[0].changes).toEqual([
      { path: 'name', label: '名称', before: '技术评审', after: '技术评审（试运行）' },
    ])
  })

  it('审批人策略变化：展开到字段级，带中文标签', () => {
    const before = withApproval()
    const after = updateNode<ApprovalNode>(before, 'approval_1', {
      assignee: { strategy: AssigneeStrategy.DEPT_LEADER, deptFieldKey: 'dept' },
    })
    const diff = diffDsl(before, after)

    const paths = diff.nodes[0].changes.map((c) => c.path)
    expect(paths).toContain('assignee.strategy')
    expect(paths).toContain('assignee.deptFieldKey')
    expect(diff.nodes[0].changes.every((c) => c.label.startsWith('审批人·'))).toBe(true)
    expect(formatDiffValue(AssigneeStrategy.DEPT_LEADER, 'assignee.strategy')).toBe('部门主管')
  })

  it('连线重接：显示名用两端节点名', () => {
    const before = withApproval()
    const after: FlowDsl = {
      ...before,
      edges: before.edges.map((edge) =>
        edge.source === 'approval_1' && edge.target === END_ID ? { ...edge, target: START_ID } : edge,
      ),
    }
    const diff = diffDsl(before, after)

    expect(diff.edges).toHaveLength(1)
    expect(diff.edges[0].kind).toBe(DiffKind.CHANGED)
    expect(diff.edges[0].label).toBe('技术评审 → 开始')
    expect(diff.edges[0].changes.map((c) => c.path)).toEqual(['target'])
  })

  it('流程属性变化：说明与通知渠道', () => {
    const before = withApproval()
    const after: FlowDsl = {
      ...before,
      meta: { ...before.meta, description: '新增说明', notifications: { channel: 'EMAIL' } },
    }
    const diff = diffDsl(before, after)

    expect(diff.meta.map((m) => m.key)).toEqual(['description', 'notifications.channel'])
    expect(diff.meta[0].label).toBe('说明')
    expect(diff.meta[1].label).toBe('通知方式·通知渠道')
  })

  it('流程变量：新增 / 删除 / 类型变化', () => {
    const before = withApproval()
    const withVars: FlowDsl = {
      ...before,
      variables: [
        { name: 'amount', type: ValueType.NUMBER },
        { name: 'legacy', type: ValueType.STRING },
      ],
    }
    const after: FlowDsl = {
      ...before,
      variables: [
        { name: 'amount', type: ValueType.STRING },
        { name: 'owner', type: ValueType.STRING },
      ],
    }
    const diff = diffDsl(withVars, after)

    expect(diff.variables.find((v) => v.name === 'amount')).toMatchObject({
      kind: DiffKind.CHANGED,
      changes: [{ path: 'type', label: '类型', before: 'NUMBER', after: 'STRING' }],
    })
    expect(diff.variables.find((v) => v.name === 'owner')?.kind).toBe(DiffKind.ADDED)
    expect(diff.variables.find((v) => v.name === 'legacy')?.kind).toBe(DiffKind.REMOVED)
  })

  it('DSL 结构代际变化会单独列出来', () => {
    const before = withApproval()
    const after: FlowDsl = { ...before, dslVersion: '2.0' }
    const diff = diffDsl(before, after)

    expect(diff.dslVersion?.label).toBe('DSL 版本')
    expect(diff.summary.changed).toBe(1)
  })
})

describe('formatDiffValue', () => {
  it('空值 / 布尔 / 数组 / 枚举都有可读形态', () => {
    expect(formatDiffValue(undefined)).toBe('（空）')
    expect(formatDiffValue('')).toBe('（空）')
    expect(formatDiffValue(false)).toBe('否')
    expect(formatDiffValue(['a', 'b'])).toBe('a、b')
    expect(formatDiffValue([], 'userOids')).toBe('（空）')
    expect(formatDiffValue('INITIATOR_LEADER', 'assignee.strategy')).toBe('发起人上级')
    expect(formatDiffValue('PERCENT', 'passRule.mode')).toBe('按通过比例')
    // 同一个 mode 在不同路径下是两个意思：节点级是定时等待的方式
    expect(formatDiffValue('DURATION', 'mode')).toBe('等待时长')
  })
})
