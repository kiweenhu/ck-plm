/**
 * CK-PLM 流程 DSL · zod schema 与类型推导（契约层，唯一事实源）
 *
 * <p>两条硬约束（spec §1）：
 * <ul>
 *   <li><b>DSL 是唯一事实源</b>：画布、面板、校验、编译全部读写本 schema 描述的结构。</li>
 *   <li><b>坐标不进语义</b>：画布坐标独立存 {@link Layout}，节点/连线里不出现 x/y。</li>
 * </ul>
 *
 * <p>纯 schema 声明，零 DOM / 零浏览器依赖，可直接单测。
 */

import { z } from 'zod'
import {
  AbstainPolicy,
  ApprovalMode,
  AssigneeStrategy,
  ConditionMode,
  ConditionOperator,
  DeadlineAnchor,
  DSL_VERSION,
  EdgeKind,
  EdgeRoute,
  EndCallbackKind,
  NodeType,
  NotifyChannel,
  OaAuthType,
  OverdueAction,
  PassRuleMode,
  RejectTarget,
  TimerMode,
  ValueType,
} from './constants'

/** 由 `{A:'A',B:'B'}` 常量对象生成同值 zod enum，避免枚举字面量二次维护 */
function enumSchema<T extends string>(values: Record<string, T>): z.ZodEnum<[T, ...T[]]> {
  return z.enum(Object.values(values) as [T, ...T[]])
}

// ==================== 审批人策略 ====================

/**
 * 审批人策略描述。
 *
 * <p>`strategy` 决定 `fallback` 之外哪些字段必填；该约束属业务规则，
 * 放在 {@link ./validate} 中（结构 schema 只校验类型）。
 */
export interface AssigneeSpec {
  strategy: AssigneeStrategy
  /** strategy=USER 时的用户 oid 列表 */
  /**
   * 指定用户策略的人员列表。
   *
   * <p>存的是 <b>Flowable 用户标识 = {@code ck_user.username}</b>（不是人员 oid）：
   * 编译后直接作为 {@code flowable:assignee} / {@code candidateUsers} 的值，引擎拿它跟
   * 当前登录用户比对。字段名保留 {@code userOids} 是因为它已随 DSL 持久化，改名会打断历史模板。
   */
  userOids?: string[]
  /** strategy=ROLE / PROJECT_ROLE 时的角色 code 列表 */
  roleCodes?: string[]
  /** strategy=VARIABLE 时的流程变量名 */
  variableName?: string
  /** strategy=EXPRESSION 时的受控表达式 */
  expression?: string
  /** strategy=DEPT_LEADER 时，业务对象上承载部门的字段 key */
  deptFieldKey?: string
  /** 兜底策略：解析为空时降级使用（可递归） */
  fallback?: AssigneeSpec
}

const assigneeSchema: z.ZodType<AssigneeSpec> = z.lazy(() =>
  z.object({
    strategy: enumSchema(AssigneeStrategy),
    userOids: z.array(z.string()).optional(),
    roleCodes: z.array(z.string()).optional(),
    variableName: z.string().optional(),
    expression: z.string().optional(),
    deptFieldKey: z.string().optional(),
    fallback: assigneeSchema.optional(),
  }),
)

// ==================== 会签通过规则 ====================

/** 会签聚合规则（spec §7 冻结项 1） */
export interface PassRule {
  mode: PassRuleMode
  /** mode=PERCENT：通过比例（0-100） */
  percent?: number
  /** mode=COUNT：必须通过票数 */
  count?: number
  /** 一票否决开关（mode=VETO 时恒为 true） */
  veto?: boolean
  /** 弃权处理 */
  abstain?: AbstainPolicy
}

const passRuleSchema: z.ZodType<PassRule> = z.object({
  mode: enumSchema(PassRuleMode),
  percent: z.number().min(0).max(100).optional(),
  count: z.number().int().min(1).optional(),
  veto: z.boolean().optional(),
  abstain: enumSchema(AbstainPolicy).optional(),
})

// ==================== 截止时间与逾期后果 ====================

/** 节点截止时间与逾期后果（spec §7 冻结项 3） */
export interface Deadline {
  /** 计时起点 */
  anchor: DeadlineAnchor
  /** 时长（小时），大于 0 */
  durationHours: number
  /** 逾期后果 */
  action: OverdueAction
  /** action=REASSIGN 时的升级对象 */
  reassignTo?: AssigneeSpec
  /** 截止前提醒（小时） */
  remindBeforeHours?: number
  /** 截止后催办（小时） */
  remindAfterHours?: number
}

const deadlineSchema: z.ZodType<Deadline> = z.object({
  anchor: enumSchema(DeadlineAnchor),
  durationHours: z.number().positive(),
  action: enumSchema(OverdueAction),
  reassignTo: assigneeSchema.optional(),
  remindBeforeHours: z.number().nonnegative().optional(),
  remindAfterHours: z.number().nonnegative().optional(),
})

// ==================== 驳回 ====================

/**
 * 驳回配置（spec §7 冻结项 2）。
 *
 * <p>与「通过路由」的关系：审批节点天然有两条路由 ——
 * <ul>
 *   <li><b>通过</b>：画布上从本节点连出的普通出边（DSL 里就是 {@link FlowEdge}）；</li>
 *   <li><b>驳回</b>：本对象声明的运行期路由。之所以不画成一条出边：
 *       驳回目标是<b>运行期决定</b>的（发起人 / 上一步 / 指定节点），
 *       而 {@link RejectTarget} 是<b>与后端联合冻结的编码</b>，
 *       若同时用出边表达目标，就会出现「边指向 A、声明写着 B」的两处真相。</li>
 * </ul>
 */
export interface RejectSpec {
  /** 本节点允不允许驳回（办理页据此显示「驳回」） */
  enabled: boolean
  /**
   * 驳回<strong>退回到哪</strong>（节点上的一步跳回）。
   *
   * <p><b>可选</b>：驳回还有另一种做法 —— 从节点连出一条「驳回」出边（见 {@link FlowEdge.route}），
   * 那条边上可以先挂自动化节点（设置状态、撤回电子签名…）再回去。
   * 两种做法是同一件事的两种表达，必须二选一：
   * <ul>
   *   <li>配了 {@code target} → 走运行期跳转（简单，但只能一步退回）；</li>
   *   <li>画了「驳回」出边 → 走图（能做别的事，但要多画几个节点）。</li>
   * </ul>
   * 校验会拦住"两个都配"（EDGE_ROUTE_CONFLICT）与"两个都没有"（REJECT_NO_TARGET）。
   */
  target?: RejectTarget
  /** target=NODE 时的目标节点 id */
  targetNodeId?: string
  /**
   * 驳回时是否必须填写意见。
   *
   * <p>与 {@link ApprovalNode.commentRequired}（审批意见必填，对「通过」同样生效）刻意区分：
   * 本项只约束「驳回」这一个动作 —— 驳回必须说明理由。
   * 未显式设置时按 <b>true</b> 处理（审批实务的通行要求）。
   */
  commentRequired?: boolean
}

const rejectSchema: z.ZodType<RejectSpec> = z.object({
  // 开关：本节点允不允许驳回（办理页据此显示「驳回」）
  enabled: z.boolean(),
  target: enumSchema(RejectTarget).optional(),
  targetNodeId: z.string().optional(),
  // 必须声明：zod 默认会「丢弃」未声明的键，漏了这条会让存盘再读回时该配置消失
  commentRequired: z.boolean().optional(),
})

// ==================== 通知方式（流程级）====================

/**
 * 邮件渠道：企业邮件服务器（SMTP）。
 *
 * <p>字段都<b>可选</b>：允许"先选通道、稍后再补配置"（保存不中断设计）。
 * 完整性由校验提示（选了邮件却没填服务器 = 运行期一定发不出去）。
 */
export interface EmailChannelConfig {
  /** 企业邮件服务器地址（SMTP 主机） */
  smtpHost?: string
  /** 端口（如 465 / 587；留空由实现取默认） */
  smtpPort?: number
  /** 发件人地址（收件人看到的 From） */
  sender?: string
  /** 认证账号 */
  username?: string
  /** 认证密码 / 授权码 */
  password?: string
  /** 是否使用 SSL/TLS */
  ssl?: boolean
}

/** OA 渠道：集成的 RESTful API */
export interface OaChannelConfig {
  /** 发送通知的 RESTful API 地址 */
  apiUrl?: string
  authType?: OaAuthType
  /** authType=BASIC：账号 */
  username?: string
  /** authType=BASIC：密码 */
  password?: string
  /** authType=TOKEN：令牌 */
  token?: string
}

/**
 * 即时通讯渠道（飞书 / 钉钉 / 企业微信）。
 *
 * <p>三家形态相似但字段叫法不同，这里共用一套存储形态、<b>面板上按渠道给不同的标签</b>
 * （飞书：App ID/Secret；钉钉：AppKey/Secret；企业微信：企业 ID/应用 ID/Secret），
 * 免得为"名字不同"建三份几乎一样的结构。群机器人只需 Webhook（+ 加签密钥）即可用。
 */
export interface ImChannelConfig {
  /** 群机器人 Webhook 地址 */
  webhook?: string
  /** 机器人签名密钥（钉钉加签 / 飞书校验） */
  secret?: string
  /** 应用 ID：飞书 App ID / 钉钉 AppKey */
  appId?: string
  /** 应用密钥：飞书 App Secret / 钉钉 AppSecret */
  appSecret?: string
  /** 企业 ID（企业微信 corpId） */
  corpId?: string
  /** 应用 ID（企业微信 agentId） */
  agentId?: string
}

/**
 * 「通知方式」—— 流程模板级：这个流程允许从哪些通道发通知，以及各通道的企业集成信息。
 *
 * <p><b>为什么在流程级而不在通知节点上</b>：通道即企业集成（邮件服务器 / OA 的 RESTful API /
 * 飞书·钉钉·企业微信的凭据），同一套配置会被多个流程共用 —— 放在节点上就等于
 * "每个流程各填一遍 SMTP 密码"，改一处要改所有流程。节点上配的是"发给谁"。
 *
 * <p>只声明结构；"选了渠道但没填连接信息"属业务规则，放 {@link ./validate} 提示。
 */
export interface NotificationConfig {
  /**
   * 通知方式（<b>单选</b>）：本流程用哪一种渠道发通知。
   *
   * <p>为什么单选：渠道是"这条流程的通知出口"，一个流程同时往邮件、OA、飞书各发一份
   * 通常不是设计者的本意，而是勾选时顺手勾多了 —— 单选让"走哪个出口"成为必须明确的一件事。
   * 要一次发多个出口，那是系统侧的事（{@code plm.notification.channels}），不是每个流程各说一套。
   *
   * <p>留空表示"跟随系统配置"—— 系统启用什么就用什么。
   */
  channel?: NotifyChannel
  /**
   * @deprecated 单通道化之前的形态（多选数组）。只为兼容历史模板的解析保留：
   * {@code parseFlowDsl} 会取第一个作为 {@link channel}，之后全链路只认新形态。
   */
  channels?: NotifyChannel[]
  /**
   * @deprecated 渠道凭据已收归<b>服务端配置</b>（application.yml 的 {@code plm.notification}）。
   * 这几个字段只为兼容历史模板保留解析，新保存不再产出，也不再参与校验与运行。
   */
  email?: EmailChannelConfig
  /** @deprecated 同上（凭据见服务端 plm.notification.oa） */
  oa?: OaChannelConfig
  /** @deprecated 同上（凭据见服务端 plm.notification.feishu） */
  feishu?: ImChannelConfig
  /** @deprecated 同上（凭据见服务端 plm.notification.dingtalk） */
  dingtalk?: ImChannelConfig
  /** @deprecated 同上（凭据见服务端 plm.notification.wecom） */
  wecom?: ImChannelConfig
}

const emailChannelSchema: z.ZodType<EmailChannelConfig> = z.object({
  smtpHost: z.string().optional(),
  smtpPort: z.number().int().positive().optional(),
  sender: z.string().optional(),
  username: z.string().optional(),
  password: z.string().optional(),
  ssl: z.boolean().optional(),
})

const oaChannelSchema: z.ZodType<OaChannelConfig> = z.object({
  apiUrl: z.string().optional(),
  authType: enumSchema(OaAuthType).optional(),
  username: z.string().optional(),
  password: z.string().optional(),
  token: z.string().optional(),
})

const imChannelSchema: z.ZodType<ImChannelConfig> = z.object({
  webhook: z.string().optional(),
  secret: z.string().optional(),
  appId: z.string().optional(),
  appSecret: z.string().optional(),
  corpId: z.string().optional(),
  agentId: z.string().optional(),
})

const notificationConfigSchema: z.ZodType<NotificationConfig> = z.object({
  // 单选；channels 是历史多选形态（见 NotificationConfig 说明），只读入不写出
  channel: enumSchema(NotifyChannel).optional(),
  channels: z.array(enumSchema(NotifyChannel)).optional(),
  email: emailChannelSchema.optional(),
  oa: oaChannelSchema.optional(),
  feishu: imChannelSchema.optional(),
  dingtalk: imChannelSchema.optional(),
  wecom: imChannelSchema.optional(),
})

// ==================== 条件 ====================

/** 条件：默认字段选择器，高级模式才允许表达式 */
export interface Condition {
  mode: ConditionMode
  /** mode=FIELD：业务对象类型 code（如 PART / DOCUMENT / ECR） */
  objectType?: string
  /** mode=FIELD：字段 key */
  fieldKey?: string
  operator?: ConditionOperator
  /** 右侧取值（IS_EMPTY 类操作符可缺省） */
  value?: string | number | boolean | string[]
  valueType?: ValueType
  /** mode=EXPRESSION：受控表达式 */
  expression?: string
}

const conditionSchema: z.ZodType<Condition> = z.object({
  mode: enumSchema(ConditionMode),
  objectType: z.string().optional(),
  fieldKey: z.string().optional(),
  operator: enumSchema(ConditionOperator).optional(),
  value: z.union([z.string(), z.number(), z.boolean(), z.array(z.string())]).optional(),
  valueType: enumSchema(ValueType).optional(),
  expression: z.string().optional(),
})

// ==================== 结束回调 ====================

export interface EndCallback {
  kind: EndCallbackKind
  /** kind=OBJECT_STATE：目标生命周期状态 code */
  targetState?: string
  /** kind=NOTIFY：通知接收人 */
  recipients?: AssigneeSpec
  /** kind=NOTIFY：通知模板 code */
  templateCode?: string
}

const endCallbackSchema: z.ZodType<EndCallback> = z.object({
  kind: enumSchema(EndCallbackKind),
  targetState: z.string().optional(),
  recipients: assigneeSchema.optional(),
  templateCode: z.string().optional(),
})

// ==================== 业务对象绑定 ====================

/** 节点绑定的主业务对象与必填字段（spec §4-C P0） */
export interface ObjectBinding {
  /** 业务对象类型 code */
  objectType: string
  /** 必填字段 key 列表 */
  requiredFields?: string[]
}

const objectBindingSchema: z.ZodType<ObjectBinding> = z.object({
  objectType: z.string().min(1),
  requiredFields: z.array(z.string()).optional(),
})

// ==================== 流程变量 ====================

export interface VariableDef {
  name: string
  label?: string
  type: ValueType
  defaultValue?: string | number | boolean
  /** 是否在任务页可见 */
  visible?: boolean
  /** 是否只读 */
  readonly?: boolean
  /** 是否允许任务中重写 */
  writable?: boolean
}

const variableSchema: z.ZodType<VariableDef> = z.object({
  name: z.string().min(1),
  label: z.string().optional(),
  type: enumSchema(ValueType),
  defaultValue: z.union([z.string(), z.number(), z.boolean()]).optional(),
  visible: z.boolean().optional(),
  readonly: z.boolean().optional(),
  writable: z.boolean().optional(),
})

// ==================== 节点 ====================

interface NodeBase {
  /** 节点唯一 id（模板内稳定，供布局与驳回目标引用） */
  id: string
  type: NodeType
  name: string
  description?: string
}

export interface StartNode extends NodeBase {
  type: 'START'
  initiator: AssigneeSpec
  /** 发起表单（CK-PLM 表单引擎 code） */
  formRef?: string
  /** 是否允许自动发起（由对象事件触发） */
  autoStart?: boolean
  /** 绑定的主业务对象 */
  binding?: ObjectBinding
}

export interface EndNode extends NodeBase {
  type: 'END'
  callbacks?: EndCallback[]
}

export interface ApprovalNode extends NodeBase {
  type: 'APPROVAL'
  assignee: AssigneeSpec
  approvalMode: ApprovalMode
  /** approvalMode=COUNTERSIGN 时必填（业务规则在 validate 中校验） */
  passRule?: PassRule
  /** 是否必须填写审批意见 */
  commentRequired?: boolean
  /** 截止时间与逾期后果 */
  deadline?: Deadline
  /** 驳回策略 */
  reject?: RejectSpec
  /** 业务对象绑定 */
  binding?: ObjectBinding
  /** 节点表单 */
  formRef?: string
}

export interface TaskNode extends NodeBase {
  type: 'TASK'
  assignee: AssigneeSpec
  formRef?: string
  /** 交付物（文档类型 code 列表） */
  deliverables?: string[]
  deadline?: Deadline
  binding?: ObjectBinding
}

export interface ServiceNode extends NodeBase {
  type: 'SERVICE'
  /** SERVICE 白名单服务 id（与 expression 二选一） */
  serviceRef?: string
  /** 服务参数：参数名 → 取值（字面量或 ${var} 变量引用） */
  params?: Record<string, string>
  /** 高级：受控表达式 */
  expression?: string
}

export interface ExclusiveGatewayNode extends NodeBase {
  type: 'EXCLUSIVE_GATEWAY'
  /** 是否启用高级表达式模式（默认字段选择器） */
  advanced?: boolean
}

export interface ParallelGatewayNode extends NodeBase {
  type: 'PARALLEL_GATEWAY'
}

export interface InclusiveGatewayNode extends NodeBase {
  type: 'INCLUSIVE_GATEWAY'
  advanced?: boolean
}

export interface NotifyNode extends NodeBase {
  type: 'NOTIFY'
  templateCode?: string
  recipients: AssigneeSpec
  /** 是否附带主业务对象链接 */
  attachPrimaryObject?: boolean
}

export interface SubProcessNode extends NodeBase {
  type: 'SUB_PROCESS'
  /** 被调用的流程定义 key */
  processKey: string
  /** 变量映射：子流程变量 → 父流程表达式 */
  variableMap?: Record<string, string>
}

export interface TimerNode extends NodeBase {
  type: 'TIMER'
  mode: TimerMode
  /** mode=DURATION：ISO-8601 时长（如 PT2H / P1D） */
  duration?: string
  /** mode=AT：目标时点（ISO-8601） */
  at?: string
}

/**
 * 会签审批节点。
 *
 * <p><b>为什么它不是「审批节点 + approvalMode=COUNTERSIGN」</b>：会签是业务上的
 * 「多人集体决策活动」，与单人审批是两种活动。独立成类型后，下游不必从
 * approvalMode 反推业务意图；校验也能表达会签特有的规则（必须给出聚合规则、
 * 审批人必须能解析出多人）。
 *
 * <p>与 {@link ApprovalNode} 的关系刻意用 `Omit` 表达而非复制字段：
 * 二者同构（assignee / 截止时间 / 驳回 / 绑定 / 表单），差别只有三点 ——
 * 没有 `approvalMode`（类型本身就是会签）、`passRule` 必需、`type` 不同。
 *
 * <p>编译产物与 `APPROVAL + COUNTERSIGN` 完全一致（共用发射器）。
 */
export interface CountersignNode extends Omit<ApprovalNode, 'type' | 'approvalMode' | 'passRule'> {
  type: 'COUNTERSIGN_APPROVAL'
  /** 会签聚合规则 —— 必需：会签必须回答「怎么算通过」（比例 / 票数 / 一票否决） */
  passRule: PassRule
}

/**
 * 设置审批人活动。
 *
 * <p>由发起人（流程 owner）执行，一次性指定后续人工活动的参与人。
 * 它<b>不产生审批结论</b>，只产生人员指派，因此没有 `reject`（驳回不适用于设置活动）。
 *
 * <p>「指定哪些活动」刻意<b>不在这里存储</b>：由下游活动用
 * {@link AssigneeStrategy}.SETUP 声明「我的人由设置活动指定」，两边靠策略约定关联。
 * 这样流程改动（新增/删除活动）不会留下悬空的目标引用。
 */
export interface SetAssigneeNode extends NodeBase {
  type: 'SET_ASSIGNEE'
  /** 执行人（默认发起人本人 —— 流程 owner 在发起后立刻指定后续人员） */
  assignee: AssigneeSpec
  /** 是否必须填写说明 */
  commentRequired?: boolean
  deadline?: Deadline
  binding?: ObjectBinding
  formRef?: string
}

export type FlowNode =
  | StartNode
  | EndNode
  | ApprovalNode
  | CountersignNode
  | SetAssigneeNode
  | TaskNode
  | ServiceNode
  | ExclusiveGatewayNode
  | ParallelGatewayNode
  | InclusiveGatewayNode
  | NotifyNode
  | SubProcessNode
  | TimerNode

const nodeBase = { id: z.string().min(1), name: z.string().min(1), description: z.string().optional() }

const flowNodeSchema: z.ZodType<FlowNode> = z.discriminatedUnion('type', [
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.START),
    initiator: assigneeSchema,
    formRef: z.string().optional(),
    autoStart: z.boolean().optional(),
    binding: objectBindingSchema.optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.END),
    callbacks: z.array(endCallbackSchema).optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.APPROVAL),
    assignee: assigneeSchema,
    approvalMode: enumSchema(ApprovalMode),
    passRule: passRuleSchema.optional(),
    commentRequired: z.boolean().optional(),
    deadline: deadlineSchema.optional(),
    reject: rejectSchema.optional(),
    binding: objectBindingSchema.optional(),
    formRef: z.string().optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.COUNTERSIGN_APPROVAL),
    assignee: assigneeSchema,
    // 必需（非 optional）：会签没有聚合规则就不成立，结构层就拦住
    passRule: passRuleSchema,
    commentRequired: z.boolean().optional(),
    deadline: deadlineSchema.optional(),
    reject: rejectSchema.optional(),
    binding: objectBindingSchema.optional(),
    formRef: z.string().optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.SET_ASSIGNEE),
    assignee: assigneeSchema,
    commentRequired: z.boolean().optional(),
    deadline: deadlineSchema.optional(),
    // 无 reject：设置活动不产生「驳回」这种结论
    binding: objectBindingSchema.optional(),
    formRef: z.string().optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.TASK),
    assignee: assigneeSchema,
    formRef: z.string().optional(),
    deliverables: z.array(z.string()).optional(),
    deadline: deadlineSchema.optional(),
    binding: objectBindingSchema.optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.SERVICE),
    serviceRef: z.string().optional(),
    params: z.record(z.string()).optional(),
    expression: z.string().optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.EXCLUSIVE_GATEWAY),
    advanced: z.boolean().optional(),
  }),
  z.object({ ...nodeBase, type: z.literal(NodeType.PARALLEL_GATEWAY) }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.INCLUSIVE_GATEWAY),
    advanced: z.boolean().optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.NOTIFY),
    templateCode: z.string().optional(),
    recipients: assigneeSchema,
    attachPrimaryObject: z.boolean().optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.SUB_PROCESS),
    processKey: z.string().min(1),
    variableMap: z.record(z.string()).optional(),
  }),
  z.object({
    ...nodeBase,
    type: z.literal(NodeType.TIMER),
    mode: enumSchema(TimerMode),
    duration: z.string().optional(),
    at: z.string().optional(),
  }),
]) as unknown as z.ZodType<FlowNode>

// ==================== 连线 ====================

/**
 * 流转连线。
 *
 * <p>设计取舍：<b>分支条件挂在连线上，而不是网关节点的 {@code branches[]}</b>。
 * 原因：BPMN 的 {@code conditionExpression} 本就属于 {@code sequenceFlow}（连线），
 * 一一对应才能让编译层零特判；网关的「N 个分支」由出边数量派生，
 * 「默认分支」即 {@code kind=DEFAULT} 的出边（编译为网关 {@code default} 属性）。
 */
/** 连线两端的锚点覆盖（手动指定；缺省 = 按路由自动分配） */
export interface EdgeAnchor {
  source?: string
  target?: string
}

/** 连线的折点（**图坐标**，与 `layout.nodes[].x/y` 同一坐标系） */
export interface EdgeWaypoint {
  x: number
  y: number
}

export interface FlowEdge {
  id: string
  source: string
  target: string
  kind: EdgeKind
  /**
   * 手动指定的附着锚点（画布表现，不参与 BPMN 语义）。
   *
   * <p>为什么不放在 `layout` 里：layout 存的是<b>会被程序改写</b>的几何（坐标、尺寸，
   * 自动排版会覆盖它们）；而锚点是用户<b>亲手指定</b>的意图，必须像分支名一样被保存与往返。
   * 缺省不填 = 按路由自动分配（通过→主锚点、驳回→下一个）。
   *
   * <p>锚点 id 形如 `bottom` / `bottom-2`（见 canvas 的 `portIdOf`）。
   * 若指定的锚点在该节点上已不存在（如节点类型被改小），渲染层<b>回退自动分配</b> ——
   * 画布表现绝不能因为一个锚点失效就让连线消失。
   */
  anchor?: EdgeAnchor
  /**
   * 手动折点（画布表现，不参与 BPMN 语义）。
   *
   * <p>与 {@link anchor} 同级，都是**用户意图**：用户拖过折角之后，自动走线与自动排版
   * 都不再覆盖这条线的走线（排版只挪节点，折点按原样保留）。缺省 / 空 = 完全由自动走线决定。
   *
   * <p>回到自动走线的入口：把这条线的起点/终点锚点改一次（改锚点会一并清掉折点）。
   */
  waypoints?: EdgeWaypoint[]
  /** 连线显示名（如「同意」/「拒绝」） */
  name?: string
  /**
   * 这条边承载的路由：通过 / 驳回（只对审批 / 会签节点有意义）。
   *
   * <p>声明之后条件由编译层生成（{@code ${approved}} / {@code ${!approved}}），
   * 用户不用手写 UEL；驳回那条边连到哪里，"驳回后先做什么"就由什么节点接续 ——
   * 这正是"驳回后要对 PES 集合设置状态 / 撤回电子签名"能表达出来的原因。
   */
  route?: EdgeRoute
  condition?: Condition
  /** 分支终止语义：触发后终止其余未完成前驱（spec §4-D P1） */
  terminateSiblings?: boolean
}

const flowEdgeSchema: z.ZodType<FlowEdge> = z.object({
  id: z.string().min(1),
  source: z.string().min(1),
  target: z.string().min(1),
  kind: enumSchema(EdgeKind),
  // 必须显式声明：zod 默认会剥掉未声明的键，漏了就等于「保存一次锚点就丢」
  anchor: z
    .object({
      source: z.string().optional(),
      target: z.string().optional(),
    })
    .optional(),
  // 同上：折点也是用户意图，漏声明就会「保存一次折点就丢」
  waypoints: z
    .array(z.object({ x: z.number(), y: z.number() }))
    .optional(),
  name: z.string().optional(),
  route: enumSchema(EdgeRoute).optional(),
  condition: conditionSchema.optional(),
  terminateSiblings: z.boolean().optional(),
})

// ==================== 布局（与语义严格分离）====================

export interface NodeLayout {
  x: number
  y: number
  width?: number
  height?: number
}

/** 泳道（按角色分组的责任带，spec §4-A P1） */
export interface SwimlaneLayout {
  id: string
  label: string
  y: number
  height: number
}

/** 分组块（纯视觉折叠，不引入执行语义，spec §4-A P1） */
export interface GroupLayout {
  id: string
  label: string
  nodeIds: string[]
  collapsed?: boolean
}

/** 画布布局：只存几何，不存业务语义 */
export interface Layout {
  nodes: Record<string, NodeLayout>
  swimlanes?: SwimlaneLayout[]
  groups?: GroupLayout[]
}

const layoutSchema: z.ZodType<Layout> = z.object({
  nodes: z.record(
    z.object({
      x: z.number(),
      y: z.number(),
      width: z.number().optional(),
      height: z.number().optional(),
    }),
  ),
  swimlanes: z
    .array(
      z.object({
        id: z.string().min(1),
        label: z.string(),
        y: z.number(),
        height: z.number(),
      }),
    )
    .optional(),
  groups: z
    .array(
      z.object({
        id: z.string().min(1),
        label: z.string(),
        nodeIds: z.array(z.string()),
        collapsed: z.boolean().optional(),
      }),
    )
    .optional(),
})

// ==================== 模板元信息 ====================

export interface FlowMeta {
  /** 流程定义 key（部署到 Flowable 的 processDefinitionKey，须全局唯一且稳定） */
  key: string
  name: string
  displayName?: string
  category?: string
  description?: string
  // 模板级「主业务对象」已移除：一个流程可能关联多个业务实体，单一 code 表达不了，
  // 该关联改由 ProcessEntitySet（流程关联的业务实体集合）承担。
  // 存量 DSL 里残留的 primaryObjectType 会被下面的 schema 自动丢弃（z.object 默认剥掉未声明键），
  // 因此老模板仍能正常解析与保存。
  /**
   * 通知方式与企业集成配置（流程级）。
   *
   * <p>放 meta 而不是 DSL 根：它属于"模板属性"（面板不选中任何节点时编辑的就是 meta），
   * 而面板的写入语义是整对象替换 {@code dsl.meta} —— 放根上会被写进 meta、存盘再读回时被 schema 丢弃。
   */
  notifications?: NotificationConfig
  }

  const metaSchema: z.ZodType<FlowMeta> = z.object({
  key: z
    .string()
    .min(1)
    .regex(/^[A-Za-z][A-Za-z0-9_-]*$/, '流程 key 只能由字母、数字、下划线、连字符组成，且以字母开头'),
  name: z.string().min(1),
  displayName: z.string().optional(),
  category: z.string().optional(),
  description: z.string().optional(),
  // 必须声明：zod 默认丢弃未声明的键，漏了这条会让"通知方式"存盘再读回时消失
  notifications: notificationConfigSchema.optional(),
  })

// ==================== 根文档 ====================

/** 流程模板 DSL 根文档 —— 模板持久化的唯一内容 */
export interface FlowDsl {
  dslVersion: string
  meta: FlowMeta
  variables?: VariableDef[]
  nodes: FlowNode[]
  edges: FlowEdge[]
  layout: Layout
}

/**
 * 根 schema。
 *
 * <p>类型参数写作 {@code ZodType<FlowDsl, ZodTypeDef, unknown>}：
 * 输出类型严格为 {@link FlowDsl}，输入放宽为 unknown ——
 * 因为 {@code dslVersion} 带 {@code .default()}，其<b>输入</b>允许缺省，
 * 而 {@link FlowDsl} 里它是必填（输出必定有值）。
 */
export const flowDslSchema: z.ZodType<FlowDsl, z.ZodTypeDef, unknown> = z.object({
  dslVersion: z.string().default(DSL_VERSION),
  meta: metaSchema,
  variables: z.array(variableSchema).optional(),
  nodes: z.array(flowNodeSchema),
  edges: z.array(flowEdgeSchema),
  layout: layoutSchema,
})

/**
 * 历史形态归一 —— 在<b>唯一的解析入口</b>做一次，之后全链路只认新形态。
 *
 * <p>两处历史遗留：
 * <ol>
 *   <li><b>通知方式曾挂在 DSL 根上</b>（后来挪到 {@code meta}，因为模板属性面板编辑的就是 meta，
 *       写整对象替换时根上的会被覆盖掉）—— 存量模板里可能还在根上，
 *       不搬过来用户在面板上就看不到自己配过的通知方式；</li>
 *   <li><b>{@code channels}（多选数组）已改为单选 {@code channel}</b> ——
 *       取第一个并丢掉其余。留着一个没人读的数组，下次就会有人把它当成"真的会发三个渠道"。</li>
 * </ol>
 *
 * <p>为什么放在这里而不是做成 zod 的 transform：{@code transform} 会改变
 * {@code flowDslSchema} 的出入类型，而它的类型是 {@code ZodType<FlowDsl>}（输入放宽为 unknown），
 * 加 transform 会让"同一份 JSON 解析前后类型不同"这件事渗到所有调用方。
 * 归一放在入口、schema 只管结构，两层各管一件事。
 */
function normalizeLegacyDsl(input: unknown): unknown {
  if (!input || typeof input !== 'object') {
    return input
  }
  const root = { ...(input as Record<string, unknown>) }
  const metaRaw = root.meta
  if (!metaRaw || typeof metaRaw !== 'object') {
    // meta 本身不是对象：交给 schema 去报"结构不合法"，这里不猜、不修
    return root
  }
  const meta = { ...(metaRaw as Record<string, unknown>) }

  // 1) 根上的 notifications 搬进 meta（meta 已有则以 meta 为准，避免两处都存在时来回改）
  if (root.notifications && !meta.notifications) {
    meta.notifications = root.notifications
  }
  delete root.notifications

  // 2) 多选 channels → 单选 channel
  const notifyRaw = meta.notifications
  if (notifyRaw && typeof notifyRaw === 'object') {
    const notify = { ...(notifyRaw as Record<string, unknown>) }
    const channels = notify.channels
    if (!notify.channel && Array.isArray(channels) && channels.length > 0) {
      notify.channel = channels[0]
    }
    delete notify.channels
    meta.notifications = notify
  }

  root.meta = meta
  root.nodes = stripStaleRejectTargets(root.nodes, root.edges)
  return root
}

/**
 * 已经画了「驳回」出边的节点，清掉它的「驳回目标」。
 *
 * <p>两者是同一件事的两种做法，必须二选一：留着会变成"看不见却仍然生效"的配置
 * （面板上那个字段已经因画了驳回边而隐藏），而且运行期两条腿都会想动手。
 *
 * <p>为什么必须在<b>加载时</b>做：面板的"隐藏即清空"只在编辑该节点时触发，
 * 而从<b>连线</b>面板画驳回边并不经过节点面板 —— 那时残留的目标没人清。
 */
function stripStaleRejectTargets(nodes: unknown, edges: unknown): unknown {
  if (!Array.isArray(nodes) || !Array.isArray(edges)) {
    return nodes
  }
  const rejectSources = new Set(
    edges
      .filter((edge) => !!edge && typeof edge === 'object'
        && (edge as Record<string, unknown>).route === EdgeRoute.REJECT)
      .map((edge) => (edge as Record<string, unknown>).source)
      .filter((source): source is string => typeof source === 'string'),
  )
  if (rejectSources.size === 0) {
    return nodes
  }
  return nodes.map((node) => {
    if (!node || typeof node !== 'object') {
      return node
    }
    const record = node as Record<string, unknown>
    if (!rejectSources.has(String(record.id))) {
      return node
    }
    const reject = record.reject
    if (!reject || typeof reject !== 'object' || !('target' in (reject as Record<string, unknown>))) {
      return node
    }
    const nextReject = { ...(reject as Record<string, unknown>) }
    delete nextReject.target
    delete nextReject.targetNodeId
    return { ...record, reject: nextReject }
  })
}

/** 校验任意 JSON 是否为合法 DSL（返回首个错误信息，供导入/加载时提示） */
export function parseFlowDsl(input: unknown): { ok: true; dsl: FlowDsl } | { ok: false; error: string } {
  const result = flowDslSchema.safeParse(normalizeLegacyDsl(input))
  if (result.success) {
    return { ok: true, dsl: result.data }
  }
  const first = result.error.issues[0]
  const path = first?.path?.join('.') ?? ''
  return { ok: false, error: `DSL 结构不合法${path ? `（${path}）` : ''}: ${first?.message ?? '未知错误'}` }
}

// ==================== 类型索引（按 type 收窄）====================

export interface NodeTypeMap {
  START: StartNode
  END: EndNode
  APPROVAL: ApprovalNode
  TASK: TaskNode
  SERVICE: ServiceNode
  EXCLUSIVE_GATEWAY: ExclusiveGatewayNode
  PARALLEL_GATEWAY: ParallelGatewayNode
  INCLUSIVE_GATEWAY: InclusiveGatewayNode
  NOTIFY: NotifyNode
  SUB_PROCESS: SubProcessNode
  TIMER: TimerNode
}
