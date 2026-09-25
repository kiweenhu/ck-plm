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
  ValueType,
  defaultFormCodeOfNode,
  defaultNodeSize,
  fixedFormCodeOfNode,
  findFormTemplate,
  formTemplatesFor,
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
      options: optionsOf(ASSIGNEE_STRATEGY_LABEL),
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
 * 路由配置。
 *
 * <p>审批节点天然有「通过 / 驳回」两条路由，因此这里不是「要不要配」，
 * 而是「这两条路由各自通向哪里」：
 * <ul>
 *   <li>通过 —— 是画布上连出的出边，故此处只做说明；</li>
 *   <li>驳回 —— 目标的三种语义（发起人 / 上一步 / 指定节点）在此配置，
 *       且默认要求填写驳回意见。</li>
 * </ul>
 *
 * <p><b>驳回不走画布上的连线</b>：它由运行期按这里的目标直接退回（见后端 reject/RejectRouter）——
 * 「上一步」要按实际走过的历史判定，「发起人」没有对应的流程节点，
 * 两者都画不成回退线。所以下面这三个字段是驳回的<b>唯一</b>依据，别指望画布上再连一条。
 */
const rejectFields = (): FieldSpec[] => [
  {
    key: 'hint-route',
    label: '路由',
    control: 'hint',
    group: '路由',
    help: '本节点有两条路由：「通过」＝ 画布上从节点下方圆点连出的出边；'
      + '「驳回」有两种做法 —— ① 下方「允许驳回 + 驳回目标」：一步跳回某个位置；'
      + '② 从节点再连一条出边、在它的属性里把「路由」设为「驳回」：'
      + '驳回后可以先走自动化节点（设置状态、撤回电子签名…）再回来。两者二选一。',
  },
  {
    key: 'reject',
    label: '允许驳回',
    control: 'object-toggle',
    group: '路由',
    // **不预填 target**：此前这里默认写死 PREVIOUS，于是"勾了允许驳回、其实什么都没选"的节点
    // 看起来是配好的（目标＝上一步），校验也永远不会报 —— 运行期才把经办人挡住。
    // 不预填之后，这个状态就是可见的「驳回目标」为空，校验据此拦保存（REJECT_NO_TARGET）。
    objectTemplate: () => ({ enabled: true, commentRequired: true }),
    help: '开启后必须二选一：在下方选「驳回目标」，或从节点连一条路由为「驳回」的出边 —— '
      + '否则驳回之后无处可去（保存时会被校验拦下）。默认还要求驳回时填写意见。关掉后办理页不再出现「驳回」',
  },
  {
    key: 'reject.target',
    label: '驳回目标',
    control: 'select',
    options: optionsOf(REJECT_TARGET_LABEL),
    group: '路由',
    // 已经画了「驳回」出边就不再问"退回哪"：那是同一条路的另一种走法（走图），
    // 两者并存会让"驳回到底听谁的"说不清 —— 见 validate 的 EDGE_ROUTE_CONFLICT
    visibleIf: (t, ctx) => has(t, 'reject') && !hasRejectRouteFrom(ctx, t),
    // 隐藏即清空：从"配目标"改成"画驳回边"时，不留下一个看不见却仍然生效的目标
    clearOnHide: true,
    help: '退回到哪里：「上一步」= 实际走过的上一个办理节点（不是画布上的前驱）；'
      + '「发起人」= 由发起人办理的那个节点（流程里没有这种节点时会报错）；「指定节点」= 指定一个审批/办理活动。'
      + '若驳回后还要先做自动化动作（设置状态、撤回电子签名…），请改用「驳回」出边（见上方「路由」）',
  },
  {
    key: 'reject.targetNodeId',
    label: '目标节点',
    control: 'node-ref',
    group: '路由',
    help: '「指定节点」时需要给出回退到哪个节点（只能是审批/办理活动，且不能是本节点）',
    visibleIf: (t, ctx) => has(t, 'reject') && is(t, 'reject.target', 'NODE') && !hasRejectRouteFrom(ctx, t),
    clearOnHide: true,
  },
  {
    key: 'reject.commentRequired',
    label: '驳回时意见必填',
    control: 'switch',
    group: '路由',
    help: '默认开启 —— 驳回必须说明理由',
    visibleIf: (t) => has(t, 'reject'),
  },
]

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
 * <p>还有一类"按节点才固定"的情形：审批节点选了「会签」模式时，它的表单必须是会签表单
 * （要显示通过规则与别人的办理情况）—— 此时下拉里只留那一项，免得用户选了却被编译层盖掉。
 */
const formAndCommentFields = (options: { fixed?: boolean } = {}): FieldSpec[] => [
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
      return taskFormOptionsOf(node.type)
    },
    disabled: options.fixed === true,
    // DSL 里还没写过值时显示"实际会用的那张"：老模板的节点可能没写过 formRef，
    // 否则会呈现一个空框（用户不知道编译出来到底是什么表单）
    defaultWhenEmpty: (ctx) =>
      defaultFormCodeOfNode(ctx.target as { type?: NodeTypeT; approvalMode?: string }),
    help: options.fixed
      ? '本活动使用内置固定表单，不可修改（编译时按活动类型写入 formKey）'
      : '留空＝按活动类型用内置默认表单（审批 → 审批意见）；要换一张，先在「业务配置 → 流程表单」里建好表单，再在这里选中它',
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

/** 表单模板下拉项（按节点类型过滤；内置模板在名字上标出） */
function taskFormOptionsOf(nodeType?: NodeTypeT): OptionItem[] {
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
    ...formAndCommentFields(),
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
  // 设置审批人：由发起人（owner）执行，固定是流程启动后的第一个节点。
  // 「要设置哪些活动」刻意不在这里维护 —— 下游所有审批 / 会签 / 办理活动
  // 都会自动进入它的任务表单（见 app/task/assignee-setup.ts）
  [NodeType.SET_ASSIGNEE]: [
    {
      key: 'hint-setup',
      label: '设置审批人',
      control: 'hint',
      group: '基本',
      help: '由流程发起人（owner）执行，固定是流程启动后的第一个节点：一次性指定后面所有人工活动的人员。任务表单会自动列出下游所有审批 / 会签 / 办理活动，无需在此维护清单。',
    },
    ...assigneeFields('assignee', '执行人'),
    {
      key: 'setup-preview',
      label: '任务表单',
      control: 'assignee-setup-preview',
      group: '任务表单',
      help: '产品内置的固定表单：列出下游所有审批 / 会签 / 办理活动，每项在该活动指定的角色成员中挑人（会签至少两人）。',
    },
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
    // 会签有专属的内置表单（显示通过规则 + 每个人的办理情况），同样预填并置为只读
    ...formAndCommentFields({ fixed: true }),
  ],
  [NodeType.APPROVAL]: [
    ...assigneeFields('assignee'),
    {
      key: 'approvalMode',
      label: '审批模式',
      control: 'select',
      options: optionsOf(APPROVAL_MODE_LABEL),
      required: true,
      help: '会签需额外配置通过规则',
    },
    {
      key: 'passRule.mode',
      label: '通过规则',
      control: 'select',
      options: optionsOf(PASS_RULE_MODE_LABEL),
      group: '会签规则',
      required: true,
      visibleIf: (t) => is(t, 'approvalMode', 'COUNTERSIGN'),
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
      visibleIf: (t) => is(t, 'approvalMode', 'COUNTERSIGN') && is(t, 'passRule.mode', 'PERCENT'),
      clearOnHide: true,
    },
    {
      key: 'passRule.count',
      label: '必须票数',
      control: 'number',
      min: 1,
      suffix: '票',
      group: '会签规则',
      visibleIf: (t) => is(t, 'approvalMode', 'COUNTERSIGN') && is(t, 'passRule.mode', 'COUNT'),
      clearOnHide: true,
    },
    {
      key: 'passRule.abstain',
      label: '弃权处理',
      control: 'select',
      options: optionsOf(ABSTAIN_POLICY_LABEL),
      group: '会签规则',
      visibleIf: (t) => is(t, 'approvalMode', 'COUNTERSIGN') && !is(t, 'passRule.mode', 'VETO'),
      clearOnHide: true,
    },
    { key: 'commentRequired', label: '必填审批意见', control: 'switch' },
    ...deadlineFields(),
    ...rejectFields(),
    // 审批表单：内置「审批意见」（同意/驳回 + 驳回位置 + 意见必填）是**默认**，
    // 不是固定 —— 企业可以在「业务配置 → 流程表单」里加自己的审批表单，再在这里换。
    // 只有「审批 + 会签模式」例外：那张会签表单是语义固定（下拉里会自动收窄到它）。
    ...formAndCommentFields(),
    ...bindingFields(),
  ],
  [NodeType.TASK]: [
    ...assigneeFields('assignee', '办理人'),
    {
      key: 'deliverables',
      label: '交付物',
      control: 'tags',
      group: '数据',
      placeholder: '输入文档类型 code 后回车',
    },
    ...deadlineFields(),
    ...formAndCommentFields(),
    ...bindingFields(),
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
      help: '触发后终止其余未完成的前驱分支',
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
