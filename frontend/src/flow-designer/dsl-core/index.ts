/**
 * `@flow-dsl-core` —— CK-PLM 流程 DSL 内核（纯函数库，零 DOM 依赖）
 *
 * <p>分层见 docs/ck-plm-flow-designer-spec.md §2.2。对外只暴露本文件，
 * 上层（canvas / compiler / app）一律从 `@flow-dsl-core` 引入，不直接深入子文件。
 */

export * from './constants'
export * from './schema'
export * from './ops'
export * from './validate'
export * from './presets'
export * from './forms'
export * from './builtin-variables'
