/**
 * CK-PLM 流程 DSL · 枚举与常量（契约层，唯一事实源）
 *
 * <p>本文件是「前后端联合冻结」的三组扩展属性的落地形式（见
 * docs/ck-plm-flow-designer-spec.md §7）：
 * <ol>
 *   <li>会签聚合方式：{@link PassRuleMode} + {@link AbstainPolicy}</li>
 *   <li>驳回目标编码：{@link RejectTarget}</li>
 *   <li>逾期（超时）后果编码：{@link OverdueAction}</li>
 * </ol>
 * 编译层以此处编码为唯一映射依据；后端防御性校验须引用同一套编码。
 *
 * <p><b>纯常量，零依赖</b> —— 此文件不 import 任何运行时库。
 */

/** DSL 版本（写入模板 JSON，供 {@code migrate} 识别结构代际） */
export const DSL_VERSION = '1.0'

// ==================== 节点类型 ====================
//
// 12 类 = M0 冻结的 11 类 + 会签审批。
//
// <p>为什么「会签审批」是独立类型，而不是「审批节点的一个模式」：
// <b>节点类型是业务词汇，编译产物是技术实现</b>。会签是「多人的集体决策活动」
// （谁参与、按什么规则聚合、意见如何计票），单人审批是「一个人的决定」——
// 业务上本就是两个词条，不该让下游（任务中心、报表、审计、加签规则）
// 再从 approvalMode 反推业务意图。
//
// <p>技术侧仍是同一条产线：userTask + multiInstanceLoopCharacteristics，
// 且照旧写 ckplm:approvalMode=COUNTERSIGN + ckplm:passRule —— <b>后端契约零变更</b>。

export const NodeType = {
  START: 'START',
  END: 'END',
  APPROVAL: 'APPROVAL',
  /** 会签审批：多人的集体决策活动（与单人审批是两种业务活动，而非一个模式的两种取值） */
  COUNTERSIGN_APPROVAL: 'COUNTERSIGN_APPROVAL',
  /**
   * 设置审批人：由发起人（流程 owner）执行，一次性指定后续人工活动的人员。
   *
   * <p>业务上**固定是流程启动后的第一个节点**。它自己不产生审批结论，只产生
   * <b>人员指派</b>：下游的审批 / 会签 / 办理活动（见 {@link SETUP_COVERED_TYPES}）
   * 一律由它指定，无需各自声明。
   */
  SET_ASSIGNEE: 'SET_ASSIGNEE',
  TASK: 'TASK',
  SERVICE: 'SERVICE',
  EXCLUSIVE_GATEWAY: 'EXCLUSIVE_GATEWAY',
  PARALLEL_GATEWAY: 'PARALLEL_GATEWAY',
  INCLUSIVE_GATEWAY: 'INCLUSIVE_GATEWAY',
  NOTIFY: 'NOTIFY',
  SUB_PROCESS: 'SUB_PROCESS',
  TIMER: 'TIMER',
} as const
export type NodeType = (typeof NodeType)[keyof typeof NodeType]

export const NODE_TYPE_LABEL: Record<NodeType, string> = {
  START: '开始',
  END: '结束',
  APPROVAL: '审批节点',
  COUNTERSIGN_APPROVAL: '会签审批',
  SET_ASSIGNEE: '设置审批人',
  TASK: '办理节点',
  SERVICE: '函数调用',
  EXCLUSIVE_GATEWAY: '条件分支',
  PARALLEL_GATEWAY: '并行分支',
  INCLUSIVE_GATEWAY: '包容分支',
  NOTIFY: '通知',
  SUB_PROCESS: '子流程',
  TIMER: '定时等待',
}

export const ALL_NODE_TYPES: NodeType[] = Object.values(NodeType)

/** 网关类节点：出边承载分支条件 */
export const GATEWAY_TYPES: NodeType[] = [
  NodeType.EXCLUSIVE_GATEWAY,
  NodeType.PARALLEL_GATEWAY,
  NodeType.INCLUSIVE_GATEWAY,
]

/** 起止节点：各自唯一 */
export const START_TYPES: NodeType[] = [NodeType.START]
export const END_TYPES: NodeType[] = [NodeType.END]

/** 参与人节点：具备审批人/办理人策略 */
export const ASSIGNEE_NODE_TYPES: NodeType[] = [
  NodeType.APPROVAL,
  NodeType.COUNTERSIGN_APPROVAL,
  NodeType.SET_ASSIGNEE,
  NodeType.TASK,
  NodeType.NOTIFY,
]

/**
 * 「设置审批人」活动会覆盖的人工活动类型。
 *
 * <p><b>按类型自动覆盖，不需要活动自己声明</b>：设置活动的职责就是"指定后面所有人工活动
 * 的审批人 / 会签人 / 办理人"，这三类活动**天然需要人去办**，因此一律纳入它的任务表单。
 * 早先的版本要求下游活动声明一个 `SETUP` 策略才纳入 —— 结果是"流程里明明有审批节点、
 * 表单却一项都没有"，还会给出一条莫名其妙的"下游没有需要指定人员的活动"。
 *
 * <p>通知（{@link NodeType.NOTIFY}）不在此列：它的接收人由自身的接收人配置表达，
 * 不属于"审批 / 办理"这类必须由发起人指派的角色岗位。
 *
 * <p>本常量是"哪些活动会被设置活动覆盖"的**唯一事实源**，校验、任务表单、编译三处共用。
 */
export const SETUP_COVERED_TYPES: NodeType[] = [
  NodeType.APPROVAL,
  NodeType.COUNTERSIGN_APPROVAL,
  NodeType.TASK,
]

// ==================== 审批人策略 ====================

export const AssigneeStrategy = {
  /** 指定用户 */
  USER: 'USER',
  /** 指定角色 */
  ROLE: 'ROLE',
  /** 部门主管（依据业务对象上的部门字段解析） */
  DEPT_LEADER: 'DEPT_LEADER',
  /** 项目角色（产品线/型号上的团队成员角色） */
  PROJECT_ROLE: 'PROJECT_ROLE',
  /** 发起人上级 */
  INITIATOR_LEADER: 'INITIATOR_LEADER',
  /** 发起人本人 */
  INITIATOR: 'INITIATOR',
  /** 流程变量取值 */
  VARIABLE: 'VARIABLE',
  /** 高级：表达式（受控白名单） */
  EXPRESSION: 'EXPRESSION',
} as const
export type AssigneeStrategy = (typeof AssigneeStrategy)[keyof typeof AssigneeStrategy]

export const ASSIGNEE_STRATEGY_LABEL: Record<AssigneeStrategy, string> = {
  USER: '指定用户',
  ROLE: '指定角色',
  DEPT_LEADER: '部门主管',
  PROJECT_ROLE: '项目角色',
  INITIATOR_LEADER: '发起人上级',
  INITIATOR: '发起人',
  VARIABLE: '流程变量',
  EXPRESSION: '表达式（高级）',
}

/** 需要 userOids 的策略 */
export const NEEDS_USERS: AssigneeStrategy[] = [AssigneeStrategy.USER]
/** 需要 roleCodes 的策略 */
export const NEEDS_ROLES: AssigneeStrategy[] = [AssigneeStrategy.ROLE, AssigneeStrategy.PROJECT_ROLE]
/** 需要变量名的策略 */
export const NEEDS_VARIABLE: AssigneeStrategy[] = [AssigneeStrategy.VARIABLE]
/** 需要表达式的策略 */
export const NEEDS_EXPRESSION: AssigneeStrategy[] = [AssigneeStrategy.EXPRESSION]

// ==================== 审批模式 ====================

export const ApprovalMode = {
  /** 单签：一人办理即通过 */
  SINGLE: 'SINGLE',
  /** 串行：按顺序逐人办理 */
  SERIAL: 'SERIAL',
  /** 并行全员：所有人办完才通过 */
  PARALLEL_ALL: 'PARALLEL_ALL',
  /** 或签：一票通过 */
  OR_SIGN: 'OR_SIGN',
  /** 会签：按通过规则聚合 */
  COUNTERSIGN: 'COUNTERSIGN',
} as const
export type ApprovalMode = (typeof ApprovalMode)[keyof typeof ApprovalMode]

export const APPROVAL_MODE_LABEL: Record<ApprovalMode, string> = {
  SINGLE: '单签',
  SERIAL: '串行',
  PARALLEL_ALL: '并行全员',
  OR_SIGN: '或签（一票通过）',
  COUNTERSIGN: '会签',
}

/** 多实例类模式（编译为 multiInstanceLoopCharacteristics） */
export const MULTI_INSTANCE_MODES: ApprovalMode[] = [
  ApprovalMode.SERIAL,
  ApprovalMode.PARALLEL_ALL,
  ApprovalMode.OR_SIGN,
  ApprovalMode.COUNTERSIGN,
]

// ==================== 会签通过规则 ====================

export const PassRuleMode = {
  /** 按比例通过 */
  PERCENT: 'PERCENT',
  /** 按必须票数通过 */
  COUNT: 'COUNT',
  /** 一票否决 */
  VETO: 'VETO',
} as const
export type PassRuleMode = (typeof PassRuleMode)[keyof typeof PassRuleMode]

export const PASS_RULE_MODE_LABEL: Record<PassRuleMode, string> = {
  PERCENT: '按通过比例',
  COUNT: '按必须票数',
  VETO: '一票否决',
}

/** 弃权处理 */
export const AbstainPolicy = {
  /** 弃权票不计入分母 */
  IGNORE: 'IGNORE',
  /** 弃权视为通过 */
  PASS: 'PASS',
  /** 弃权视为驳回 */
  REJECT: 'REJECT',
} as const
export type AbstainPolicy = (typeof AbstainPolicy)[keyof typeof AbstainPolicy]

export const ABSTAIN_POLICY_LABEL: Record<AbstainPolicy, string> = {
  IGNORE: '弃权不计入',
  PASS: '弃权视为通过',
  REJECT: '弃权视为驳回',
}

/** 会签通过比例下限（业务规则，spec §4-I） */
export const MIN_PASS_PERCENT = 50

// ==================== 逾期（超时）后果 ====================

export const OverdueAction = {
  /** 跳过（自动流转到下一节点） */
  SKIP: 'SKIP',
  /** 标记完成 */
  MARK_COMPLETE: 'MARK_COMPLETE',
  /** 重新分配（升级给指定对象） */
  REASSIGN: 'REASSIGN',
  /** 自动通过 */
  AUTO_APPROVE: 'AUTO_APPROVE',
} as const
export type OverdueAction = (typeof OverdueAction)[keyof typeof OverdueAction]

export const OVERDUE_ACTION_LABEL: Record<OverdueAction, string> = {
  SKIP: '跳过',
  MARK_COMPLETE: '标记为已完成',
  REASSIGN: '重新分配（升级）',
  AUTO_APPROVE: '自动通过',
}

export const DeadlineAnchor = {
  /** 从「节点开始」计时 */
  NODE_START: 'NODE_START',
  /** 从「流程启动」计时 */
  PROCESS_START: 'PROCESS_START',
} as const
export type DeadlineAnchor = (typeof DeadlineAnchor)[keyof typeof DeadlineAnchor]

export const DEADLINE_ANCHOR_LABEL: Record<DeadlineAnchor, string> = {
  NODE_START: '节点开始',
  PROCESS_START: '流程启动',
}

// ==================== 驳回目标 ====================

export const RejectTarget = {
  /** 驳回到发起人 */
  INITIATOR: 'INITIATOR',
  /** 驳回到上一步 */
  PREVIOUS: 'PREVIOUS',
  /** 驳回到指定节点 */
  NODE: 'NODE',
} as const
export type RejectTarget = (typeof RejectTarget)[keyof typeof RejectTarget]

export const REJECT_TARGET_LABEL: Record<RejectTarget, string> = {
  INITIATOR: '发起人',
  PREVIOUS: '上一步',
  NODE: '指定节点',
}

// ==================== 通知方式（流程级）====================

/**
 * 通知渠道 —— 流程模板属性里的「通知方式」：<b>本流程允许从哪些通道发通知</b>。
 *
 * <p><b>渠道与凭据（SMTP 地址、OA 接口、飞书应用凭据…）由服务端统一配置</b>，
 * 见 application.yml 的 {@code plm.notification}：一个部署只有一台邮件服务器、一个 OA 接口、
 * 一套即时通讯应用凭据 —— 那是<b>企业集成</b>，不属于某个流程。
 *
 * <p>早期版本把凭据放在流程模板里，于是"改一次密码要改所有流程"，凭据还随模板 JSON
 * 复制/导出到处跑。现在模板里只剩下"允许用哪些通道"这一件事，
 * 选项应与系统已启用渠道取交集（{@code GET /api/notifications/channels}）。
 */
export const NotifyChannel = {
  /** 站内信（平台自带，无需任何集成配置） */
  CK_PLM: 'CK_PLM',
  /** 邮件系统（需企业邮件服务器） */
  EMAIL: 'EMAIL',
  /** OA 系统（需集成的 RESTful API） */
  OA: 'OA',
  /** 飞书 */
  FEISHU: 'FEISHU',
  /** 钉钉 */
  DINGTALK: 'DINGTALK',
  /** 企业微信 */
  WECOM: 'WECOM',
} as const
export type NotifyChannel = (typeof NotifyChannel)[keyof typeof NotifyChannel]

export const NOTIFY_CHANNEL_LABEL: Record<NotifyChannel, string> = {
  CK_PLM: 'CK-PLM（站内）',
  EMAIL: '邮件系统',
  OA: 'OA 系统',
  FEISHU: '飞书',
  DINGTALK: '钉钉',
  WECOM: '企业微信',
}

/** OA 集成的认证方式 */
export const OaAuthType = {
  /** 无认证（内网直连） */
  NONE: 'NONE',
  /** 账号密码（Basic） */
  BASIC: 'BASIC',
  /** Token（Bearer / 自定义头） */
  TOKEN: 'TOKEN',
} as const
export type OaAuthType = (typeof OaAuthType)[keyof typeof OaAuthType]

export const OA_AUTH_TYPE_LABEL: Record<OaAuthType, string> = {
  NONE: '无认证',
  BASIC: '账号密码',
  TOKEN: 'Token',
}

// ==================== 连线与条件 ====================

export const EdgeKind = {
  /** 普通流转 */
  NORMAL: 'NORMAL',
  /** 条件流转（含 conditionExpression） */
  CONDITION: 'CONDITION',
  /** 默认分支（网关的 default 属性） */
  DEFAULT: 'DEFAULT',
} as const
export type EdgeKind = (typeof EdgeKind)[keyof typeof EdgeKind]

/**
 * 连线承载的「路由」—— 审批 / 会签节点上的 通过 与 驳回 两条路。
 *
 * <p><b>为什么要有它（而不是只在节点上配"驳回目标"）</b>：驳回之后往往还要对业务对象做事 ——
 * 设置状态（PES 集合）、撤回电子签名、发通知…… 那些动作本身就是节点，需要一条边把它们串起来。
 * 而节点上的「驳回目标」只能"一步跳回某处"，表达不了"先做几件事再回去"。
 *
 * <p>编译层据此生成互斥条件（通过 = {@code ${approved}}、驳回 = {@code ${!approved}}），
 * 用户不必手写 UEL（spec 原则 2：业务语义一等公民，不碰 XML/UEL）。
 */
export const EdgeRoute = {
  /** 通过（同意）*/
  PASS: 'PASS',
  /** 驳回 */
  REJECT: 'REJECT',
} as const
export type EdgeRoute = (typeof EdgeRoute)[keyof typeof EdgeRoute]

export const EDGE_ROUTE_LABEL: Record<EdgeRoute, string> = {
  PASS: '通过',
  REJECT: '驳回',
}

export const ConditionMode = {
  /** 字段选择器（默认，业务用户友好） */
  FIELD: 'FIELD',
  /** 手写表达式（高级，需白名单校验） */
  EXPRESSION: 'EXPRESSION',
} as const
export type ConditionMode = (typeof ConditionMode)[keyof typeof ConditionMode]

export const ConditionOperator = {
  EQ: 'EQ',
  NE: 'NE',
  GT: 'GT',
  GE: 'GE',
  LT: 'LT',
  LE: 'LE',
  IN: 'IN',
  NOT_IN: 'NOT_IN',
  CONTAINS: 'CONTAINS',
  IS_EMPTY: 'IS_EMPTY',
  IS_NOT_EMPTY: 'IS_NOT_EMPTY',
} as const
export type ConditionOperator = (typeof ConditionOperator)[keyof typeof ConditionOperator]

export const CONDITION_OPERATOR_LABEL: Record<ConditionOperator, string> = {
  EQ: '等于',
  NE: '不等于',
  GT: '大于',
  GE: '大于等于',
  LT: '小于',
  LE: '小于等于',
  IN: '属于（多值）',
  NOT_IN: '不属于（多值）',
  CONTAINS: '包含',
  IS_EMPTY: '为空',
  IS_NOT_EMPTY: '不为空',
}

/** 不需要右侧取值的操作符 */
export const UNARY_OPERATORS: ConditionOperator[] = [
  ConditionOperator.IS_EMPTY,
  ConditionOperator.IS_NOT_EMPTY,
]

/** 取值类型（决定字段选择器的控件与表达式字面量形态） */
export const ValueType = {
  STRING: 'STRING',
  NUMBER: 'NUMBER',
  BOOLEAN: 'BOOLEAN',
  DATE: 'DATE',
} as const
export type ValueType = (typeof ValueType)[keyof typeof ValueType]

// ==================== 定时等待 ====================

export const TimerMode = {
  /** 相对时长（如 PT2H） */
  DURATION: 'DURATION',
  /** 等待至指定日期时间 */
  AT: 'AT',
} as const
export type TimerMode = (typeof TimerMode)[keyof typeof TimerMode]

export const TIMER_MODE_LABEL: Record<TimerMode, string> = {
  DURATION: '等待时长',
  AT: '等待至时点',
}

// ==================== 结束回调 ====================

export const EndCallbackKind = {
  /** 变更业务对象生命周期状态 */
  OBJECT_STATE: 'OBJECT_STATE',
  /** 发送通知 */
  NOTIFY: 'NOTIFY',
} as const
export type EndCallbackKind = (typeof EndCallbackKind)[keyof typeof EndCallbackKind]

export const END_CALLBACK_KIND_LABEL: Record<EndCallbackKind, string> = {
  OBJECT_STATE: '变更对象状态',
  NOTIFY: '发送通知',
}

// ==================== 校验等级与问题码 ====================

export const IssueLevel = {
  /** 阻断保存 */
  ERROR: 'ERROR',
  /** 允许保存，画布黄色角标 */
  WARNING: 'WARNING',
} as const
export type IssueLevel = (typeof IssueLevel)[keyof typeof IssueLevel]

/** 问题码（前端菜单/文档/后端防御性校验共用同一套编码） */
export const IssueCode = {
  NO_START: 'NO_START',
  NO_END: 'NO_END',
  MULTIPLE_START: 'MULTIPLE_START',
  MULTIPLE_END: 'MULTIPLE_END',
  ISOLATED_NODE: 'ISOLATED_NODE',
  UNREACHABLE_NODE: 'UNREACHABLE_NODE',
  NO_OUTGOING: 'NO_OUTGOING',
  START_HAS_INCOMING: 'START_HAS_INCOMING',
  END_HAS_OUTGOING: 'END_HAS_OUTGOING',
  GATEWAY_TOO_FEW_BRANCHES: 'GATEWAY_TOO_FEW_BRANCHES',
  GATEWAY_NO_DEFAULT: 'GATEWAY_NO_DEFAULT',
  GATEWAY_MULTIPLE_DEFAULT: 'GATEWAY_MULTIPLE_DEFAULT',
  PARALLEL_HAS_CONDITION: 'PARALLEL_HAS_CONDITION',
  /**
   * 并行分支没有汇聚点：多条支路走到同一个活动。
   *
   * <p>ERROR（拦保存）：该活动会被重复创建（每个分支各带一个 token），
   * 且任一支路走到「结束」时整个实例提前结束 —— 其它分支上还在办的任务会凭空消失。
   * 这类结构错误没有运行期补救手段，只能在保存前挡住。
   */
  PARALLEL_BRANCH_NOT_MERGED: 'PARALLEL_BRANCH_NOT_MERGED',
  /** 并行分支上配了退回（驳回）：回退会重走分支，而旧分支的 token 不会消失（活动被重复创建） */
  PARALLEL_BRANCH_HAS_REJECT: 'PARALLEL_BRANCH_HAS_REJECT',
  MISSING_ASSIGNEE: 'MISSING_ASSIGNEE',
  /** 默认分支标记用在了非「条件分支 / 包容分支」的连线上（该标记不会生效） */
  DEFAULT_BRANCH_UNEXPECTED: 'DEFAULT_BRANCH_UNEXPECTED',
  /** 流程里有审批/会签/办理活动，却没有「设置审批人」活动 —— 那些人不会被发起人指派 */
  SETUP_MISSING: 'SETUP_MISSING',
  /** 「设置审批人」不是流程的第一步（它之前的人工活动不会被它覆盖） */
  SETUP_NOT_FIRST: 'SETUP_NOT_FIRST',
  /**
   * 两个「设置审批人」活动覆盖到同一个活动。
   *
   * <p>撞车时是 ERROR（拦保存）：运行期谁后执行谁写入该活动的人员变量，先执行的那份人选被静默覆盖
   * —— 发起人以为指派了 A，实际落到 B 头上。只是"多设了几个"、覆盖范围不重叠时是 WARNING。
   */
  SETUP_MULTIPLE: 'SETUP_MULTIPLE',
  /** 设置审批人活动下游没有任何审批/会签/办理活动（没有可设置的对象） */
  SETUP_NO_TARGET: 'SETUP_NO_TARGET',
  MISSING_CONDITION: 'MISSING_CONDITION',
  COUNTERSIGN_NO_PASS_RULE: 'COUNTERSIGN_NO_PASS_RULE',
  PASS_PERCENT_TOO_LOW: 'PASS_PERCENT_TOO_LOW',
  COUNTERSIGN_NO_APPROVERS: 'COUNTERSIGN_NO_APPROVERS',
  REJECT_NODE_NOT_FOUND: 'REJECT_NODE_NOT_FOUND',
  /** 驳回目标指向节点自己（退回去等于原地不动） */
  REJECT_TARGET_SELF: 'REJECT_TARGET_SELF',
  /** 驳回目标不是审批/办理活动（退回去没有人能办理） */
  REJECT_TARGET_NOT_HUMAN: 'REJECT_TARGET_NOT_HUMAN',
  /** 驳回到「发起人」，但流程里没有由发起人办理的节点 */
  REJECT_NO_INITIATOR_NODE: 'REJECT_NO_INITIATOR_NODE',
  /** 驳回到「上一步」，但本节点之前没有任何办理活动 */
  REJECT_NO_PREVIOUS_NODE: 'REJECT_NO_PREVIOUS_NODE',
  SERVICE_NOT_SET: 'SERVICE_NOT_SET',
  SUB_PROCESS_NO_KEY: 'SUB_PROCESS_NO_KEY',
  TIMER_NOT_SET: 'TIMER_NOT_SET',
  DEADLINE_REASSIGN_NO_TARGET: 'DEADLINE_REASSIGN_NO_TARGET',
  NOTIFY_NO_RECIPIENT: 'NOTIFY_NO_RECIPIENT',
  EXPRESSION_NOT_ALLOWED: 'EXPRESSION_NOT_ALLOWED',
  VARIABLE_NOT_DEFINED: 'VARIABLE_NOT_DEFINED',
  /** 声明的流程变量与内置变量同名（运行期平台写入的值会覆盖它） */
  VARIABLE_NAME_BUILTIN: 'VARIABLE_NAME_BUILTIN',
  /** 流程声明了某个通知方式，但系统未启用它（渠道由服务端 plm.notification 配置，运行期发不出去） */
  NOTIFY_CHANNEL_NOT_ENABLED: 'NOTIFY_CHANNEL_NOT_ENABLED',
  /** 流程里有通知节点，但模板属性里一个通知方式都没选 */
  NOTIFY_NODE_WITHOUT_CHANNEL: 'NOTIFY_NODE_WITHOUT_CHANNEL',
  /** 同一节点的出边里，同一种路由被声明了多次（通过/驳回各至多一条） */
  EDGE_ROUTE_DUPLICATED: 'EDGE_ROUTE_DUPLICATED',
  /** 声明了路由，但该节点的其它出边没声明（一条按结论判、一条无条件 → 通过时两条都走） */
  EDGE_ROUTE_PARTIAL: 'EDGE_ROUTE_PARTIAL',
  /** 画了「驳回」路由，但节点没开启「允许驳回」—— 那条边永远走不到 */
  EDGE_ROUTE_WITHOUT_REJECT: 'EDGE_ROUTE_WITHOUT_REJECT',
  /** 声明了路由却缺「通过」路由：同意之后无路可走 */
  EDGE_ROUTE_NO_PASS: 'EDGE_ROUTE_NO_PASS',
  /** 开了「允许驳回」，但既没配「驳回目标」、也没画「驳回」出边：驳回之后无处可去 */
  REJECT_NO_TARGET: 'REJECT_NO_TARGET',
  /** 既画了「驳回」路由，又配了节点上的「驳回目标」—— 两套机制抢戏 */
  EDGE_ROUTE_CONFLICT: 'EDGE_ROUTE_CONFLICT',
  /** 审批/会签有多条出边却没声明路由：引擎会当成并行（两条都走） */
  EDGE_ROUTE_UNSPECIFIED: 'EDGE_ROUTE_UNSPECIFIED',
  /** 路由只对审批/会签有意义（其它节点没有"结论"可分） */
  EDGE_ROUTE_NOT_APPLICABLE: 'EDGE_ROUTE_NOT_APPLICABLE',
  DUPLICATE_NODE_ID: 'DUPLICATE_NODE_ID',
  EDGE_ENDPOINT_MISSING: 'EDGE_ENDPOINT_MISSING',
  UNSUPPORTED_ELEMENT: 'UNSUPPORTED_ELEMENT',
} as const
export type IssueCode = (typeof IssueCode)[keyof typeof IssueCode]

// ==================== SERVICE 白名单（开放问题 2 的提案）====================

/**
 * 自动服务描述符 —— SERVICE 节点的可选项来源。
 *
 * <p>提案：服务由后端注册（id / 参数 schema / 权限），前端只做选择与参数校验；
 * 编译层把 {@code id} 写成 {@code delegateExpression}，禁止用户直接写 Bean 名或类名
 * （spec §4-J「Launch Application 类节点一律不做」）。
 */
export interface ServiceDescriptor {
  /** 服务 id（编译层映射到 delegateExpression，如 {@code plmService:partCheckout}） */
  id: string
  /** 显示名 */
  label: string
  /** 参数声明（驱动面板动态渲染与校验） */
  params: ServiceParamDescriptor[]
  /**
   * 除声明之外还接受"自由参数"（键值对）。
   *
   * <p>典型是「REST 接口调用」：声明的三样（系统 / 路径 / 方法）是<b>控制参数</b>，
   * 真正要传给对方的业务参数由设计者按接口约定自由填写。
   * 没有这个标记，面板会因为"参数都有专用控件了"而把参数集编辑器隐藏掉 —— 业务参数就无处可填。
   */
  acceptsExtraParams?: boolean
}

/**
 * 服务参数的候选取值来源。
 *
 * <p>声明了它的参数说明"取值来自某个清单"，面板据此渲染成<b>下拉</b>而不是让人手输 code
 * （具体取数在 {@code app/panel/options.ts}）。清单本身归各业务模块管，这里只登记"要哪一份"。
 */
export type ServiceParamOptionSource =
  | 'LIFECYCLE_STATES'
  | 'ROLES'
  | 'USERS'
  | 'OBJECT_TYPES'
  /** 目标系统认证方式（静态清单，见 AUTH_TYPE_LABEL） */
  | 'AUTH_TYPE'
  /** HTTP 方法（静态清单，见 HTTP_METHOD_LABEL） */
  | 'HTTP_METHOD'
  /** 目标系统注册表（「系统配置 → 目标系统」里维护：地址与凭据在那边，流程只引用） */
  | 'TARGET_SYSTEMS'

/** 集成调用的认证方式（静态清单：面板渲染成下拉，后端按同一套取值分支） */
export const AUTH_TYPE_LABEL: Record<string, string> = {
  NONE: '无认证',
  BASIC: 'Basic（用户名 / 口令）',
  BEARER: 'Bearer Token',
  API_KEY: 'API Key（请求头）',
}

/**
 * 出站调用的 HTTP 方法。
 *
 * <p>GET 是给「REST 接口调用」查数据用的（业务参数拼成查询串）；
 * 「系统集成」走的是"提交一份数据"，后端会拒绝 GET（GET 带不了报文），
 * 所以发布类节点仍应选 POST/PUT/PATCH。
 */
export const HTTP_METHOD_LABEL: Record<string, string> = {
  GET: 'GET',
  POST: 'POST',
  PUT: 'PUT',
  PATCH: 'PATCH',
}

export interface ServiceParamDescriptor {
  name: string
  label: string
  type: ValueType
  required: boolean
  /** 参数来源：固定值 / 流程变量 / 业务对象字段 */
  source?: 'LITERAL' | 'VARIABLE' | 'OBJECT_FIELD'
  defaultValue?: string
  /** 取值来自清单 → 面板渲染成下拉（不给就还是通用键值输入） */
  optionsSource?: ServiceParamOptionSource
  /**
   * 自由文本参数的控件类型（与 optionsSource 二选一）：
   * 给了它就渲染成<b>带标签的输入框</b>，而不是让人在通用键值编辑器里手输参数名。
   * {@code password} 用密码框（不回显明文，如口令 / Token）。
   */
  control?: 'text' | 'password'
  /** 字段说明（面板显示在该参数下方） */
  help?: string
}

/**
 * 「设置状态」服务 id。
 *
 * <p>单独提出来是因为它有三处引用：服务白名单、节点库的「设置状态」入口（见 presets）、
 * 以及面板里"目标状态"下拉的可见性判断。写三遍字面量迟早会有一处漏改。
 */
export const SERVICE_SET_STATE = 'object.setLifecycleState'

/**
 * 「系统集成」服务 id（旧称「发布到目标系统」）。
 *
 * <p>与「设置状态」并列的一条自动化服务：把本流程的<b>业务对象集合</b>（ProcessEntitySet，
 * 即发起时选定的那些对象）按配置发布到目标系统的 REST 接口。
 * 目标系统地址 / 接口路径 / HTTP 方法 / 认证方式与凭据<b>都配在节点参数上</b>（见下方 params）。
 *
 * <p>提出来是因为它有两处引用：服务白名单与节点库的「系统集成」入口（见 presets）。
 *
 * <p><b>id 保持不变</b>：已部署模板里存的就是 {@code integration.publishToSystem}，
 * 改 id 会让那些模板的服务引用失效 —— 改的只是显示名。
 */
export const SERVICE_PUBLISH_TO_SYSTEM = 'integration.publishToSystem'

/**
 * 「REST 接口调用」服务 id。
 *
 * <p>通用出站：<b>系统与凭据来自目标系统注册表</b>（管理员在「系统配置 → 目标系统」里维护），
 * 节点上只填路径 / 方法 / 业务参数。与「系统集成」的分工在报文结构：
 * 系统集成固定发"业务对象集合"，这里由设计者按被调接口的约定自由组织参数。
 */
export const SERVICE_REST_CALL = 'integration.restCall'

/**
 * 对象类动作的服务 id（检出 / 检入 / 修订 / 更新属性）。
 *
 * <p>这四个在节点库「自动化」里各有<b>独立入口</b>（见 presets）：它们是对象上最常用的动作，
 * 拖出来就已选好服务，不必先放一个「函数调用」再去下拉里翻。因此它们**不再出现在**
 * 「服务」下拉的候选里 —— 同一个动作有两处入口，设计者反而要判断"两处有没有差别"。
 *
 * <p>提成常量是因为每处都有两处引用：服务白名单 + 节点库入口。
 */
export const SERVICE_CHECKOUT = 'object.checkout'
export const SERVICE_CHECKIN = 'object.checkin'
export const SERVICE_PROMOTE = 'object.promote'
export const SERVICE_SET_ATTRIBUTE = 'object.setAttribute'

/** M0 内置服务白名单（提案，待后端确认注册机制后改为接口下发） */
export const BUILTIN_SERVICES: ServiceDescriptor[] = [
  // 下面四个在节点库「自动化」里各有独立入口（见 presets），「服务」下拉里不再重复列它们
  { id: SERVICE_CHECKOUT, label: '检出对象', params: [] },
  { id: SERVICE_CHECKIN, label: '检入对象', params: [] },
  // 旧显示名是「升版（新建大版本）」：业务上就叫「修订对象」，与节点库入口同名 ——
  // 同一个东西两个名字最容易出错（一处改另处漏）
  { id: SERVICE_PROMOTE, label: '修订对象', params: [] },
  {
    // id 保持不变：「设置生命周期状态」时期部署出去的模板里存的就是它
    // （编译产物 serviceId 字段），改 id 会让那些模板的服务引用失效。
    // 改的是名称与填法 —— 目标状态本来要手输 code（通用键值编辑器），现在有清单可选。
    id: SERVICE_SET_STATE,
    label: '设置状态',
    params: [
      {
        name: 'state',
        label: '目标状态',
        type: ValueType.STRING,
        required: true,
        optionsSource: 'LIFECYCLE_STATES',
        help: '候选为生命周期模板里定义的状态；运行期按当前对象的状态迁移规则执行',
      },
    ],
  },
  {
    id: SERVICE_SET_ATTRIBUTE,
    // 旧显示名是「设置对象属性」：与节点库入口统一叫「更新对象属性」（同一个东西一个名字）
    label: '更新对象属性',
    params: [
      { name: 'fieldKey', label: '属性', type: ValueType.STRING, required: true },
      { name: 'value', label: '取值', type: ValueType.STRING, required: true },
    ],
  },
  {
    id: 'notification.send',
    label: '发送通知',
    params: [
      { name: 'templateCode', label: '通知模板', type: ValueType.STRING, required: true },
      { name: 'recipients', label: '接收人', type: ValueType.STRING, required: true },
    ],
  },
  {
    // REST 接口调用：通用出站（系统 + 路径 + 方法 + 业务参数）。
    //
    // 与「系统集成」的分工：系统集成的报文结构固定（把业务对象集合发过去），
    // 这里由设计者按被调接口的约定自由组织参数；地址与凭据都在目标系统注册表里，
    // 节点只引用 code —— 改地址 / 换密码不用动流程，凭据也不会随流程 DSL 复制导出。
    id: SERVICE_REST_CALL,
    label: 'REST 接口调用',
    // 声明的三样是"控制参数"；真正的业务参数由设计者自由填写（见 acceptsExtraParams）
    acceptsExtraParams: true,
    params: [
      {
        name: 'systemCode',
        label: '目标系统',
        type: ValueType.STRING,
        required: true,
        optionsSource: 'TARGET_SYSTEMS',
        help: '在「系统配置 → 目标系统」里维护：地址、认证与凭据都在那边，流程里只引用它',
      },
      {
        // 参数名用 path / method（而非 apiPath / httpMethod）：面板字段 key 是 `params.<参数名>`，
        // 而「系统集成」已经占了那两个名字 —— 同名会在同一节点类型下生成重复 key
        name: 'path',
        label: '接口路径',
        type: ValueType.STRING,
        required: true,
        control: 'text',
        help: '拼在系统地址之后，如 /api/order/sync',
      },
      {
        name: 'method',
        label: 'HTTP 方法',
        type: ValueType.STRING,
        required: false,
        optionsSource: 'HTTP_METHOD',
        defaultValue: 'POST',
        help: 'GET 时业务参数拼成查询串，其余方法作为 JSON 报文发送；不填按 POST',
      },
    ],
  },
  {
    // 系统集成：把业务对象集合发布到目标系统。
    //
    // 为什么参数配在节点上（而不是"业务配置里先建一个目标系统再引用"）：
    // 这一版先把链路打通（节点 → 编译 → 运行期调用），目标系统与凭据随节点走，
    // 一眼能看出"这条流程把数据发给了谁"。缺点也明确：凭据会随流程定义走、
    // 多个节点要各配一份 —— 后续要做「目标系统注册表 + 节点只填引用」时，
    // 只需把这里的 params 换成 systemCode，运行期改从注册表读，节点结构不变。
    id: SERVICE_PUBLISH_TO_SYSTEM,
    label: '系统集成',
    params: [
      {
        name: 'baseUrl',
        label: '目标系统地址',
        type: ValueType.STRING,
        required: true,
        control: 'text',
        help: '目标系统的根地址，如 https://erp.example.com（不含接口路径）',
      },
      {
        name: 'apiPath',
        label: '发布接口路径',
        type: ValueType.STRING,
        required: true,
        control: 'text',
        help: 'RESTful 接口路径，如 /api/plm/entity-set；最终地址 = 地址 + 路径',
      },
      {
        name: 'httpMethod',
        label: 'HTTP 方法',
        type: ValueType.STRING,
        required: false,
        optionsSource: 'HTTP_METHOD',
        defaultValue: 'POST',
        help: '默认 POST：发布＝向对方提交一份数据',
      },
      {
        name: 'authType',
        label: '认证方式',
        type: ValueType.STRING,
        required: false,
        optionsSource: 'AUTH_TYPE',
        defaultValue: 'NONE',
        help: '连接目标系统时的认证方式；除"无认证"外都需要下方的凭据',
      },
      {
        name: 'username',
        label: '用户名 / AppKey',
        type: ValueType.STRING,
        required: false,
        control: 'text',
        help: 'Basic 认证的用户名；API Key 认证时填请求头名称（如 X-API-Key）',
      },
      {
        name: 'secret',
        label: '口令 / Token / Secret',
        type: ValueType.STRING,
        required: false,
        control: 'password',
        help: 'Basic 口令、Bearer Token 或 API Key 的取值。保存在流程定义里（当前为明文），'
          + '请勿使用个人口令；后续版本会改为「目标系统注册表 + 加密存储」',
      },
    ],
  },
]

/** 按 id 取服务描述符（面板与编译告警共用） */
export function serviceById(id: string | undefined | null): ServiceDescriptor | undefined {
  return id ? BUILTIN_SERVICES.find((service) => service.id === id) : undefined
}

/**
 * 该服务的参数是否<b>全部</b>由下拉承载。
 *
 * <p>是的话面板就不该再显示通用键值编辑器：让用户把刚刚在上面选过的参数又手输一遍
 * （还得记准参数名）是最典型的"同一件事两处填"。没有可选清单的服务（如「设置对象属性」）
 * 仍走键值编辑器 —— 它们的取值是自由文本。
 *
 * <p>例外是声明了 {@code acceptsExtraParams} 的服务（如「REST 接口调用」）：
 * 它声明的只是控制参数，真正的业务参数没有声明、只能在这里填 —— 判成"全部由下拉承载"
 * 就等于把参数集编辑器藏掉，业务参数无处可填。
 */
export function serviceParamsAllFromList(serviceId: string | undefined | null): boolean {
  const service = serviceById(serviceId)
  if (service?.acceptsExtraParams) {
    return false
  }
  return !!service && service.params.length > 0
    && service.params.every((p) => !!p.optionsSource || !!p.control)
}

/** 表达式白名单函数（高级模式语法校验用，spec §4-J） */
export const ALLOWED_EXPRESSION_FUNCTIONS: string[] = [
  'isEmpty',
  'notEmpty',
  'len',
  'contains',
  'startsWith',
  'endsWith',
  'toUpper',
  'toLower',
  'now',
  'daysBetween',
]
