/**
 * CK-PLM 流程 DSL · 校验器（结构 + 业务规则）
 *
 * <p>原则（spec §1 第 4 条）：<b>不合法就不保存</b>。
 * 校验在 DSL 层实时完成，产出可定位到节点/连线的问题列表，
 * 供画布角标（未配置/错误/警告三态）与保存拦截使用。
 *
 * <p>零 DOM 依赖，可直接单测。
 */

import {
  ALLOWED_EXPRESSION_FUNCTIONS,
  ApprovalMode,
  AssigneeStrategy,
  ConditionMode,
  ConditionOperator,
  EdgeKind,
  EdgeRoute,
  EDGE_ROUTE_LABEL,
  GATEWAY_TYPES,
  IssueCode,
  IssueLevel,
  MIN_PASS_PERCENT,
  NodeType,
  NOTIFY_CHANNEL_LABEL,
  NotifyChannel,
  OverdueAction,
  PassRuleMode,
  RejectTarget,
  SETUP_COVERED_TYPES,
  TimerMode,
  UNARY_OPERATORS,
  type NodeType as NodeTypeT,
} from './constants'
import {
  canReach,
  incomingEdges,
  outgoingEdges,
  reachableNodeIds,
  setupNodes,
} from './ops'
import { builtinVariableOf, isBuiltinVariableName } from './builtin-variables'
import type {
  ApprovalNode,
  CountersignNode,
  SetAssigneeNode,
  AssigneeSpec,
  Condition,
  FlowDsl,
  FlowEdge,
  FlowNode,
  NotifyNode,
  RejectSpec,
  ServiceNode,
  SubProcessNode,
  TaskNode,
  TimerNode,
} from './schema'

export interface Issue {
  code: IssueCode
  level: IssueLevel
  message: string
  /** 归属节点（画布角标定位） */
  nodeId?: string
  /** 归属连线 */
  edgeId?: string
}

/**
 * 校验的<b>外部输入</b>。
 *
 * <p>校验本身是纯函数（同一份 DSL 得出同一份结论，便于单测与"保存前拦截"），
 * 但有些规则取决于<b>服务端配置</b>：比如"流程声明的通知渠道，系统到底启用了没有"。
 * 这类输入由调用方传进来，而不是让校验器自己去请求 —— 纯函数一旦发请求，
 * 就没法离线单测，也说不清"同一份 DSL 为什么两次校验结果不同"。
 */
export interface ValidateOptions {
  /**
   * 系统当前可用的通知渠道（来自 {@code GET /api/notifications/channels}，
   * 即服务端 {@code plm.notification} 的配置）。
   *
   * <p>不传则跳过"渠道是否启用"这条规则：拿不到系统配置时不猜，也不误报。
   */
  availableNotifyChannels?: NotifyChannel[]
}

export interface ValidationReport {
  /** 无 ERROR 即可保存 */
  ok: boolean
  issues: Issue[]
  errorCount: number
  warningCount: number
  /** 节点 → 问题（画布角标） */
  byNode: Record<string, Issue[]>
  /** 连线 → 问题 */
  byEdge: Record<string, Issue[]>
  /** 节点状态三态：OK / WARNING / ERROR（未配置的节点由 nodeConfigured 判定） */
  nodeState: Record<string, 'OK' | 'WARNING' | 'ERROR'>
}

type Add = (issue: Issue) => void

/** 判断节点是否「已完成配置」（用于画布三态的「未配置」态） */
export function nodeConfigured(node: FlowNode): boolean {
  switch (node.type) {
    case NodeType.SET_ASSIGNEE:
      // 执行人（默认发起人）配好即可；它"设置谁"由下游活动的策略决定
      return hasAssignee((node as SetAssigneeNode).assignee)
    // 会签的「已配置」标准比单签高：既要有人，也要有聚合规则（怎么算通过）
    case NodeType.COUNTERSIGN_APPROVAL: {
      const countersign = node as CountersignNode
      return hasAssignee(countersign.assignee) && !!countersign.passRule
    }
    case NodeType.APPROVAL: {
      const approval = node as ApprovalNode
      return (
        hasAssignee(approval.assignee) &&
        (approval.approvalMode !== ApprovalMode.COUNTERSIGN || !!approval.passRule)
      )
    }
    case NodeType.TASK:
      return hasAssignee((node as TaskNode).assignee)
    case NodeType.SERVICE: {
      const service = node as ServiceNode
      return !!service.serviceRef || !!service.expression
    }
    case NodeType.NOTIFY:
      return hasAssignee((node as NotifyNode).recipients)
    case NodeType.SUB_PROCESS:
      return !!(node as SubProcessNode).processKey
    case NodeType.TIMER: {
      const timer = node as TimerNode
      return timer.mode === TimerMode.DURATION ? !!timer.duration : !!timer.at
    }
    default:
      return true
  }
}

/** 审批人策略是否「可用」（至少声明了策略所需的取值） */
export function hasAssignee(spec?: AssigneeSpec): boolean {
  if (!spec) {
    return false
  }
  switch (spec.strategy) {
    case AssigneeStrategy.USER:
      return !!spec.userOids?.length
    case AssigneeStrategy.ROLE:
    case AssigneeStrategy.PROJECT_ROLE:
      return !!spec.roleCodes?.length
    case AssigneeStrategy.VARIABLE:
      return !!spec.variableName
    case AssigneeStrategy.EXPRESSION:
      return !!spec.expression
    case AssigneeStrategy.DEPT_LEADER:
      return !!spec.deptFieldKey
    default:
      return true
  }
}

export function validateDsl(dsl: FlowDsl, options?: ValidateOptions): ValidationReport {
  const issues: Issue[] = []
  const add: Add = (issue) => issues.push(issue)

  checkStructure(dsl, add)
  checkEdges(dsl, add)
  checkEdgeRoutes(dsl, add)
  checkSetup(dsl, add)
  checkVariables(dsl, add)
  checkNotifications(dsl, add, options)
  dsl.nodes.forEach((node) => checkNode(dsl, node, add))

  const byNode: Record<string, Issue[]> = {}
  const byEdge: Record<string, Issue[]> = {}
  const nodeState: Record<string, 'OK' | 'WARNING' | 'ERROR'> = {}
  for (const node of dsl.nodes) {
    nodeState[node.id] = nodeConfigured(node) ? 'OK' : 'WARNING'
  }
  for (const issue of issues) {
    if (issue.nodeId) {
      ;(byNode[issue.nodeId] ??= []).push(issue)
      if (issue.level === IssueLevel.ERROR) {
        nodeState[issue.nodeId] = 'ERROR'
      } else if (nodeState[issue.nodeId] !== 'ERROR') {
        nodeState[issue.nodeId] = 'WARNING'
      }
    }
    if (issue.edgeId) {
      ;(byEdge[issue.edgeId] ??= []).push(issue)
    }
  }

  const errorCount = issues.filter((i) => i.level === IssueLevel.ERROR).length
  return {
    ok: errorCount === 0,
    issues,
    errorCount,
    warningCount: issues.length - errorCount,
    byNode,
    byEdge,
    nodeState,
  }
}

/**
 * 会签聚合规则校验 —— <b>唯一实现</b>。
 *
 * <p>两个入口共用：独立的「会签审批」类型，以及历史上用
 * `APPROVAL + approvalMode=COUNTERSIGN` 表达的会签（存量模板与 BPMN 导入产物）。
 * 抽成函数是为了让「怎么算通过」「弃权怎么算」这类规则的判定只有一处 ——
 * 否则两个入口必然漂移，出现「同一个流程换个入口就报不同的错」。
 */
function checkCountersignAggregation(
  node: FlowNode,
  assignee: AssigneeSpec,
  rule: ApprovalNode['passRule'],
  add: Add,
): void {
  if (!rule) {
    add({
      code: IssueCode.COUNTERSIGN_NO_PASS_RULE,
      level: IssueLevel.ERROR,
      nodeId: node.id,
      message: `会签节点「${node.name}」未配置通过规则（比例 / 票数 / 一票否决）`,
    })
  } else {
    if (rule.mode === PassRuleMode.PERCENT) {
      if (rule.percent === undefined) {
        add({
          code: IssueCode.COUNTERSIGN_NO_PASS_RULE,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `会签节点「${node.name}」选择了按比例通过但未设置比例`,
        })
      } else if (rule.percent < MIN_PASS_PERCENT) {
        add({
          code: IssueCode.PASS_PERCENT_TOO_LOW,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `会签通过比例不得低于 ${MIN_PASS_PERCENT}%（当前 ${rule.percent}%）`,
        })
      }
    }
    if (rule.mode === PassRuleMode.COUNT && rule.count === undefined) {
      add({
        code: IssueCode.COUNTERSIGN_NO_PASS_RULE,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `会签节点「${node.name}」选择了按票数通过但未设置必须票数`,
      })
    }
  }
  // 单值策略（发起人 / 发起人上级）解析出来只有一个人，「会签」在业务上不成立
  if (
    assignee.strategy === AssigneeStrategy.INITIATOR ||
    assignee.strategy === AssigneeStrategy.INITIATOR_LEADER
  ) {
    add({
      code: IssueCode.COUNTERSIGN_NO_APPROVERS,
      level: IssueLevel.WARNING,
      nodeId: node.id,
      message: `会签节点「${node.name}」的审批人是单值策略（${assignee.strategy}），会签通常需要多人`,
    })
  }
}

// ==================== 「设置审批人」跨节点校验 ====================

/**
 * 「设置审批人」活动的跨节点校验。
 *
 * <p>规则<b>天然跨节点</b>（覆盖范围要靠可达性推导），所以不放进按节点跑的
 * {@link checkNode}。
 *
 * <p>口径与业务一致：设置活动<b>固定是流程启动后的第一个节点</b>，下游的
 * 审批 / 会签 / 办理活动（{@link SETUP_COVERED_TYPES}）一律由它指定。
 * 因此这里没有"某活动声明了却没有设置活动"这类规则 ——
 * 需要人办的活动就那三类，覆盖与否由**类型 + 可达性**自动决定，不靠声明。
 */
function checkSetup(dsl: FlowDsl, add: Add): void {
  const setters = setupNodes(dsl)
  const covered = dsl.nodes.filter((node) => SETUP_COVERED_TYPES.includes(node.type))

  // ① 有需要人办的活动，却没有设置活动 → 这些人不会被发起人指派（各自按配置解析）
  if (covered.length > 0 && setters.length === 0) {
    add({
      code: IssueCode.SETUP_MISSING,
      level: IssueLevel.WARNING,
      message:
        `流程里的审批 / 会签 / 办理活动（${covered.map((n) => `「${n.name}」`).join('、')}）` +
        '不会由发起人指派 —— 需要「设置审批人」活动（它固定跟在「开始」之后）',
    })
  }
  if (setters.length === 0) {
    return
  }

  // ② 多个设置活动：这里要分清"多设了几个"和"真的撞车了"。
  //
  // 撞车（两个设置活动都能到达同一个活动）不是建议，是**会丢数据的错误**：运行期谁后执行谁写入
  // 那个 `ckplmSetupAssignees_<活动id>` 变量，先执行的那个人选被静默覆盖 —— 发起人以为指派了 A，
  // 实际落到 B 头上，事后从图上完全看不出来。所以按真实覆盖范围判定，撞车报 ERROR（拦保存）。
  //
  // 不撞车（各管各的分支）只是"多设了几个"，给提醒即可 —— 把合法的用法也拦下来，
  // 用户会学会忽略这条规则。
  const coveredBy = new Map<string, string[]>()
  const targetsOf = new Map<string, string[]>()
  for (const setter of setters) {
    const targets = covered.filter((node) => canReach(dsl, setter.id, node.id))
    targetsOf.set(setter.id, targets.map((node) => node.id))
    for (const node of targets) {
      coveredBy.set(node.id, [...(coveredBy.get(node.id) ?? []), setter.name])
    }
  }
  let conflicted = false
  for (const node of covered) {
    const names = coveredBy.get(node.id) ?? []
    if (names.length < 2) {
      continue
    }
    conflicted = true
    add({
      code: IssueCode.SETUP_MULTIPLE,
      level: IssueLevel.ERROR,
      nodeId: node.id,
      message:
        `「${node.name}」的人员被 ${names.length} 个「设置审批人」活动同时覆盖` +
        `（${names.map((name) => `「${name}」`).join('、')}）：` +
        '运行期由后执行的那一个写入，先执行的那一个所填人员会被静默覆盖 —— ' +
        '发起人以为指定了人，实际落到另一批人头上。请只保留一个能覆盖到它的「设置审批人」活动',
    })
  }
  if (setters.length > 1 && !conflicted) {
    add({
      code: IssueCode.SETUP_MULTIPLE,
      level: IssueLevel.WARNING,
      message:
        `流程里有 ${setters.length} 个「设置审批人」活动（目前各自覆盖的活动不重叠）：` +
        '一旦后续改动让它们覆盖到同一个活动，后执行者会覆盖先执行者',
    })
  }

  for (const setter of setters) {
    // ③ 它必须是流程的第一步：否则在它之前执行的活动，人员还没被它定下来
    const predecessors = incomingEdges(dsl, setter.id).map((edge) =>
      dsl.nodes.find((node) => node.id === edge.source),
    )
    const rightAfterStart = predecessors.length === 1 && predecessors[0]?.type === NodeType.START
    if (!rightAfterStart) {
      add({
        code: IssueCode.SETUP_NOT_FIRST,
        level: IssueLevel.WARNING,
        nodeId: setter.id,
        message:
          `「${setter.name}」应直接跟在「开始」之后` +
          '（它是流程启动后的第一个节点）：在它之前执行的活动，人员不会被它指派',
      })
    }

    // ④ 下游没有任何审批 / 会签 / 办理活动 → 它没有可设置的对象，表单会是空的
    //（覆盖范围在上面算过，这里复用，不重复走一遍可达性）
    const targets = targetsOf.get(setter.id) ?? []
    if (targets.length === 0) {
      add({
        code: IssueCode.SETUP_NO_TARGET,
        level: IssueLevel.WARNING,
        nodeId: setter.id,
        message: `「${setter.name}」下游没有任何审批 / 会签 / 办理活动（它的任务表单会是空的）`,
      })
    }
  }
}

// ==================== 结构校验（spec §4-I）====================

function checkStructure(dsl: FlowDsl, add: Add): void {
  const starts = dsl.nodes.filter((n) => n.type === NodeType.START)
  const ends = dsl.nodes.filter((n) => n.type === NodeType.END)

  if (starts.length === 0) {
    add({ code: IssueCode.NO_START, level: IssueLevel.ERROR, message: '流程缺少开始节点' })
  } else if (starts.length > 1) {
    for (const node of starts) {
      add({
        code: IssueCode.MULTIPLE_START,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: '流程只能有一个开始节点',
      })
    }
  }
  if (ends.length === 0) {
    add({ code: IssueCode.NO_END, level: IssueLevel.ERROR, message: '流程缺少结束节点' })
  } else if (ends.length > 1) {
    // 多个结束节点（如「驳回结束」）是常见建模，仅提示不阻断
    add({
      code: IssueCode.MULTIPLE_END,
      level: IssueLevel.WARNING,
      message: `流程存在 ${ends.length} 个结束节点，请确认是否符合预期`,
    })
  }

  const duplicated = new Set<string>()
  const seen = new Set<string>()
  for (const node of dsl.nodes) {
    if (seen.has(node.id)) {
      duplicated.add(node.id)
    }
    seen.add(node.id)
  }
  for (const id of duplicated) {
    add({
      code: IssueCode.DUPLICATE_NODE_ID,
      level: IssueLevel.ERROR,
      nodeId: id,
      message: `节点 id 重复: ${id}`,
    })
  }

  const reachable = reachableNodeIds(dsl)
  for (const node of dsl.nodes) {
    const incoming = incomingEdges(dsl, node.id).length
    const outgoing = outgoingEdges(dsl, node.id).length
    const isStart = node.type === NodeType.START
    const isEnd = node.type === NodeType.END

    if (incoming === 0 && outgoing === 0) {
      add({
        code: IssueCode.ISOLATED_NODE,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」是孤立节点，未与流程连通`,
      })
      continue
    }
    if (isStart && incoming > 0) {
      add({
        code: IssueCode.START_HAS_INCOMING,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: '开始节点不能有入边',
      })
    }
    if (isEnd && outgoing > 0) {
      add({
        code: IssueCode.END_HAS_OUTGOING,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: '结束节点不能有出边',
      })
    }
    if (outgoing === 0 && !isEnd) {
      // 审批节点的「通过」就是它的出边，用它的词汇提示，用户才知道缺的是哪条路由
      add({
        code: IssueCode.NO_OUTGOING,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message:
          node.type === NodeType.APPROVAL
            ? `审批节点「${node.name}」缺少「通过」路由：请从节点连出一条出边`
            : `节点「${node.name}」没有出边，流程无法继续`,
      })
    }
    if (!isStart && !reachable.has(node.id)) {
      add({
        code: IssueCode.UNREACHABLE_NODE,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」从开始节点不可达`,
      })
    }
  }
}

// ==================== 连线与网关校验 ====================

/**
 * 默认分支标记是否用在了合适的地方。
 *
 * <p>「默认分支」只对<b>条件分支 / 包容分支</b>有意义 —— 它们是"按条件择一"，
 * 需要一个兜底出口。挂在别处（普通连线、并行分支的出边）既不影响编译、也不报错，
 * 属于「看着配了、其实不生效」的静默状态：并行分支是无条件全走，普通连线也不需要兜底。
 *
 * <p>面板把该选项做成可见按钮后（原来藏在下拉里），误点更容易发生，故必须拦。
 */
function checkDefaultBranchEdge(dsl: FlowDsl, edge: FlowEdge, add: Add): void {
  if (edge.kind !== EdgeKind.DEFAULT) {
    return
  }
  const source = dsl.nodes.find((n) => n.id === edge.source)
  if (!source) {
    return
  }
  if (source.type === NodeType.EXCLUSIVE_GATEWAY || source.type === NodeType.INCLUSIVE_GATEWAY) {
    return
  }
  add({
    code: IssueCode.DEFAULT_BRANCH_UNEXPECTED,
    level: IssueLevel.WARNING,
    edgeId: edge.id,
    nodeId: edge.source,
    message:
      `「${source.name}」的这条连线被标为默认分支，但默认分支只对条件分支 / 包容分支有意义` +
      '（并行分支是无条件全走，普通连线不需要兜底出口），该标记不会生效',
  })
}

/**
 * 出边上的「路由」（通过 / 驳回）—— 让"驳回之后还要对业务对象做点事"能画出来。
 *
 * <p>为什么要有这条路径：节点上的「驳回目标」只能"一步跳回某处"，
 * 表达不了"驳回 → 设置状态（PES 集合）→ 撤回电子签名 → 再回到某处"。
 * 把「驳回」变成一条<b>正常的出边</b>之后，后面接什么节点由画布决定。
 *
 * <p>契约（与编译层 {@code routeExpression} 一致，通过 = {@code ${approved}}、驳回 = {@code ${!approved}}）：
 * <ol>
 *   <li>路由只对审批 / 会签有意义（只有它们产生"结论"）；</li>
 *   <li>一旦有出边声明了路由，该节点的<b>所有</b>出边都要声明 ——
 *       否则那条无条件的边会被无条件走掉（通过时两条都走，变成并行拆分）；</li>
 *   <li>「通过」恰好一条，「驳回」至多一条；</li>
 *   <li>驳回路由要求节点开启「允许驳回」，否则那条边走不到；</li>
 *   <li>驳回路由与节点上的「驳回目标」二选一：一个走图、一个运行期跳转，同时配就是两套机制抢戏。</li>
 * </ol>
 * <p>另有提示：多条出边却一条都没声明路由 —— 引擎会当作并行分支（两条都走）。
 */
function checkEdgeRoutes(dsl: FlowDsl, add: Add): void {
  for (const node of dsl.nodes) {
    const outgoing = dsl.edges.filter((edge) => edge.source === node.id)
    if (outgoing.length === 0) {
      continue
    }
    const routed = outgoing.filter((edge) => !!edge.route)
    const isApproval = node.type === NodeType.APPROVAL || node.type === NodeType.COUNTERSIGN_APPROVAL
    const rejectEnabled = (node as { reject?: { enabled?: boolean } }).reject?.enabled === true

    if (routed.length > 0 && !isApproval) {
      add({
        code: IssueCode.EDGE_ROUTE_NOT_APPLICABLE,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」不是审批 / 会签活动，它的出边不该声明「通过 / 驳回」路由`
          + '（只有产生审批结论的节点才有这两个结论）',
      })
      continue
    }
    if (!isApproval) {
      continue
    }

    if (routed.length > 0 && routed.length < outgoing.length) {
      const rest = outgoing.filter((edge) => !edge.route).map((edge) => edge.name ?? edge.id).join('、')
      add({
        code: IssueCode.EDGE_ROUTE_PARTIAL,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」有 ${outgoing.length} 条出边，但只有一部分声明了路由（未声明：${rest}）：`
          + '未声明的那条没有条件限制，会被无条件走掉（通过时两条都走）；请给每条出边都选上路由',
      })
    }

    for (const route of [EdgeRoute.PASS, EdgeRoute.REJECT]) {
      const same = routed.filter((edge) => edge.route === route)
      if (same.length > 1) {
        add({
          code: IssueCode.EDGE_ROUTE_DUPLICATED,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `节点「${node.name}」有 ${same.length} 条「${EDGE_ROUTE_LABEL[route]}」路由：`
            + `每种结论只能走一条路（${same.map((edge) => edge.name ?? edge.id).join('、')}）`,
        })
      }
    }

    const hasRejectRoute = routed.some((edge) => edge.route === EdgeRoute.REJECT)
    const rejectTarget = (node as { reject?: { target?: string } }).reject?.target
    if (hasRejectRoute && !rejectEnabled) {
      add({
        code: IssueCode.EDGE_ROUTE_WITHOUT_REJECT,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」画了「驳回」路由，但节点没有开启「允许驳回」—— 这条边永远走不到；`
          + '请开启「允许驳回」，或删掉这条出边',
      })
    }
    if (hasRejectRoute && rejectTarget) {
      add({
        code: IssueCode.EDGE_ROUTE_CONFLICT,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」既画了「驳回」路由、又配了「驳回目标」：两者是同一件事的两种做法`
          + '（走图 vs 一步跳回），请二选一 —— 要"驳回后先做自动化动作"就保留驳回路由并清空「驳回目标」',
      })
    }
    // 「允许驳回但无处可去」（REJECT_NO_TARGET）由 checkRejectTarget 判定 —— 那里与出边无关，
    // 没有出边的孤立方块也报；此处只负责"出边声明"相关的几条
    if (routed.length > 0 && !routed.some((edge) => edge.route === EdgeRoute.PASS)) {
      add({
        code: IssueCode.EDGE_ROUTE_NO_PASS,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」声明了路由却没有「通过」路由：同意之后无路可走`,
      })
    }
    if (outgoing.length > 1 && routed.length === 0) {
      add({
        code: IssueCode.EDGE_ROUTE_UNSPECIFIED,
        level: IssueLevel.WARNING,
        nodeId: node.id,
        message: `节点「${node.name}」有 ${outgoing.length} 条出边却都没声明路由：引擎会把它们当作并行分支（两条都走）。`
          + '要表达"同意走这条、驳回走那条"，请给每条出边选上路由',
      })
    }
  }
}

/**
 * 并行分支的「重复实例」两条规则 —— 都来自真实事故（NPI 模板 v25 实测）。
 *
 * <p>并行网关分出 N 个 token 之后，BPMN <b>不会</b>自动"等它们都回来"：必须有一个并行汇聚
 * （入度 ≥2 的网关）把它们合流。少了它，每个 token 各走一遍下游，于是：
 * <ol>
 *   <li><b>多条支路在同一个活动上碰头</b>：该活动被创建 N 份（用户看到"同一个节点冒出多条待办"），
 *       且任一支路走到「结束」时整个实例提前结束 —— 其他分支上还在办的任务凭空消失；</li>
 *   <li><b>分支上带退回（驳回）</b>：回退要重走它的上游，而这个分支的 token 不会随之消失，
 *       一退一进就把分支上的活动又创建一遍。</li>
 * </ol>
 *
 * <p>实测数据（v25 实例）：并行网关走了 2 次、会签审批被实例化 <b>4 次</b>（2 轮 × 2 人）、
 * 最后三条支路的活动在同一毫秒全部结束（其中一条走到结束事件，实例提前终止）。
 * 这两条都不是"画得不好看"，而是<b>运行期会出乱子</b>，所以必须给提示 ——
 * 定为 WARNING 而不是 ERROR：它们不阻断保存，但会出现在校验清单里。
 */
function checkParallelBranches(
  dsl: FlowDsl,
  gateway: { id: string; name?: string },
  outgoing: FlowEdge[],
  add: Add,
): void {
  if (outgoing.length < 2) {
    return
  }
  const branches = outgoing.map((edge) => parallelBranchNodes(dsl, edge.target, gateway.id))

  // ① 多条支路走到同一个活动 ⇒ 该活动会被重复创建
  const merged = new Set<string>()
  for (let i = 0; i < branches.length; i += 1) {
    for (let j = i + 1; j < branches.length; j += 1) {
      const shared = [...branches[i]].find((id) => branches[j].has(id))
      if (!shared || merged.has(shared)) {
        continue
      }
      merged.add(shared)
      const sharedNode = dsl.nodes.find((n) => n.id === shared)
      // ERROR（拦保存）而不是提醒：这不是"风格建议"，是**会重复创建活动、并让在办任务凭空消失**的
      // 结构错误 —— 每个分支各带一个 token 走到同一个活动，该活动被创建多份（谁办哪份说不清）；
      // 任一支路走到「结束」时整个实例提前结束，另一条支路上还在办的任务会直接消失。
      // 这种流程一旦发出去，问题只会出现在运行期、且没有补救手段，因此必须在保存前挡住。
      add({
        code: IssueCode.PARALLEL_BRANCH_NOT_MERGED,
        level: IssueLevel.ERROR,
        nodeId: shared,
        message:
          `并行分支「${gateway.name ?? gateway.id}」的多条支路都走到了「${sharedNode?.name ?? shared}」`
          + '却没有汇聚点：每个分支各带一个 token 走到底，该活动会被创建多份；'
          + '并且任一支路走到「结束」时整个流程会提前结束（其他分支上还在办的任务会消失）。'
          + '请在分叉与合流之间放一个「并行分支」网关做汇聚（两条支路都接进它）',
      })
    }
  }

  // ② 分支上配了退回（驳回）⇒ 一退一进重复创建分支上的活动
  //（按节点去重：分支集合会因为"退回绕回来"而互相重叠，同一个节点只该报一次）
  const reported = new Set<string>()
  for (const branch of branches) {
    for (const id of branch) {
      if (reported.has(id)) {
        continue
      }
      const branchNode = dsl.nodes.find((n) => n.id === id)
      if (!branchNode) {
        continue
      }
      const hasRejectRoute = outgoingEdges(dsl, id).some((edge) => edge.route === EdgeRoute.REJECT)
      const hasRejectJump = !!(branchNode as { reject?: unknown }).reject
      if (!hasRejectRoute && !hasRejectJump) {
        continue
      }
      reported.add(id)
      add({
        code: IssueCode.PARALLEL_BRANCH_HAS_REJECT,
        level: IssueLevel.WARNING,
        nodeId: id,
        message:
          `并行分支上的「${branchNode.name}」配了退回（驳回）：退回要重走它的上游，`
          + '而这个并行分支的 token 不会随之消失 —— 一退一进就把分支上的活动又创建一遍'
          + '（实测：会签节点从 2 条变 4 条）。退回请放在并行汇聚之后，分支内只用「同意」',
      })
    }
  }
}

/**
 * 并行分支从某条出边起、到「汇聚点」为止能到达的节点集合。
 *
 * <p>汇聚点（入度 ≥2 的网关）<b>不计入、也不穿过</b>：正确画法的并行分支正是靠它合流，
 * 把它算进来会让画对的流程也被判成"分支碰头"—— 假报警会让整条规则失去信任。
 */
function parallelBranchNodes(dsl: FlowDsl, startId: string, forkId: string): Set<string> {
  const result = new Set<string>()
  const queue = [startId]
  while (queue.length > 0) {
    const id = queue.shift() as string
    if (result.has(id) || id === forkId) {
      // 又绕回这个并行网关（典型是分支上配了退回）：到此为止 ——
      // "退回来重走一遍"由 PARALLEL_BRANCH_HAS_REJECT 单独报，不必再污染分支集合
      continue
    }
    const node = dsl.nodes.find((n) => n.id === id)
    if (!node) {
      continue
    }
    const incomingCount = dsl.edges.filter((edge) => edge.target === id).length
    if (GATEWAY_TYPES.includes(node.type as NodeTypeT) && incomingCount > 1) {
      continue
    }
    result.add(id)
    for (const edge of outgoingEdges(dsl, id)) {
      queue.push(edge.target)
    }
  }
  return result
}

function checkEdges(dsl: FlowDsl, add: Add): void {
  const nodeIds = new Set(dsl.nodes.map((n) => n.id))
  for (const edge of dsl.edges) {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) {
      add({
        code: IssueCode.EDGE_ENDPOINT_MISSING,
        level: IssueLevel.ERROR,
        edgeId: edge.id,
        message: '连线端点不存在（节点可能已被删除）',
      })
    }
    checkCondition(edge, add)
    checkDefaultBranchEdge(dsl, edge, add)
  }

  for (const node of dsl.nodes) {
    const outgoing = outgoingEdges(dsl, node.id)
    const isGateway = GATEWAY_TYPES.includes(node.type as NodeTypeT)

    if (isGateway) {
      const incoming = dsl.edges.filter((edge) => edge.target === node.id).length
      // 网关有两种角色，判据不同：
      //   分叉 —— 至少 2 条**出边**；
      //   汇聚 —— 至少 2 条**入边**（出边正常就是 1 条，接续下面的活动）。
      // 以前只看出边，于是「2 入 1 出」的汇聚网关被判成"至少需要 2 条出边"——
      // 而那正是最标准的并行合流画法，等于把画对的流程标红。
      // 只有"既不分叉也不汇聚"（1 进 1 出）才是真的多余，那种网关没有任何流转意义。
      if (outgoing.length < 2 && incoming < 2) {
        add({
          code: IssueCode.GATEWAY_TOO_FEW_BRANCHES,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `分支节点「${node.name}」既不是分叉也不是汇聚`
            + `（出边 ${outgoing.length} 条、入边 ${incoming} 条）：`
            + '分叉需要至少 2 条出边，汇聚需要至少 2 条入边',
        })
      }
    }

    if (isGateway) {
      const defaults = outgoing.filter((e) => e.kind === EdgeKind.DEFAULT)
      if (defaults.length > 1) {
        add({
          code: IssueCode.GATEWAY_MULTIPLE_DEFAULT,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `分支节点「${node.name}」存在多条默认分支，只能保留一条`,
        })
      }
      // 排他/包容网关必须给 default，保证条件都不命中时仍可流转。
      // 只在**多条出边**时才要求：出边只有 1 条时不存在"选哪条"的问题，
      // 而"2 入 1 出"的汇聚用法同样不该被要求配默认分支。
      if (node.type !== NodeType.PARALLEL_GATEWAY && outgoing.length > 1 && defaults.length === 0) {
        add({
          code: IssueCode.GATEWAY_NO_DEFAULT,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message:
            `分支节点「${node.name}」缺少默认分支（所有条件均不命中时流程会卡住）：` +
            '选中它的一条出边，把「类型」设为「默认分支」即可',
        })
      }
      // 排他/包容网关的「非默认」出边必须配条件。
      // 原因：BPMN 里无条件的边恒为真，网关按文档顺序取第一条为真的 ——
      // 一条无条件的普通流转会把它后面的分支（含默认分支）全部压成死分支，且不报任何错。
      if (node.type !== NodeType.PARALLEL_GATEWAY) {
        for (const edge of outgoing) {
          if (edge.kind !== EdgeKind.DEFAULT && !hasUsableCondition(edge.condition)) {
            add({
              code: IssueCode.MISSING_CONDITION,
              level: IssueLevel.ERROR,
              edgeId: edge.id,
              nodeId: node.id,
              message:
                `分支节点「${node.name}」的这条出边既不是默认分支、也没有条件：` +
                '它会被无条件走掉，使其后分支（含默认分支）失效',
            })
          }
        }
      }
      // 并行网关的分支是「无条件并行」，不允许挂条件
      if (node.type === NodeType.PARALLEL_GATEWAY) {
        for (const edge of outgoing) {
          if (edge.condition || edge.kind === EdgeKind.CONDITION) {
            add({
              code: IssueCode.PARALLEL_HAS_CONDITION,
              level: IssueLevel.ERROR,
              edgeId: edge.id,
              nodeId: node.id,
              message: `并行分支不允许配置条件（连线「${edge.name ?? edge.id}」）`,
            })
          }
        }
        checkParallelBranches(dsl, node, outgoing, add)
      }
    } else if (outgoing.length > 1) {
      // 非网关多出边（常见于审批节点的「同意/拒绝」），必须每条都有条件或其一为默认。
      // 注意判定是<b>逐条</b>的：早先写成「没有默认分支时才检查条件」，
      // 于是「一条无条件边 + 一条默认分支」这种组合静默通过 ——
      // 而那条无条件边恒为真，会把默认分支压成死分支。
      for (const edge of outgoing) {
        // 声明了「路由」的边条件由编译层生成（通过 ${approved} / 驳回 ${!approved}），
        // 用户不用填条件 —— 这里必须认它，否则"驳回画成一条边"会被这条老规则挡住（真实反馈）
        if (edge.kind !== EdgeKind.DEFAULT && !edge.route && !hasUsableCondition(edge.condition)) {
          add({
            code: IssueCode.MISSING_CONDITION,
            level: IssueLevel.ERROR,
            edgeId: edge.id,
            nodeId: node.id,
            message: `节点「${node.name}」有多条出边时，每条都需配置条件、路由，或指定一条为默认分支`,
          })
        }
      }
    }
  }
}

function checkCondition(edge: FlowEdge, add: Add): void {
  const condition = edge.condition
  if (!condition) {
    return
  }
  if (condition.mode === ConditionMode.FIELD) {
    if (!condition.fieldKey || !condition.operator) {
      add({
        code: IssueCode.MISSING_CONDITION,
        level: IssueLevel.ERROR,
        edgeId: edge.id,
        message: `条件分支「${edge.name ?? edge.id}」未选完字段与操作符`,
      })
      return
    }
    const unary = UNARY_OPERATORS.includes(condition.operator)
    if (!unary && (condition.value === undefined || condition.value === '')) {
      add({
        code: IssueCode.MISSING_CONDITION,
        level: IssueLevel.ERROR,
        edgeId: edge.id,
        message: `条件分支「${edge.name ?? edge.id}」缺少比较值`,
      })
    }
  } else if (!condition.expression) {
    add({
      code: IssueCode.MISSING_CONDITION,
      level: IssueLevel.ERROR,
      edgeId: edge.id,
      message: `条件分支「${edge.name ?? edge.id}」选择了表达式模式但未填写表达式`,
    })
  } else {
    const illegal = illegalFunctions(condition.expression)
    if (illegal.length > 0) {
      add({
        code: IssueCode.EXPRESSION_NOT_ALLOWED,
        level: IssueLevel.ERROR,
        edgeId: edge.id,
        message: `表达式使用了白名单外的函数: ${illegal.join(', ')}`,
      })
    }
  }
}

function hasUsableCondition(condition?: Condition): boolean {
  if (!condition) {
    return false
  }
  if (condition.mode === ConditionMode.EXPRESSION) {
    return !!condition.expression
  }
  const unary = condition.operator ? UNARY_OPERATORS.includes(condition.operator) : false
  return !!condition.fieldKey && !!condition.operator && (unary || condition.value !== undefined)
}

/** 提取表达式中的函数调用名，返回不在白名单内的部分 */
export function illegalFunctions(expression: string): string[] {
  const names = new Set<string>()
  const regex = /([A-Za-z_][A-Za-z0-9_]*)\s*\(/g
  let match: RegExpExecArray | null
  while ((match = regex.exec(expression)) !== null) {
    names.add(match[1])
  }
  return [...names].filter((name) => !ALLOWED_EXPRESSION_FUNCTIONS.includes(name))
}

// ==================== 流程变量声明 ====================

/**
 * 校验模板声明的流程变量。
 *
 * <p>目前只有一条规则：<b>不要声明与内置变量同名的变量</b>。
 * 运行期平台自己会写这些名字（发起写 initiator / tenantOid / 业务对象上下文，办理写
 * approved / lastAction / lastComment），同名声明拿不到控制权 —— 声明里配的
 * 「默认值 / 只读 / 允许任务中重写」全部无效，最后显示的是平台值。
 * 这属于"配了却不生效"，不阻断保存，但要说清楚。
 */
function checkVariables(dsl: FlowDsl, add: Add): void {
  for (const variable of dsl.variables ?? []) {
    if (!isBuiltinVariableName(variable.name)) {
      continue
    }
    const builtin = builtinVariableOf(variable.name)
    add({
      code: IssueCode.VARIABLE_NAME_BUILTIN,
      level: IssueLevel.WARNING,
      message:
        `流程变量「${variable.name}」与内置变量同名（${builtin?.display ?? variable.name}，` +
        `${builtin?.timing ?? '运行期'}写入）：运行期平台的值会覆盖它，` +
        '本行配置的默认值 / 只读等设置不会生效，建议改名',
    })
  }
}

// ==================== 通知方式（流程级）====================

/**
 * 校验「通知方式」（{@code dsl.notifications}）。
 *
 * <p>两条规则，都是 <b>WARNING</b>（不阻断保存）：
 * <ol>
 *   <li>流程声明的渠道<b>系统没启用</b> —— 运行期一定发不出去，早说比等"通知没到"再回头查好；</li>
 *   <li>流程里有通知节点，却一个渠道都没声明 —— 语义上会跟随系统配置，但多半是漏配。</li>
 * </ol>
 *
 * <p><b>凭据不再由流程负责</b>：SMTP 地址、OA 接口、飞书应用凭据都是系统级配置
 * （application.yml 的 {@code plm.notification}），所以这里不再校验"模板里填没填" ——
 * 那件事已经不在流程的职责范围内了。第 1 条要看系统配置，因此需要调用方把
 * {@link ValidateOptions#availableNotifyChannels} 传进来；拿不到就不报（不猜、不误报）。
 *
 * <p>为什么不阻断：渠道与流程设计常是两件事（系统那边还没开邮件渠道，流程可以先设计完）。
 */
function checkNotifications(dsl: FlowDsl, add: Add, options?: ValidateOptions): void {
  const config = dsl.meta?.notifications
  // 单选；channels 是历史形态（合并迁移前的对象也可能被直接校验），有则取第一个
  const channel = config?.channel ?? config?.channels?.[0]

  // 1) 流程声明的通知方式必须<b>系统已启用</b> —— 渠道与凭据在服务端配置（plm.notification），
  //    流程里写一个系统没启用的渠道，运行期一定发不出去（这类"配了但不生效"最难查）
  const available = options?.availableNotifyChannels
  if (available && channel && !available.includes(channel)) {
    const availableText = available.length
      ? available.map((item) => NOTIFY_CHANNEL_LABEL[item]).join('、')
      : '（无）'
    add({
      code: IssueCode.NOTIFY_CHANNEL_NOT_ENABLED,
      level: IssueLevel.WARNING,
      message: `通知方式选了「${NOTIFY_CHANNEL_LABEL[channel]}」，但系统未启用该渠道`
        + `（当前可用：${availableText}）。渠道由服务端配置（application.yml 的 plm.notification）`,
    })
  }

  // 2) 有通知节点却没选通知方式：按"跟随系统配置"理解也能发，
  //    但流程作者多半是漏配了，提示一句比默默替它决定好
  const hasNotifyNode = dsl.nodes.some((node) => node.type === NodeType.NOTIFY)
  if (hasNotifyNode && !channel) {
    add({
      code: IssueCode.NOTIFY_NODE_WITHOUT_CHANNEL,
      level: IssueLevel.WARNING,
      message: '流程里有通知节点，但「流程模板 → 通知方式」里没有选择通知方式；'
        + '不选则跟随系统启用的渠道，建议明确选择本流程使用的通知方式',
    })
  }
}


// ==================== 节点业务规则 ====================

/**
 * 驳回目标的可用性 —— 把"配了也一定跑不通"的组合在设计期拦下。
 *
 * <p>三条规则不是凭空定的，而是照着<b>运行期的真实口径</b>写的（见后端
 * {@code process/reject/RejectRouter}）：驳回在运行期是"按目标跳转"，跳不到就明确失败。
 * 设计期不说，用户就会在办理时才发现 —— 那时人已经在流程里等着了。
 *
 * <ul>
 *   <li><b>「上一步」</b>＝该实例实际走过的上一个办理节点；本节点之前没有任何人工活动时，
 *       "上一步"无处可退（它是第一步）；</li>
 *   <li><b>「发起人」</b>＝退回到"由发起人办理"的那个节点（发起本身不是流程节点）；
 *       流程里没有这样的节点时，退回去没有人能办；</li>
 *   <li><b>「指定节点」</b>必须是一个能产生任务的人工活动，且不能是自己（退回原地等于不动）。</li>
 * </ul>
 */
function checkRejectTarget(dsl: FlowDsl, node: FlowNode, reject: RejectSpec | undefined, add: Add): void {
  if (!reject?.enabled) {
    return
  }
  const humanTypes: NodeTypeT[] = [
    NodeType.APPROVAL,
    NodeType.COUNTERSIGN_APPROVAL,
    NodeType.TASK,
    // 「设置审批人」也要算：它确实产生一个要人办的任务（编译成 userTask），
    // 「驳回 → 重新指派审批人 → 重走审批」是常见且合理的编排。
    // 漏掉它会让这类流程在设计期报"退回去没有人能办理"，而运行期明明能跳过去 ——
    // 设计期与运行期必须同一口径，否则用户被自己的设计器挡住
    NodeType.SET_ASSIGNEE,
  ]
  const isHuman = (candidate: FlowNode) => humanTypes.includes(candidate.type as NodeTypeT)

  // 「允许驳回」开着，却既没选目标、也没画驳回边 → 驳回之后无处可去。
  //
  // 放在这里（而不是出边校验里）是因为它**与出边无关**：还没连线的孤立方块同样要报 ——
  // 出边校验开头 `if (outgoing.length === 0) continue` 会把整段跳过，
  // 于是"勾了允许驳回、什么都没配"的节点此前一声不响（运行期才报错把经办人挡住）。
  const hasRejectRoute = dsl.edges.some(
    (edge) => edge.source === node.id && edge.route === EdgeRoute.REJECT,
  )
  if (!reject.target && !hasRejectRoute) {
    add({
      code: IssueCode.REJECT_NO_TARGET,
      level: IssueLevel.ERROR,
      nodeId: node.id,
      message: `节点「${node.name}」开启了「允许驳回」，但既没选「驳回目标」、也没画「驳回」出边：`
        + '驳回之后无处可去。请二选一：选一个退回目标，或连一条路由为「驳回」的出边',
    })
    return
  }

  if (reject.target === RejectTarget.NODE) {
    const target = dsl.nodes.find((candidate) => candidate.id === reject.targetNodeId)
    if (!target) {
      add({
        code: IssueCode.REJECT_NODE_NOT_FOUND,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」的驳回目标节点不存在`,
      })
    } else if (target.id === node.id) {
      add({
        code: IssueCode.REJECT_TARGET_SELF,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」的驳回目标是它自己，退回去等于原地不动；请改选其它目标`,
      })
    } else if (!isHuman(target)) {
      add({
        code: IssueCode.REJECT_TARGET_NOT_HUMAN,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」的驳回目标「${target.name}」不是人工活动，退回去没有人能办理；`
          + '请改选审批 / 会签 / 办理 / 设置审批人这类会产生任务的节点',
      })
    }
    return
  }

  if (reject.target === RejectTarget.INITIATOR) {
    const hasInitiatorNode = dsl.nodes.some((candidate) =>
      isHuman(candidate) && assigneeOf(candidate)?.strategy === AssigneeStrategy.INITIATOR)
    if (!hasInitiatorNode) {
      add({
        code: IssueCode.REJECT_NO_INITIATOR_NODE,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」的驳回目标是「发起人」，但流程里没有由发起人办理的节点，`
          + '退回去没有人能办；请改选「上一步」或「指定节点」，或加一个由发起人办理的节点',
      })
    }
    return
  }

  if (reject.target === RejectTarget.PREVIOUS) {
    const hasPrevious = dsl.nodes.some((candidate) =>
      candidate.id !== node.id && isHuman(candidate) && canReach(dsl, candidate.id, node.id))
    if (!hasPrevious) {
      add({
        code: IssueCode.REJECT_NO_PREVIOUS_NODE,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」的驳回目标是「上一步」，但它之前没有任何办理活动，无处可退`,
      })
    }
  }
}

/** 取人工活动的审批人/执行人声明（不同节点类型的字段名不同，这里统一取） */
function assigneeOf(node: FlowNode): AssigneeSpec | undefined {
  const candidate = node as { assignee?: AssigneeSpec; initiator?: AssigneeSpec }
  return candidate.assignee ?? candidate.initiator
}

function checkNode(dsl: FlowDsl, node: FlowNode, add: Add): void {
  const variableNames = new Set((dsl.variables ?? []).map((v) => v.name))
  const checkAssignee = (spec: AssigneeSpec | undefined, required: boolean, label: string): void => {
    if (!spec) {
      if (required) {
        add({
          code: IssueCode.MISSING_ASSIGNEE,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `节点「${node.name}」未配置${label}`,
        })
      }
      return
    }
    if (!hasAssignee(spec)) {
      add({
        code: IssueCode.MISSING_ASSIGNEE,
        level: IssueLevel.ERROR,
        nodeId: node.id,
        message: `节点「${node.name}」的${label}未选完（策略：${spec.strategy}）`,
      })
    }
    // 内置变量（initiator / approved / ckplmSetupAssignees_<活动id> …）是运行期自动注入的，
    // 引用它们不需要声明 —— 否则"引用 ${approved}"这种正确用法会被误报，
    // 真正的漏声明反而被淹掉
    if (spec.strategy === AssigneeStrategy.VARIABLE && spec.variableName
      && !variableNames.has(spec.variableName) && !isBuiltinVariableName(spec.variableName)) {
      add({
        code: IssueCode.VARIABLE_NOT_DEFINED,
        level: IssueLevel.WARNING,
        nodeId: node.id,
        message: `节点「${node.name}」引用了未声明的流程变量 ${spec.variableName}`,
      })
    }
    if (spec.fallback) {
      checkAssignee(spec.fallback, false, `${label}兜底策略`)
    }
  }

  switch (node.type) {
    case NodeType.START:
      checkAssignee(node.initiator, true, '发起人')
      break

    case NodeType.SET_ASSIGNEE: {
      const setup = node as SetAssigneeNode
      checkAssignee(setup.assignee, true, '执行人')
      checkDeadline(node, setup.deadline, add)
      break
    }

    // 会签审批：独立业务类型，校验比单签更严（必须有聚合规则、审批人应能解析出多人）
    case NodeType.COUNTERSIGN_APPROVAL: {
      const countersign = node as CountersignNode
      checkAssignee(countersign.assignee, true, '会签参与人')
      checkCountersignAggregation(node, countersign.assignee, countersign.passRule, add)
      checkRejectTarget(dsl, node, countersign.reject, add)
      checkDeadline(node, countersign.deadline, add)
      break
    }

    case NodeType.APPROVAL: {
      const approval = node as ApprovalNode
      checkAssignee(approval.assignee, true, '审批人')
      if (approval.approvalMode === ApprovalMode.COUNTERSIGN) {
        // 与「会签审批」类型共用同一实现（见 checkCountersignAggregation）
        checkCountersignAggregation(node, approval.assignee, approval.passRule, add)
      }
      checkRejectTarget(dsl, node, approval.reject, add)
      checkDeadline(node, approval.deadline, add)
      break
    }

    case NodeType.TASK: {
      const task = node as TaskNode
      checkAssignee(task.assignee, true, '办理人')
      checkDeadline(node, task.deadline, add)
      break
    }

    case NodeType.SERVICE: {
      const service = node as ServiceNode
      if (!service.serviceRef && !service.expression) {
        add({
          code: IssueCode.SERVICE_NOT_SET,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `自动服务节点「${node.name}」未选择服务或填写表达式`,
        })
      }
      if (service.expression) {
        const illegal = illegalFunctions(service.expression)
        if (illegal.length > 0) {
          add({
            code: IssueCode.EXPRESSION_NOT_ALLOWED,
            level: IssueLevel.ERROR,
            nodeId: node.id,
            message: `节点「${node.name}」的表达式使用了白名单外的函数: ${illegal.join(', ')}`,
          })
        }
      }
      break
    }

    case NodeType.NOTIFY:
      checkAssignee((node as NotifyNode).recipients, true, '通知接收人')
      break

    case NodeType.SUB_PROCESS:
      if (!(node as SubProcessNode).processKey) {
        add({
          code: IssueCode.SUB_PROCESS_NO_KEY,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `子流程节点「${node.name}」未选择被调用流程`,
        })
      }
      break

    case NodeType.TIMER: {
      const timer = node as TimerNode
      const missing =
        timer.mode === TimerMode.DURATION ? !timer.duration : !timer.at
      if (missing) {
        add({
          code: IssueCode.TIMER_NOT_SET,
          level: IssueLevel.ERROR,
          nodeId: node.id,
          message: `定时节点「${node.name}」未设置${timer.mode === TimerMode.DURATION ? '等待时长' : '目标时点'}`,
        })
      }
      break
    }

    default:
      break
  }
}

function checkDeadline(
  node: FlowNode,
  deadline: ApprovalNode['deadline'],
  add: Add,
): void {
  if (!deadline) {
    return
  }
  if (deadline.action === OverdueAction.REASSIGN && !hasAssignee(deadline.reassignTo)) {
    add({
      code: IssueCode.DEADLINE_REASSIGN_NO_TARGET,
      level: IssueLevel.ERROR,
      nodeId: node.id,
      message: `节点「${node.name}」的逾期后果为「重新分配」但未指定升级对象`,
    })
  }
}

function nodeExists(dsl: FlowDsl, nodeId?: string): boolean {
  return !!nodeId && dsl.nodes.some((n) => n.id === nodeId)
}
