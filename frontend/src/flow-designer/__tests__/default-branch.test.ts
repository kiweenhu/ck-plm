/**
 * 默认分支：模型不变量 + 校验可操作性 + 面板入口。
 *
 * <p>放在跨层测试目录（而不是 `dsl-core/__tests__`）：它同时断言了 dsl-core 的
 * 写入不变量与 `app/panel` 的字段声明，是跨层契约，放在任一层的单测目录都会造成依赖倒挂。
 *
 * <p><b>背景</b>：排他/包容网关必须有一条默认分支（所有条件都不命中时走它），
 * 否则流程会卡住。这条能力早就有（`EdgeKind.DEFAULT`），但用户反馈
 * 「在条件表达式后面找不到设置默认分支的地方」—— 说明问题不在能力，在<b>入口</b>：
 * 它当时藏在「类型」下拉里。本文件钉住两件事：
 * <ol>
 *   <li>写入路径维护<b>模型不变量</b>：一个分支节点最多一条默认分支（不靠事后报错）；</li>
 *   <li>面板把「默认分支」作为<b>可见选项</b>暴露，且校验信息给出操作指引。</li>
 * </ol>
 */

import { describe, expect, it } from 'vitest'
import {
  AssigneeStrategy,
  EdgeKind,
  IssueCode,
  IssueLevel,
  NodeType,
  createEmptyDsl,
  replaceEdge,
  validateDsl,
  type FlowDsl,
  type FlowEdge,
} from '@flow-dsl-core'
import { edgeFields, edgeKindOptionsFor, edgeKindOptionsWithCurrent } from '../app/panel/node-schemas'

/** 开始 → 条件分支 →（审批A / 审批B）→ 结束；两条出边分别是「有条件的」和「无条件的」 */
function gatewayFlow(): FlowDsl {
  const base = createEmptyDsl({ key: 'gw_flow', name: '默认分支测试' })
  return {
    ...base,
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '条件分支' },
      { id: 'a1', type: NodeType.APPROVAL, name: '审批A', assignee: { strategy: AssigneeStrategy.INITIATOR }, approvalMode: 'SINGLE' },
      { id: 'a2', type: NodeType.APPROVAL, name: '审批B', assignee: { strategy: AssigneeStrategy.INITIATOR }, approvalMode: 'SINGLE' },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    edges: [
      { id: 'e0', kind: EdgeKind.NORMAL, source: 'start_1', target: 'gw_1' },
      {
        id: 'e1',
        kind: EdgeKind.CONDITION,
        source: 'gw_1',
        target: 'a1',
        condition: { mode: 'FIELD', fieldKey: 'amount', operator: 'GT', value: '1000' },
      } as never,
      { id: 'e2', kind: EdgeKind.CONDITION, source: 'gw_1', target: 'a2' },
      { id: 'e3', kind: EdgeKind.NORMAL, source: 'a1', target: 'end_1' },
      { id: 'e4', kind: EdgeKind.NORMAL, source: 'a2', target: 'end_1' },
    ],
  } as FlowDsl
}

const edgeById = (dsl: FlowDsl, id: string): FlowEdge => dsl.edges.find((e) => e.id === id) as FlowEdge
const codesOf = (dsl: FlowDsl): string[] => validateDsl(dsl).issues.map((i) => i.code)

describe('默认分支：模型不变量（写入路径保证）', () => {
  it('把一条边置为默认分支，它就变成 DEFAULT', () => {
    const base = gatewayFlow()
    const dsl = replaceEdge(base, 'e2', { ...edgeById(base, 'e2'), kind: EdgeKind.DEFAULT })
    expect(edgeById(dsl, 'e2').kind).toBe(EdgeKind.DEFAULT)
  })

  it('再置另一条：原默认分支自动降级（不会出现两条默认分支）', () => {
    const base = gatewayFlow()
    const first = replaceEdge(base, 'e1', { ...edgeById(base, 'e1'), kind: EdgeKind.DEFAULT })
    const second = replaceEdge(first, 'e2', { ...edgeById(first, 'e2'), kind: EdgeKind.DEFAULT })

    expect(edgeById(second, 'e2').kind).toBe(EdgeKind.DEFAULT)
    // e1 原本带条件 → 降级回「条件流转」（条件仍在，不是普通流转）
    expect(edgeById(second, 'e1').kind).toBe(EdgeKind.CONDITION)
    expect(edgeById(second, 'e1').condition).toBeDefined()
    // 不变量成立：不再有「多条默认分支」的错误
    expect(codesOf(second)).not.toContain(IssueCode.GATEWAY_MULTIPLE_DEFAULT)
  })

  it('无条件的旧默认分支被降级后回到「普通流转」', () => {
    const base = gatewayFlow()
    const first = replaceEdge(base, 'e2', { ...edgeById(base, 'e2'), kind: EdgeKind.DEFAULT })
    const second = replaceEdge(first, 'e1', { ...edgeById(first, 'e1'), kind: EdgeKind.DEFAULT })

    expect(edgeById(second, 'e2').kind).toBe(EdgeKind.NORMAL)
  })

  it('只动同源的边：另一个分支节点的默认分支不受影响', () => {
    const base = gatewayFlow()
    const extra = {
      ...base,
      nodes: [...base.nodes, { id: 'gw_2', type: NodeType.EXCLUSIVE_GATEWAY, name: '另一个分支' }],
      edges: [
        ...base.edges.map((e) => (e.id === 'e3' ? { ...e, target: 'gw_2' } : e)),
        { id: 'e5', kind: EdgeKind.DEFAULT, source: 'gw_2', target: 'end_1' },
      ],
    } as FlowDsl
    const withDefault = replaceEdge(extra, 'e2', { ...edgeById(extra, 'e2'), kind: EdgeKind.DEFAULT })

    expect(edgeById(withDefault, 'e5').kind).toBe(EdgeKind.DEFAULT)
    expect(edgeById(withDefault, 'e2').kind).toBe(EdgeKind.DEFAULT)
  })
})

describe('默认分支：校验与入口', () => {
  it('没有默认分支时，错误信息给出可操作指引（而不是只说"缺少"）', () => {
    const issue = validateDsl(gatewayFlow()).issues.find(
      (i) => i.code === IssueCode.GATEWAY_NO_DEFAULT,
    )
    expect(issue).toBeDefined()
    expect(issue?.nodeId).toBe('gw_1')
    expect(issue?.message).toContain('默认分支')
    expect(issue?.message).toContain('类型')
  })

  it('设好默认分支后，该错误消失', () => {
    const base = gatewayFlow()
    const dsl = replaceEdge(base, 'e2', { ...edgeById(base, 'e2'), kind: EdgeKind.DEFAULT })
    expect(codesOf(dsl)).not.toContain(IssueCode.GATEWAY_NO_DEFAULT)
  })

  it('面板把「默认分支」作为可见选项暴露（不是藏在下拉里的能力）', () => {
    const kindField = edgeFields().find((f) => f.key === 'kind')
    expect(kindField, '连线必须有类型字段').toBeDefined()
    // 专用控件（按钮组）而不是普通下拉：默认分支必须一眼可见
    expect(kindField?.control).toBe('edge-kind')
    expect((kindField?.options ?? []).map((o) => o.value)).toContain(EdgeKind.DEFAULT)
  })

  it('默认分支标在非分支节点的连线上 → 提醒（该标记本就不会生效）', () => {
    const base = gatewayFlow()
    // e3 的源是「审批A」，不是分支节点
    const dsl = replaceEdge(base, 'e3', { ...edgeById(base, 'e3'), kind: EdgeKind.DEFAULT })
    const issue = validateDsl(dsl).issues.find(
      (i) => i.code === IssueCode.DEFAULT_BRANCH_UNEXPECTED,
    )
    expect(issue, '应提醒该标记不会生效').toBeDefined()
    expect(issue?.edgeId).toBe('e3')
    expect(issue?.level).toBe(IssueLevel.WARNING)
  })
})

describe('「普通流转」只在该出现的地方出现', () => {
  it('条件分支的出边：不给「普通流转」（无条件的边会压死其他分支）', () => {
    const dsl = gatewayFlow()
    const options = edgeKindOptionsFor(dsl, edgeById(dsl, 'e1')).map((o) => o.value)
    expect(options).toEqual([EdgeKind.CONDITION, EdgeKind.DEFAULT])
    expect(options).not.toContain(EdgeKind.NORMAL)
  })

  it('并行分支的出边：只给「普通流转」（无条件才是它的语义）', () => {
    const base = gatewayFlow()
    const dsl = {
      ...base,
      nodes: base.nodes.map((n) =>
        n.id === 'gw_1' ? { ...n, type: NodeType.PARALLEL_GATEWAY } : n,
      ),
    } as FlowDsl
    expect(edgeKindOptionsFor(dsl, edgeById(dsl, 'e1')).map((o) => o.value)).toEqual([
      EdgeKind.NORMAL,
    ])
  })

  it('普通节点：单条出边给「普通流转」，多条出边就不给', () => {
    const single = gatewayFlow()
    expect(edgeKindOptionsFor(single, edgeById(single, 'e3')).map((o) => o.value)).toEqual([
      EdgeKind.NORMAL,
      EdgeKind.CONDITION,
    ])

    const base = gatewayFlow()
    const two = {
      ...base,
      edges: [
        ...base.edges,
        { id: 'e6', kind: EdgeKind.CONDITION, source: 'a1', target: 'end_1' },
      ],
    } as FlowDsl
    expect(edgeKindOptionsFor(two, edgeById(two, 'e3')).map((o) => o.value)).toEqual([
      EdgeKind.CONDITION,
      EdgeKind.DEFAULT,
    ])
  })

  it('当前值已不适用时也补回选项（否则单选组一个都不选中）', () => {
    const base = gatewayFlow()
    const two = {
      ...base,
      edges: [
        ...base.edges,
        { id: 'e6', kind: EdgeKind.CONDITION, source: 'a1', target: 'end_1' },
      ],
    } as FlowDsl
    // e3 仍是 NORMAL，但 a1 已有两条出边 → 候选里没有 NORMAL，需要补回当前值
    const options = edgeKindOptionsWithCurrent(two, edgeById(two, 'e3')).map((o) => o.value)
    expect(options).toContain(EdgeKind.NORMAL)
    expect(options).toContain(EdgeKind.CONDITION)
  })
})

describe('静默缺陷：无条件的边会压死后面的分支', () => {
  it('网关既有默认分支、又有一条无条件出边 → 仍然报错（原先静默通过的那一种）', () => {
    const base = gatewayFlow()
    const dsl = {
      ...base,
      edges: base.edges.map((e) => {
        if (e.id === 'e1') {
          return { ...e, kind: EdgeKind.DEFAULT, condition: undefined } as FlowEdge
        }
        if (e.id === 'e2') {
          return { ...e, kind: EdgeKind.NORMAL, condition: undefined } as FlowEdge
        }
        return e
      }),
    } as FlowDsl

    const issues = validateDsl(dsl).issues.filter((i) => i.code === IssueCode.MISSING_CONDITION)
    expect(issues.map((i) => i.edgeId), '无条件的那条要被指出').toContain('e2')
    // 默认分支本身不需要条件
    expect(issues.map((i) => i.edgeId)).not.toContain('e1')
    // 有默认分支 → 不再是「缺少默认分支」的问题
    expect(codesOf(dsl)).not.toContain(IssueCode.GATEWAY_NO_DEFAULT)
  })

  it('普通节点多出边：一条无条件边 + 一条默认分支同样报错', () => {
    const base = gatewayFlow()
    const dsl = {
      ...base,
      nodes: base.nodes.map((n) =>
        n.id === 'a1' ? { ...n, assignee: { strategy: AssigneeStrategy.INITIATOR } } : n,
      ),
      edges: [
        ...base.edges.map((e) =>
          e.id === 'e3'
            ? ({ ...e, kind: EdgeKind.DEFAULT, condition: undefined } as FlowEdge)
            : e,
        ),
        // a1 的第二条出边：无条件
        { id: 'e6', kind: EdgeKind.NORMAL, source: 'a1', target: 'a2' } as FlowEdge,
      ],
    } as FlowDsl

    const issues = validateDsl(dsl).issues.filter((i) => i.code === IssueCode.MISSING_CONDITION)
    expect(issues.map((i) => i.edgeId)).toContain('e6')
    expect(issues.map((i) => i.edgeId)).not.toContain('e3')
  })
})
