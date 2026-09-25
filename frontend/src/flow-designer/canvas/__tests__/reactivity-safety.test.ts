/**
 * 响应式安全回归测试。
 *
 * <p><b>为什么必须单独有一份</b>：这个缺陷是真实发生过的白屏事故，而它<b>无法被普通单测发现</b> ——
 * `useFlowDesigner` 用 `ref<FlowDsl>` 持有 DSL，`ref` 会把对象深度转为 Vue 的响应式 Proxy；
 * `structuredClone` 无法克隆 Proxy，会抛 `DataCloneError`。由于 `cloneDsl` 被所有 ops 调用、
 * `DslHistory.reset` 在 setup 阶段就被调用，异常直接打断组件 setup → 整页白屏（控制台只剩一堆红字）。
 *
 * <p>普通单测传的都是纯对象，所以一路绿灯；只有把 DSL 包成响应式再跑，才暴露得出来。
 * 本文件守住一条契约：<b>dsl-core 的 ops 与 canvas 的历史栈必须接受响应式输入</b>。
 *
 * <p>注意断言口径：本文件只断言「不抛 `DataCloneError`」与「脱离响应式 / 快照隔离」，
 * 不断言连线等业务语义 —— 那些由 `canConnect` 与 `validateDsl` 各自负责，避免测试互相绑死。
 */

import { describe, expect, it } from 'vitest'
import { isReactive, ref } from 'vue'
import {
  EdgeKind,
  NodeType,
  addEdge,
  addNode,
  cloneDsl,
  createEmptyDsl,
  moveNode,
  removeEdge,
  removeNode,
  replaceNode,
  setVariables,
  updateNode,
  validateDsl,
  type FlowDsl,
} from '@flow-dsl-core'
import { DslHistory } from '../undo-stack'

/** 模拟 Vue 层：`useFlowDesigner` 用 ref 持有 DSL，故 ops / 历史栈拿到的都是 Proxy */
function refDsl(key = 'reactive_flow') {
  return ref<FlowDsl>(createEmptyDsl({ key, name: '响应式测试' }))
}

/**
 * 断言「即便抛错，也绝不是克隆失败」。
 *
 * <p>刻意不写 `.not.toThrow(DataCloneError)`：`DataCloneError` 在 Node 里不保证是全局，
 * 一旦为 undefined，`not.toThrow(undefined)` 会退化为「必须什么都不抛」，
 * 对可能被业务规则合法拒绝的操作（如 `addEdge`）产生误判。
 */
function expectNoCloneFailure(operation: () => unknown): void {
  try {
    operation()
  } catch (error) {
    const name = (error as { name?: string } | null)?.name ?? ''
    const message = error instanceof Error ? error.message : String(error)
    expect(`${name}: ${message}`).not.toMatch(/DataCloneError|could not be cloned/i)
  }
}

/** 审批节点（可安全插入的合法形状） */
const approvalNode = {
  id: 'a1',
  type: NodeType.APPROVAL,
  name: '审批',
  assignee: { strategy: 'ROLE', roleCodes: ['ENG'] },
  approvalMode: 'SINGLE',
}

describe('cloneDsl 必须接受 Vue 的响应式 Proxy', () => {
  it('克隆 Proxy 不抛异常，且产出纯数据（脱离响应式）', () => {
    const dsl = refDsl()
    expect(isReactive(dsl.value)).toBe(true)

    let cloned: FlowDsl | undefined
    expect(() => {
      cloned = cloneDsl(dsl.value)
    }).not.toThrow()

    expect(cloned).toBeDefined()
    // 关键：克隆结果必须脱离响应式，否则快照会与实时 DSL 共享底层对象
    expect(isReactive(cloned)).toBe(false)
    expect(cloned?.meta.key).toBe('reactive_flow')
  })

  it('克隆不产生别名：改克隆体不影响原 DSL', () => {
    const dsl = refDsl()
    const cloned = cloneDsl(dsl.value) as FlowDsl
    cloned.meta.name = '被改坏'
    expect(dsl.value.meta.name).toBe('响应式测试')
  })
})

describe('ops 在响应式输入下均可用（它们内部都走 cloneDsl）', () => {
  it('addNode / updateNode / moveNode / replaceNode / setVariables / removeNode 均不抛 DataCloneError', () => {
    const dsl = refDsl()

    const withNode = addNode(dsl.value, approvalNode as never, { x: 10, y: 20 })
    expect(isReactive(withNode)).toBe(false)
    expect(withNode.nodes.some((n) => n.id === 'a1')).toBe(true)

    // 每一步都把结果重新包成响应式，模拟 Vue 层真实调用形态
    const reactive = ref(withNode)
    expect(() =>
      updateNode<FlowDsl['nodes'][number]>(reactive.value, 'a1', { name: '技术评审' } as never),
    ).not.toThrow()

    const renamed = ref(
      updateNode<FlowDsl['nodes'][number]>(reactive.value, 'a1', { name: '技术评审' } as never),
    )
    const moved = moveNode(renamed.value, 'a1', { x: 88, y: 99 })
    expect(moved.layout.nodes.a1).toMatchObject({ x: 88, y: 99 })

    const replaced = replaceNode(moved, 'a1', { ...approvalNode, name: '整对象替换' } as never)
    expect(replaced.nodes.find((n) => n.id === 'a1')?.name).toBe('整对象替换')

    const withVars = setVariables(replaced, [{ name: 'amount', type: 'NUMBER' } as never])
    expect(withVars.variables).toHaveLength(1)

    const removedNode = removeNode(withVars, 'a1')
    expect(removedNode.nodes.some((n) => n.id === 'a1')).toBe(false)
  })

  it('addEdge / removeEdge 不因响应式输入抛 DataCloneError', () => {
    const dsl = refDsl()
    const withNode = addNode(dsl.value, approvalNode as never, { x: 0, y: 0 })
    const reactive = ref(withNode)

    // 是否被 canConnect 拒绝由业务规则决定，本测试只锁定「不是克隆失败」
    expectNoCloneFailure(() => addEdge(reactive.value, 'start_1', 'a1'))
    expectNoCloneFailure(() =>
      addEdge(reactive.value, 'start_1', 'a1', { kind: EdgeKind.CONDITION, name: '提交' } as never),
    )
    expect(() => removeEdge(reactive.value, reactive.value.edges[0].id)).not.toThrow()
  })

  it('immutability 仍成立：ops 不改动传入的响应式 DSL', () => {
    const dsl = refDsl()
    const before = JSON.stringify(dsl.value)
    addNode(dsl.value, approvalNode as never, { x: 0, y: 0 })
    expect(JSON.stringify(dsl.value)).toBe(before)
  })

  it('validateDsl 可直接读响应式 DSL', () => {
    const dsl = refDsl()
    expect(() => validateDsl(dsl.value)).not.toThrow()
    expect(validateDsl(dsl.value).ok).toBe(true)
  })
})

describe('DslHistory 必须接受 Vue 的响应式 DSL', () => {
  it('reset / push / undo / redo 不抛异常（reset 在 setup 中即被调用）', () => {
    const dsl = refDsl()
    const history = new DslHistory()

    expect(() => history.reset(dsl.value)).not.toThrow()
    expect(history.canUndo).toBe(false)

    const v2 = addNode(dsl.value, approvalNode as never, { x: 0, y: 0 })
    expect(() => history.push(ref(v2).value)).not.toThrow()
    expect(history.canUndo).toBe(true)

    expect(history.undo()?.nodes.some((n) => n.id === 'a1')).toBe(false)
    expect(history.redo()?.nodes.some((n) => n.id === 'a1')).toBe(true)
  })

  it('快照与实时 DSL 隔离：改实时对象不会污染历史', () => {
    const dsl = refDsl()
    const history = new DslHistory()
    history.reset(dsl.value)

    // 模拟后续编辑（含直接改动）——若快照持有响应式引用，历史会被一起改掉
    dsl.value.meta.name = '后续被改'
    dsl.value.nodes.push({ id: 'injected', type: NodeType.END, name: '注入' } as never)

    const snapshot = history.current()
    expect(snapshot?.meta.name).toBe('响应式测试')
    expect(snapshot?.nodes.some((n) => n.id === 'injected')).toBe(false)
  })

  it('历史项本身不是响应式对象', () => {
    const dsl = refDsl()
    const history = new DslHistory()
    history.reset(dsl.value)
    expect(isReactive(history.current())).toBe(false)
  })
})

describe('响应式输入下的完整编辑序列（模拟用户操作 → 撤销 → 重做）', () => {
  it('拖入节点 → 改名 → 移动 → 撤销逐级回退 → 重做逐级前进', () => {
    const dsl = refDsl()
    const history = new DslHistory()
    history.reset(dsl.value)

    // 1 拖入节点
    const step1 = addNode(dsl.value, approvalNode as never, { x: 120, y: 160 })
    history.push(step1)
    expect(step1.nodes.find((n) => n.id === 'a1')?.name).toBe('审批')

    // 2 改名（把上一步结果包成响应式再喂进去）
    const step2 = replaceNode(ref(step1).value, 'a1', { ...approvalNode, name: '技术评审' } as never)
    history.push(step2)
    expect(step2.nodes.find((n) => n.id === 'a1')?.name).toBe('技术评审')

    // 3 移动
    const step3 = moveNode(ref(step2).value, 'a1', { x: 300, y: 400 })
    history.push(step3)
    expect(step3.layout.nodes.a1).toMatchObject({ x: 300, y: 400 })

    // 4 撤销：移动 → 改名 → 节点（逐级回退）
    expect(history.undo()?.layout.nodes.a1).toMatchObject({ x: 120, y: 160 })
    expect(history.undo()?.nodes.find((n) => n.id === 'a1')?.name).toBe('审批')
    expect(history.undo()?.nodes.some((n) => n.id === 'a1')).toBe(false)

    // 5 重做：回到「已改名」
    expect(history.redo()?.nodes.find((n) => n.id === 'a1')?.name).toBe('审批')
    expect(history.redo()?.nodes.find((n) => n.id === 'a1')?.name).toBe('技术评审')
  })
})
