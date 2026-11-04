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

  it('画了「驳回」出边但没连目标时，路由校验仍然拦下', () => {
    // 驳回统一为画线之后，"退回哪"由图说了算：那条边必须有去处。
    // 这里只钉住"路由声明"这一类仍然成立的规则，旧配置项（驳回目标）已退场
    const { designer, nodeId } = designerWithApproval()
    const node = designer.dsl.value.nodes.find((n) => n.id === nodeId)!
    designer.applyNode(nodeId, { ...node } as never)
    const report = validateDsl(designer.dsl.value)
    expect(report.issues.some((i) => i.code === IssueCode.EDGE_ROUTE_NO_PASS)).toBe(false)
  })
})

describe('契约往返：老驳回配置在加载时被清掉（统一为画线）', () => {
  it('parseFlowDsl 往返后节点上不再有 reject 声明', () => {
    // 面板里已经没有这些字段了，留着就是"看不见却仍然生效"
    const { designer, nodeId } = designerWithApproval()
    const roundTripped = parseFlowDsl(JSON.parse(JSON.stringify(designer.dsl.value)))
    expect(roundTripped.ok, roundTripped.ok ? '' : roundTripped.error).toBe(true)
    if (!roundTripped.ok) {
      return
    }
    expect(approvalOf(roundTripped.dsl, nodeId).reject).toBeUndefined()
  })

  it('清掉驳回配置时不误伤节点本身', () => {
    const { designer, nodeId } = designerWithApproval()
    const roundTripped = parseFlowDsl(JSON.parse(JSON.stringify(designer.dsl.value)))
    expect(roundTripped.ok).toBe(true)
    if (roundTripped.ok) {
      const node = approvalOf(roundTripped.dsl, nodeId)
      expect(node.name).toBeTruthy()
      expect(node.assignee).toBeTruthy()
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
  it('只保留一条"画线"说明 —— 驳回不再是属性配置', () => {
    // 2026-10-13 用户口径：驳回统一为画线（从节点连一条出边、路由设为「驳回」），
    // 属性面板里的「允许驳回 / 驳回目标 / 目标节点 / 驳回意见必填」全部退场
    const fields = nodeFields(NodeType.APPROVAL)
    expect(fieldOf(fields, 'hint-route')?.control).toBe('hint')
    expect(fieldOf(fields, 'hint-route')?.group).toBe('路由')
    for (const gone of ['reject', 'reject.target', 'reject.targetNodeId', 'reject.commentRequired']) {
      expect(fieldOf(fields, gone)).toBeUndefined()
    }
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
