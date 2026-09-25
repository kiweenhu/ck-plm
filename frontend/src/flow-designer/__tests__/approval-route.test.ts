/**
 * 审批节点「通过 / 驳回」双路由 —— 跨层集成测试。
 *
 * <p>本文件跨越契约层（dsl-core）、画布层（canvas 默认值）、面板层（app 字段声明）
 * 与编译层（bpmn-compiler），因为「路由」这件事的完整性恰恰体现在四层一致：
 * 默认值给出两条路由 → 面板能配 → zod 往返不丢 → 编译产物带上。
 *
 * <p>其中最容易被忽略、后果最隐蔽的是 <b>zod 往返</b>：zod 默认会丢弃未声明的键，
 * 若新增字段忘了写进 schema，存盘再读回时它会<b>静默消失</b>（保存成功但配置丢了）。
 */

import { describe, expect, it } from 'vitest'
import {
  IssueCode,
  NodeType,
  createEmptyDsl,
  parseFlowDsl,
  removeEdge,
  validateDsl,
  type FlowDsl,
} from '@flow-dsl-core'
import { compileToBpmnXml } from '@flow-compiler'
import { useFlowDesigner } from '@flow-canvas'
import { nodeFields } from '@flow-app/panel/node-schemas'
import type { FieldSpec } from '@flow-app/panel/field-schema'

/** 新建一个带审批节点的流程（审批节点由设计器的默认值逻辑产出） */
function designerWithApproval() {
  const designer = useFlowDesigner(createEmptyDsl({ key: 'approval_route', name: '审批路由' }))
  const node = designer.addNodeAt(NodeType.APPROVAL, { x: 240, y: 160 })
  return { designer, nodeId: node.id }
}

const approvalOf = (dsl: FlowDsl, nodeId: string) =>
  dsl.nodes.find((n) => n.id === nodeId) as {
    reject?: { enabled?: boolean; target?: string; targetNodeId?: string; commentRequired?: boolean }
  }

const fieldOf = (fields: FieldSpec[], key: string): FieldSpec | undefined =>
  fields.find((f) => f.key === key)

describe('默认值：新审批节点即带「通过 + 驳回」两条路由', () => {
  it('默认写入 reject 且驳回意见必填', () => {
    const { designer, nodeId } = designerWithApproval()
    const reject = approvalOf(designer.dsl.value, nodeId).reject

    expect(reject, '新审批节点应默认带驳回路由').toBeDefined()
    expect(reject?.enabled).toBe(true)
    expect(reject?.target).toBe('PREVIOUS')
    expect(reject?.commentRequired, '驳回时意见必填应默认开启').toBe(true)
  })

  it('新节点尚未连线时报「孤立节点」（此时还谈不上路由）', () => {
    const { designer, nodeId } = designerWithApproval()
    const report = validateDsl(designer.dsl.value)
    const own = report.issues.filter((i) => i.nodeId === nodeId)
    expect(own.length).toBeGreaterThan(0)
    // 无入边也无出边 → 先命中 ISOLATED_NODE（validate 见孤立即 continue）
    expect(own.some((i) => i.code === IssueCode.ISOLATED_NODE)).toBe(true)
  })

  it('有入边但没连出「通过」时，文案明确指向缺失的路由', () => {
    const { designer, nodeId } = designerWithApproval()
    // 腾出「开始」的唯一出边（BPMN 规定开始事件只能有一条出边），改为 开始 → 审批
    const edgeId = designer.dsl.value.edges[0].id
    designer.replaceDsl(removeEdge(designer.dsl.value, edgeId))
    const connected = designer.connect('start_1', nodeId)
    expect(connected.ok, connected.error).toBe(true)

    const messages = validateDsl(designer.dsl.value)
      .issues.filter((i) => i.nodeId === nodeId)
      .map((i) => i.message)
      .join(' | ')
    expect(messages).toContain('缺少「通过」路由')
  })

  it('驳回目标为「指定节点」但未给出节点时被校验拦下', () => {
    const { designer, nodeId } = designerWithApproval()
    const node = designer.dsl.value.nodes.find((n) => n.id === nodeId)!
    designer.applyNode(nodeId, {
      ...node,
      reject: { enabled: true, target: 'NODE', commentRequired: true },
    } as never)

    const report = validateDsl(designer.dsl.value)
    expect(report.issues.some((i) => i.code === IssueCode.REJECT_NODE_NOT_FOUND)).toBe(true)
  })
})

describe('契约往返：commentRequired 不能被 zod 丢弃', () => {
  it('parseFlowDsl 往返后驳回意见必填仍在', () => {
    const { designer, nodeId } = designerWithApproval()
    const roundTripped = parseFlowDsl(JSON.parse(JSON.stringify(designer.dsl.value)))
    expect(roundTripped.ok, roundTripped.ok ? '' : roundTripped.error).toBe(true)
    if (!roundTripped.ok) {
      return
    }
    const reject = approvalOf(roundTripped.dsl, nodeId).reject
    expect(reject?.commentRequired).toBe(true)
    expect(reject?.target).toBe('PREVIOUS')
  })

  it('显式关闭后同样能往返', () => {
    const { designer, nodeId } = designerWithApproval()
    const node = designer.dsl.value.nodes.find((n) => n.id === nodeId)!
    designer.applyNode(nodeId, {
      ...node,
      reject: { enabled: true, target: 'INITIATOR', commentRequired: false },
    } as never)

    const roundTripped = parseFlowDsl(JSON.parse(JSON.stringify(designer.dsl.value)))
    expect(roundTripped.ok).toBe(true)
    if (roundTripped.ok) {
      expect(approvalOf(roundTripped.dsl, nodeId).reject?.commentRequired).toBe(false)
    }
  })
})

describe('编译产物：驳回声明带上意见必填', () => {
  it('ckplm:reject 中包含 commentRequired', () => {
    const { designer } = designerWithApproval()
    const compiled = compileToBpmnXml(designer.dsl.value)
    expect(compiled.xml).toContain('reject')
    expect(compiled.xml).toContain('commentRequired')
  })

  it('关闭后编译产物中不再出现 commentRequired（避免陈述与配置不符）', () => {
    const { designer, nodeId } = designerWithApproval()
    const node = designer.dsl.value.nodes.find((n) => n.id === nodeId)!
    designer.applyNode(nodeId, {
      ...node,
      reject: { enabled: true, target: 'PREVIOUS' },
    } as never)

    const compiled = compileToBpmnXml(designer.dsl.value)
    expect(compiled.xml).not.toContain('commentRequired')
  })
})

describe('属性面板：审批节点的「路由」区', () => {
  it('声明了路由说明与驳回意见必填开关', () => {
    const fields = nodeFields(NodeType.APPROVAL)
    expect(fieldOf(fields, 'hint-route')?.control).toBe('hint')
    expect(fieldOf(fields, 'reject.commentRequired')?.control).toBe('switch')
  })

  it('路由相关字段都属于同一分组，便于集中呈现', () => {
    const fields = nodeFields(NodeType.APPROVAL)
    for (const key of ['hint-route', 'reject', 'reject.target', 'reject.targetNodeId', 'reject.commentRequired']) {
      expect(fieldOf(fields, key)?.group, `${key} 应归入「路由」分组`).toBe('路由')
    }
  })

  it('驳回未启用时，驳回相关字段全部隐藏', () => {
    const fields = nodeFields(NodeType.APPROVAL)
    const noReject = { approvalMode: 'SINGLE' }
    expect(fieldOf(fields, 'reject.target')?.visibleIf?.(noReject, {} as never)).toBe(false)
    expect(fieldOf(fields, 'reject.commentRequired')?.visibleIf?.(noReject, {} as never)).toBe(false)

    const withReject = { reject: { enabled: true, target: 'PREVIOUS' } }
    expect(fieldOf(fields, 'reject.commentRequired')?.visibleIf?.(withReject, {} as never)).toBe(true)
    // 「指定节点」才需要目标节点
    expect(fieldOf(fields, 'reject.targetNodeId')?.visibleIf?.(withReject, {} as never)).toBe(false)
    expect(
      fieldOf(fields, 'reject.targetNodeId')?.visibleIf?.(
        { reject: { enabled: true, target: 'NODE' } },
        {} as never,
      ),
    ).toBe(true)
  })

  it('办理节点没有驳回路由（它不承担决策语义）', () => {
    const fields = nodeFields(NodeType.TASK)
    expect(fieldOf(fields, 'reject')).toBeUndefined()
    expect(fieldOf(fields, 'reject.commentRequired')).toBeUndefined()
  })
})

describe('完整链路：默认值 → 连出通过路由 → 校验通过', () => {
  it('接上通过出边后，审批节点不再报缺出边', () => {
    const { designer, nodeId } = designerWithApproval()
    // 通过路由 = 从审批节点连到结束节点
    const result = designer.connect(nodeId, 'end_1')
    expect(result.ok, result.error).toBe(true)

    const report = validateDsl(designer.dsl.value)
    expect(report.issues.filter((i) => i.nodeId === nodeId && i.code === IssueCode.NO_OUTGOING)).toHaveLength(0)
    // 并确认文案确实是审批节点的措辞
    const all = validateDsl(designer.dsl.value).issues.map((i) => i.message).join(' | ')
    expect(all).not.toContain('缺少「通过」路由')
  })
})
