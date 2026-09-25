/**
 * 驳回目标的校验 —— 设计期就拦下"配了也一定跑不通"的组合。
 *
 * <p>这些规则照着<b>运行期的真实口径</b>写（后端 {@code reject/RejectRouter} 按目标跳转，
 * 跳不到就明确失败）。设计期不拦，用户会在办理时才发现 —— 那时人已经在流程里等着了。
 *
 * <p>钉住四件事：目标不存在 / 目标是自己 / 目标不是人工活动 / 无处可退（上一步、发起人）。
 */

import { describe, expect, it } from 'vitest'
import {
  AssigneeStrategy,
  EdgeKind,
  IssueCode,
  IssueLevel,
  NodeType,
  RejectTarget,
  createEmptyDsl,
  validateDsl,
  type FlowDsl,
  type FlowNode,
  type RejectSpec,
} from '../index'

/** 造一条流程：开始 → appr1 → appr2 → 结束（驳回配置挂在哪个节点可选） */
function flow(options: {
  reject?: RejectSpec
  /** 驳回配置挂在哪个节点上；默认第一个审批（它前面没有任何办理活动） */
  rejectOn?: 'appr_1' | 'appr_2'
  firstAssignee?: AssigneeStrategy
  /** 额外挂一个节点（验证"目标能不能当驳回落点"，不接连线也不影响本文件的断言） */
  extraNode?: FlowNode
} = {}): FlowDsl {
  const base = createEmptyDsl({ key: 'reject_flow', name: '驳回校验测试' })
  const withReject = (id: string) => (options.reject && (options.rejectOn ?? 'appr_1') === id
    ? { reject: options.reject }
    : {})
  const nodes: FlowNode[] = [
    { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
    {
      id: 'appr_1',
      type: NodeType.APPROVAL,
      name: '初审',
      assignee: { strategy: options.firstAssignee ?? AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      approvalMode: 'SINGLE',
      ...withReject('appr_1'),
    },
    {
      id: 'appr_2',
      type: NodeType.APPROVAL,
      name: '复审',
      assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['MGR'] },
      approvalMode: 'SINGLE',
      ...withReject('appr_2'),
    },
    { id: 'end_1', type: NodeType.END, name: '结束' },
  ]
  if (options.extraNode) {
    nodes.push(options.extraNode)
  }
  const edges: FlowDsl['edges'] = [
    { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'appr_1' },
    { id: 'e2', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'appr_2' },
    { id: 'e3', kind: EdgeKind.NORMAL, source: 'appr_2', target: 'end_1' },
  ]
  return { ...base, nodes, edges }
}

function codesOf(dsl: FlowDsl): string[] {
  return validateDsl(dsl).issues.map((issue) => issue.code)
}

/** 取该 code 的问题（找不到返回 undefined） */
function issueOf(dsl: FlowDsl, code: string) {
  return validateDsl(dsl).issues.find((issue) => issue.code === code)
}

describe('驳回目标 · 指定节点', () => {
  it('目标节点不存在 → 错误（与既有口径一致，阻断保存）', () => {
    const issue = issueOf(flow({ reject: { enabled: true, target: RejectTarget.NODE, targetNodeId: 'ghost' } }),
      IssueCode.REJECT_NODE_NOT_FOUND)
    expect(issue?.level).toBe(IssueLevel.ERROR)
  })

  it('目标节点是自己 → 错误（退回去等于原地不动）', () => {
    const issue = issueOf(flow({ reject: { enabled: true, target: RejectTarget.NODE, targetNodeId: 'appr_1' } }),
      IssueCode.REJECT_TARGET_SELF)
    expect(issue?.level).toBe(IssueLevel.ERROR)
    expect(issue?.message).toContain('初审')
  })

  it('目标不是人工活动 → 错误（退回去没有人能办理）', () => {
    const issue = issueOf(flow({ reject: { enabled: true, target: RejectTarget.NODE, targetNodeId: 'end_1' } }),
      IssueCode.REJECT_TARGET_NOT_HUMAN)
    expect(issue?.level).toBe(IssueLevel.ERROR)
  })

  it('目标是一个正常的人工活动 → 无问题', () => {
    expect(codesOf(flow({ reject: { enabled: true, target: RejectTarget.NODE, targetNodeId: 'appr_2' } })))
      .not.toContain(IssueCode.REJECT_TARGET_NOT_HUMAN)
  })

  it('目标是「设置审批人」→ 合法：它确实产生要人办的任务，运行期也跳得过去', () => {
    // 回归用例：「驳回 → 重新指派审批人 → 重走审批」是常见编排。
    // 这里曾经漏掉 SET_ASSIGNEE，导致这类流程在设计期报"退回去没有人能办理"，
    // 而运行期（RejectRouter 认 userTask）明明能跳 —— 设计期与运行期必须同一口径
    const dsl = flow({
      reject: { enabled: true, target: RejectTarget.NODE, targetNodeId: 'setup_1' },
      extraNode: {
        id: 'setup_1',
        type: NodeType.SET_ASSIGNEE,
        name: '设置审批人',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      },
    })
    expect(codesOf(dsl)).not.toContain(IssueCode.REJECT_TARGET_NOT_HUMAN)
  })
})

describe('驳回目标 · 发起人', () => {
  it('流程里没有"由发起人办理"的节点 → 错误（退回去没有人能办）', () => {
    const issue = issueOf(flow({ reject: { enabled: true, target: RejectTarget.INITIATOR } }),
      IssueCode.REJECT_NO_INITIATOR_NODE)
    expect(issue?.level).toBe(IssueLevel.ERROR)
    expect(issue?.message).toContain('没有由发起人办理的节点')
  })

  it('存在由发起人办理的节点 → 无问题', () => {
    expect(codesOf(flow({
      reject: { enabled: true, target: RejectTarget.INITIATOR },
      firstAssignee: AssigneeStrategy.INITIATOR,
    }))).not.toContain(IssueCode.REJECT_NO_INITIATOR_NODE)
  })
})

describe('驳回目标 · 上一步', () => {
  it('本节点前没有任何办理活动 → 错误（无处可退）', () => {
    // 只有一个审批节点，且它就是第一个活动：它的"上一步"不存在
    const base = createEmptyDsl({ key: 'reject_single', name: '单节点' })
    const dsl: FlowDsl = {
      ...base,
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'appr_1',
          type: NodeType.APPROVAL,
          name: '唯一审批',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: 'SINGLE',
          reject: { enabled: true, target: RejectTarget.PREVIOUS },
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'appr_1' },
        { id: 'e2', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'end_1' },
      ],
    }
    const issue = issueOf(dsl, IssueCode.REJECT_NO_PREVIOUS_NODE)
    expect(issue?.level).toBe(IssueLevel.ERROR)
    expect(issue?.message).toContain('无处可退')
  })

  it('前面有办理活动 → 无问题', () => {
    // 驳回配在第二个审批上：它前面有「初审」，"上一步"有处可退
    expect(codesOf(flow({
      reject: { enabled: true, target: RejectTarget.PREVIOUS },
      rejectOn: 'appr_2',
    }))).not.toContain(IssueCode.REJECT_NO_PREVIOUS_NODE)
  })
})

describe('驳回目标 · 开启但没配（既无目标也无驳回边）', () => {
  /** 只有一个审批节点的最小流程：**没有出边**（还没连线） */
  function lonelyApproval(reject: Record<string, unknown>): FlowDsl {
    const base = createEmptyDsl({ key: 'reject_no_target', name: '驳回无处可去' })
    return {
      ...base,
      nodes: [
        {
          id: 'appr_1',
          type: NodeType.APPROVAL,
          name: '会签审批',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          reject,
        },
      ],
      edges: [],
    } as unknown as FlowDsl
  }

  it('没有出边的节点同样要报 —— 出边校验会整段跳过，这条曾经因此一声不响', () => {
    // 回归：规则原先挂在出边校验里，而那里开头是 `if (outgoing.length === 0) continue`，
    // 于是"孤立方块 + 勾了允许驳回 + 什么都没配"的节点校验通过，运行期才报错
    const issue = issueOf(lonelyApproval({ enabled: true }), IssueCode.REJECT_NO_TARGET)
    expect(issue?.level).toBe(IssueLevel.ERROR)
    expect(issue?.message).toContain('会签审批')
    expect(issue?.message).toContain('驳回之后无处可去')
  })

  it('画了「驳回」出边 → 不算无处可去（那条边就是去处）', () => {
    const dsl = lonelyApproval({ enabled: true })
    const withEdge = {
      ...dsl,
      edges: [{ id: 'e_rej', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'appr_1', route: 'REJECT' }],
    } as unknown as FlowDsl
    expect(codesOf(withEdge)).not.toContain(IssueCode.REJECT_NO_TARGET)
  })

  it('关掉「允许驳回」→ 不报（没在用这条能力就不打扰）', () => {
    expect(codesOf(lonelyApproval({ enabled: false }))).not.toContain(IssueCode.REJECT_NO_TARGET)
  })
})

describe('驳回目标 · 未开启驳回时不校验', () => {
  it('reject 关掉（enabled:false）时，即便目标是自己不报错 —— 不打扰没在用它的流程', () => {
    expect(codesOf(flow({ reject: { enabled: false, target: RejectTarget.NODE, targetNodeId: 'appr_1' } })))
      .not.toContain(IssueCode.REJECT_TARGET_SELF)
  })
})
