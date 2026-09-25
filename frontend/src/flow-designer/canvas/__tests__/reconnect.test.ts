/**
 * 连线重连（拖动端点改接）—— 画布手势到 ops 的那一层。
 *
 * <p>为什么单独锁这一层：`graph.ts` 只负责把 X6 的端点拖动报上来，`ops.ts` 只负责改 DSL，
 * 中间"把它们接起来"的动作在 `useFlowDesigner.reconnectEdge` 里 —— 而这一层最容易出的问题
 * 恰恰是用户抱怨的那两个：
 * <ol>
 *   <li><b>改了没生效</b>：事件被忽略（`isNew === false` 早先直接被 return），
 *       画布上看着改了、DSL 没改，下一次重绘就复原（"锚点永远只能是那两个"）；</li>
 *   <li><b>配置丢失</b>：想改起止点只能删了重画，分支名 / 类型 / 条件 / 路由全要重设。</li>
 * </ol>
 * 本文件用"一条已配好的连线"把这两条都钉住。
 */

import { describe, expect, it } from 'vitest'
import {
  ApprovalMode,
  AssigneeStrategy,
  EdgeKind,
  EdgeRoute,
  NodeType,
  addEdge,
  addNode,
  createEmptyDsl,
  type FlowDsl,
} from '@flow-dsl-core'
import { useFlowDesigner } from '../useFlowDesigner'

/** 审批 → 结束 的一条"配好了"的连线，外加一个可改接过去的办理节点 */
function configuredDsl(): FlowDsl {
  let dsl = createEmptyDsl({ key: 'reconnect_flow', name: '重连测试' })
  // 默认直连 start→end 会挡住改接（同一对节点只允许一条线），先删掉
  dsl = { ...dsl, edges: [] }
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
  dsl = addNode(
    dsl,
    {
      id: 'task_1',
      type: NodeType.TASK,
      name: '实施变更',
      assignee: { strategy: AssigneeStrategy.INITIATOR },
    },
    { x: 200, y: 260 },
  )
  dsl = addEdge(dsl, 'start_1', 'approval_1')
  // 路由/分支名/类型都配上了 —— 重连之后这三样一个都不能丢
  dsl = addEdge(dsl, 'approval_1', 'end_1', {
    name: '同意',
    kind: EdgeKind.CONDITION,
    route: EdgeRoute.PASS,
  })
  dsl = addEdge(dsl, 'task_1', 'end_1')
  return dsl
}

const edgeOf = (dsl: FlowDsl, id: string) => dsl.edges.find((e) => e.id === id)!

describe('新建连线时的锚点（画布 → ops）', () => {
  it('起手点中的那个锚点被记下来（否则新线一律被自动分配拉回中点）', () => {
    const designer = useFlowDesigner(configuredDsl())
    // 从「审批」下方靠左的锚点起手，落到「结束」
    const result = designer.connect('approval_1', 'task_1', { sourcePort: 'bottom-2' })

    expect(result.ok).toBe(true)
    const created = designer.dsl.value.edges.find((e) => e.target === 'task_1')!
    expect(created.anchor?.source).toBe('bottom-2')
  })

  it('背对目标那一侧的锚点也照认（不再替用户做取舍 —— 按了哪儿就从哪儿出去）', () => {
    const designer = useFlowDesigner(configuredDsl())
    // task_1 在 approval_1 下方：即便落在「上方」的锚点，也认（线绕不绕是观感问题，
    // 静默丢弃用户的选择才是 bug）；想回到自动，面板里选「自动」即可
    designer.connect('approval_1', 'task_1', { sourcePort: 'top-2' })

    const created = designer.dsl.value.edges.find((e) => e.target === 'task_1')!
    expect(created.anchor?.source).toBe('top-2')
  })

  it('该节点上不存在的锚点被忽略（类型改过 / 老数据里的失效 id）', () => {
    const designer = useFlowDesigner(configuredDsl())
    designer.connect('approval_1', 'task_1', { sourcePort: 'bottom-9' })

    expect(designer.dsl.value.edges.find((e) => e.target === 'task_1')!.anchor).toBeUndefined()
  })

  it('没吸附到具体锚点时照旧自动分配（不写入 anchor）', () => {
    const designer = useFlowDesigner(configuredDsl())
    designer.connect('approval_1', 'task_1')

    expect(designer.dsl.value.edges.find((e) => e.target === 'task_1')!.anchor).toBeUndefined()
  })
})

describe('拖动端点重连（画布 → ops）', () => {
  it('只换两端：分支名 / 类型 / 路由全部保留，锚点记下拖到的那个圆点', () => {
    const designer = useFlowDesigner(configuredDsl())
    const target = designer.dsl.value.edges.find((e) => e.source === 'approval_1')!

    const result = designer.reconnectEdge({
      edgeId: target.id,
      source: 'approval_1',
      target: 'task_1',
      end: 'target',
      anchor: 'top-2',
    })

    expect(result.ok).toBe(true)
    const edge = edgeOf(designer.dsl.value, target.id)
    expect(edge.target).toBe('task_1')
    // 这三样正是"删了重画"会丢的东西
    expect(edge.name).toBe('同意')
    expect(edge.kind).toBe(EdgeKind.CONDITION)
    expect(edge.route).toBe(EdgeRoute.PASS)
    // 只认被拖的那一端；另一端（起点）不该被顺手钉住
    expect(edge.anchor).toEqual({ target: 'top-2' })
  })

  it('整次重连只占一步撤销（改端点 + 锚点，撤销一次就回到原样）', () => {
    const designer = useFlowDesigner(configuredDsl())
    const target = designer.dsl.value.edges.find((e) => e.source === 'approval_1')!
    const before = designer.dsl.value

    designer.reconnectEdge({
      edgeId: target.id,
      source: 'approval_1',
      target: 'task_1',
      end: 'target',
      anchor: 'top-2',
    })
    expect(designer.dsl.value).not.toBe(before)
    expect(designer.canUndo.value).toBe(true)

    designer.undo()
    const restored = edgeOf(designer.dsl.value, target.id)
    expect(restored.target).toBe('end_1')
    expect(restored.anchor).toBeUndefined()
  })

  it('非法改接：返回可读原因、DSL 一动不动（画布据此提示并回滚视图）', () => {
    const designer = useFlowDesigner(configuredDsl())
    const target = designer.dsl.value.edges.find((e) => e.source === 'approval_1')!
    const before = designer.dsl.value

    // 自己连自己（自环）被 canConnect 拦下
    const result = designer.reconnectEdge({
      edgeId: target.id,
      source: 'approval_1',
      target: 'approval_1',
      end: 'target',
    })

    expect(result.ok).toBe(false)
    expect(result.error).toBeTruthy()
    expect(designer.lastReject.value).toBe(result.error)
    // 没有产生半成品改动，也不该多一步撤销记录
    expect(designer.dsl.value).toBe(before)
  })

  it('拖到节点附近（没精确命中圆点）→ 清掉该端锚点、回到自动分配', () => {
    const designer = useFlowDesigner(configuredDsl())
    const target = designer.dsl.value.edges.find((e) => e.source === 'approval_1')!

    // 先精确拖到圆点上：钉住
    designer.reconnectEdge({
      edgeId: target.id,
      source: 'approval_1',
      target: 'task_1',
      end: 'target',
      anchor: 'top-2',
    })
    expect(edgeOf(designer.dsl.value, target.id).anchor).toEqual({ target: 'top-2' })

    // 再随手拖到节点附近（画布层判定不是精确命中，于是不上报锚点）：
    // 必须把这一端的钉子清掉 —— 否则端点会被吸在背对的一侧，线绕节点兜一圈（视频反馈）
    designer.reconnectEdge({
      edgeId: target.id,
      source: 'approval_1',
      target: 'task_1',
      end: 'target',
    })
    expect(edgeOf(designer.dsl.value, target.id).anchor).toBeUndefined()
  })
})
