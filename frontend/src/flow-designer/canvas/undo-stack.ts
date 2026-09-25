/**
 * CK-PLM 流程设计器 · 撤销/重做（DSL 快照栈）
 *
 * <p>规格 §2.3 明确：<b>撤销/重做 = DSL 快照栈</b>，而不是 X6 的图变更历史。
 * 理由：事实源是 DSL，只有对 DSL 做快照，撤销才与「业务语义」对齐
 * —— 例如「把会签比例从 80% 改成 60%」应作为一个可撤销步骤，
 * 而画布底层的多次坐标/属性微调不该各自成为一步。
 *
 * <p>纯数据结构，零 DOM 依赖，可直接单测。
 */

import type { FlowDsl } from '@flow-dsl-core'

export class DslHistory {
  private stack: FlowDsl[] = []
  private index = -1
  private readonly limit: number

  constructor(limit = 50) {
    this.limit = Math.max(2, limit)
  }

  get canUndo(): boolean {
    return this.index > 0
  }

  get canRedo(): boolean {
    return this.index >= 0 && this.index < this.stack.length - 1
  }

  get size(): number {
    return this.stack.length
  }

  /** 当前快照（无历史时为 null） */
  current(): FlowDsl | null {
    return this.index >= 0 ? this.stack[this.index] : null
  }

  /** 重置（加载模板、切换模板时调用），不产生可撤销步骤 */
  reset(dsl: FlowDsl | null): void {
    this.stack = dsl ? [clone(dsl)] : []
    this.index = dsl ? 0 : -1
  }

  /**
   * 记录一个新状态。
   *
   * <p>在 redo 分支上继续编辑时，丢弃 redo 尾部 —— 与编辑器惯例一致。
   * 超出上限时丢弃最老的一步。
   */
  push(dsl: FlowDsl): void {
    const snapshot = clone(dsl)
    const current = this.stack[this.index]
    // 与当前快照完全相同则不入栈。
    // 原因：画布重绘会触发 node:moved，若每次都记一步，撤销栈里会塞满
    // 「按了没反应」的空步骤（Ctrl+Z 看起来失效）。
    if (current && sameDsl(current, snapshot)) {
      return
    }
    if (this.index < this.stack.length - 1) {
      this.stack = this.stack.slice(0, this.index + 1)
    }
    this.stack.push(snapshot)
    if (this.stack.length > this.limit) {
      this.stack.shift()
    }
    this.index = this.stack.length - 1
  }

  /** 撤销：返回上一步的 DSL（无可撤销时返回 null） */
  undo(): FlowDsl | null {
    if (!this.canUndo) {
      return null
    }
    this.index -= 1
    return clone(this.stack[this.index])
  }

  /** 重做：返回下一步的 DSL（无可重做时返回 null） */
  redo(): FlowDsl | null {
    if (!this.canRedo) {
      return null
    }
    this.index += 1
    return clone(this.stack[this.index])
  }
}

/**
 * 快照克隆：纯数据、脱离响应式。
 *
 * <p>不能用 `structuredClone`：入参可能是 Vue 的响应式 Proxy（`ref` 深包装），
 * 克隆 Proxy 会抛 `DataCloneError` 并打断组件 setup → 整页白屏。
 * 同理，快照也不能持有响应式引用，否则历史项会与实时 DSL 共享底层对象。
 * 详见 `dsl-core` 中 `cloneDsl` 的说明。
 */
function clone(dsl: FlowDsl): FlowDsl {
  return JSON.parse(JSON.stringify(dsl)) as FlowDsl
}

/**
 * 两份 DSL 内容是否等价。
 *
 * <p>DSL 是纯 JSON 结构，直接比序列化结果即可。用于「无变化就不入状态/不入栈」的判定 ——
 * 这是斩断渲染回环的关键一环（详见 `__tests__/render-stability.test.ts`）。
 */
export function sameDsl(a: FlowDsl, b: FlowDsl): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}
