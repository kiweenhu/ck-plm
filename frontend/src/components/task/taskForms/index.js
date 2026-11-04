/**
 * 任务表单模板的**运行期视图注册表**。
 *
 * <p>与设计期那份 `dsl-core/forms.ts`（`TASK_FORM_TEMPLATES`）是同一契约的两端：
 * <ul>
 *   <li><b>设计期</b>：决定"节点面板里能选到什么模板、编译成什么 formKey"；</li>
 *   <li><b>运行期</b>（本文件）：决定"formKey 由哪个 Vue 组件渲染"。</li>
 * </ul>
 *
 * <p>解析顺序（{@link resolveTaskFormView}）：
 * <pre>
 *   ① 语义固定表单 / 部署时写的 formKey（effectiveFormCode）
 *   ② 内置视图表 TASK_FORM_VIEWS —— 按 code 命中（平台三张，code 即渲染契约）
 *   ③ 组件名视图表 FORM_COMPONENT_VIEWS —— 按【模板声明的 component 名】命中（行业表单）
 *   ④ 兜底 GenericApproveForm（同意 / 驳回 + 意见）
 * </pre>
 *
 * <p>解析不到不等于办理不了：模板找不到时也必须能办完一件事，所以 ④ 永远兜得住。
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
import ComponentQualificationForm from './ComponentQualificationForm.vue'
import ComponentQualificationReviewForm from './ComponentQualificationReviewForm.vue'
import ComponentQualificationCountersignForm from './ComponentQualificationCountersignForm.vue'
import TechnicalReviewApplyForm from './TechnicalReviewApplyForm.vue'
import TechnicalReviewChecklistForm from './TechnicalReviewChecklistForm.vue'
import TechnicalReviewIssueForm from './TechnicalReviewIssueForm.vue'
import TechnicalReviewFixForm from './TechnicalReviewFixForm.vue'
import TechnicalReviewReverifyForm from './TechnicalReviewReverifyForm.vue'
import TechnicalReviewVerdictForm from './TechnicalReviewVerdictForm.vue'
import GateDecisionApplyForm from './GateDecisionApplyForm.vue'
import GateDecisionMaterialForm from './GateDecisionMaterialForm.vue'
import GateDecisionMeetingForm from './GateDecisionMeetingForm.vue'
import GateDecisionRecordForm from './GateDecisionRecordForm.vue'
import EcrImpactCountersignForm from './EcrImpactCountersignForm.vue'
import EcrCcbDecisionForm from './EcrCcbDecisionForm.vue'
import EcnReviewForm from './EcnReviewForm.vue'
import EcnCountersignForm from './EcnCountersignForm.vue'
import GenericApproveForm from './GenericApproveForm.vue'

/** 内置模板：按 **code** 派发视图（code 就是渲染契约，随 platform-base 包装入每个租户） */
export const TASK_FORM_VIEWS = [
  { code: SETUP_ASSIGNEE_FORM_CODE, component: SetupAssigneeForm },
  { code: APPROVAL_OPINION_FORM_CODE, component: ApprovalOpinionForm },
  { code: COUNTERSIGN_FORM_CODE, component: CountersignForm },
]

/**
 * 行业表单：按**模板声明的 component 名**派发视图（2026-10-12 起的新范式）。
 *
 * <p>为什么按名而不是按 code：<b>表单组件固化在系统里</b>，行业包在 `forms.json` 上
 * 只做两件事 —— 开启（enabled）+ 标注 component 名。该名随 `ck_process_form_template`
 * 落库、经 `registerTaskFormTemplates` 注入 dsl-core 注册表；行业换一张表单的渲染器，
 * 改自己那份 JSON 即可，不必改前端代码。没登记（或组件尚未实现）的名字一律落到兜底表单。
 */
export const FORM_COMPONENT_VIEWS = {
  ComponentQualificationForm,
  ComponentQualificationReviewForm,
  ComponentQualificationCountersignForm,
  TechnicalReviewApplyForm,
  TechnicalReviewChecklistForm,
  TechnicalReviewIssueForm,
  TechnicalReviewFixForm,
  TechnicalReviewReverifyForm,
  TechnicalReviewVerdictForm,
  GateDecisionApplyForm,
  GateDecisionMaterialForm,
  GateDecisionMeetingForm,
  GateDecisionRecordForm,
  EcrImpactCountersignForm,
  EcrCcbDecisionForm,
  EcnReviewForm,
  EcnCountersignForm,
}

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

/**
 * 按模板声明的 component 名找视图（查不到返回 undefined）。
 *
 * <p>读的是**行业模板自己声明的渲染器名**（后端表注入到 dsl-core 注册表的那份）；
 * 前端内置注册表优先那套只针对同 code 的内置模板，与这里不冲突。
 */
function viewByComponentName(formCode) {
  const declared = findFormTemplate(formCode)?.component
  return declared ? FORM_COMPONENT_VIEWS[declared] : undefined
}

/** 表单上下文 → 视图组件；没有专属模板 → 兜底模板 */
export function resolveTaskFormView(form) {
  const code = effectiveFormCode(form)
  const view = TASK_FORM_VIEWS.find((item) => item.code === code)?.component
    || viewByComponentName(code)
  return view || FALLBACK_FORM_VIEW
}

/**
 * 能在「发起流程」时填写的模板（按**组件名**登记，与 {@link FORM_COMPONENT_VIEWS} 同一口径）。
 *
 * <p>判定标准只有一条：<b>不依赖任务上下文</b>。发起时还没有任务实例，模板组件拿不到
 * {@code taskId} —— 「元器件认证申请」「元器件认证审批」「元器件认证会签」都靠
 * {@code getTaskContext(taskId)} 带出关联器件与流程变量，在发起页只能是空白。
 *
 * <p><b>新增模板时注意</b>：只有组件内不读 {@code props.taskId} 的才可加进来；
 * 名单刻意放在视图注册表旁 —— 它是"模板能力"，不是某个页面的偏好。
 */
const START_CAPABLE_COMPONENTS = new Set([
  // 阶段门：申请单与决策记录都不依赖关联对象（"本表不依赖上下文"）
  'GateDecisionApplyForm',
  'GateDecisionRecordForm',
  // 元器件认证申请：**双来源**（2026-10-13 用户要求）—— 有任务时读流程关联的 PBS，
  // 发起时读"发起弹框里选中的对象"（发起页通过 form.entities 带进来），
  // 所以它两侧都能用：发起即填认证申请，办理节点仍按实例关联带出器件与 MPN
  'ComponentQualificationForm',
  // 技术评审申请单：**双来源**（2026-10-14 用户要求）—— 待评对象有任务时读流程关联的
  // PBS、发起时读弹框里选中的对象；选择评审模板 / 阶段 / 关联门与送审材料全部手填，
  // 没有任何字段是"只有办理时才拿得到"的。流程侧早已就绪：electronics-core 的
  // technical-review 流程 start_1 的 formRef 就是它（nodeTypes 也声明了 START）
  'TechnicalReviewApplyForm',
])

/**
 * 发起表单视图：开始节点配的表单**能在发起页填**时返回组件，否则返回 {@code null}。
 *
 * <p>返回 null 的三种情形，发起弹框都按"不渲染表单区"处理（其中②③会在界面上说明原因）：
 * <ol>
 *   <li>开始节点没配表单（{@code formKey} 为空）—— 老流程的常态，零影响；</li>
 *   <li>配的是办理类表单（内置三张 / 依赖任务上下文的行业表单）—— 发起时填不了；</li>
 *   <li>模板清单里找不到（未注入或组件未实现）。</li>
 * </ol>
 *
 * <p>为什么不用兜底模板：兜底那张是「同意 / 驳回 + 意见」，是"给这次办理下结论"，
 * 发起场景没有这个语义 —— 没有专属表单就该什么都不显示。
 */
export function resolveStartFormView(form) {
  const code = effectiveFormCode(form)
  if (!code) return null
  const declared = findFormTemplate(code)?.component
  if (!declared || !START_CAPABLE_COMPONENTS.has(declared)) return null
  return FORM_COMPONENT_VIEWS[declared] || null
}

/**
 * 是否命中专属模板（页面据此决定卡片标题、以及"是否属兜底"的判断）。
 *
 * <p>内置按 code 命中；行业表单按声明的 component 名命中 —— 两边都算"有专属模板"，
 * 否则标题会停在兜底文案「审批处理」上（2026-10-12 实际踩过一次）。
 */
export function hasOwnTaskForm(formCode) {
  return TASK_FORM_VIEWS.some((view) => view.code === formCode) || !!viewByComponentName(formCode)
}

/** 模板显示名（卡片标题用）：注册表里的 label，兜底时给"审批处理" */
export function taskFormLabel(formCode) {
  return findFormTemplate(formCode)?.label || '审批处理'
}
