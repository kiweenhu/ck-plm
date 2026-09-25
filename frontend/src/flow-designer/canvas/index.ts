/**
 * `@flow-canvas` —— CK-PLM 流程设计器画布层（AntV X6 封装）
 *
 * <p>分层见 docs/ck-plm-flow-designer-spec.md §2.2。
 *
 * <pre>
 * node-style.ts      节点视觉规格（外观 / 角标三态 / 端口）—— 纯数据
 * render.ts          DSL → 图元映射 —— 纯函数，可单测
 * undo-stack.ts      DSL 快照栈（撤销/重做）—— 纯数据结构，可单测
 * graph.ts           X6 图工厂与整体重绘（唯一依赖 DOM 的文件）
 * useFlowDesigner.ts 状态与动作（Vue 组合式）
 * FlowCanvas.vue     画布组件
 * </pre>
 */

export * from './node-style'
export * from './render'
export * from './undo-stack'
export * from './graph'
export * from './useFlowDesigner'
