/**
 * 「审批意见」表单 —— 审批活动的固定模板：同意 / 驳回、驳回位置、意见必填。
 *
 * <p><b>背景</b>：审批节点此前没有专属模板，运行期落回通用审批表单 —— 事能办，
 * 但看不出"这一下驳回会把流程退回哪里"。驳回目标（发起人 / 上一步 / 指定节点）是
 * 设计期配置，运行期只负责<b>显示</b>。
 *
 * <p>本文件钉住三件事：
 * <ol>
 *   <li>驳回目标怎么读（三种语义，指定节点要带出节点名）；</li>
 *   <li>是否允许驳回（关掉就只剩同意）；</li>
 *   <li>意见必填的口径：<b>驳回默认必填</b>，同意按节点声明。</li>
 * </ol>
 */

import { describe, expect, it } from 'vitest'
import {
  ApprovalMode,
  AssigneeStrategy,
  EdgeKind,
  EdgeRoute,
  NodeType,
  createEmptyDsl,
  type FlowDsl,
} from '@flow-dsl-core'
import {
  buildApprovalOpinionModel,
  validateApprovalOpinion,
} from '../approval-opinion'

/** 一个审批活动 + 一个下游节点（作为「指定节点」驳回的目标） */
function flow(reject?: Record<string, unknown>): FlowDsl {
  const base = createEmptyDsl({ key: 'approval_flow', name: '审批意见测试' })
  return {
    ...base,
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      {
        id: 'appr_1',
        type: NodeType.APPROVAL,
        name: '技术评审',
        assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
        approvalMode: ApprovalMode.SINGLE,
        ...(reject ? { reject } : {}),
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
      { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'appr_1' },
      { id: 'e2', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'task_1' },
      { id: 'e3', kind: EdgeKind.NORMAL, source: 'task_1', target: 'end_1' },
    ],
  } as FlowDsl
}

describe('审批意见 · 驳回位置', () => {
  it('配了驳回目标（上一步）→ 说明是"实际走过的那一步"（运行期判定）', () => {
    const model = buildApprovalOpinionModel(flow({ enabled: true, target: 'PREVIOUS' }), 'appr_1')
    expect(model.allowReject).toBe(true)
    // 只说"上一步"没人知道是哪个节点：运行期取的是"实际走过的那一步"（并行/会签下未必是图上前驱）
    expect(model.rejectTargetText).toBe('上一步（实际走过的那一步）')
  })

  it('配了驳回但没给目标 → 说明走的是流程图上的「驳回」路径（不是运行期跳转）', () => {
    // 没目标 = 设计者把驳回画成了一条出边（可以先挂自动化节点再回去）。
    // 这时若还说"退回上一步"，经办人就会按错的预期点下去
    const model = buildApprovalOpinionModel(flow({ enabled: true }), 'appr_1')
    expect(model.allowReject).toBe(true)
    expect(model.rejectTargetText).toBe('按流程图上的「驳回」路径')
  })

  it('图上有「驳回」出边 → 带出那条边指向的节点名（只说"按驳回路径"等于什么都没说）', () => {
    const dsl = flow({ enabled: true })
    const withEdge = {
      ...dsl,
      edges: [
        ...dsl.edges,
        { id: 'e_rej', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'task_1', route: EdgeRoute.REJECT },
      ],
    } as FlowDsl
    expect(buildApprovalOpinionModel(withEdge, 'appr_1').rejectTargetText)
      .toBe('按流程图上的「驳回」路径 → 「资料补齐」')
  })

  it('驳回边先落到自动化节点 → 说"先执行"（免得被读成"已经退回到某人了"）', () => {
    const dsl = flow({ enabled: true })
    const withService = {
      ...dsl,
      nodes: [
        ...dsl.nodes,
        { id: 'svc_1', type: NodeType.SERVICE, name: '设置PES状态', serviceRef: 'object.setLifecycleState' },
      ],
      edges: [
        ...dsl.edges,
        { id: 'e_rej', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'svc_1', route: EdgeRoute.REJECT },
      ],
    } as FlowDsl
    expect(buildApprovalOpinionModel(withService, 'appr_1').rejectTargetText)
      .toBe('按流程图上的「驳回」路径 → 先执行「设置PES状态」')
  })

  it('退到发起人：说明退回去之后要重新办理并提交', () => {
    expect(buildApprovalOpinionModel(flow({ enabled: true, target: 'INITIATOR' }), 'appr_1').rejectTargetText)
      .toBe('发起人（重新办理后提交）')
  })

  it('退到指定节点时带上节点名 —— 只说"指定节点"没人知道是哪个', () => {
    const model = buildApprovalOpinionModel(
      flow({ enabled: true, target: 'NODE', targetNodeId: 'task_1' }),
      'appr_1',
    )
    expect(model.rejectTargetText).toBe('指定节点「资料补齐」')
  })

  it('目标节点找不到时只给语义，不显示一个不存在的名字', () => {
    const model = buildApprovalOpinionModel(
      flow({ enabled: true, target: 'NODE', targetNodeId: 'ghost' }),
      'appr_1',
    )
    expect(model.rejectTargetText).toBe('指定节点')
  })

  it('关掉驳回后不再允许驳回（显式 enabled:false）', () => {
    expect(buildApprovalOpinionModel(flow({ enabled: false }), 'appr_1').allowReject).toBe(false)
  })

  it('审批节点<b>没有</b> reject 声明＝设计器关掉了该开关，一律不允许驳回', () => {
    // 设计器的「允许驳回」是 object-toggle，关掉时把整个 reject 键删掉；
    // 编译层也是"没 enabled 就不写 ckplm:reject"。把"没写"当"允许"会让开关形同虚设。
    const model = buildApprovalOpinionModel(flow(), 'appr_1')
    expect(model.allowReject).toBe(false)
    expect(validateApprovalOpinion(model, 'reject', '理由充分')).toBe('该活动未开启驳回')
  })

  it('存量形态：reject 只写了 target 没写 enabled，按允许处理（不误伤）', () => {
    expect(buildApprovalOpinionModel(flow({ target: 'INITIATOR' }), 'appr_1').allowReject).toBe(true)
  })

  it('会签审批同样按节点声明判断', () => {
    const dsl = flow()
    const withCountersign = {
      ...dsl,
      nodes: dsl.nodes.map((n) => (n.id === 'appr_1'
        ? { ...n, type: NodeType.COUNTERSIGN_APPROVAL, passRule: { mode: 'ALL' } }
        : n)),
    } as FlowDsl
    expect(buildApprovalOpinionModel(withCountersign, 'appr_1').allowReject).toBe(false)
    expect(buildApprovalOpinionModel(
      { ...withCountersign, nodes: withCountersign.nodes.map((n) => (n.id === 'appr_1' ? { ...n, reject: { enabled: true } } : n)) } as FlowDsl,
      'appr_1',
    ).allowReject).toBe(true)
  })

  it('非审批节点（办理节点）没有这个开关，保持允许驳回', () => {
    expect(buildApprovalOpinionModel(flow({ enabled: false }), 'task_1').allowReject).toBe(true)
  })

  it('取不到 DSL（老实例）时按默认口径：可驳回、退上一步', () => {
    const model = buildApprovalOpinionModel(null, 'appr_1')
    expect(model.allowReject).toBe(true)
    expect(model.rejectTargetText).toBe('上一步（实际走过的那一步）')
  })

  it('活动 id 在 DSL 里对不上（模板换过版本）时也按默认口径，不挡住办事', () => {
    expect(buildApprovalOpinionModel(flow({ enabled: false }), 'ghost_node').allowReject).toBe(true)
  })
})

describe('审批意见 · 意见必填', () => {
  it('驳回默认必填（驳回总得说个理由）', () => {
    const model = buildApprovalOpinionModel(flow({ enabled: true }), 'appr_1')
    expect(model.commentRequiredOnReject).toBe(true)
    expect(validateApprovalOpinion(model, 'reject', '')).toBe('驳回必须填写审批意见')
    expect(validateApprovalOpinion(model, 'reject', '尺寸标注有误')).toBe('')
  })

  it('显式关掉后驳回可以不写理由', () => {
    const model = buildApprovalOpinionModel(flow({ enabled: true, commentRequired: false }), 'appr_1')
    expect(model.commentRequiredOnReject).toBe(false)
    expect(validateApprovalOpinion(model, 'reject', '')).toBe('')
  })

  it('同意是否必填由节点声明决定，不在这里写死', () => {
    const model = buildApprovalOpinionModel(flow(), 'appr_1')
    expect(model.commentRequiredOnApprove).toBe(false)
    expect(validateApprovalOpinion(model, 'approve', '')).toBe('')

    const strict = buildApprovalOpinionModel(
      { ...flow(), nodes: flow().nodes.map((n) => (n.id === 'appr_1' ? { ...n, commentRequired: true } : n)) } as FlowDsl,
      'appr_1',
    )
    expect(strict.commentRequiredOnApprove).toBe(true)
    expect(validateApprovalOpinion(strict, 'approve', '')).toBe('该活动要求填写审批意见')
  })

  it('关掉驳回的活动不会被提交成驳回（后端不该收到一个流程没开的口子）', () => {
    const model = buildApprovalOpinionModel(flow({ enabled: false }), 'appr_1')
    expect(validateApprovalOpinion(model, 'reject', '理由充分')).toBe('该活动未开启驳回')
  })
})
