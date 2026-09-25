/**
 * 任务表单模板注册表（契约层）。
 *
 * <p><b>为什么需要注册表</b>：节点表单原先是一个手填的「表单引擎 code」文本框 ——
 * 填错一个字符设计期毫无感知，运行期才发现取不到表单；而内置表单（如「设置流程参与者」）
 * 本来就是代码生成的固定表单，让人去"选"它也没有意义。
 *
 * <p><b>两个来源合在一处</b>：
 * <ol>
 *   <li><b>内置模板</b>（本文件 {@link TASK_FORM_TEMPLATES}）：与 Vue 组件一一对应，
 *       是"用哪个组件渲染"的前端契约；同时后端启动器会把同一批 code 登记进
 *       {@code ck_process_form_template} 表（业务配置 → 流程表单里能看到它们）；</li>
 *   <li><b>企业自定义模板</b>（{@link registerTaskFormTemplates} 注入，来自后端表）：
 *       同一节点类型可以挂多张 —— 例如「审批」既可用内置「审批意见」，也可用企业自己的
 *       「技术评审单」。节点用 DSL 的 `formRef` 指到自己要用的那一张。</li>
 * </ol>
 *
 * <p><b>表单从哪来</b>（三档，前两处都能看到同一份清单）：
 * <pre>
 *   节点 DSL formRef（显式指定，优先）
 *     ↓ 没指定时
 *   类型默认（{@link defaultFormCodeOfNode}：审批→审批意见）
 *     ↓ 都没有
 *   运行期兜底（各页面注册的通用表单）
 * </pre>
 *
 * <p>放在契约层（而不是 app 层）是因为三处都要读它：设计器面板、编译器、运行期。
 */

import { ApprovalMode, NodeType, NODE_TYPE_LABEL, type NodeType as NodeTypeT } from './constants'

export interface TaskFormTemplate {
  /** 注册码：写入 DSL 的 `formRef`，并编译为 `flowable:formKey` */
  code: string
  label: string
  description: string
  /** 内置表单：随应用启动自动登记，不可删除 */
  builtin: boolean
  /**
   * 语义固定：活动的含义就写在这张表单里，**不允许被替换**（如「设置审批人」的挑人表单、
   * 会签表单里的通过规则）。
   *
   * <p>与 {@link builtin} 的区别：内置只是"系统自带的表单"，固定是"这段流程语义上必须先有它"。
   * 「审批意见」是内置的，但企业完全可以换成自己的审批表单 —— 它是默认、不是固定。
   */
  fixed?: boolean
  /** 前端渲染器 key（后端登记的内置模板会带；自定义模板为空 → 运行期走兜底表单） */
  component?: string
  /** 只允许挂在哪些节点类型上；缺省＝不限制 */
  nodeTypes?: NodeTypeT[]
}

/** 「设置流程参与者」—— 「设置审批人」活动固定使用的内置表单 */
export const SETUP_ASSIGNEE_FORM_CODE = 'CKPLM_SETUP_ASSIGNEE'

/** 「审批意见」—— 审批活动未指定其它表单时使用的内置默认表单 */
export const APPROVAL_OPINION_FORM_CODE = 'CKPLM_APPROVAL_OPINION'

/** 「会签」—— 会签审批活动固定使用的内置表单 */
export const COUNTERSIGN_FORM_CODE = 'CKPLM_COUNTERSIGN'

/** 已注册的内置表单模板。新增内置表单在此登记一处即可（面板与运行期都按 code 派发） */
export const TASK_FORM_TEMPLATES: TaskFormTemplate[] = [
  {
    code: SETUP_ASSIGNEE_FORM_CODE,
    label: '设置流程参与者',
    description:
      '内置固定表单：列出下游所有审批 / 会签 / 办理活动，由发起人在各自角色成员中挑人',
    builtin: true,
    fixed: true,
    component: SETUP_ASSIGNEE_FORM_CODE,
    nodeTypes: [NodeType.SET_ASSIGNEE],
  },
  {
    code: APPROVAL_OPINION_FORM_CODE,
    label: '审批意见',
    description:
      '内置表单：同意 / 驳回两条路由；驳回时显示退回位置（设计期配置），并要求填写意见。'
      + '审批节点未指定其它表单时默认用它',
    builtin: true,
    // 默认而非固定：企业可以在「业务配置 → 流程表单」里加自己的审批表单，再让节点指过去
    fixed: false,
    component: APPROVAL_OPINION_FORM_CODE,
    nodeTypes: [NodeType.APPROVAL],
  },
  {
    code: COUNTERSIGN_FORM_CODE,
    label: '会签',
    description:
      '内置固定表单：显示会签通过规则（如「1 个通过即通过」）与每个参与人的办理情况'
      + '（谁同意、谁驳回、各自意见），本人再给出自己的结论与意见',
    builtin: true,
    fixed: true,
    component: COUNTERSIGN_FORM_CODE,
    nodeTypes: [NodeType.COUNTERSIGN_APPROVAL],
  },
]

/**
 * 可挂表单的节点类型（业务配置的选择项 + 设计器下拉的过滤依据）。
 *
 * <p>只列"要人动手填东西"的活动：网关 / 服务 / 通知 / 开始结束这几类要么不产生任务，
 * 要么表单由自身语义决定，给它们挂表单没有落点。
 */
export const FORM_NODE_TYPES: NodeTypeT[] = [
  NodeType.START,
  NodeType.APPROVAL,
  NodeType.COUNTERSIGN_APPROVAL,
  NodeType.TASK,
  NodeType.SET_ASSIGNEE,
]

/** 节点类型的中文名（配置页展示用；未知类型回落到 code 本身） */
export function formNodeTypeLabel(nodeType: string): string {
  return (NODE_TYPE_LABEL as Record<string, string>)[nodeType] || nodeType
}

// ==================== 企业自定义模板（后端注入） ====================

let externalTemplates: TaskFormTemplate[] = []

/**
 * 注入后端模板表里的模板（设计器启动时拉一次）。
 *
 * <p>同 code 以**本文件的内置注册表为准**：内置模板的渲染组件是前端契约，
 * 后端那份只多了"业务配置里的展示与启用状态"，不该反过来改前端怎么渲染。
 */
export function registerTaskFormTemplates(list: TaskFormTemplate[] | null | undefined): void {
  externalTemplates = Array.isArray(list) ? list : []
}

/** 内置 + 企业自定义（按 code 去重，内置优先） */
export function allTaskFormTemplates(): TaskFormTemplate[] {
  const builtinCodes = new Set(TASK_FORM_TEMPLATES.map((template) => template.code))
  return [...TASK_FORM_TEMPLATES, ...externalTemplates.filter((item) => !builtinCodes.has(item.code))]
}

/** 某节点类型可选的表单模板（含企业自定义） */
export function formTemplatesFor(nodeType?: NodeTypeT): TaskFormTemplate[] {
  return allTaskFormTemplates().filter(
    (template) => !template.nodeTypes || (nodeType !== undefined && template.nodeTypes.includes(nodeType)),
  )
}

export function findFormTemplate(code?: string): TaskFormTemplate | undefined {
  return code ? allTaskFormTemplates().find((template) => template.code === code) : undefined
}

/**
 * 该节点类型**语义固定**的表单 code（没有固定表单时返回 undefined）。
 *
 * <p>「固定」= 面板预填 + 只读：这类活动的表单由代码生成，不存在"换一个"的余地，
 * 让使用者去选反而可能选错（运行期取不到表单）。
 */
export function fixedFormCodeOf(nodeType: NodeTypeT): string | undefined {
  const template = TASK_FORM_TEMPLATES.find(
    (item) => item.builtin && item.fixed === true && item.nodeTypes?.length === 1
      && item.nodeTypes[0] === nodeType,
  )
  return template?.code
}

/**
 * 按**节点**（不只是类型）判定固定表单。
 *
 * <p>为什么需要它：会签在 DSL 里有两种表达 —— 专门的「会签审批」节点，以及
 * 「审批节点 + 审批模式=会签」（见 {@code ApprovalNode.approvalMode}）。两者运行期是同一件事
 * （多人多票 + 通过规则），表单也就该是同一张；只看类型会让后者落到「审批意见」表单上，
 * 看不到会签规则与其他人的办理情况。
 */
export function fixedFormCodeOfNode(node?: { type?: NodeTypeT; approvalMode?: string } | null): string | undefined {
  if (!node?.type) {
    return undefined
  }
  if (node.type === NodeType.APPROVAL && node.approvalMode === ApprovalMode.COUNTERSIGN) {
    return COUNTERSIGN_FORM_CODE
  }
  return fixedFormCodeOf(node.type)
}

/**
 * 节点**未显式指定** `formRef` 时该用的默认表单。
 *
 * <p>顺序：语义固定的表单 → 类型默认（审批 → 审批意见）→ undefined（运行期兜底）。
 * 编译器用它生成 `flowable:formKey`，面板用它做 `defaultWhenEmpty`：
 * 两处必须同源 —— 否则面板显示的和编译进 BPMN 的会是两张表单。
 */
export function defaultFormCodeOfNode(node?: { type?: NodeTypeT; approvalMode?: string } | null): string | undefined {
  const fixed = fixedFormCodeOfNode(node)
  if (fixed) {
    return fixed
  }
  return node?.type === NodeType.APPROVAL ? APPROVAL_OPINION_FORM_CODE : undefined
}
