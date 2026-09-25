/**
 * CK-PLM 流程 DSL · 内置变量清单
 *
 * <p><b>内置变量</b>＝运行期由平台自己写入流程作用域的变量：发起时写一批（发起人 / 租户 /
 * 业务对象集合 / 发起时的对象状态），每次办理再写一批（本次结论 / 最近动作与意见）。
 * 它们是"不用声明就可用"的，模板里直接 `$ {...}` 引用即可 ——
 * 与模板自己声明的流程变量（`dsl.variables`）是两回事。
 *
 * <p><b>为什么要有一份清单</b>：
 * <ol>
 *   <li>设计器的「流程变量」区只列得出模板声明过的变量，内置变量原本哪里都看不到 ——
 *       使用者以为不存在，于是要么把 `initiator` / `approved` 又声明一遍（声明了也会被平台值覆盖），
 *       要么干脆不敢用条件分支；</li>
 *   <li>校验器要能认出它们：引用 `${approved}` 不该报"未声明的流程变量"
 *       （那会让真正的漏声明淹没在误报里）。</li>
 * </ol>
 *
 * <p><b>只登记"确实有人写"的变量</b>。`bpmn-compiler/namespaces.ts` 的 `RUNTIME_VARIABLES`
 * 里还声明了几个运行期契约（`ckplmDeptLeader` / `ckplmInitiatorLeader` / `ckplmApprovers` /
 * `ckplmApprover` / `ckplmVetoed`），但后端目前没有任何组件写入它们 ——
 * 登记进来只会让人配出一个永远取不到值的流程，所以刻意不收（等运行期实现补齐再登记）。
 */
import { ValueType } from './constants'

/** 内置变量的写入时机（决定"什么时候开始有值"） */
export const BuiltinVariableTiming = {
  /** 发起时写入，之后不再变 */
  START: '发起时',
  /** 每次办理后写入（同一实例反复被覆盖，取到的总是"最近一次"） */
  COMPLETE: '每次办理后',
} as const
export type BuiltinVariableTiming = (typeof BuiltinVariableTiming)[keyof typeof BuiltinVariableTiming]

/**
 * 「对象」形态 —— {@link ValueType} 之外的一种取值形态。
 *
 * <p>DSL 的变量声明只有 STRING / NUMBER / BOOLEAN / DATE 四种（那是给表单字段用的），
 * 而内置变量里有 {@code businessObjectSet} 这种<b>复合对象</b>：它不是基本类型，
 * 但确实存在、也确实要在面板上标出来。所以这里单独给一个展示用形态，
 * 不动 DSL 的 {@link ValueType}（动它会牵连字段选择器与表单控件）。
 */
export const BUILTIN_OBJECT_TYPE = 'OBJECT' as const
export type BuiltinVariableType = ValueType | typeof BUILTIN_OBJECT_TYPE

/** 面板上的类型展示：OBJECT 显示成「对象」，其余用 ValueType 原值（STRING / BOOLEAN…） */
export function builtinVariableTypeLabel(type: BuiltinVariableType): string {
  return type === BUILTIN_OBJECT_TYPE ? '对象' : type
}

export interface BuiltinVariable {
  /** 变量名；`perActivity` 为 true 时它是<b>前缀</b>，实际名按活动拼出来 */
  name: string
  /** 展示用变量名（前缀型补上 `<活动id>` 占位，避免被当成可照抄的固定名） */
  display: string
  type: BuiltinVariableType
  timing: BuiltinVariableTiming
  /** 说明：它是干什么的、怎么用（面板按纯文本渲染，不要写 HTML 标签） */
  help: string
  /** 按活动拼名（一个活动一个变量），因此不能作为固定名直接引用 */
  perActivity?: boolean
  /**
   * 能否直接用作「审批人 = 流程变量」的取值。
   *
   * <p>该策略要求变量里装的是<b>人员标识</b>（用户名 / 人员 oid），所以只有 initiator 合适：
   * 把 {@code approved}（布尔）或 {@code businessObjectSet}（对象）选成审批人，
   * 编译出来是 {@code assignee="${approved}"}，运行期根本指派不到人。
   */
  assignee?: true
}

/**
 * 内置变量清单（与运行期写入点一一对应）。
 *
 * <p>写入点：{@code ProcessInstanceController#start}（发起类：initiator / tenantOid /
 * lifecycleStatus / businessObjectSet）与 {@code ProcessServiceImpl#completeTask}
 * （办理类：approved / lastAction / lastComment / ckplmSetupAssignees_*）。
 *
 * <p><b>业务对象用「一个对象」表达</b>：{@code businessObjectSet}（见
 * {@code ProcessInstanceController#businessObjectSetOf}）。原先的
 * {@code businessObjectType / businessObjectOid / businessObjectCode} 三个平铺变量已退役 ——
 * 流程与业务对象的关联本身是集合（{@code ck_process_entity_set} 一行一个对象、各带大版本），
 * 三个标量变量表达不了它。
 */
export const BUILTIN_VARIABLES: BuiltinVariable[] = [
  {
    name: 'initiator',
    display: 'initiator',
    type: ValueType.STRING,
    timing: BuiltinVariableTiming.START,
    assignee: true,
    help: '发起人用户名。审批人策略「发起人」编译出来就是 ${initiator}，也可用于条件判断（如"只有发起人能办"）。',
  },
  {
    name: 'tenantOid',
    display: 'tenantOid',
    type: ValueType.STRING,
    timing: BuiltinVariableTiming.START,
    help: '所属租户 oid。一般不用手写，用于需要按租户隔离取数的服务节点。',
  },
  {
    name: 'lifecycleStatus',
    display: 'lifecycleStatus',
    type: ValueType.STRING,
    timing: BuiltinVariableTiming.START,
    help: '发起时业务对象的生命周期状态 code（如 DRAFT）。注意是「发起那一刻」的快照，不随后续状态变更刷新。',
  },
  {
    name: 'businessObjectSet',
    display: 'businessObjectSet',
    type: BUILTIN_OBJECT_TYPE,
    timing: BuiltinVariableTiming.START,
    help:
      '本流程关联的业务对象集合（ck_process_entity_set 在流程里的引用）。' +
      '${businessObjectSet.primary} 是主对象：typeCode / rootTypeCode / entityOid / entityVersion / code / businessKey；' +
      '${businessObjectSet.entities} 是本次关联的全部对象。' +
      '一个实例可以关联多个对象、各带大版本，所以它是一个集合对象 —— 不要再按"三个并列变量"去找对象信息。',
  },
  {
    name: 'approved',
    display: 'approved',
    type: ValueType.BOOLEAN,
    timing: BuiltinVariableTiming.COMPLETE,
    help: '本次办理结论：同意为 true、驳回为 false。条件分支写 ${approved} / ${!approved} 即走审批通过 / 驳回两条路。',
  },
  {
    name: 'lastAction',
    display: 'lastAction',
    type: ValueType.STRING,
    timing: BuiltinVariableTiming.COMPLETE,
    help: '最近一次办理动作（approve / reject），用于历史追溯与通知文案。',
  },
  {
    name: 'lastComment',
    display: 'lastComment',
    type: ValueType.STRING,
    timing: BuiltinVariableTiming.COMPLETE,
    help: '最近一次审批意见。要把它带进通知正文时直接引用。',
  },
  {
    name: 'ckplmSetupAssignees_',
    display: 'ckplmSetupAssignees_<活动id>',
    type: ValueType.STRING,
    timing: BuiltinVariableTiming.COMPLETE,
    perActivity: true,
    help:
      '「设置审批人」为某个活动指定的人员（人员 oid）：单签是单个值、会签是列表。' +
      '一个活动一个变量（名 = 本前缀 + 活动 id），所以不要照抄这个名字，' +
      '在活动上选「流程变量」策略时由设计器自动绑定。',
  },
]

/** 可当作固定名直接引用的内置变量（排除按活动拼名的那种） */
export const FIXED_BUILTIN_VARIABLES: BuiltinVariable[] = BUILTIN_VARIABLES.filter((v) => !v.perActivity)

/**
 * 可直接填进「审批人 = 流程变量」的内置变量。
 *
 * <p>只挑"变量里装的是人员标识"的：把布尔（approved）或对象（businessObjectSet）
 * 选成审批人，编译结果是 {@code assignee="${approved}"}，运行期指派不到任何人 ——
 * 这种"能选但一定错"的选项不如不给。
 */
export const ASSIGNEE_BUILTIN_VARIABLES: BuiltinVariable[] = BUILTIN_VARIABLES.filter(
  (v) => v.assignee === true,
)

/** 内置变量名集合（固定名部分） */
export const BUILTIN_VARIABLE_NAMES: string[] = FIXED_BUILTIN_VARIABLES.map((v) => v.name)

/**
 * 是否为内置变量名。
 *
 * <p>按活动拼名的那类按<b>前缀</b>匹配：`ckplmSetupAssignees_task_p7anuf` 也算内置
 * （它的后半截是活动 id，无法逐一枚举）。
 */
export function isBuiltinVariableName(name: string | undefined | null): boolean {
  if (!name) return false
  if (BUILTIN_VARIABLE_NAMES.includes(name)) return true
  return BUILTIN_VARIABLES.some((v) => v.perActivity === true && name.startsWith(v.name))
}

/** 取内置变量定义（按固定名精确匹配；按活动拼名的那类返回其前缀所在条目） */
export function builtinVariableOf(name: string | undefined | null): BuiltinVariable | undefined {
  if (!name) return undefined
  return (
    BUILTIN_VARIABLES.find((v) => v.name === name) ??
    BUILTIN_VARIABLES.find((v) => v.perActivity === true && name.startsWith(v.name))
  )
}
