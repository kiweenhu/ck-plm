/**
 * 「审批意见」表单模型 —— 审批活动的固定表单（纯函数，零 DOM，可单测）。
 *
 * <p><b>为什么需要它</b>：审批活动天然是「同意 / 驳回」两条路由，但审批节点此前没有专属模板，
 * 运行期落回通用审批表单 —— 事能办，但看不出<b>驳回会退到哪里</b>。
 * 经办人在点下"驳回"之前必须知道这一下会把流程退回哪儿。
 *
 * <p><b>退回哪不是让经办人挑的</b>：它是流程设计的一部分（2026-10-13 起统一为画布上的
 * 「驳回」出边 —— 属性面板不再有「允许驳回 / 驳回目标」，见 app/panel/node-schemas.ts），
 * 运行期只负责如实显示。让经办人临时选，等于把设计决定搬到执行时刻，流程就不可预期了。
 *
 * <p>与「设置审批人」同一套路：节点上读得出的事实（有没有驳回出边、意见是否必填）
 * 由本文件解释，表单只做渲染与收集 —— 规则集中在这里才钉得住。
 */

import {
  EdgeRoute,
  NodeType,
  REJECT_TARGET_LABEL,
  type FlowDsl,
  type FlowNode,
  type RejectTarget,
} from '@flow-dsl-core'

/** 审批意见表单的模型（渲染与校验都只看它，不再各自去翻 DSL） */
export interface ApprovalOpinionModel {
  /** 该活动是否允许驳回（设计器「允许驳回」开关；缺省视为允许） */
  allowReject: boolean
  /** 驳回后会回到哪里，如「上一步」「指定节点「技术评审」」——给经办人看的 */
  rejectTargetText: string
  /** 同意时是否必须填意见（节点级「必填审批意见」） */
  commentRequiredOnApprove: boolean
  /** 驳回时是否必须填意见（默认必填：驳回总得说个理由） */
  commentRequiredOnReject: boolean
}

/**
 * 「怎么算可驳回」—— 2026-10-13 起：**画了「驳回」出边就是可驳回**。
 *
 * <p>驳回已统一为"画线"：设计器属性面板里不再有「允许驳回 / 驳回目标」（见
 * app/panel/node-schemas.ts 的 `rejectFields()`），节点上也不会再有 `reject` 声明，
 * 退回哪就等于那条出边指向哪。所以判断依据只剩一件事 ——
 * 从本节点出发有没有一条 {@code route: 'REJECT'} 的出边。
 *
 * <p>两个"仍算可驳回"的例外，都是为了不让历史数据挡住办事：
 * <ul>
 *   <li><b>老实例</b>：部署时刻的 DSL 里可能还带着 reject 声明（那会儿走的是运行期跳转），
 *       后端 {@code reject/RejectRouter} 也还认它 —— 照着放行；</li>
 *   <li><b>取不到节点</b>（老实例、DSL 缺失）：保持允许，不让数据问题把驳回拿走。</li>
 * </ul>
 *
 * <p>顺带一个行为修正：<b>办理节点（TASK）不再无条件可驳回</b>。以前"节点上没有开关
 * 就默认允许"，于是办理节点也能点驳回，而它的驳回会照走默认出边继续往前 ——
 * 界面写着"驳回"、流程实际往前走，是最难被发现的一类错。
 */

/**
 * 会"产生要人办的任务"的节点类型（审批 / 会签 / 办理 / 设置审批人）。
 *
 * <p>与设计期驳回目标校验同一口径（validate.ts 的 `checkRejectTarget` 里那份局部集合，
 * 报错文案也是这四个）。这里只用来决定提示里要不要加"先执行"—— 集合若漂移，最坏是一句提示不精确，
 * 不会影响流程行为。
 */
const HUMAN_TASK_TYPES: ReadonlySet<string> = new Set([
  NodeType.APPROVAL,
  NodeType.COUNTERSIGN_APPROVAL,
  NodeType.TASK,
  NodeType.SET_ASSIGNEE,
])

/**
 * 「画在流程图上的驳回出边」指向哪里 —— <b>直接连线目标节点</b>的名称。
 *
 * <p>设计器里「驳回」可以画成一条出边（`route: 'REJECT'`），它可能先落到一个自动化节点
 * （如「设置PES状态」「撤回电子签名」）再回到某个活动。所以这里给的是<b>连线直接指向的那个节点名</b>，
 * 不做"穿透到下一个活动"的猜测 —— 猜错会让经办人按错误的预期点下去，
 * 而原先把这话说成"按流程图上的「驳回」路径"，等于什么都没说。
 *
 * <p>目标不是人工活动时补一句"先执行"，免得被读成"已经退回到某人了"。
 */
function rejectEdgeTargetText(dsl: FlowDsl | null | undefined, nodeId: string): string {
  if (!nodeId) {
    return ''
  }
  const edge = (dsl?.edges ?? []).find(
    (item) => item.source === nodeId && item.route === EdgeRoute.REJECT,
  )
  if (!edge) {
    return ''
  }
  const target = (dsl?.nodes ?? []).find((node) => node.id === edge.target)
  const name = target?.name || edge.target
  if (!target) {
    return `「${name}」`
  }
  return HUMAN_TASK_TYPES.has(target.type) ? `「${name}」` : `先执行「${name}」`
}

/**
 * 带「路由」声明的节点视图。
 *
 * <p>用结构类型而不是直接写 {@code ApprovalNode}：驳回声明不只审批有，会签也有
 * （设计器里两者共用 `rejectFields()`）；这里只关心"有没有这两个字段"。
 */
type RoutedNode = FlowNode & {
  reject?: {
    enabled?: boolean
    target?: RejectTarget
    targetNodeId?: string
    commentRequired?: boolean
  }
  commentRequired?: boolean
}

function nodeOf(dsl: FlowDsl | null | undefined, nodeId: string): RoutedNode | null {
  const nodes = dsl?.nodes ?? []
  return (nodes.find((node) => node.id === nodeId) as RoutedNode | undefined) ?? null
}

/**
 * 由流程定义派生出该审批活动要用的表单模型。
 *
 * <p>取不到节点（没给 DSL / 活动 id 对不上）时全部走默认值 —— 审批本身不依赖 DSL，
 * 不该因为数据问题让经办人办不了事。
 */
export function buildApprovalOpinionModel(
  dsl: FlowDsl | null | undefined,
  nodeId: string,
): ApprovalOpinionModel {
  const node = nodeOf(dsl, nodeId)
  // 老实例可能还带着 reject 声明（那时驳回=运行期按目标跳转），有就用它；
  // 新流程没有这个字段 —— "退回哪"由那条驳回出边决定，文案走"按图上路径"分支。
  // 刻意**不**预填 target：拿默认的"上一步"去覆盖，会把图上画的路径说成运行期跳转。
  const reject = node?.reject ?? {}
  return {
    allowReject: allowsReject(dsl, nodeId, node),
    rejectTargetText: rejectTargetText(dsl, reject.target, reject.targetNodeId, nodeId),
    commentRequiredOnApprove: node?.commentRequired === true,
    // 默认必填，只有显式关掉才放开（「驳回必须说明理由」是默认口径）
    commentRequiredOnReject: reject.commentRequired !== false,
  }
}

/**
 * 该活动是否允许驳回（口径见文件头那段说明，这里只做判定）。
 */
function allowsReject(dsl: FlowDsl | null | undefined, nodeId: string, node: RoutedNode | null): boolean {
  // ① 画了「驳回」出边 → 可驳回（统一为画线之后的正规做法）
  const hasRejectEdge = (dsl?.edges ?? []).some(
    (edge) => edge.source === nodeId && edge.route === EdgeRoute.REJECT,
  )
  if (hasRejectEdge) {
    return true
  }
  // ② 老实例：节点上还带着 reject 声明 → 后端 RejectRouter 仍按它跳转，放行
  if (node?.reject) {
    return node.reject.enabled !== false
  }
  // ③ 取不到节点（老实例 / DSL 缺失）：不让数据问题把驳回拿走
  return !node
}

/**
 * 驳回目标文案。
 *
 * <p>指定节点时带上节点名（只说"指定节点"没人知道是哪个）；
 * 「上一步」「发起人」补一句口径说明 —— 这两个是<b>运行期判定</b>的
 * （上一步＝实际走过的那一步，发起人＝退回发起人办理的节点），
 * 经办人得知道这一下会回到哪儿，而不是看到两个含糊的词。
 */
function rejectTargetText(
  dsl: FlowDsl | null | undefined,
  target: RejectTarget | undefined,
  targetNodeId: string | undefined,
  /** 当前活动的节点 id：用来找那条「驳回」出边，取它指向的节点名 */
  nodeId: string,
): string {
  if (!target) {
    // 没有「驳回目标」＝这条驳回走的是画布上的「驳回」出边（可能先做若干自动化处理）。
    // 这里不能说"上一步"——那会把图上画的路径说成运行期跳转，经办人按错的预期点下去。
    // 但只说"按图上的驳回路径"也等于什么都没说：把那条边指向的节点名带出来
    const viaEdge = rejectEdgeTargetText(dsl, nodeId)
    return viaEdge ? `按流程图上的「驳回」路径 → ${viaEdge}` : '按流程图上的「驳回」路径'
  }
  const key = target
  const label = REJECT_TARGET_LABEL[key] ?? REJECT_TARGET_LABEL.PREVIOUS
  if (key === 'PREVIOUS') {
    return `${label}（实际走过的那一步）`
  }
  if (key === 'INITIATOR') {
    return `${label}（重新办理后提交）`
  }
  if (key !== 'NODE') {
    return label
  }
  const targetNode = (dsl?.nodes ?? []).find((node) => node.id === targetNodeId)
  return targetNode?.name ? `${label}「${targetNode.name}」` : label
}

/**
 * 提交前校验。
 *
 * <p>三条规则：「不允许驳回的活动」不能被驳回；驳回必须说明理由（默认）；
 * 同意时是否要意见由节点声明决定。
 *
 * @returns 不合规的原因；合规返回空串
 */
export function validateApprovalOpinion(
  model: ApprovalOpinionModel,
  action: string,
  comment: string,
): string {
  const text = (comment || '').trim()
  if (action === 'reject') {
    if (!model.allowReject) {
      return '该活动未开启驳回'
    }
    return model.commentRequiredOnReject && !text ? '驳回必须填写审批意见' : ''
  }
  return model.commentRequiredOnApprove && !text ? '该活动要求填写审批意见' : ''
}
