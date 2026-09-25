/**
 * 「设置审批人」活动 —— 任务表单模型（纯函数，零 DOM，可单测）。
 *
 * <p><b>业务定位</b>：该活动固定是<b>流程启动后的第一个节点</b>，由发起人（流程 owner）
 * 执行，一次性把后面所有人工活动的人一次指定好 —— 即下游的<b>审批节点 / 会签节点 /
 * 办理节点</b>（见 {@link SETUP_COVERED_TYPES}）。
 *
 * <p><b>表单项从流程自动得出，不需要活动自己声明</b>。这里记一笔教训：最初把覆盖规则做成
 * "下游活动显式声明一个 `SETUP` 策略"，结果是"流程里明明有审批节点、表单却一项都没有"，
 * 还会报出一条「下游没有需要指定人员的活动」—— 覆盖与否是**类型 + 可达性**的推论，
 * 不该让使用者去声明。
 *
 * <p><b>人选范围 = 该活动指定的角色</b>：每个槽位让发起人在该活动的角色成员里挑人
 * （会签至少两人）。活动没指定角色时退化为全体用户，并在槽位上如实说明 ——
 * 不静默给一个空列表（那会让发起人以为"没人可派"）。
 *
 * <p><b>几个人的事就几个人选</b>：控件是单选还是多选由 {@link allowMultipleOf} 决定 ——
 * 单人拍板的审批、一件事一个人办的办理只需一人；会签与多签审批才给多选。
 * 让所有槽位一律多选，等于在每个"一个人的决定"上都留一个"可以选好几个"的错觉。
 *
 * <p>「谁被设置活动覆盖」的判定<b>唯一实现在 dsl-core</b>（`setupControlledIds` /
 * `participantSpecOf`），本文件只做表单层面的派生与校验 —— 否则会出现
 * 「校验说没人要指定、表单却列出了活动」这种两处真相。
 */

import {
  ApprovalMode,
  AssigneeStrategy,
  NodeType,
  SETUP_COVERED_TYPES,
  participantSpecOf,
  type FlowDsl,
  type FlowNode,
} from '@flow-dsl-core'
import { RUNTIME_VARIABLES } from '@flow-compiler'

/** 槽位措辞：决定表单上写「审批人」还是「会签人」 */
export type SetupSlotKind = '审批人' | '会签人' | '办理人' | '参与人'

export interface AssigneeSetupSlot {
  nodeId: string
  nodeName: string
  nodeType: FlowNode['type']
  /** 表单措辞（审批人 / 会签人 / 办理人） */
  kind: SetupSlotKind
  /** 至少需要几人（会签在业务上至少两人） */
  minPeople: number
  /**
   * 该活动指定的角色 code —— 即**候选范围**。
   *
   * <p>为空表示该活动没有用角色策略：候选退化为全体用户，表单上会注明这一点。
   */
  roleCodes: string[]
  /**
   * 该活动的运行期变量取<b>单个人员标识</b>（true）还是<b>人员标识列表</b>（false）。
   * 人员标识 = 用户名（见 {@link toSetupVariables}）。
   *
   * <p>由编译产物的形态决定（见 bpmn-compiler/compile.ts）：单签审批输出
   * {@code flowable:assignee="${...}"} → 单个 oid；会签 / 并行 / 或签输出 {@code collection}、
   * 办理节点输出 {@code candidateUsers} → 列表。形态错了下游任务会拿不到人
   * （或把列表当成一个人的 id），所以这条规则收口在这里、由单测锁定。
   */
  singleValue: boolean
  /**
   * 该活动是否<b>允许多人</b>（决定表单控件是单选还是多选）。
   *
   * <p>与会签 / 多签审批对应，见 {@link allowMultipleOf}。与 {@link singleValue} 不是同一件事：
   * 那是"变量形态"，这是"这件事由几个人办"。
   */
  allowMultiple: boolean
  /** 从设置活动起算的流程步数（表单按此排序，即「按流程顺序」） */
  order: number
}

export interface AssigneeSetupModel {
  slots: AssigneeSetupSlot[]
  /** 构建期提示（如某些活动没有角色约束，候选范围因此放宽） */
  warnings: string[]
}

/**
 * 填写值：活动 id → 人员标识列表（列表顺序即串行审批的先后顺序）。
 *
 * <p>人员标识 = {@code ck_user.username}（Flowable 侧 userId 就是用户名，见后端
 * {@code ProcessIdentitySupport}）；字段名沿用 {@code userOids} 是为了与 DSL 里已持久化的
 * 同名字段保持一致，<b>不要按字面理解成"必须是 oid"</b>。
 */
export type AssigneeSetupValues = Record<string, string[]>

/** 提交给运行期的载荷（一项一个活动）；{@code userOids} 是人员标识（用户名）列表 */
export interface AssigneeSetupPayload {
  nodeId: string
  userOids: string[]
}

export interface AssigneeSetupIssue {
  nodeId: string
  message: string
}

const KIND_BY_TYPE: Partial<Record<FlowNode['type'], SetupSlotKind>> = {
  [NodeType.APPROVAL]: '审批人',
  [NodeType.COUNTERSIGN_APPROVAL]: '会签人',
  [NodeType.TASK]: '办理人',
}

/** 该活动最少需要几人。会签是集体决策，一人不成会签 */
function minPeopleOf(node: FlowNode): number {
  return node.type === NodeType.COUNTERSIGN_APPROVAL ? 2 : 1
}

/**
 * 该活动的角色（候选范围）。
 *
 * <p>只有「指定角色」策略才构成角色约束：别的策略（指定用户 / 发起人 / 部门主管…）
 * 并没有声明"这件事该由哪一类岗位来做"，此时候选人只能放宽到全体用户。
 */
function roleCodesOf(node: FlowNode): string[] {
  const spec = participantSpecOf(node)
  if (spec?.strategy !== AssigneeStrategy.ROLE) {
    return []
  }
  return spec.roleCodes ?? []
}

/**
 * 该活动的变量取单个 oid 还是列表。
 *
 * <p>只有<b>单签审批</b>是单个：编译产物给它输出 {@code flowable:assignee="${...}"}；
 * 会签 / 并行 / 或签走多实例 {@code collection}，办理节点走 {@code candidateUsers}，都是列表。
 */
function singleValueOf(node: FlowNode): boolean {
  return node.type === NodeType.APPROVAL && node.approvalMode === ApprovalMode.SINGLE
}

/**
 * 该活动允许多人（表单给多选），还是只由一人办（表单给单选）。
 *
 * <p>业务口径：
 * <ul>
 *   <li><b>会签</b>：天生的多人集体决策 → 多选（且至少两人，见 {@link minPeopleOf}）；</li>
 *   <li><b>审批</b>：单签是一人拍板 → 单选；串行 / 并行全员 / 或签本身就是"多人参与"的模式
 *       （编译产物是 {@code collection}），把它们锁成一人等于让这个模式失去意义 → 多选；</li>
 *   <li><b>办理</b>：一件事一个人办 → 单选（编译产物给 {@code candidateUsers}，
 *       给一个人就是指定了他；多人办理应当用会签或并行活动表达）。</li>
 * </ul>
 */
function allowMultipleOf(node: FlowNode): boolean {
  if (node.type === NodeType.COUNTERSIGN_APPROVAL) {
    return true
  }
  if (node.type === NodeType.APPROVAL) {
    return node.approvalMode !== ApprovalMode.SINGLE
  }
  return false
}

/**
 * 从某节点做广度优先遍历，返回「可达节点 → 步数」。
 *
 * <p>一并得到两件事：哪些活动在本活动下游（归它管）、以及流程顺序（表单排序）。
 */
function flowOrderFrom(dsl: FlowDsl, fromId: string): Map<string, number> {
  const distance = new Map<string, number>([[fromId, 0]])
  const queue: string[] = [fromId]
  while (queue.length > 0) {
    const current = queue.shift() as string
    const next = (distance.get(current) ?? 0) + 1
    for (const edge of dsl.edges) {
      if (edge.source !== current || distance.has(edge.target)) {
        continue
      }
      distance.set(edge.target, next)
      queue.push(edge.target)
    }
  }
  return distance
}

/**
 * 构建「设置审批人」任务表单的结构。
 *
 * <p>下游所有审批 / 会签 / 办理活动都会自动成为槽位，按流程顺序排列，无需任何手工同步。
 */
export function buildAssigneeSetupModel(dsl: FlowDsl, setterNodeId: string): AssigneeSetupModel {
  const reachable = flowOrderFrom(dsl, setterNodeId)
  const slots: AssigneeSetupSlot[] = []

  for (const node of dsl.nodes) {
    if (!SETUP_COVERED_TYPES.includes(node.type)) {
      continue
    }
    const order = reachable.get(node.id)
    if (order === undefined) {
      // 不在本活动下游：不归它管（可能是它之前执行的，或另一条分支上的）
      continue
    }
    slots.push({
      nodeId: node.id,
      nodeName: node.name,
      nodeType: node.type,
      kind: KIND_BY_TYPE[node.type] ?? '参与人',
      minPeople: minPeopleOf(node),
      roleCodes: roleCodesOf(node),
      singleValue: singleValueOf(node),
      allowMultiple: allowMultipleOf(node),
      order,
    })
  }

  slots.sort((a, b) => a.order - b.order || a.nodeName.localeCompare(b.nodeName))

  const noRole = slots.filter((slot) => slot.roleCodes.length === 0)
  const warnings: string[] = []
  if (noRole.length > 0) {
    warnings.push(
      `以下活动未指定角色，候选人为全部用户：${noRole.map((s) => `「${s.nodeName}」`).join('、')}` +
        '（在活动属性里把参与人策略设为「指定角色」，候选范围就会收敛到该角色成员）',
    )
  }

  return { slots, warnings }
}

/**
 * 校验填写结果（提交前置校验）。
 *
 * <p>三条口径：没填 → 提示去填；单选的活动（审批单签 / 办理）多于一人 → 拦下
 * （多出来的那个会被运行期丢掉，与其静默丢弃不如说清楚）；会签少于 minPeople → 提示人数不够。
 */
export function validateAssigneeSetup(
  model: AssigneeSetupModel,
  values: AssigneeSetupValues,
): AssigneeSetupIssue[] {
  const issues: AssigneeSetupIssue[] = []
  for (const slot of model.slots) {
    const picked = dedupe(values[slot.nodeId] ?? [])
    if (picked.length === 0) {
      issues.push({ nodeId: slot.nodeId, message: `请为「${slot.nodeName}」指定${slot.kind}` })
      continue
    }
    if (!slot.allowMultiple && picked.length > 1) {
      issues.push({
        nodeId: slot.nodeId,
        message: `「${slot.nodeName}」是${slot.kind}活动，只能指定 1 人（当前 ${picked.length} 人）`,
      })
      continue
    }
    if (picked.length < slot.minPeople) {
      issues.push({
        nodeId: slot.nodeId,
        message: `「${slot.nodeName}」是${slot.kind}活动，至少需要 ${slot.minPeople} 人（当前 ${picked.length} 人）`,
      })
    }
  }
  return issues
}

/** 转成提交载荷（已去重；未填的槽位不出现，由校验拦住） */
export function toSetupPayload(
  model: AssigneeSetupModel,
  values: AssigneeSetupValues,
): AssigneeSetupPayload[] {
  return model.slots
    .map((slot) => ({ nodeId: slot.nodeId, userOids: dedupe(values[slot.nodeId] ?? []) }))
    .filter((item) => item.userOids.length > 0)
}

/**
 * 表单值 → 运行期流程变量：`ckplmSetupAssignees_<活动id>` → 单个人员标识或列表。
 *
 * <p>人员标识 = 用户名（Flowable userId 口径）：下游 BPMN 用
 * {@code flowable:assignee="${变量}"} / {@code collection} / {@code candidateUsers} 取它，
 * 引擎再拿它跟"我的任务"里的当前用户比对 —— 写成 oid 就会出现"负责人在进度里看得到自己、
 * 待办里却什么都没有"。
 *
 * <p>随"任务完成"一起提交，<b>必须在任务完成时就进入流程作用域</b>：下游任务的负责人
 * 是在那一刻求值的，晚一步（或事后补写）下游就已经"无人可办"了。
 *
 * <p>未填的槽位不产生变量 —— 由 {@link validateAssigneeSetup} 在提交前拦住。
 */
export function toSetupVariables(
  model: AssigneeSetupModel,
  values: AssigneeSetupValues,
): Record<string, string | string[]> {
  const variables: Record<string, string | string[]> = {}
  for (const item of toSetupPayload(model, values)) {
    const slot = model.slots.find((candidate) => candidate.nodeId === item.nodeId)
    variables[`${RUNTIME_VARIABLES.SETUP_ASSIGNEES}${item.nodeId}`] =
      slot?.singleValue ? item.userOids[0] : item.userOids
  }
  return variables
}

function dedupe(list: string[]): string[] {
  return Array.from(new Set(list.filter(Boolean)))
}
