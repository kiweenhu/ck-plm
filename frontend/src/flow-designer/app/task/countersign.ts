/**
 * 「会签」表单模型 —— 会签审批活动的固定表单（纯函数，零 DOM，可单测）。
 *
 * <p><b>为什么需要它</b>：会签是"多人多票一起决定"，而通用审批表单只问"你同意还是驳回" ——
 * 经办人看不到<b>其他人办得怎么样了</b>，也看不到<b>这一票在什么规则下才算数</b>
 * （1 个通过就通过？还是要全票？）。这两件事恰恰是会签活动的全部含义，所以必须显式呈现。
 *
 * <p>数据从两处来，各有各的出处：
 * <ul>
 *   <li><b>通过规则</b>：设计期就定好的（`passRule`），从部署那一版的 DSL 读（运行期只显示，不让改 ——
 *       让经办人临时改规则等于把设计决定搬到执行时刻）；</li>
 *   <li><b>每人办理情况</b>：流程进度里同一活动下的每条任务（会签多实例＝一人一条）。
 *       逐人结论来自后端的任务级变量（`decision`），存量数据没有就如实显示"已办理"。</li>
 * </ul>
 *
 * <p>本人这一票的动作与校验沿用审批那一套口径（是否允许驳回、驳回退回哪里、意见是否必填），
 * 直接复用 {@link buildApprovalOpinionModel} —— 会签与审批在"结论 + 意见"上没有区别。
 */

import { PassRuleMode, type FlowDsl, type PassRule } from '@flow-dsl-core'
import {
  buildApprovalOpinionModel,
  validateApprovalOpinion,
  type ApprovalOpinionModel,
} from './approval-opinion'

/** 一个会签参与人（同一活动下的每条任务 = 一人一票） */
export interface CountersignParticipant {
  /** 任务 id（后端流程进度给出；存量数据可能为 null） */
  taskId?: string
  /** 显示名（解析不到用户名时退回原值） */
  name: string
  /** 是否已办理 */
  done: boolean
  /** 结论：APPROVE 同意 / REJECT 驳回；未办理或存量数据为 undefined */
  decision?: string
  /** 该人的办理意见 */
  comment?: string
  /** 办理时间文案（已办给出结束时间，在办给出开始时间） */
  timeText?: string
  /** 是不是当前这条任务（表单里标「我」） */
  isMe: boolean
}

export interface CountersignModel {
  /** 一句话规则，如「1 个通过即通过」 */
  ruleText: string
  /** 规则的设计期原文（模式 + 参数 + 弃权处理），作为一句话规则的注解 */
  ruleDetail: string
  participants: CountersignParticipant[]
  total: number
  doneCount: number
  approvedCount: number
  rejectedCount: number
  /** 「已办 1/3」 */
  progressText: string
  /** 本人这一票的动作 / 意见口径（与审批一致：是否允许驳回、退回哪里、意见是否必填） */
  opinion: ApprovalOpinionModel
}

/** 流程进度里的一条活动记录（只声明本模型用得到的字段，避免与后端 VO 强耦合） */
export interface ActivityLike {
  activityId?: string
  status?: string
  taskId?: string
  assignees?: string[]
  assigneeNames?: string[]
  startTime?: string
  endTime?: string
  comment?: string
  /** 逐人结论（APPROVE / REJECT）；存量数据为 undefined */
  decision?: string
}

const DECISION_APPROVE = 'APPROVE'
const DECISION_REJECT = 'REJECT'

/**
 * 通过规则 → 一句话 + 注解。
 *
 * <p>缺省（节点没配 passRule）不是"没规则"：编译层不生成 completionCondition 时，
 * 引擎按"所有人都办完"推进 —— 即<b>全部通过</b>。如实说出来，好过留白让人猜。
 */
export function passRuleText(rule?: PassRule | null): { text: string; detail: string } {
  const abstain = abstainText(rule?.abstain)
  if (!rule || !rule.mode) {
    return {
      text: '须全部通过',
      detail: `该节点未配置通过规则，引擎按默认：所有参与人都办完才继续${abstain}`,
    }
  }
  const mode = rule.mode
  if (mode === PassRuleMode.VETO) {
    return {
      text: '一票否决：任一驳回即不通过',
      detail: `有一个人驳回，整个会签就不通过${abstain}`,
    }
  }
  if (mode === PassRuleMode.COUNT) {
    const count = rule.count ?? 1
    const text = count <= 1 ? '1 个通过即通过' : `须 ${count} 个通过`
    return {
      text,
      detail: `${count} 个参与人投同意即通过（不再等其余人）${abstain}`,
    }
  }
  // PERCENT
  const percent = rule.percent ?? 100
  const text = percent >= 100 ? '须全部通过（100%）' : `通过比例达到 ${percent}% 即通过`
  return {
    text,
    detail: `同意票占参与人的 ${percent}% 及以上即通过${abstain}`,
  }
}

/** 弃权处理的注解（没配就不提，不占版面） */
function abstainText(abstain?: string): string {
  if (abstain === 'IGNORE') {
    return '；弃权票不计入分母'
  }
  if (abstain === 'PASS') {
    return '；弃权视为通过'
  }
  if (abstain === 'REJECT') {
    return '；弃权视为驳回'
  }
  return ''
}

/**
 * 由「部署那一版的 DSL + 流程进度」组装会签表单模型。
 *
 * <p>{@code activities} 里同一 {@code activityId} 会有多条（会签多实例一人一条）—— 这正是
 * "他人的会签情况"的来源，不需要额外的接口。
 *
 * @param dsl             该实例部署那一版的 DSL（解析不到传 null，规则按缺省文案）
 * @param nodeId          当前任务所在活动 id（= 会签节点 id）
 * @param activities      流程进度（TaskContextVO.activities）
 * @param currentTaskId   当前任务 id（用于标出"我"那一行）
 */
export function buildCountersignModel(input: {
  dsl?: FlowDsl | null
  nodeId?: string
  activities?: ActivityLike[] | null
  currentTaskId?: string
}): CountersignModel {
  const node = findNode(input.dsl, input.nodeId)
  const rule = (node?.passRule ?? null) as PassRule | null
  const { text, detail } = passRuleText(rule)

  const rows = (input.activities ?? []).filter(
    (item) => !!input.nodeId && item.activityId === input.nodeId,
  )
  const participants: CountersignParticipant[] = rows.map((item) => {
    const done = item.status === 'completed'
    const decision = normalizeDecision(item.decision)
    return {
      taskId: item.taskId,
      name: displayNameOf(item),
      done,
      decision,
      comment: item.comment,
      timeText: timeTextOf(item, done),
      isMe: !!input.currentTaskId && item.taskId === input.currentTaskId,
    }
  })

  const approvedCount = participants.filter((p) => p.decision === DECISION_APPROVE).length
  const rejectedCount = participants.filter((p) => p.decision === DECISION_REJECT).length
  const doneCount = participants.filter((p) => p.done).length

  return {
    ruleText: text,
    ruleDetail: detail,
    participants,
    total: participants.length,
    doneCount,
    approvedCount,
    rejectedCount,
    progressText: `已办 ${doneCount}/${participants.length}`,
    // 本人这一票的校验口径与会签无关，直接复用审批那一套（会签同样可配驳回）
    opinion: buildApprovalOpinionModel(input.dsl, input.nodeId ?? ''),
  }
}

/** 本人这一票的提交前校验（与会签规则无关，口径同审批） */
export function validateCountersign(
  model: CountersignModel,
  action: string,
  comment: string,
): string {
  return validateApprovalOpinion(model.opinion, action, comment)
}

function findNode(dsl: FlowDsl | null | undefined, nodeId?: string): Record<string, unknown> | null {
  if (!dsl || !nodeId) {
    return null
  }
  const found = (dsl.nodes ?? []).find((item) => item.id === nodeId)
  return (found as unknown as Record<string, unknown>) ?? null
}

function normalizeDecision(value?: string): string | undefined {
  if (!value) {
    return undefined
  }
  const upper = String(value).toUpperCase()
  if (upper === DECISION_APPROVE || upper === 'APPROVE' || upper === 'APPROVED') {
    return DECISION_APPROVE
  }
  if (upper === DECISION_REJECT || upper === 'REJECT' || upper === 'REJECTED') {
    return DECISION_REJECT
  }
  return undefined
}

/** 显示名优先用后端解析好的名字（assignee 可能是用户名也可能是 oid） */
function displayNameOf(item: ActivityLike): string {
  const name = (item.assigneeNames ?? []).find((value) => !!value)
  const raw = (item.assignees ?? []).find((value) => !!value)
  return name || raw || '未指派'
}

/** 已办给出办理时间，在办给出到达时间 —— 会签里"谁什么时候办的"是判断进度的一部分 */
function timeTextOf(item: ActivityLike, done: boolean): string | undefined {
  const value = done ? item.endTime : item.startTime
  if (!value) {
    return undefined
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`
}
