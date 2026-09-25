/**
 * 「审批意见」表单模型 —— 审批活动的固定表单（纯函数，零 DOM，可单测）。
 *
 * <p><b>为什么需要它</b>：审批活动天然是「同意 / 驳回」两条路由，但审批节点此前没有专属模板，
 * 运行期落回通用审批表单 —— 事能办，但看不出<b>驳回会退到哪里</b>。而"驳回目标"
 * （发起人 / 上一步 / 指定节点）是设计期就定好的（见设计器节点属性「路由」一组），
 * 经办人在点下"驳回"之前必须知道这一下会把流程退回哪儿。
 *
 * <p><b>驳回目标不是让经办人挑的</b>：它是流程设计的一部分，运行期只负责如实显示。
 * 让经办人临时选，等于把设计决定搬到执行时刻，流程就不可预期了。
 *
 * <p>与「设置审批人」同一套路：节点上已声明的东西（是否允许驳回、驳回到哪、意见是否必填）
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
 * 「允许驳回」开启时写入的默认配置 —— 与设计器面板 `object-toggle` 的 `objectTemplate` 一致
 * （见 app/panel/node-schemas.ts 的 `objectTemplate`）。
 *
 * <p>只用于"目标 / 是否必填"的缺省补齐，<b>不参与"是否允许驳回"的判断</b>：
 * 那个判断看的是节点上有没有 reject 声明（见 {@link allowsReject}）。
 */
const DEFAULT_REJECT = {
  enabled: true,
  target: 'PREVIOUS' as RejectTarget,
  commentRequired: true,
}

/**
 * 设计器里带「允许驳回」开关的节点类型。
 *
 * <p>只有这两种审批节点有该开关（见 app/panel/node-schemas.ts：`rejectFields()` 只挂在
 * APPROVAL 与 COUNTERSIGN_APPROVAL 上）。其它节点（办理节点等）本来就没有这个配置项，
 * 运行期不能因为"节点上没写"就把驳回拿走。
 */
const REJECT_CONFIGURABLE: ReadonlySet<string> = new Set([
  NodeType.APPROVAL,
  NodeType.COUNTERSIGN_APPROVAL,
])

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
  // 有声明就用声明的：它可能**没有 target**（那是"驳回走图上的驳回边"的形态，
  // 此时不能拿默认的"上一步"去覆盖 —— 那会把图上画的路径说成运行期跳转）
  const reject = node?.reject
    ? { commentRequired: DEFAULT_REJECT.commentRequired, ...node.reject }
    : { ...DEFAULT_REJECT }
  return {
    allowReject: allowsReject(node),
    rejectTargetText: rejectTargetText(dsl, reject.target, reject.targetNodeId, nodeId),
    commentRequiredOnApprove: node?.commentRequired === true,
    // 默认必填，只有显式关掉才放开（「驳回必须说明理由」是默认口径）
    commentRequiredOnReject: reject.commentRequired !== false,
  }
}

/**
 * 该活动是否允许驳回 —— <b>以设计器的节点声明为准</b>。
 *
 * <p>这里曾经把"节点上没写 reject"当成"允许驳回"（默认 enabled=true），
 * 于是设计器里关掉「允许驳回」的审批节点，办理页照样摆着「驳回」—— 开关形同虚设。
 * 关键在于"没写"并不等于"没配"：设计器的「允许驳回」是 object-toggle，
 * <b>关掉时会把整个 reject 键删掉</b>（见 app/panel/FieldRenderer.vue）——
 * 所以"没有 reject 键"就是"不允许驳回"。
 * （编译层照 DSL 原样写：有 reject 键就写进 BPMN 的 {@code ckplm:reject}，
 * 于是运行期也能读到同一份声明，见后端 {@code reject/RejectRouter}。）
 *
 * <p>三种情形分开处理：
 * <ul>
 *   <li><b>审批类节点</b>：看有没有 reject 声明（存量数据里若只有 target 没写 enabled，按允许处理，不误伤）；</li>
 *   <li><b>非审批类节点</b>（办理节点等）：设计器里根本没有这个开关，保持允许；</li>
 *   <li><b>取不到节点</b>（老实例 / DSL 缺失）：保持允许，不让数据问题挡住办事。</li>
 * </ul>
 */
function allowsReject(node: RoutedNode | null): boolean {
  if (!node || !REJECT_CONFIGURABLE.has(node.type)) {
    return true
  }
  return !!node.reject && node.reject.enabled !== false
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
