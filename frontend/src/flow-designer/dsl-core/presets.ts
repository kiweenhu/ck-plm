/**
 * 节点库目录 —— 纯数据，零依赖。
 *
 * <p>只负责「节点库里有什么、怎么分组、按什么顺序展示」。
 *
 * <p><b>业务默认值刻意不在这里</b>：它属于节点类型本身（见画布层的 `defaultNodeOf`）。
 * 因为「会签审批默认全员通过」是<b>这种业务活动的属性</b>，不是「节点库条目的属性」——
 * 放在条目上，同一个类型从别的入口（BPMN 导入、模板复制、后端生成）创建时
 * 就会拿到不同的默认值。
 *
 * <p>历史上本文件还承载过「同一节点类型的多个业务入口」（会签审批曾是 `APPROVAL` 的预设）。
 * 自会签升为独立类型后，条目与节点类型<b>一对一</b>。若将来又出现「同类型的业务变体」，
 * 在这里加字段即可 —— 但请先判断它是否本就该是一个独立类型（业务词汇优先）。
 */

import {
  NODE_TYPE_LABEL,
  NodeType,
  SERVICE_CHECKIN,
  SERVICE_CHECKOUT,
  SERVICE_PROMOTE,
  SERVICE_PUBLISH_TO_SYSTEM,
  SERVICE_REST_CALL,
  SERVICE_SET_ATTRIBUTE,
  SERVICE_SET_STATE,
  serviceById,
  type NodeType as NodeTypeT,
} from './constants'

/** 节点库分组名（顺序即展示顺序） */
export const NODE_PRESET_GROUPS = ['事件', '人工', '自动化', '分支'] as const
export type NodePresetGroup = (typeof NODE_PRESET_GROUPS)[number]

export interface NodePreset {
  /** 节点类型 */
  type: NodeTypeT
  /**
   * 条目 id（同一类型有多个业务入口时用它区分；缺省 = 类型名）。
   *
   * <p>拖拽载荷带的是它（不是类型）—— 落点处据此取回条目，条目才带得出 {@link prefill}。
   */
  id?: string
  /** 显示名（缺省回落类型的业务名，避免重复维护标签） */
  label?: string
  group: NodePresetGroup
  /**
   * 落点时要写进节点的初始属性（如「设置状态」预置 serviceRef 与节点名）。
   *
   * <p>只放「这个入口特有」的值；类型的公共默认值仍归画布层的 {@code defaultNodeOf}——
   * 那是"这种活动是什么"的属性，从别的入口（BPMN 导入 / 模板复制 / 后端生成）创建时也该一样。
   * 这里预置的都是 DSL 里的普通字段，编译 → 反解析往返后仍在，不会丢。
   */
  prefill?: Record<string, unknown>
  /**
   * 该入口对应的服务是否**不再出现在「服务」下拉**里。
   *
   * <p>对象动作（检出 / 检入 / 修订 / 更新属性）带此标记：同一个动作既能从节点库拖、
   * 也能在下拉里选，设计者就得先判断"两处是不是不一样"—— 而它们没有差别。
   * 通用入口（函数调用）与后端注册的定制函数不标：它们本来就靠下拉选。
   */
  hideFromServiceList?: boolean
}

/** 「设置状态」入口的 id（与类型区分开，便于将来同一类型再加别的入口） */
export const SET_STATE_PRESET_ID = `service.${SERVICE_SET_STATE}`

/**
 * 「设置状态」入口在节点库里的标签。
 *
 * <p>取自服务描述符的 label（服务下拉、节点库两处同源）；<b>节点名另有一个</b>
 * （{@link SET_STATE_NODE_NAME}）—— 标签要短、便于在节点库里扫，节点名要说清"改的是什么"。
 */
export const SET_STATE_PRESET_LABEL = serviceById(SERVICE_SET_STATE)?.label ?? '设置状态'

/** 拖到画布后的节点名：比标签具体，看画布就知道这个自动服务在改什么 */
export const SET_STATE_NODE_NAME = '设置对象状态'

/** 「系统集成」入口的 id */
export const PUBLISH_PRESET_ID = `service.${SERVICE_PUBLISH_TO_SYSTEM}`

/** 节点库里的标签（与服务下拉同源） */
export const PUBLISH_PRESET_LABEL = serviceById(SERVICE_PUBLISH_TO_SYSTEM)?.label ?? '系统集成'

/** 拖到画布后的节点名（与「设置状态」同取法：标签短、节点名说清这条线在做什么） */
export const PUBLISH_NODE_NAME = '系统集成'

/**
 * 「函数调用」入口的 id。
 *
 * <p><b>不预置服务</b>：它是"流程里要执行一段后端函数"的通用入口 —— 调哪个后端已注册的
 * 服务/函数（如 {@code custom.xxx}）由设计者按项目选（安全可控、可审计），参数集在面板里以
 * 键值对配置，取值可写 {@code ${流程变量}}（运行期由后端的服务委托统一解析）。
 */
export const FUNCTION_CALL_PRESET_ID = 'service.function'

/** 拖到画布后的节点名 */
export const FUNCTION_CALL_NODE_NAME = '函数调用'

/** 「REST 接口调用」入口的 id */
export const REST_CALL_PRESET_ID = `service.${SERVICE_REST_CALL}`

/** 节点库里的标签（与服务下拉同源） */
export const REST_CALL_PRESET_LABEL = serviceById(SERVICE_REST_CALL)?.label ?? 'REST 接口调用'

/** 拖到画布后的节点名：画布上的短名，一眼看出这条线要做什么 */
export const REST_CALL_NODE_NAME = '调用接口'

/**
 * 对象动作四件套（检出 / 检入 / 修订 / 更新属性）的入口。
 *
 * <p>它们是对象上最常用的动作，值得从「服务」下拉里<b>独立出来</b>：拖出来就已选好服务，
 * 少两步点击，也不会因为下拉里选项多而找不到。标签与服务描述符同源（改一处不会漂），
 * 节点名直接用标签 —— 这类动作的短名本身就是它自己。
 */
export const CHECKOUT_PRESET_ID = `service.${SERVICE_CHECKOUT}`
export const CHECKOUT_PRESET_LABEL = serviceById(SERVICE_CHECKOUT)?.label ?? '检出对象'
export const CHECKIN_PRESET_ID = `service.${SERVICE_CHECKIN}`
export const CHECKIN_PRESET_LABEL = serviceById(SERVICE_CHECKIN)?.label ?? '检入对象'
export const PROMOTE_PRESET_ID = `service.${SERVICE_PROMOTE}`
export const PROMOTE_PRESET_LABEL = serviceById(SERVICE_PROMOTE)?.label ?? '修订对象'
export const SET_ATTRIBUTE_PRESET_ID = `service.${SERVICE_SET_ATTRIBUTE}`
export const SET_ATTRIBUTE_PRESET_LABEL =
  serviceById(SERVICE_SET_ATTRIBUTE)?.label ?? '更新对象属性'

/** 节点库全部条目（顺序即分组内的展示顺序） */
export const NODE_PRESETS: NodePreset[] = [
  // 事件
  { type: NodeType.START, group: '事件' },
  { type: NodeType.END, group: '事件' },
  { type: NodeType.TIMER, group: '事件' },
  // 人工：按业务上的使用顺序排序
  // 「设置审批人」排在最前，因为它通常就是开始后的第一个节点
  { type: NodeType.SET_ASSIGNEE, group: '人工' },
  { type: NodeType.APPROVAL, group: '人工' },
  { type: NodeType.COUNTERSIGN_APPROVAL, group: '人工' },
  { type: NodeType.TASK, group: '人工' },
  // 自动化：通用入口在前（要调哪段后端函数由设计者选），常用动作紧随其后（拖出来就已选好服务）
  {
    // 函数调用：流程里要执行一段后端函数的通用入口。
    // 不预置服务 ——"调哪个函数"（后端已注册的服务 id）由设计者按项目选；
    // 参数集在面板里配（键值对，取值可写 ${流程变量}，运行期由 PlmServiceDelegate 解析）。
    id: FUNCTION_CALL_PRESET_ID,
    type: NodeType.SERVICE,
    label: '函数调用',
    group: '自动化',
    prefill: { name: FUNCTION_CALL_NODE_NAME },
  },
  {
    // 对象动作四件套：最常用的对象操作，从「服务」下拉里独立出来（拖出来即已选好服务）。
    // hideFromServiceList：同一个动作不再在下拉里重复出现（见 serviceIdsHiddenFromServiceList）
    id: CHECKOUT_PRESET_ID,
    type: NodeType.SERVICE,
    label: CHECKOUT_PRESET_LABEL,
    group: '自动化',
    hideFromServiceList: true,
    prefill: { name: CHECKOUT_PRESET_LABEL, serviceRef: SERVICE_CHECKOUT },
  },
  {
    id: CHECKIN_PRESET_ID,
    type: NodeType.SERVICE,
    label: CHECKIN_PRESET_LABEL,
    group: '自动化',
    hideFromServiceList: true,
    prefill: { name: CHECKIN_PRESET_LABEL, serviceRef: SERVICE_CHECKIN },
  },
  {
    id: PROMOTE_PRESET_ID,
    type: NodeType.SERVICE,
    label: PROMOTE_PRESET_LABEL,
    group: '自动化',
    hideFromServiceList: true,
    prefill: { name: PROMOTE_PRESET_LABEL, serviceRef: SERVICE_PROMOTE },
  },
  {
    id: SET_ATTRIBUTE_PRESET_ID,
    type: NodeType.SERVICE,
    label: SET_ATTRIBUTE_PRESET_LABEL,
    group: '自动化',
    hideFromServiceList: true,
    prefill: { name: SET_ATTRIBUTE_PRESET_LABEL, serviceRef: SERVICE_SET_ATTRIBUTE },
  },
  {
    // 仍是 SERVICE 节点（编译产物 serviceTask + serviceId 不变，反解析也读得回），
    // 只是"从节点库拖出来时"预置好服务与节点名 —— 它是同一个技术产线的业务入口。
    id: SET_STATE_PRESET_ID,
    type: NodeType.SERVICE,
    label: SET_STATE_PRESET_LABEL,
    group: '自动化',
    prefill: { name: SET_STATE_NODE_NAME, serviceRef: SERVICE_SET_STATE },
  },
  {
    // 系统集成：把业务对象集合发布到目标系统的 REST 接口。
    // 与「设置状态」同一技术产线（SERVICE 节点 + serviceId），只是预置了另一个服务。
    id: PUBLISH_PRESET_ID,
    type: NodeType.SERVICE,
    label: PUBLISH_PRESET_LABEL,
    group: '自动化',
    prefill: { name: PUBLISH_NODE_NAME, serviceRef: SERVICE_PUBLISH_TO_SYSTEM },
  },
  {
    // REST 接口调用：通用出站。与「系统集成」并列的"调外部系统"入口，
    // 区别在报文 —— 这里由设计者按被调接口的约定组织业务参数；
    // 地址与凭据来自目标系统注册表（节点只引用 code）。
    id: REST_CALL_PRESET_ID,
    type: NodeType.SERVICE,
    label: REST_CALL_PRESET_LABEL,
    group: '自动化',
    prefill: { name: REST_CALL_NODE_NAME, serviceRef: SERVICE_REST_CALL },
  },
  { type: NodeType.NOTIFY, group: '自动化' },
  { type: NodeType.SUB_PROCESS, group: '自动化' },
  // 分支
  { type: NodeType.EXCLUSIVE_GATEWAY, group: '分支' },
  { type: NodeType.PARALLEL_GATEWAY, group: '分支' },
  { type: NodeType.INCLUSIVE_GATEWAY, group: '分支' },
]

/** 条目 id（缺省即类型名） */
export function presetId(preset: NodePreset): string {
  return preset.id ?? preset.type
}

/**
 * 按条目 id 取条目。
 *
 * <p>刻意也接受类型名：条目 id 缺省就是类型名，于是老的载荷（只有类型）照样能落点 ——
 * 不需要为了这次改动去同步别处的调用。
 */
export function presetById(id: string | undefined | null): NodePreset | undefined {
  return id ? NODE_PRESETS.find((preset) => presetId(preset) === id) : undefined
}

/** 按类型取目录条目（同类型有多个入口时取第一个，即通用入口） */
export function presetByType(type: NodeTypeT): NodePreset | undefined {
  return NODE_PRESETS.find((preset) => preset.type === type)
}

/** 节点库显示名（缺省回落类型的业务名） */
export function presetLabel(preset: NodePreset): string {
  return preset.label ?? NODE_TYPE_LABEL[preset.type]
}

/**
 * 已有独立入口、且声明"不重复出现"的服务 id —— 「服务」下拉据此过滤。
 *
 * <p>从条目声明里推导而不是另写一份清单：将来再加一个独立入口，
 * 只要它标了 {@link NodePreset.hideFromServiceList}，下拉自动不再重复列它。
 */
export function serviceIdsHiddenFromServiceList(): Set<string> {
  return new Set(
    NODE_PRESETS
      .filter((preset) => preset.hideFromServiceList)
      .map((preset) => preset.prefill?.serviceRef)
      .filter((ref): ref is string => typeof ref === 'string'),
  )
}

/** 按分组聚合（保持 NODE_PRESET_GROUPS 的顺序） */
export function presetGroups(): Array<{ group: NodePresetGroup; items: NodePreset[] }> {
  return NODE_PRESET_GROUPS.map((group) => ({
    group,
    items: NODE_PRESETS.filter((preset) => preset.group === group),
  }))
}
