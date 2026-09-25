/**
 * 流程表单模板的取数与注入（设计器启动时一次）。
 *
 * <p>为什么要有这一层：节点的「节点表单」下拉是**同步**渲染的（`optionsFromDsl` 拿不到
 * 异步结果），而企业自定义表单存在后端 `ck_process_form_template` 里 —— 于是像系统通知渠道那样，
 * 启动时拉一次、注入契约层的注册表（`dsl-core/forms.ts` 的 `registerTaskFormTemplates`），
 * 面板按需同步读取。取不到就只用前端内置注册表，不阻断设计器。
 *
 * <p>同 code 以**前端内置注册表**为准（见 `registerTaskFormTemplates` 的说明）：
 * 内置模板的渲染组件是前端契约，后端那份只多了"业务配置里的展示与启用状态"。
 */
import { listFormTemplates } from '@/api'
import {
  registerTaskFormTemplates,
  type NodeType as NodeTypeT,
  type TaskFormTemplate,
} from '@flow-dsl-core'

/** 后端返回的模板行（字段与 ck_process_form_template 一一对应） */
export interface FormTemplateRow {
  code?: string
  name?: string
  description?: string
  /** 逗号分隔的节点类型：一种类型可以挂多张模板 */
  nodeTypes?: string
  /** 前端渲染器 key（内置模板有，自定义为空） */
  component?: string
  builtin?: boolean
}

/** 逗号分隔的节点类型 → 数组 */
export function formTemplateNodeTypes(nodeTypes?: string): string[] {
  return String(nodeTypes || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

/** 后端模板行 → 契约层的模板对象 */
export function toTaskFormTemplate(row: FormTemplateRow): TaskFormTemplate {
  return {
    code: String(row.code || ''),
    label: String(row.name || row.code || ''),
    description: String(row.description || ''),
    builtin: !!row.builtin,
    // 后端登记的模板一律可替换：语义固定与否由前端注册表判定（fixedFormCodeOf），
    // 那是"运行期怎么渲染"的契约，不该由配置数据决定
    fixed: false,
    component: row.component ? String(row.component) : undefined,
    nodeTypes: formTemplateNodeTypes(row.nodeTypes) as NodeTypeT[],
  }
}

/** 拉取后端表单模板并注入注册表（失败静默：设计器退回内置清单） */
export async function loadFormTemplates(): Promise<void> {
  try {
    // api/index.js 是 JS，axios 的返回类型在这里推不出来 —— 按统一包装的形状声明一次
    const res = (await listFormTemplates()) as unknown as {
      code?: number
      data?: FormTemplateRow[]
    }
    if (res?.code === 200) {
      registerTaskFormTemplates((res.data || []).map(toTaskFormTemplate))
    }
  } catch {
    // 后端不可用时设计器仍要能用：节点表单下拉退回内置模板
    registerTaskFormTemplates([])
  }
}
