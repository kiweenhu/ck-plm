/**
 * 问题闭环状态机（前端镜像）
 *
 * 与后端 `cn.ck.plm.improve.flow.IssueFlow` 同一口径：状态 / 动作 / 流转规则
 * 两边必须同步修改（测试钉住关键行为）。前端用它决定"当前状态给哪些动作按钮、
 * 要不要填说明"——真正的闸门在后端（非法流转返 400）。
 */

/** 状态（按展示顺序） */
export const ISSUE_STATUSES = ['OPEN', 'IN_PROGRESS', 'VERIFYING', 'CLOSED', 'CANCELLED']

export const ISSUE_STATUS_LABEL = {
  OPEN: '待处理',
  IN_PROGRESS: '处理中',
  VERIFYING: '待验证',
  CLOSED: '已关闭',
  CANCELLED: '已取消'
}

export const ISSUE_STATUS_COLOR = {
  OPEN: 'blue',
  IN_PROGRESS: 'orange',
  VERIFYING: 'purple',
  CLOSED: 'green',
  CANCELLED: 'default'
}

/** 严重度（与检查规则、阶段门同一口径） */
export const ISSUE_SEVERITY_LABEL = { BLOCK: '阻塞', WARN: '警告' }
export const ISSUE_SEVERITY_COLOR = { BLOCK: 'red', WARN: 'orange' }

/** 来源 */
export const ISSUE_SOURCE_LABEL = {
  AUDIT: '内部审核',
  CUSTOMER: '客户反馈',
  PRODUCTION: '生产现场',
  TEST: '测试发现',
  OTHER: '其他'
}

/** 动作 */
export const ISSUE_ACTION_LABEL = {
  CREATE: '提出',
  UPDATE: '更新',
  ASSIGN: '指派',
  START: '开始处理',
  SUBMIT_VERIFY: '提交验证',
  VERIFY_PASS: '验证通过',
  VERIFY_FAIL: '验证退回',
  CANCEL: '取消问题',
  REOPEN: '重开问题',
  COMMENT: '备注'
}

/** 按钮风格：primary 主推进 / danger 取消 / 其余灰 */
export const ISSUE_ACTION_KIND = {
  START: 'primary',
  SUBMIT_VERIFY: 'primary',
  VERIFY_PASS: 'primary',
  VERIFY_FAIL: 'danger',
  CANCEL: 'danger',
  REOPEN: 'default'
}

/**
 * 流转规则表（动作 → { 当前状态: 目标状态 }）—— 与后端 IssueFlow.RULES 一致。
 * 设计要点：不许一步关闭（OPEN 不能直接 VERIFY_PASS）；CANCELLED 是终态；仅 CLOSED 可重开。
 */
const TRANSITIONS = {
  START: { OPEN: 'IN_PROGRESS' },
  SUBMIT_VERIFY: { IN_PROGRESS: 'VERIFYING' },
  VERIFY_PASS: { VERIFYING: 'CLOSED' },
  VERIFY_FAIL: { VERIFYING: 'IN_PROGRESS' },
  CANCEL: { OPEN: 'CANCELLED', IN_PROGRESS: 'CANCELLED', VERIFYING: 'CANCELLED' },
  REOPEN: { CLOSED: 'OPEN' }
}

/** 给定动作与当前状态，返回目标状态；不合法返回 null */
export function targetStatusOf(action, fromStatus) {
  const byFrom = TRANSITIONS[action]
  return byFrom ? byFrom[fromStatus] || null : null
}

/** 当前状态可用的动作（按动作表顺序） */
export function availableActions(status) {
  return Object.keys(TRANSITIONS).filter((action) => TRANSITIONS[action][status])
}

/** 该动作是否要求填说明 */
export function requiresComment(action) {
  return action === 'VERIFY_FAIL' || action === 'CANCEL' || action === 'REOPEN'
}

/** 该动作是否要求填验证结论 */
export function requiresVerifyResult(action) {
  return action === 'VERIFY_PASS'
}

/** 该动作是否要求填根因分析（提交验证必须回答"为什么会发生"） */
export function requiresRootCause(action) {
  return action === 'SUBMIT_VERIFY'
}

/** 该动作是否要求填处理措施（提交验证必须交代"怎么处理的"） */
export function requiresResolution(action) {
  return action === 'SUBMIT_VERIFY'
}

/** 表格行上的"快捷推进"动作（每个状态最多一个主推进入口；无则 null） */
export function primaryAction(status) {
  for (const action of ['START', 'SUBMIT_VERIFY', 'VERIFY_PASS']) {
    if (TRANSITIONS[action][status]) return action
  }
  return null
}

/**
 * 该动作是否只能由「责任人」执行：START（开始处理）/ SUBMIT_VERIFY（提交验证）是处理人自己的活 ——
 * 别人代点，"谁在处理、谁认这个结果"就落不到人头上；验证类归提出人，取消 / 重开不限。
 *
 * 与后端 `IssueFlow.requiresOwner` / `IssueServiceImpl.transition` 同一口径：
 * 后端才是真闸门（非责任人返 403），前端据此决定按钮给不给；两边必须同步修改。
 */
export function requiresOwner(action) {
  return action === 'START' || action === 'SUBMIT_VERIFY'
}
