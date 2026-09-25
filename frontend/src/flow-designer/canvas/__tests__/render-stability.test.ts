/**
 * 渲染稳定性回归测试。
 *
 * <p><b>背景</b>：画布「持续闪烁」曾是一个真实事故。根因是渲染回环 ——
 * 画布重绘会把节点重新 setPosition，触发 `node:change:position`；
 * 该事件无条件回写 DSL（即便坐标没变也产出新对象），于是
 * 「重绘 → 位置事件 → 改 DSL → 重绘」无限循环；同时每次重绘都清空选中，
 * 属性面板被打回「模板」态。
 *
 * <p>断环的关键是让这条链路上的每一环都**幂等**：
 * 位置没变不写 DSL（本文件）、快照相同不入撤销栈（本文件）、
 * 画布就地增量更新而非销毁重建（`graph.ts`，需 DOM 无法在此覆盖）。
 */

import { describe, expect, it } from 'vitest'
import { NodeType, addNode, createEmptyDsl, type FlowDsl } from '@flow-dsl-core'
import { DslHistory } from '../undo-stack'
import { useFlowDesigner } from '../useFlowDesigner'

const blank = (key = 'stable_flow'): FlowDsl => createEmptyDsl({ key, name: '稳定性测试' })

const extraEndNode = {
  id: 'a1',
  type: NodeType.END,
  name: '另一个结束',
}

describe('DslHistory 去重：不留「按了没反应」的空步骤', () => {
  it('内容相同不入栈', () => {
    const history = new DslHistory()
    history.reset(blank())
    expect(history.size).toBe(1)

    history.push(blank())
    expect(history.size).toBe(1)
    expect(history.canUndo).toBe(false)
  })

  it('内容变化才入栈', () => {
    const history = new DslHistory()
    const base = blank()
    history.reset(base)

    history.push(addNode(base, extraEndNode as never, { x: 0, y: 0 }))
    expect(history.size).toBe(2)
    expect(history.canUndo).toBe(true)
  })

  it('连续重复 push 只保留一步', () => {
    const history = new DslHistory()
    const base = blank()
    const changed = addNode(base, extraEndNode as never, { x: 0, y: 0 })
    history.reset(base)
    history.push(changed)
    history.push(changed)
    history.push(changed)
    expect(history.size).toBe(2)
  })
})

describe('moveNode 幂等：斩断「重绘 → 位置事件 → 改 DSL → 重绘」回环', () => {
  it('坐标未变时保持原对象引用（即画布不会重绘）', () => {
    const designer = useFlowDesigner(blank())
    const before = designer.dsl.value
    const layout = before.layout.nodes.start_1
    expect(layout).toBeDefined()

    designer.moveNode('start_1', { x: layout.x, y: layout.y })

    // 引用未变 —— 这是「不触发重绘」的充要信号
    expect(designer.dsl.value).toBe(before)
  })

  it('坐标变化时才产出新 DSL', () => {
    const designer = useFlowDesigner(blank())
    const before = designer.dsl.value

    designer.moveNode('start_1', { x: 123, y: 456 })

    expect(designer.dsl.value).not.toBe(before)
    expect(designer.dsl.value.layout.nodes.start_1).toMatchObject({ x: 123, y: 456 })
  })

  it('反复写入同一坐标始终不再产出新对象（模拟重绘造成的重复触发）', () => {
    const designer = useFlowDesigner(blank())
    designer.moveNode('start_1', { x: 300, y: 300 })
    const settled = designer.dsl.value

    for (let i = 0; i < 20; i += 1) {
      designer.moveNode('start_1', { x: 300, y: 300 })
    }
    expect(designer.dsl.value).toBe(settled)
  })

  it('未知节点 id 不抛异常也不改 DSL', () => {
    const designer = useFlowDesigner(blank())
    const before = designer.dsl.value
    expect(() => designer.moveNode('not_exists', { x: 1, y: 1 })).not.toThrow()
    expect(designer.dsl.value).toBe(before)
  })
})

describe('属性面板编辑不引起额外状态抖动', () => {
  it('整对象替换同内容时也不产生新的 canonical 差异', () => {
    const designer = useFlowDesigner(blank())
    const node = designer.dsl.value.nodes.find((n) => n.id === 'start_1')!
    const before = designer.dsl.value

    // 用完全相同的节点内容替换（模拟面板「改了又改回来」）
    designer.applyNode('start_1', { ...node })

    // 允许产生新对象（面板编辑是显式动作，需要一步可撤销），但内容必须等价
    expect(JSON.stringify(designer.dsl.value.nodes)).toBe(JSON.stringify(before.nodes))
  })

  it('改名后面板编辑产出可撤销的一步', () => {
    const designer = useFlowDesigner(blank())
    const node = designer.dsl.value.nodes.find((n) => n.id === 'start_1')!
    designer.applyNode('start_1', { ...node, name: '流程起点' } as never)

    expect(designer.canUndo.value).toBe(true)
    expect(designer.dsl.value.nodes.find((n) => n.id === 'start_1')?.name).toBe('流程起点')

    designer.undo()
    expect(designer.dsl.value.nodes.find((n) => n.id === 'start_1')?.name).toBe('开始')
  })
})
