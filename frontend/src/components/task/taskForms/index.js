/**
 * 任务表单模板的**运行期视图注册表**。
 *
 * <p>与设计期那份 `dsl-core/forms.ts`（`TASK_FORM_TEMPLATES`）是同一契约的两端：
 * <ul>
 *   <li><b>设计期</b>：决定"节点面板里能选到什么模板、编译成什么 formKey"；</li>
 *   <li><b>运行期</b>（本文件）：决定"formKey 由哪个 Vue 组件渲染"。</li>
 * </ul>
 * 两端的 code 必须一致，所以这里<b>不重写字面量</b> —— 直接复用 dsl-core 的常量与查询函数。
 * 这也是为什么它属于"架构"而不只是"多写几个组件"：新增一个内置表单只需在 dsl-core 注册契约、
 * 在这里登记视图，设计器面板与运行期同时生效，不用改任何页面。
 *
 * <p>解析不到（节点没配表单、或配了已下线的 code）→ 走通用审批表单兜底：
 * 办理不能因为"模板找不到"而打不开。
 */

import {
  APPROVAL_OPINION_FORM_CODE,
  COUNTERSIGN_FORM_CODE,
  SETUP_ASSIGNEE_FORM_CODE,
  defaultFormCodeOfNode,
  findFormTemplate,
  fixedFormCodeOfNode,
} from '@flow-dsl-core'
import SetupAssigneeForm from './SetupAssigneeForm.vue'
import ApprovalOpinionForm from './ApprovalOpinionForm.vue'
import CountersignForm from './CountersignForm.vue'
import GenericApproveForm from './GenericApproveForm.vue'

/** 已登记视图的内置表单模板（code 与 dsl-core 契约一致） */
export const TASK_FORM_VIEWS = [
  { code: SETUP_ASSIGNEE_FORM_CODE, component: SetupAssigneeForm },
  { code: APPROVAL_OPINION_FORM_CODE, component: ApprovalOpinionForm },
  { code: COUNTERSIGN_FORM_CODE, component: CountersignForm },
]

/** 兜底模板：没有专属模板时用它（同意 / 驳回 + 意见） */
export const FALLBACK_FORM_VIEW = GenericApproveForm

/**
 * 表单上下文里该节点的 DSL 声明（拿不到返回 null）。
 *
 * <p>老实例可能没有 dslJson（部署时未关联模板版本），此时只能按"无声明"处理，不能当异常。
 */
export function dslNodeOf(form) {
  const raw = form?.dslJson
  if (!raw || !form?.taskDefinitionKey) return null
  try {
    return (JSON.parse(raw)?.nodes || []).find((node) => node.id === form.taskDefinitionKey) || null
  } catch {
    return null
  }
}

/**
 * 该任务**实际生效**的表单 code。三档，与编译期同一套顺序（见 dsl-core 的 forms.ts）：
 *
 * <ol>
 *   <li><b>语义固定的表单</b>（设置审批人 / 会签 / 「审批 + 会签模式」）优先 ——
 *       老模板可能残留一个历史 formKey（当时这个表单还没登记），不该盖过它，
 *       否则会表现为"设计器里写着「会签」，办理页却是通用表单"；</li>
 *   <li>否则用部署时写进 BPMN 的 `formKey`（它已经包含了节点显式指定的表单，
 *       以及未指定时的类型默认）；</li>
 *   <li>老实例没有 formKey 时，按节点类型派生一次默认
 *       （{@link defaultFormCodeOfNode}）—— 模板是在<b>部署那一刻</b>编译的，
 *       此后登记的内置表单不会凭空出现在在跑的实例里，没有这层派生就会
 *       "改了却看不到"（仍旧停在通用表单上）。</li>
 * </ol>
 */
export function effectiveFormCode(form) {
  const node = dslNodeOf(form)
  const fixed = fixedFormCodeOfNode(node)
  if (fixed) return fixed
  return form?.formKey || defaultFormCodeOfNode(node) || ''
}

/** 表单上下文 → 视图组件；没有专属模板 → 兜底模板 */
export function resolveTaskFormView(form) {
  const code = effectiveFormCode(form)
  return TASK_FORM_VIEWS.find((view) => view.code === code)?.component || FALLBACK_FORM_VIEW
}

/** 是否命中专属模板（页面据此决定卡片标题、以及"是否属兜底"的判断） */
export function hasOwnTaskForm(formCode) {
  return TASK_FORM_VIEWS.some((view) => view.code === formCode)
}

/** 模板显示名（卡片标题用）：注册表里的 label，兜底时给"审批处理" */
export function taskFormLabel(formCode) {
  return findFormTemplate(formCode)?.label || '审批处理'
}
