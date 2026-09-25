/**
 * `@flow-compiler` —— CK-PLM 流程 DSL ⇄ BPMN 2.0 双向编译层
 *
 * <p>分层见 docs/ck-plm-flow-designer-spec.md §2.2。上层（canvas / app）只从本文件引入。
 * 编译层只依赖 `@flow-dsl-core` 与 BPMN 语义，不依赖 DOM / X6 / Vue。
 */

export * from './namespaces'
export * from './compile'
export * from './parse'
