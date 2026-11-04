/**
 * CK-PLM 流程设计器 · 字段声明（唯一字段事实源）
 *
 * <p>本文件是「人怎么填」的唯一事实源，与 `dsl-core/schema.ts`（「合不合法」）
 * 同一结构的两面。`__tests__/field-schema.test.ts` 用覆盖测试锁定二者一致：
 * 每个节点类型都必须有字段声明，且声明的路径必须能被写进 DSL 而不产生未知字段。
 *
 * <p>声明顺序即面板显示顺序；同 `group` 的字段归入同一折叠区。
 */

import {
  ABSTAIN_POLICY_LABEL,
  APPROVAL_MODE_LABEL,
  ASSIGNEE_STRATEGY_LABEL,
  BUILTIN_SERVICES,
  CONDITION_OPERATOR_LABEL,
  DEADLINE_ANCHOR_LABEL,
  ASSIGNEE_BUILTIN_VARIABLES,
  END_CALLBACK_KIND_LABEL,
  EdgeKind,
  EDGE_ROUTE_LABEL,
  MIN_PASS_PERCENT,
  NEEDS_ROLES,
  NEEDS_USERS,
  NEEDS_VARIABLE,
  NEEDS_EXPRESSION,
  NodeType,
  NOTIFY_CHANNEL_LABEL,
  OVERDUE_ACTION_LABEL,
  PASS_RULE_MODE_LABEL,
  REJECT_TARGET_LABEL,
  TIMER_MODE_LABEL,
  serviceIdsHiddenFromServiceList,
  serviceParamsAllFromList,
  UNARY_OPERATORS,
  UNSUPPORTED_ASSIGNEE_STRATEGIES,
  ValueType,
  defaultFormCodeOfNode,
  defaultNodeSize,
  fixedFormCodeOfNode,
  findFormTemplate,
  formTemplatesFor,
  isCountersignNode,
  type AssigneeStrategy as AssigneeStrategyT,
  type FlowDsl,
  type FlowEdge,
  type NodeType as NodeTypeT,
} from '@flow-dsl-core'
// 锚点清单由画布层给出（面板与画布必须同一份推导，见 anchorOptions 的说明）
import { anchorsOf } from '@flow-canvas'
import type { FieldSpec, OptionItem, SchemaContext } from './field-schema'

/** 把 `{CODE: '中文'}` 常量映射转为下拉选项 */
function optionsOf(labelMap: Record<string, string>): OptionItem[] {
  return Object.entries(labelMap).map(([value, label]) => ({ value, label }))
}

/**
 * 审批人策略下拉 —— 运行期未实现的策略保留在列表里（存量模板里可能有这个值，
 * 删掉选项会让下拉框显示空白），但标签上直说，别让人配完才发现跑不起来。
 * 判定清单见 `UNSUPPORTED_ASSIGNEE_STRATEGIES`（与校验、编译层同一份）。
 */
function assigneeStrategyOptions(): OptionItem[] {
  return optionsOf(ASSIGNEE_STRATEGY_LABEL).map((option) =>
    UNSUPPORTED_ASSIGNEE_STRATEGIES.includes(option.value as AssigneeStrategyT)
      ? { ...option, label: `${option.label}（运行期未实现）` }
      : option,
  )
}

/**
 * 读取目标上的字段（<b>支持点号路径</b>）。
 *
 * <p>必须支持点号路径：可见性判定大量使用 `passRule.mode`、`deadline.action`
 * 这类嵌套路径（如「逾期后果=重新分配时才显示升级对象」）。
 */
function get(target: Record<string, unknown>, path: string): unknown {
  let cursor: unknown = target
  for (const segment of path.split('.')) {
    if (!cursor || typeof cursor !== 'object') {
      return undefined
    }
    cursor = (cursor as Record<string, unknown>)[segment]
  }
  return cursor
}

/** 取字段（等价于 get，保留语义化命名） */
function pick(target: Record<string, unknown>, key: string): unknown {
  return get(target, key)
}

/** 判断字段是否等于某值（支持嵌套路径） */
function is(target: Record<string, unknown>, key: string, value: string): boolean {
  return get(target, key) === value
}

/** 子对象是否存在（object-toggle 的可见性依据） */
function has(target: Record<string, unknown>, key: string): boolean {
  const value = get(target, key)
  return !!value && typeof value === 'object'
}

// ==================== 审批人策略（复合控件，四处复用）====================

/**
 * 生成一组审批人策略字段。
 *
 * <p>`strategy` 决定后续哪些字段可见 —— 与 `dsl-core/validate.ts` 的
 * `hasAssignee()` 判定同源（同一组 NEEDS_* 常量），因此面板不会让用户
 * 配了「策略不需要的东西」，也不会漏配「策略必需的东西」。
 *
 * @param prefix 路径前缀（如 `assignee` / `initiator` / `recipients`；子对象用 `deadline.reassignTo`）
 * @param group  分组名（用于折叠区标题）
 */
export function assigneeFields(prefix: string, group = '参与人'): FieldSpec[] {
  return [
    {
      key: `${prefix}.strategy`,
      label: '策略',
      control: 'select',
      options: assigneeStrategyOptions(),
      required: true,
      group,
      // 与「设置审批人」的分工：本活动若位于设置活动下游，运行期的人由发起人在设置页
      // 指定，此处的「指定角色」就退化为**候选范围**（发起人只在该角色成员里挑人）
      help: '指定角色时，若本活动在「设置审批人」活动下游，该角色即为发起人挑人的候选范围',
    },
    {
      key: `${prefix}.userOids`,
      label: '指定用户',
      control: 'multi-select',
      optionsSource: 'USERS',
      group,
      placeholder: '选择用户',
      visibleIf: (t) => NEEDS_USERS.includes(get(t, `${prefix}.strategy`) as never),
      clearOnHide: true,
    },
    {
      key: `${prefix}.roleCodes`,
      label: '角色',
      control: 'multi-select',
      optionsSource: 'ROLES',
      group,
      placeholder: '选择角色',
      visibleIf: (t) => NEEDS_ROLES.includes(get(t, `${prefix}.strategy`) as never),
      clearOnHide: true,
    },
    {
      key: `${prefix}.variableName`,
      label: '流程变量',
      control: 'select',
      // 内置里只列"装的是人员标识"的那几个（如 initiator）：本字段是审批人的取值，
      // 布尔 / 对象类内置变量选了也指派不到人。自定义变量排在后面且需先声明。
      optionsFromDsl: (ctx) => [
        ...ASSIGNEE_BUILTIN_VARIABLES.map((v) => ({
          value: v.name,
          label: `${v.name}（内置·${v.timing}）`,
        })),
        ...(ctx.dsl.variables ?? []).map((v) => ({ value: v.name, label: v.label ?? v.name })),
      ],
      group,
      help: '内置的发起人变量可直接选；自定义变量需先在「流程设置」中声明',
      visibleIf: (t) => NEEDS_VARIABLE.includes(get(t, `${prefix}.strategy`) as never),
      clearOnHide: true,
    },
    {
      key: `${prefix}.expression`,
      label: '表达式',
      control: 'text',
      group,
      help: '受控白名单函数，如 empty(part.material)',
      visibleIf: (t) => NEEDS_EXPRESSION.includes(get(t, `${prefix}.strategy`) as never),
      clearOnHide: true,
    },
    {
      key: `${prefix}.deptFieldKey`,
      label: '部门字段',
      control: 'text',
      group,
      help: '业务对象上承载部门的字段 key',
      visibleIf: (t) => is(t, `${prefix}.strategy`, 'DEPT_LEADER'),
      clearOnHide: true,
    },
  ]
}

// ==================== 截止时间 / 驳回 / 绑定（可选子对象）====================

const deadlineFields = (): FieldSpec[] => [
  {
    key: 'deadline',
    label: '启用截止时间',
    control: 'object-toggle',
    group: '截止时间',
    objectTemplate: () => ({ anchor: 'NODE_START', durationHours: 24, action: 'SKIP' }),
  },
  {
    key: 'deadline.anchor',
    label: '计时起点',
    control: 'select',
    options: optionsOf(DEADLINE_ANCHOR_LABEL),
    group: '截止时间',
    visibleIf: (t) => has(t, 'deadline'),
  },
  {
    key: 'deadline.durationHours',
    label: '时长',
    control: 'number',
    min: 1,
    suffix: '小时',
    group: '截止时间',
    visibleIf: (t) => has(t, 'deadline'),
  },
  {
    key: 'deadline.action',
    label: '逾期后果',
    control: 'select',
    options: optionsOf(OVERDUE_ACTION_LABEL),
    group: '截止时间',
    visibleIf: (t) => has(t, 'deadline'),
  },
  ...assigneeFields('deadline.reassignTo', '截止时间').map((f) => ({
    ...f,
    visibleIf: (t: Record<string, unknown>) =>
      has(t, 'deadline') && is(t, 'deadline.action', 'REASSIGN'),
  })),
  {
    key: 'deadline.remindBeforeHours',
    label: '提前提醒',
    control: 'number',
    min: 0,
    suffix: '小时',
    group: '截止时间',
    visibleIf: (t) => has(t, 'deadline'),
  },
  {
    key: 'deadline.remindAfterHours',
    label: '逾期催办',
    control: 'number',
    min: 0,
    suffix: '小时',
    group: '截止时间',
    visibleIf: (t) => has(t, 'deadline'),
  },
]

/**
 * 驳回的路由说明 —— **不再是配置项**（2026-10-13 用户口径：统一为画线）。
 *
 * <p>原先这里挂着「允许驳回 + 驳回目标（上一步 / 发起人 / 指定节点）+ 驳回意见必填」，
 * 与"从节点连一条路由为「驳回」的出边"是同一件事的两套表达：两者并存时既报
 * {@code EDGE_ROUTE_CONFLICT}（"驳回到底听谁的"说不清），又让用户以为画了线就完事。
 * 现在只保留一种做法 —— <b>画线</b>：
 * <ul>
 *   <li>画了「驳回」出边 = 该节点可驳回（办理页出现「驳回」）；</li>
 *   <li>没画 = 只能同意；</li>
 *   <li>驳回意见默认必填（驳回总得说个理由）。</li>
 * </ul>
 * 走线的另一个好处正是当初引入它的原因：驳回之后可以先走自动化节点
 * （设置状态、撤回电子签名…）再回到某个活动。
 *
 * <p>老模板里残留的 {@code reject} 配置由加载归一清掉
 * （见 dsl-core/schema.ts 的 {@code stripLegacyReject}）；依赖旧配置驳回的老流程，
 * 需要补一条驳回出边。
 */
const rejectFields = (): FieldSpec[] => [
  {
    key: 'hint-route',
    label: '驳回',
    control: 'hint',
    group: '路由',
    help: '「通过」＝从节点下方圆点连出的默认出边。'
      + '「驳回」＝再连一条出边，在【连线】的属性面板里把「路由」设为「驳回」——'
      + '这样驳回后可以先走自动化节点（设置状态、撤回电子签名…）再回到某个活动。'
      + '没画驳回边＝该节点只能同意。',
  },
]

/**
 * 「绑定业务对象」字段组 —— 现在**只给开始节点用**（2026-10-13 用户口径）。
 *
 * <p>审批 / 会签 / 办理节点已不再提供这个开关：业务对象在流程发起时就绑定了
 * （businessObjectSet），各节点再声明一次既没人读、又要用户多配一遍。
 * 老模板里残留的 binding 由加载归一清掉（见 dsl-core/schema.ts 的 stripNodeBinding）。
 */
const bindingFields = (): FieldSpec[] => [
  {
    key: 'binding',
    label: '绑定业务对象',
    control: 'object-toggle',
    group: '数据',
    objectTemplate: () => ({ objectType: '' }),
    help: '声明本节点操作的主对象与必填字段',
  },
  {
    key: 'binding.objectType',
    label: '对象类型',
    control: 'select',
    optionsSource: 'OBJECT_TYPES',
    group: '数据',
    required: true,
    visibleIf: (t) => has(t, 'binding'),
  },
  {
    key: 'binding.requiredFields',
    label: '必填字段',
    control: 'tags',
    group: '数据',
    placeholder: '输入字段 key 后回车',
    visibleIf: (t) => has(t, 'binding'),
  },
]

/**
 * 节点表单字段。
 *
 * <p><b>是下拉，不是手填</b>：模板 code 手填错一个字符，设计期毫无感知 ——
 * 到运行期才发现取不到表单。候选项来自**已注册的表单模板**：内置的在前端注册表
 * （`dsl-core/forms.ts`），企业自定义的在「业务配置 → 流程表单」里维护、由后端下发，
 * 两者在这里合流（同一节点类型可以有多张，选哪张写进节点的 `formRef`）。
 *
 * <p>`fixed` 用于「本活动只能使用某个内置表单」的节点类型（如「设置审批人」用
 * 「设置流程参与者」）：字段预先填好并**置为只读**。这类表单的结构由代码生成，
 * 不存在"换一个"的余地 —— 让它可改只可能改错。
 *
 * <p><b>会签不是固定项（2026-10-13 修正）</b>：内置「会签」只是**默认**（由
 * {@link defaultFormCodeOfNode} 兜底），会签节点与企业一样可以换用别的会签表单 ——
 * 因为会签人常常还需要看到"在评审什么"（业务申请信息），固定的内置表只能显示规则与各人意见。
 * 原先这里把会签锁成只读，导致行业会签表配不上、下拉里还会直接回显 code。
 */
const formAndCommentFields = (options: { fixed?: boolean; help?: string } = {}): FieldSpec[] => [
  {
    key: 'formRef',
    label: '节点表单',
    control: 'select',
    group: '数据',
    placeholder: '选择表单模板（留空＝用内置默认）',
    optionsFromDsl: (ctx) => {
      const node = ctx.target as { type?: NodeTypeT; approvalMode?: string }
      const pinned = fixedFormCodeOfNode(node)
      // 语义固定的表单只给这一项：编译层同样以它为准，多给选项只会让人以为"选了会生效"
      if (pinned) {
        return [{ value: pinned, label: `${findFormTemplate(pinned)?.label ?? pinned}（固定）` }]
      }
      return taskFormOptionsOfNode(node)
    },
    disabled: options.fixed === true,
    // DSL 里还没写过值时显示"实际会用的那张"：老模板的节点可能没写过 formRef，
    // 否则会呈现一个空框（用户不知道编译出来到底是什么表单）
    defaultWhenEmpty: (ctx) =>
      defaultFormCodeOfNode(ctx.target as { type?: NodeTypeT; approvalMode?: string }),
    // 节点可以说自己的话（开始节点的口径与"审批/会签默认表单"完全不同，见 NODE_FIELDS[START]）
    help: options.help
      ?? (options.fixed
        ? '本活动使用内置固定表单，不可修改（编译时按活动类型写入 formKey）'
        : '留空＝按活动类型用内置默认表单（审批 → 审批意见；会签 → 会签）；要换一张，先在「业务配置 → 流程表单」里建好表单，再在这里选中它'),
  },
]

/**
 * 由服务描述符派生的「参数下拉」字段。
 *
 * <p>带 {@code optionsSource} 的参数（如「设置状态」的目标状态）渲染成下拉，候选来自对应清单
 * （生命周期状态 / 角色 / 用户 / 对象类型，取数见 `options.ts`）：这类取值本来就是"从清单里挑一个"，
 * 让人手输 code 只可能输错（错在哪儿还得等到运行期才发现）。
 *
 * <p>字段路径写在 {@code params.<参数名>}：与编译层读参数的位置一致
 * （{@code node.params} → flowable:field），因此下拉选完就是编译产物里的那个值。
 */
export function serviceParamFields(): FieldSpec[] {
  const fields: FieldSpec[] = []
  for (const service of BUILTIN_SERVICES) {
    for (const param of service.params) {
      // 两种"有专门控件"的参数：清单型（下拉）与自由文本型（带标签输入框 / 密码框）。
      // 其余参数仍走通用键值编辑器 —— 它们的取值确实是自由键值对。
      if (!param.optionsSource && !param.control) {
        continue
      }
      const isPassword = param.control === 'password'
      fields.push({
        key: `params.${param.name}`,
        label: param.label,
        control: param.optionsSource ? 'select' : (isPassword ? 'password' : 'text'),
        optionsSource: param.optionsSource,
        placeholder: param.optionsSource
          ? `选择${param.label}`
          : (isPassword ? `填写${param.label}（不回显）` : `填写${param.label}`),
        required: param.required,
        help: param.help,
        // 只对选中该服务的节点可见；切走时清掉，避免 DSL 里留着别的服务的参数
        // （通用键值编辑器刻意没有 clearOnHide：否则切服务时会把这里的值一起抹掉）
        visibleIf: (t) => pick(t, 'serviceRef') === service.id,
        clearOnHide: true,
      })
    }
  }
  return fields
}

/**
 * 表单模板下拉项（按**节点**过滤；内置模板在名字上标出）。
 *
 * <p>为什么按节点而不是只按类型：会签在 DSL 里有两种表达 —— 专门的「会签审批」节点，
 * 以及「审批节点 + 审批模式=会签」。后者若只按 `node.type` 过滤，就一个会签模板都列不出来，
 * 企业/行业想给会签换一张表（如「元器件认证会签」）无从下手（2026-10-13 修）。
 */
function taskFormOptionsOfNode(node: { type?: NodeTypeT; approvalMode?: string }): OptionItem[] {
  const nodeType = isCountersignNode(node) ? NodeType.COUNTERSIGN_APPROVAL : node.type
  return formTemplatesFor(nodeType).map((template) => ({
    value: template.code,
    label: template.builtin ? `${template.label}（内置）` : template.label,
  }))
}

// ==================== 按节点类型的字段声明 ====================

/** 所有节点共有字段 */
const COMMON_FIELDS: FieldSpec[] = [
  { key: 'name', label: '名称', control: 'text', required: true },
  { key: 'description', label: '说明', control: 'textarea' },
]

/** 各节点类型的专属字段 */
const NODE_FIELDS: Record<NodeTypeT, FieldSpec[]> = {
  [NodeType.START]: [
    ...assigneeFields('initiator', '发起人'),
    { key: 'autoStart', label: '允许自动发起', control: 'switch', help: '由对象事件触发，无需人工发起' },
    // 开始节点的表单 = 发起时填的申请单，**只在"发起"这个没有任务的场景里用** ——
    // 下拉里的模板并非都能在这儿填（2026-10-13 用户踩过：配了认证表，发起页整段显示不出来）
    ...formAndCommentFields({
      help: '发起时要填的申请单（在发起弹框里点「发起」后弹出填写）。'
        + '下拉里的模板不是都能在这儿用：发起时还没有任务实例，「元器件认证申请」「技术评审申请单」'
        + '这类靠任务带出关联器件 / 流程变量的表单，发起页无法填写（届时会给出说明）；'
        + '请选「元器件引入申请单」「阶段门决策申请单」这类不依赖任务上下文的申请单。'
        + '留空＝发起时不填表单（弹框只确认业务对象）。',
    }),
    ...bindingFields(),
  ],
  [NodeType.END]: [
    {
      key: 'callbacks',
      label: '结束回调',
      control: 'object-list',
      itemTitleKey: 'kind',
      newItem: () => ({ kind: 'OBJECT_STATE' }),
      itemFields: [
        { key: 'kind', label: '类型', control: 'select', options: optionsOf(END_CALLBACK_KIND_LABEL), required: true },
        {
          key: 'targetState',
          label: '目标状态',
          control: 'select',
          optionsSource: 'LIFECYCLE_STATES',
          visibleIf: (t) => is(t, 'kind', 'OBJECT_STATE'),
        },
        {
          key: 'recipients',
          label: '通知接收人',
          control: 'assignee',
          visibleIf: (t) => is(t, 'kind', 'NOTIFY'),
        },
        {
          key: 'templateCode',
          label: '通知模板',
          control: 'text',
          visibleIf: (t) => is(t, 'kind', 'NOTIFY'),
        },
      ],
    },
  ],
  // 设置审批人：由发起人（owner）执行，通常就是流程启动后的第一个节点
  //（不作硬约束 —— 它之前的活动只要都是"由发起人办理"即可，否则校验报错）。
  // 「要设置哪些活动」刻意不在这里维护 —— 下游所有审批 / 会签 / 办理活动
  // 都会自动进入它的节点表单（见 app/task/assignee-setup.ts）
  [NodeType.SET_ASSIGNEE]: [
    {
      key: 'hint-setup',
      label: '设置审批人',
      control: 'hint',
      group: '基本',
      help: '由流程发起人（owner）执行，通常放在流程启动后的第一个节点：一次性指定后面所有人工活动的人员。它只管下游 —— 若它之前还有活动，那些活动必须自己声明由谁办（如"由发起人办理"），否则校验会拦下（承办人会落空）。节点表单会自动列出下游所有审批 / 会签 / 办理活动，无需在此维护清单。',
    },
    ...assigneeFields('assignee', '执行人'),
    { key: 'commentRequired', label: '必填说明', control: 'switch' },
    ...deadlineFields(),
    // 本活动的表单是内置固定的（设置流程参与者）：默认填好、不可修改
    ...formAndCommentFields({ fixed: true }),
  ],
  // 会签审批：与审批节点共用大部分字段，但**没有审批模式**（它就是会签本身），
  // 且通过规则恒可见 —— 不存在「先选模式才看得到规则」的两段式操作
  [NodeType.COUNTERSIGN_APPROVAL]: [
    {
      key: 'hint-countersign',
      label: '会签',
      control: 'hint',
      group: '会签规则',
      help: '会签是多人集体决策：参与人策略应能解析出多人（「发起人」「发起人上级」这类单值策略会被校验提醒），并需给出「怎么算通过」。',
    },
    ...assigneeFields('assignee'),
    {
      key: 'passRule.mode',
      label: '通过规则',
      control: 'select',
      options: optionsOf(PASS_RULE_MODE_LABEL),
      group: '会签规则',
      required: true,
      help: '会签必须回答「怎么算通过」',
    },
    {
      key: 'passRule.percent',
      label: '通过比例',
      control: 'number',
      min: MIN_PASS_PERCENT,
      max: 100,
      suffix: '%',
      group: '会签规则',
      help: `不得低于 ${MIN_PASS_PERCENT}%`,
      visibleIf: (t) => is(t, 'passRule.mode', 'PERCENT'),
      clearOnHide: true,
    },
    {
      key: 'passRule.count',
      label: '必须票数',
      control: 'number',
      min: 1,
      suffix: '票',
      group: '会签规则',
      visibleIf: (t) => is(t, 'passRule.mode', 'COUNT'),
      clearOnHide: true,
    },
    {
      key: 'passRule.abstain',
      label: '弃权处理',
      control: 'select',
      options: optionsOf(ABSTAIN_POLICY_LABEL),
      group: '会签规则',
      visibleIf: (t) => !is(t, 'passRule.mode', 'VETO'),
      clearOnHide: true,
    },
    { key: 'commentRequired', label: '必填审批意见', control: 'switch' },
    ...deadlineFields(),
    ...rejectFields(),
    // 会签表单：默认是内置「会签」（显示通过规则 + 每个人的办理情况），**可换** ——
    // 行业会签表往往还要展示业务申请信息（如「元器件认证会签」）
    ...formAndCommentFields(),
  ],
  [NodeType.APPROVAL]: [
    ...assigneeFields('assignee'),
    /**
     * 审批模式 —— **固定「单签」，不再让编辑者选**（2026-10-13 用户口径）。
     *
     * <p>原先这里是下拉（单签 / 串行 / 并行全员 / 或签 / 会签），并由它决定「会签规则」
     * 那组字段是否出现 —— 于是"审批"这个活动承担了两种完全不同的语义（一人拍板 vs 多人多票）。
     * 现在把它固定为单签：**需要多人集体决策就用「会签审批」节点**（它自带通过规则、
     * 每个人的办理情况，且表单可换）。这样"审批"与"会签"各自只有一种语义，编辑者不会选错。
     *
     * <p>老模板里配过其它模式的节点，由加载归一统一成单签（见 dsl-core/schema.ts 的
     * {@code normalizeApprovalMode}）。
     */
    {
      key: 'hint-approval-mode',
      label: '审批模式',
      control: 'hint',
      group: '审批规则',
      help: '固定为「单签」：一人办理即通过。需要多人集体决策（会签）请改用「会签审批」节点 ——'
        + '它有通过规则与每个人的办理情况。',
    },
    { key: 'commentRequired', label: '必填审批意见', control: 'switch' },
    ...deadlineFields(),
    ...rejectFields(),
    // 审批表单：内置「审批意见」（同意/驳回 + 驳回位置 + 意见必填）是**默认**，
    // 不是固定 —— 企业可以在「业务配置 → 流程表单」里加自己的审批表单，再在这里换。
    // 只有「审批 + 会签模式」例外：那张会签表单是语义固定（下拉里会自动收窄到它）。
    ...formAndCommentFields(),
    // 「绑定业务对象」不在这里（2026-10-13 用户口径）：审批 / 会签 / 办理节点不再声明
    // 主对象与必填字段 —— 业务对象在流程发起时就绑定了（businessObjectSet），
    // 各节点再声明一次既没人读、又要用户多配一遍。老模板里残留的 binding 由加载归一清掉
    // （见 dsl-core/schema.ts 的 stripNodeBinding）
  ],
  [NodeType.TASK]: [
    ...assigneeFields('assignee', '办理人'),
    // 交付件：先问"要不要"，要的话多选**业务对象类型**（2026-10-13 用户口径）。
    // 原先是一个「交付物」输入框，让人手填文档类型 code —— 手填既容易错，
    // 又说不清这件交付件是挂在哪个业务对象上的
    {
      key: 'needDeliverables',
      label: '是否需要交付件',
      control: 'switch',
      group: '数据',
      help: '需要时，下面指明交付件挂在哪些业务对象类型上',
    },
    {
      key: 'deliverableObjectTypes',
      label: '业务对象类型',
      control: 'multi-select',
      optionsSource: 'OBJECT_TYPES',
      group: '数据',
      placeholder: '选择业务对象类型（可多选）',
      visibleIf: (t) => is(t, 'needDeliverables', true),
      // 关掉开关时一并清空：不留下一个看不见却仍然生效的绑定
      clearOnHide: true,
    },
    ...deadlineFields(),
    ...formAndCommentFields(),
  ],
  [NodeType.SERVICE]: [
    /**
     * 配置说明 / 配置样例 —— 只在**还没选服务**时出现。
     *
     * <p>为什么不常驻：选定服务后面板会列出该服务声明的参数（下拉、输入框）与「服务参数」
     * 编辑器，那时再摆一段"怎么填"就是噪声；而没选服务时面板只有两个空控件，
     * 正是最需要"填成什么样"的时候（真实反馈：拖出来不知道填什么）。
     */
    {
      key: 'hint-service',
      label: '配置说明',
      control: 'hint',
      visibleIf: (t) => !pick(t, 'serviceRef'),
      help: '这个节点执行的是「后端已注册的一段函数/服务」：执行哪一段由上面的「服务」决定。'
        + '选定服务后，它声明过的参数会变成专用控件（如「目标状态」下拉），'
        + '其余参数在「服务参数」里按 参数名 → 取值 填写，取值可写常量或 ${流程变量}。'
        + '「表达式（高级）」是平台内置动作的另一种写法（受控白名单），与服务二选一：'
        + '常规函数调用请走「服务」。',
    },
    {
      key: 'hint-service-example',
      label: '配置样例',
      control: 'hint',
      visibleIf: (t) => !pick(t, 'serviceRef'),
      help: '① 调后端函数：服务选 custom.sendMail，服务参数填 '
        + 'to → ${initiator}；subject → 流程待办通知；body → 工单 ${ecrNo} 已提交。'
        + ' 下拉里没有的服务，说明后端还没注册它（新函数需后端先实现）。',
    },
    {
      key: 'serviceRef',
      label: '服务',
      control: 'service-ref',
      // 参数名提示只加在"要手输参数"的服务上：有下拉可选的（设置状态）报参数名
      // 反而是噪声 —— 它已经有一个标着「目标状态」的下拉了
      options: BUILTIN_SERVICES
        // 已有独立节点库入口的服务（检出 / 检入 / 修订 / 更新属性）不在这里重复出现：
        // 同一个动作两处入口，设计者得先判断两者有没有差别（见 presets 的 hideFromServiceList）
        .filter((service) => !serviceIdsHiddenFromServiceList().has(service.id))
        .map((service) => ({
          value: service.id,
          label: (service.params.length && !serviceParamsAllFromList(service.id))
            ? `${service.label}（${service.params.map((p) => `${p.name}${p.required ? '*' : ''}`).join('、')}）`
            : service.label,
        })),
      help: '平台已注册/已实现的自动服务（含后端新增的 custom.* 定制服务）；与表达式二选一'
        + ' —— 下拉里没有的服务，说明后端还没实现它',
    },
    // 参数下拉（由服务描述符派生）：如「设置状态」的目标状态直接挑生命周期状态，
    // 不用记参数名 state、也不用背状态 code
    ...serviceParamFields(),
    {
      key: 'params',
      label: '服务参数',
      control: 'key-value',
      // 参数全部有下拉的服务不再显示它（同一件事不两处填）；自由文本参数（如
      // 「设置对象属性」的属性/取值）仍在这里填
      visibleIf: (t) => !!pick(t, 'serviceRef') && !serviceParamsAllFromList(pick(t, 'serviceRef') as string),
      // 取不到变量时会中断这一步（不是把 ${x} 原样发出去）——失败策略写在这里，
      // 否则设计者只在运行期看到"流程卡住"却不知道是自己写错了变量名
      help: '参数名须与所选服务的参数声明一致，例如 orderNo → ${ecrNo}、notify → true；'
        + '取值可写常量或 ${变量名}（运行期取不到该变量会中断这一步，不会把 ${变量名} 原样发出去）',
    },
    {
      key: 'expression',
      label: '表达式（高级）',
      control: 'text',
      visibleIf: (t) => !pick(t, 'serviceRef'),
      help: '受控白名单函数；配置服务后此项隐藏',
    },
  ],
  [NodeType.EXCLUSIVE_GATEWAY]: [
    {
      key: 'advanced',
      label: '启用表达式模式',
      control: 'switch',
      help: '关闭时按字段选择器配条件（在出边的属性里配置）',
    },
    {
      key: 'hint-branches',
      label: '分支条件',
      control: 'hint',
      help: '分支条件挂在「出边」上：选中连线即可配置，网关的默认分支建议显式指定',
    },
  ],
  [NodeType.PARALLEL_GATEWAY]: [
    {
      key: 'hint-parallel',
      label: '并行分支',
      control: 'hint',
      help: '并行网关的所有出边同时激活，因此不允许配置条件（配了会被校验拦截）',
    },
  ],
  [NodeType.INCLUSIVE_GATEWAY]: [
    {
      key: 'advanced',
      label: '启用表达式模式',
      control: 'switch',
      help: '包容分支按条件求值决定激活哪些出边',
    },
    {
      key: 'hint-branches',
      label: '分支条件',
      control: 'hint',
      help: '分支条件挂在「出边」上：选中连线即可配置',
    },
  ],
  [NodeType.NOTIFY]: [
    ...assigneeFields('recipients', '接收人'),
    { key: 'templateCode', label: '通知模板', control: 'text' },
    { key: 'attachPrimaryObject', label: '附带业务对象链接', control: 'switch' },
  ],
  [NodeType.SUB_PROCESS]: [
    { key: 'processKey', label: '流程 key', control: 'text', required: true, help: '被调用流程定义的 key' },
    {
      key: 'variableMap',
      label: '变量映射',
      control: 'key-value',
      help: '子流程变量名 → 父流程表达式（如 ${ecrNo}）',
    },
    // 1:M 子流程：一张通告派生 N 个任务
    {
      key: 'multiInstance',
      label: '多实例（1:M）',
      control: 'object-toggle',
      group: '多实例',
      objectTemplate: () => ({ collection: '', elementVariable: 'item', sequential: false }),
      help: '按集合变量逐个拉起独立的子流程实例，全部结束才继续 —— 用于「一张通告派生 N 个变更任务」',
    },
    {
      key: 'multiInstance.collection',
      label: '集合变量',
      control: 'text',
      group: '多实例',
      required: true,
      placeholder: 'changeTaskList',
      visibleIf: (t) => has(t, 'multiInstance'),
      help: '父流程里存放清单的变量名：只填名字，不要写 ${}',
    },
    {
      key: 'multiInstance.elementVariable',
      label: '元素变量',
      control: 'text',
      group: '多实例',
      placeholder: 'item',
      visibleIf: (t) => has(t, 'multiInstance'),
      help: '每个实例取到的当前元素；业务 key 相对它取字段',
    },
    {
      key: 'multiInstance.businessKey',
      label: '业务 key 字段',
      control: 'text',
      group: '多实例',
      placeholder: 'oid',
      visibleIf: (t) => has(t, 'multiInstance'),
      help: '元素上的字段名，编译为 ${元素变量.字段}；留空则子实例共用父流程业务 key',
    },
    {
      key: 'multiInstance.sequential',
      label: '串行执行',
      control: 'switch',
      group: '多实例',
      visibleIf: (t) => has(t, 'multiInstance'),
      help: '默认并行；并行会等待全部子实例结束后再往下走',
    },
  ],
  [NodeType.TIMER]: [
    { key: 'mode', label: '等待方式', control: 'select', options: optionsOf(TIMER_MODE_LABEL), required: true },
    {
      key: 'duration',
      label: '时长',
      control: 'text',
      placeholder: 'PT2H / P1D',
      help: 'ISO-8601 时长格式',
      visibleIf: (t) => is(t, 'mode', 'DURATION'),
      clearOnHide: true,
    },
    {
      key: 'at',
      label: '等待至',
      control: 'text',
      placeholder: '2026-09-13T09:00:00',
      help: 'ISO-8601 时点',
      visibleIf: (t) => is(t, 'mode', 'AT'),
      clearOnHide: true,
    },
  ],
}

/** 取某节点类型的完整字段声明（共有字段 + 专属字段） */
export function nodeFields(type: NodeTypeT): FieldSpec[] {
  return [...COMMON_FIELDS, ...(NODE_FIELDS[type] ?? [])]
}

/** 字段声明覆盖率自检（供测试与开发期断言） */
export function missingNodeSchemaTypes(): NodeTypeT[] {
  return (Object.values(NodeType) as NodeTypeT[]).filter((t) => !NODE_FIELDS[t])
}

// ==================== 连线字段 ====================

const EDGE_KIND_NORMAL: OptionItem = { value: EdgeKind.NORMAL, label: '普通流转' }
const EDGE_KIND_CONDITION: OptionItem = { value: EdgeKind.CONDITION, label: '条件流转' }
const EDGE_KIND_DEFAULT: OptionItem = { value: EdgeKind.DEFAULT, label: '默认分支' }

const EDGE_KIND_OPTIONS: OptionItem[] = [
  EDGE_KIND_NORMAL,
  EDGE_KIND_CONDITION,
  EDGE_KIND_DEFAULT,
]

/**
 * 这条连线**该出现**哪些类型选项（依据是校验规则，不是口味）。
 *
 * <p>核心事实：BPMN 里<b>无条件的边恒为真</b>，网关/多出边节点按文档顺序取第一条为真的。
 * 所以只要一个节点有多条出边，任何一条"无条件"的边都会把它后面的分支（含默认分支）
 * 压成死分支 —— 因此这种场合<b>不能提供「普通流转」</b>，否则等于在 UI 上引导用户踩坑。
 *
 * <ul>
 *   <li>并行分支的出边：一律无条件（`PARALLEL_HAS_CONDITION`）→ 只有「普通流转」；</li>
 *   <li>条件分支 / 包容分支的出边：要么配条件、要么当默认分支（`GATEWAY_NO_DEFAULT`）
 *       → 「条件流转 / 默认分支」；</li>
 *   <li>普通节点有多条出边（如审批的同意/拒绝）：每条都要有条件或其一为默认
 *       （`MISSING_CONDITION`）→ 「条件流转 / 默认分支」；</li>
 *   <li>普通节点只有一条出边：顺序流转 → 「普通流转 / 条件流转」。</li>
 * </ul>
 */
export function edgeKindOptionsFor(dsl: FlowDsl, edge: FlowEdge): OptionItem[] {
  const sourceType = dsl.nodes.find((n) => n.id === edge.source)?.type
  if (sourceType === NodeType.PARALLEL_GATEWAY) {
    return [EDGE_KIND_NORMAL]
  }
  if (
    sourceType === NodeType.EXCLUSIVE_GATEWAY ||
    sourceType === NodeType.INCLUSIVE_GATEWAY
  ) {
    return [EDGE_KIND_CONDITION, EDGE_KIND_DEFAULT]
  }
  const outgoingCount = dsl.edges.filter((e) => e.source === edge.source).length
  return outgoingCount > 1
    ? [EDGE_KIND_CONDITION, EDGE_KIND_DEFAULT]
    : [EDGE_KIND_NORMAL, EDGE_KIND_CONDITION]
}

/**
 * 上面的选项，外加「当前值」。
 *
 * <p>存量数据（导入的 BPMN、手改过的 JSON、或流程结构改了导致原来的选项不再适用）
 * 可能出现"当前值不在候选里"的情况。此时把它补回去，否则单选组会变成
 * **一个都没选中**，用户只会觉得界面坏了；校验会同时指出这条边需要改。
 */
export function edgeKindOptionsWithCurrent(dsl: FlowDsl, edge: FlowEdge): OptionItem[] {
  const options = edgeKindOptionsFor(dsl, edge)
  if (options.some((option) => option.value === edge.kind)) {
    return options
  }
  const current = EDGE_KIND_OPTIONS.find((option) => option.value === edge.kind)
  return current ? [...options, current] : options
}

/** 连线字段（分支条件是连线的属性，与 BPMN 一致） */
/**
 * 该节点是否已经画了「驳回」出边。
 *
 * <p>画了就不再问「驳回目标」：两者是同一件事的两种做法（走图 / 一步跳回），
 * 并存会让"驳回到底听谁的"说不清 —— 见 validate 的 {@code EDGE_ROUTE_CONFLICT}。
 */
function hasRejectRouteFrom(ctx: SchemaContext, target: Record<string, unknown>): boolean {
  const id = target.id as string | undefined
  return !!id && ctx.dsl.edges.some((edge) => edge.source === id && edge.route === 'REJECT')
}

/**
 * 连线面板「路由」字段的可见性：源节点是不是审批 / 会签。
 *
 * <p>要整份 DSL 才知道（连线只记了 source 的 id），这也正是 visibleIf 拿得到 ctx 的原因。
 */
function isApprovalSource(target: Record<string, unknown>, ctx: SchemaContext): boolean {
  const source = ctx.dsl.nodes.find((node) => node.id === target.source)
  return !!source
    && (source.type === NodeType.APPROVAL || source.type === NodeType.COUNTERSIGN_APPROVAL)
}

export function edgeFields(): FieldSpec[] {
  return [
    {
      // 只在"源节点是审批/会签"时出现：只有它们有通过/驳回两个结论可分。
      // 条件由编译层按路由生成（${approved} / ${!approved}），用户不用手写 UEL
      key: 'route',
      label: '路由',
      control: 'select',
      options: [{ value: '', label: '普通流转（不带路由）' }, ...optionsOf(EDGE_ROUTE_LABEL)],
      visibleIf: (t, ctx) => isApprovalSource(t, ctx),
      help: '这条边承载哪个结论：「通过」走它、「驳回」也走它。'
        + '驳回之后若还要对业务对象做事（设置状态、撤回电子签名…），把这些节点接在「驳回」这条边上即可；'
        + '只想"一步退回某处"，用节点属性里的「驳回目标」（两者二选一）',
    },
    { key: 'name', label: '分支名', control: 'text', placeholder: '如「同意」/「拒绝」' },
    {
      key: 'kind',
      label: '类型',
      // 用单选而不是下拉：「默认分支」是分支节点必须配的一项能力，
      // 藏在下拉里就会出现「找不到设置默认分支的地方」（真实反馈）。
      control: 'edge-kind',
      options: EDGE_KIND_OPTIONS,
      required: true,
      help: '「默认分支」是该分支节点的兜底出口：所有条件都不命中时走它；它不参与条件判断（填了条件也不生效）',
    },
    {
      key: 'condition.mode',
      label: '条件模式',
      control: 'select',
      group: '条件',
      options: [
        { value: 'FIELD', label: '字段选择器' },
        { value: 'EXPRESSION', label: '表达式（高级）' },
      ],
      visibleIf: (t) => is(t, 'kind', 'CONDITION'),
    },
    {
      key: 'condition.objectType',
      label: '业务对象',
      control: 'select',
      group: '条件',
      optionsSource: 'OBJECT_TYPES',
      visibleIf: (t) => is(t, 'kind', 'CONDITION') && !is(t, 'condition.mode', 'EXPRESSION'),
    },
    {
      key: 'condition.fieldKey',
      label: '字段',
      control: 'text',
      group: '条件',
      visibleIf: (t) => is(t, 'kind', 'CONDITION') && !is(t, 'condition.mode', 'EXPRESSION'),
    },
    {
      key: 'condition.operator',
      label: '操作符',
      control: 'select',
      group: '条件',
      options: optionsOf(CONDITION_OPERATOR_LABEL),
      visibleIf: (t) => is(t, 'kind', 'CONDITION') && !is(t, 'condition.mode', 'EXPRESSION'),
    },
    {
      key: 'condition.value',
      label: '取值',
      control: 'text',
      group: '条件',
      help: '多值操作符用英文逗号分隔',
      visibleIf: (t) =>
        is(t, 'kind', 'CONDITION') &&
        !is(t, 'condition.mode', 'EXPRESSION') &&
        !UNARY_OPERATORS.includes(get(t, 'condition.operator') as never),
      clearOnHide: true,
    },
    {
      key: 'condition.expression',
      label: '表达式',
      control: 'text',
      group: '条件',
      help: '受控白名单函数，如 amount > 10000',
      visibleIf: (t) => is(t, 'kind', 'CONDITION') && is(t, 'condition.mode', 'EXPRESSION'),
      clearOnHide: true,
    },
    {
      key: 'terminateSiblings',
      label: '终止同层分支',
      control: 'switch',
      help: '触发后终止其余未完成的前驱分支（仅并行 / 包容分支内有意义）；'
        + '所在并行块必须有汇聚网关，否则运行期无法安全收口',
      visibleIf: (t) => is(t, 'kind', 'CONDITION'),
    },
    {
      key: 'anchor.source',
      label: '起点锚点',
      control: 'select',
      group: '画布',
      optionsFromDsl: (ctx) => anchorOptions(ctx, 'source'),
      help: '这条线从起点节点的哪个落点出发。默认「自动」按路由分配（通过→主锚点、驳回→下一个）；'
        + '也可以直接在画布上拖这条线的端点 —— 拖到哪个锚点就是哪个，分支名/条件等配置都会保留',
    },
    {
      key: 'anchor.target',
      label: '终点锚点',
      control: 'select',
      group: '画布',
      optionsFromDsl: (ctx) => anchorOptions(ctx, 'target'),
      help: '这条线落到终点节点的哪个锚点。默认「自动」按朝向选；手动选过之后就不会被自动分配改动',
    },
  ]
}

/**
 * 连线某一端的锚点可选项：`自动` + 该端节点上<b>真实存在</b>的锚点。
 *
 * <p>锚点清单由画布层同一份推导给出（`anchorsOf`）—— 面板里能选到的，
 * 一定是画布上真实存在的那个点；两处各算一遍必然分叉，用户会选中一个"选了没反应"的锚点。
 *
 * <p>值为空字符串即删掉该字段（`assignByPath` 的约定）：回到自动分配。
 */
function anchorOptions(ctx: SchemaContext, end: 'source' | 'target'): OptionItem[] {
  const auto = { value: '', label: '自动（按路由分配）' }
  const nodeId = get(ctx.target, end) as string | undefined
  const node = ctx.dsl.nodes.find((n) => n.id === nodeId)
  if (!node) {
    return [auto]
  }
  return [auto, ...anchorsOf(node.type, defaultNodeSize(node.type)).map((a) => ({ value: a.id, label: a.label }))]
}

/** 连线类型可选项（并行网关出边不允许条件，由校验拦截） */
export function edgeKindOptions(): OptionItem[] {
  return EDGE_KIND_OPTIONS
}

// ==================== 模板（流程级）字段 ====================

export function metaFields(): FieldSpec[] {
  return [
    { key: 'key', label: '流程 key', control: 'text', required: true, help: '部署到流程引擎的唯一标识，稳定且不可随意更改' },
    { key: 'name', label: '名称', control: 'text', required: true },
    { key: 'displayName', label: '显示名', control: 'text' },
    // 分类（分组）刻意不在这里：分组是清单页的导航骨架（先建分组 → 选分组 → 组内设计），
    // 由清单页左侧统一维护与移动。放在这里会变成"设计器改了、保存却不生效"的第二处真相
    // ——后端保存时只写 dslJson，不改 category 列（详见 ProcessCategory 的说明）。
    //
    // 模板级的「主业务对象」同样不在这里：一个流程可能关联多个业务实体，单一 code 表达不了，
    // 该关联由 ProcessEntitySet（流程关联的业务实体集合）承担。节点级的业务对象绑定
    // （binding.objectType / condition.objectType）不受影响，仍在各自的节点字段里。
    { key: 'description', label: '说明', control: 'textarea' },
    ...notificationFields(),
  ]
}

/**
 * 模板级「通知方式」字段 —— <b>只声明本流程允许用哪些通道</b>。
 *
 * <p>渠道凭据（邮件服务器 / OA 的 RESTful API / 飞书·钉钉·企业微信的应用凭据）属于
 * <b>企业集成</b>：一个部署只有一套，已统一收归服务端配置（application.yml 的
 * {@code plm.notification}）。因此这里<b>不再有</b> SMTP 地址、应用密钥这类字段 ——
 * 早先把它们放在模板里，导致"改一次密码要改所有流程"，凭据还随模板 JSON 复制/导出到处跑。
 *
 * <p>「CK-PLM（站内）」不需要任何凭据；其余渠道是否真的可用，以服务端配置为准
 * （{@code GET /api/notifications/channels}）。
 */
export function notificationFields(): FieldSpec[] {
  return [
    {
      // 不再设 group：面板会先渲染组标题再渲染字段标签，组名与字段名同为"通知方式"时
      // 屏幕上就是同一个词连着出现两遍（看着像重复配置）
      key: 'notifications.channel',
      label: '通知方式',
      // 单选：一个流程一个通知出口（详见 dsl-core/schema.ts 的 NotificationConfig 说明）
      control: 'select',
      options: optionsOf(NOTIFY_CHANNEL_LABEL),
      placeholder: '选择本流程使用的通知方式',
      help: '本流程使用哪一种通知方式；渠道凭据由系统管理员在服务端统一配置'
        + '（application.yml 的 plm.notification），这里只做选择。留空表示跟随系统启用的渠道',
    },
  ]
}

/** 即时通讯渠道的配置字段（三家形态相似、只是叫法不同） */
/** 流程变量字段（模板级列表） */
export function variableFields(): FieldSpec[] {
  return [
    { key: 'name', label: '变量名', control: 'text', required: true },
    { key: 'label', label: '显示名', control: 'text' },
    {
      key: 'type',
      label: '类型',
      control: 'select',
      required: true,
      options: optionsOf(ValueType),
    },
    { key: 'defaultValue', label: '默认值', control: 'text' },
    { key: 'visible', label: '任务页可见', control: 'switch' },
    { key: 'readonly', label: '只读', control: 'switch' },
    { key: 'writable', label: '允许任务中重写', control: 'switch' },
  ]
}

/** 供测试断言：字段声明中使用到的路径是否都落在 DSL 结构内（由测试提供实际结构比对） */
export function declaredPaths(fields: FieldSpec[]): string[] {
  return fields.map((f) => f.key)
}

/** 类型守卫：确认 FieldSpec 集合无重复 key */
export function duplicateKeys(fields: FieldSpec[]): string[] {
  const seen = new Set<string>()
  const duplicated: string[] = []
  for (const f of fields) {
    if (seen.has(f.key)) {
      duplicated.push(f.key)
    }
    seen.add(f.key)
  }
  return duplicated
}

/** 供面板使用：判断当前上下文是否有可配字段 */
export function hasFields(kind: SchemaContext['kind'], fields: FieldSpec[]): boolean {
  return kind === 'meta' || fields.length > 0
}
