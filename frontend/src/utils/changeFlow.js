/**
 * 变更管理状态机（前端镜像）
 *
 * 与后端 `cn.ck.plm.change.flow.*Flow` 同一口径：状态 / 动作 / 流转规则必须同步修改
 * （后端才是真闸门：非法流转返 400、越权返 403）。前端只用它决定
 * "当前状态显示哪些按钮、要不要填说明"。
 */

// ==================== 变更请求（ECR）====================

// SUBMITTED 仅存量：早期"提交 / 开始评审"两步合并为「发起评审」后，新单据不再产生该状态
export const CHANGE_REQUEST_STATUSES = ['DRAFT', 'SUBMITTED', 'ANALYZING', 'REVIEWING', 'ACCEPTED', 'REJECTED', 'CLOSED', 'CANCELLED']

export const CHANGE_REQUEST_STATUS_LABEL = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  ANALYZING: '影响分析中',
  REVIEWING: '评审中',
  ACCEPTED: '已受理',
  REJECTED: '已否决',
  CLOSED: '已关闭',
  CANCELLED: '已取消'
}

export const CHANGE_REQUEST_STATUS_COLOR = {
  DRAFT: 'default',
  SUBMITTED: 'blue',
  ANALYZING: 'gold',
  REVIEWING: 'orange',
  ACCEPTED: 'cyan',
  REJECTED: 'red',
  CLOSED: 'green',
  CANCELLED: 'default'
}

export const CHANGE_REQUEST_ACTION_LABEL = {
  CREATE: '提出',
  UPDATE: '更新',
  ANALYZE: '开始影响分析',
  SUBMIT: '发起评审',
  ACCEPT: '受理',
  REJECT: '否决',
  REOPEN: '重新起草',
  CLOSE: '关闭',
  CANCEL: '取消'
}

const REQUEST_TRANSITIONS = {
  // 第②段影响分析：草稿 → 影响分析中（可跳过，直接从草稿发起评审）
  ANALYZE: { DRAFT: 'ANALYZING' },
  // 发起评审一步到位；SUBMITTED 是存量状态、ANALYZING 是评估完的单，都一键收口到评审中
  SUBMIT: { DRAFT: 'REVIEWING', SUBMITTED: 'REVIEWING', ANALYZING: 'REVIEWING' },
  ACCEPT: { REVIEWING: 'ACCEPTED' },
  REJECT: { REVIEWING: 'REJECTED' },
  REOPEN: { REJECTED: 'DRAFT' },
  CLOSE: { ACCEPTED: 'CLOSED' },
  CANCEL: { DRAFT: 'CANCELLED', SUBMITTED: 'CANCELLED', ANALYZING: 'CANCELLED', REVIEWING: 'CANCELLED', REJECTED: 'CANCELLED' }
}

export function availableRequestActions(status) {
  return Object.keys(REQUEST_TRANSITIONS).filter((action) => REQUEST_TRANSITIONS[action][status])
}

/** 行内主推进动作（每状态最多一个；无则 null） */
export function primaryRequestAction(status) {
  for (const action of ['SUBMIT', 'ACCEPT', 'CLOSE']) {
    if (REQUEST_TRANSITIONS[action][status]) return action
  }
  return null
}

export function requestRequiresComment(action) {
  return action === 'REJECT' || action === 'REOPEN' || action === 'CANCEL'
}

// ==================== 变更通告（ECN）====================

export const CHANGE_NOTICE_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'APPROVING',
  'APPROVED',
  'IN_WORK',
  'COMPLETED',
  'CLOSED',
  'REJECTED',
  'CANCELLED'
]

export const CHANGE_NOTICE_STATUS_LABEL = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVING: '审批中',
  APPROVED: '已批准',
  IN_WORK: '实施中',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  REJECTED: '已驳回',
  CANCELLED: '已取消'
}

export const CHANGE_NOTICE_STATUS_COLOR = {
  DRAFT: 'default',
  SUBMITTED: 'blue',
  APPROVING: 'orange',
  APPROVED: 'cyan',
  IN_WORK: 'processing',
  COMPLETED: 'green',
  CLOSED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default'
}

export const CHANGE_NOTICE_ACTION_LABEL = {
  CREATE: '新建',
  UPDATE: '更新',
  ADD_AFFECTED: '添加受影响对象',
  REMOVE_AFFECTED: '移除受影响对象',
  SUBMIT: '提交',
  START_APPROVAL: '发起审批',
  APPROVE: '审批通过',
  REJECT: '审批驳回',
  REVISE: '修改后重新提交',
  START_WORK: '开始实施',
  COMPLETE: '实施完成',
  CLOSE: '关闭',
  CANCEL: '取消',
  // 管理员直接拨状态（运维纠错）：处理记录里要认得出这是人为干预，不是正常流转来的
  ADMIN_SET_STATUS: '管理员设置状态'
}

/**
 * 动作文案（只对随租户配置变的那一个做动态化）：`SUBMIT` 在"需要审批"时是
 * **提交审批** —— 后端这一下就直接拉起审批流程，不再是先停在「已提交」再点「发起审批」；
 * 免审时仍是「提交」（提交即通过）。
 *
 * <p>时间线等历史文案继续用静态的 {@link CHANGE_NOTICE_ACTION_LABEL} ——
 * 历史记录不该因为后来改了配置而改说法。
 */
export function noticeActionLabel(action, approvalRequired) {
  if (action === 'SUBMIT') return approvalRequired ? '提交审批' : '提交'
  return CHANGE_NOTICE_ACTION_LABEL[action] || action
}

const NOTICE_TRANSITIONS = {
  SUBMIT: { DRAFT: 'SUBMITTED' },
  START_APPROVAL: { SUBMITTED: 'APPROVING' },
  APPROVE: { APPROVING: 'APPROVED' },
  REJECT: { APPROVING: 'REJECTED' },
  REVISE: { REJECTED: 'DRAFT' },
  START_WORK: { APPROVED: 'IN_WORK' },
  COMPLETE: { IN_WORK: 'COMPLETED' },
  CLOSE: { COMPLETED: 'CLOSED' },
  CANCEL: {
    DRAFT: 'CANCELLED',
    SUBMITTED: 'CANCELLED',
    APPROVING: 'CANCELLED',
    APPROVED: 'CANCELLED',
    IN_WORK: 'CANCELLED'
  }
}

/**
 * 审批结论由流程引擎驱动：审批人在「任务中心」办理，流程结束时后端回调回写状态。
 * 单据上再放「审批通过 / 审批驳回」按钮等于开了第二条写状态的路径 ——
 * 两边不同步时会出现"任务中心点了同意、单据还停在审批中"。
 */
const NOTICE_ACTIONS_DRIVEN_BY_PROCESS = ['APPROVE', 'REJECT']

/**
 * 可用动作 = 状态机里当前状态能去的动作。
 *
 * <p>2026-10-21 起 `START_APPROVAL` 不再屏蔽：后端已接流程引擎（拉起 change-notice，
 * 审批结束由 ProcessOutcomeHandler 回写状态）。原先屏蔽是因为后端会明确拒绝，
 * 现在点了是真的能发起。
 */
export function availableNoticeActions(status) {
  const actions = Object.keys(NOTICE_TRANSITIONS).filter((action) => NOTICE_TRANSITIONS[action][status])
  if (status === 'APPROVING') {
    return actions.filter((action) => !NOTICE_ACTIONS_DRIVEN_BY_PROCESS.includes(action))
  }
  return actions
}

export function primaryNoticeAction(status) {
  // SUBMITTED 的主动作是「发起审批」—— 不排进去的话，这个状态会没有任何主按钮
  for (const action of ['SUBMIT', 'START_APPROVAL', 'START_WORK', 'COMPLETE', 'CLOSE']) {
    if (NOTICE_TRANSITIONS[action][status]) return action
  }
  return null
}

export function noticeRequiresComment(action) {
  return action === 'REJECT' || action === 'REVISE' || action === 'CANCEL'
}

/**
 * 已闭环的通告状态 —— 来源变更请求（ECR）能关闭的前提。
 *
 * <p>与后端 `ChangeNoticeMapper#selectOpenByRequest` 的 `status NOT IN ('CLOSED','CANCELLED')`
 * 同一口径：通告还在审批中 / 实施中 / 已完成，都算"没关完"，父单先关就再也看不出
 * 这次变更到底做完没有。前端据此把「关闭」置灰并说明还差哪几张。
 */
export const NOTICE_CLOSED_STATUSES = ['CLOSED', 'CANCELLED']

/** 该状态的通告是否已经闭环（已关闭 / 已取消） */
export function noticeClosed(status) {
  return NOTICE_CLOSED_STATUSES.includes(status)
}

/** 该状态的受控编辑（仅草稿/已驳回） */
export function noticeEditable(status) {
  return status === 'DRAFT' || status === 'REJECTED'
}

/** 该状态能否增删受影响对象 */
export function noticeAffectedEditable(status) {
  return status === 'DRAFT' || status === 'REJECTED'
}

// ==================== 变更任务（ECT）====================

export const CHANGE_TASK_STATUS_LABEL = {
  DRAFT: '待生效',
  OPEN: '待开始',
  IN_WORK: '进行中',
  SUBMITTED: '待验收',
  COMPLETED: '已完成'
}

export const CHANGE_TASK_STATUS_COLOR = {
  DRAFT: 'default',
  OPEN: 'default',
  IN_WORK: 'processing',
  SUBMITTED: 'orange',
  COMPLETED: 'green'
}

/**
 * 任务动作文案。
 *
 * <p>没有 {@code DISPATCH}（派发）——2026-11-03 用户口径「不需要派发操作」：
 * 审批通过时后端会把这批草稿任务自动置为 OPEN（{@code ChangeTaskService#dispatchByNotice}），
 * 人工再点一次「派发」纯属重复劳动；审批通过后再新建的任务本来就是 OPEN，也用不上它。
 * 于是 DRAFT 在界面上就是「等通告审批通过，然后自动生效」，没有可点的动作。
 */
export const CHANGE_TASK_ACTION_LABEL = {
  CREATE: '新建',
  UPDATE: '更新',
  START: '开始处理',
  SUBMIT: '提交验收',
  PASS: '验收通过',
  RETURN: '验收退回'
}

const TASK_TRANSITIONS = {
  START: { OPEN: 'IN_WORK' },
  SUBMIT: { IN_WORK: 'SUBMITTED' },
  PASS: { SUBMITTED: 'COMPLETED' },
  RETURN: { SUBMITTED: 'IN_WORK' }
}

export function availableTaskActions(status) {
  return Object.keys(TASK_TRANSITIONS).filter((action) => TASK_TRANSITIONS[action][status])
}

/** 处理人自己的活：只能责任人本人点（与后端 ChangeTaskFlow.requiresOwner 同口径） */
export function taskRequiresOwner(action) {
  return action === 'START' || action === 'SUBMIT'
}

export function taskRequiresComment(action) {
  return action === 'RETURN'
}

// ==================== 字典 ====================

export const CHANGE_TYPE_LABEL = {
  DESIGN: '设计变更',
  DOCUMENT: '文档变更',
  BOM: '结构变更',
  PROCESS: '工艺变更',
  MATERIAL: '物料变更'
}

/**
 * 变更分级 —— 不只是标签：评审流程按它分流审签路径。
 *
 * <p>快速 = 单人审批；标准 = 影响分析会签 + 产品经理审批；重大 = 影响分析会签 + CCB 会签。
 * 因此一旦发起评审就锁死（后端 update 会拒绝改）。
 */
export const CHANGE_LEVEL_LABEL = { QUICK: '快速', STANDARD: '标准', MAJOR: '重大' }
export const CHANGE_LEVEL_COLOR = { QUICK: 'default', STANDARD: 'blue', MAJOR: 'red' }
export const CHANGE_LEVEL_OPTIONS = Object.entries(CHANGE_LEVEL_LABEL).map(([value, label]) => ({ value, label }))

/** 影响分析三项：空 = 未评估（不默认成"无影响"，否则等于伪造结论） */
export const CERT_IMPACT_LABEL = { NONE: '无', REVIEW: '需评估/重测', YES: '需重新认证' }
export const CUSTOMER_IMPACT_LABEL = { NONE: '无', NOTIFY: '需知会', APPROVAL: '需客户批准' }
export const INVENTORY_IMPACT_LABEL = { NONE: '无', PENDING: '待在制盘点', DISPOSAL: '需报废返工' }

/**
 * 影响分析会签给出的「影响程度」。
 *
 * <p>会签人按专业写入流程变量 {@code impactAssessment__<taskId>}，
 * CCB 决策会签要把这些结论汇总起来看（见 EcrCcbDecisionForm）——
 * 两处共用同一套词表，避免同一个等级在两张表里叫法不同。
 */
export const ASSESSMENT_LEVEL_LABEL = {
  NONE: '无影响',
  MINOR: '需关注',
  MODERATE: '需评估',
  MAJOR: '重大影响'
}
export const ASSESSMENT_LEVEL_COLOR = { NONE: 'green', MINOR: 'blue', MODERATE: 'orange', MAJOR: 'red' }

export const PRIORITY_LABEL = { HIGH: '高', MEDIUM: '中', LOW: '低' }
export const PRIORITY_COLOR = { HIGH: 'red', MEDIUM: 'orange', LOW: 'default' }

/** 受影响对象类型（与 CheckoutProvider / ReviseProvider 同口径） */
export const ENTITY_TYPE_LABEL = {
  PART: '零组件',
  DOCUMENT: '文档',
  ENG_DOCUMENT: '工程文档'
}

export const TASK_TYPE_LABEL = {
  DESIGN: '设计',
  DOCUMENT: '文档',
  BOM: '结构',
  PROCESS: '工艺',
  VERIFY: '验证'
}

export const CHANGE_TYPE_OPTIONS = Object.entries(CHANGE_TYPE_LABEL).map(([value, label]) => ({ value, label }))
export const PRIORITY_OPTIONS = Object.entries(PRIORITY_LABEL).map(([value, label]) => ({ value, label }))
export const ENTITY_TYPE_OPTIONS = Object.entries(ENTITY_TYPE_LABEL).map(([value, label]) => ({ value, label }))
export const TASK_TYPE_OPTIONS = Object.entries(TASK_TYPE_LABEL).map(([value, label]) => ({ value, label }))
