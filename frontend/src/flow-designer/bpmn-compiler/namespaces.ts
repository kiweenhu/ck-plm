/**
 * BPMN 命名空间与 CK-PLM 扩展属性编码约定（编译层的唯一映射依据）
 *
 * <h3>为什么需要自定义命名空间</h3>
 * 业务语义里有三组信息 BPMN/Flowable 没有原生对应物（spec §7 冻结项）：
 * 会签聚合规则、驳回目标、逾期后果。做法是<b>双编码</b>：
 * <ul>
 *   <li><b>标准属性</b>（{@code flowable:assignee} / {@code candidateGroups} /
 *       {@code multiInstanceLoopCharacteristics} / {@code completionCondition}）——
 *       让引擎今天就能执行；</li>
 *   <li><b>ckplm 扩展属性</b>（JSON）—— 承载完整业务意图（策略、兜底、弃权处理…），
 *       供导入无损回读，以及未来运行期解析器（占位符 {@code ${ckplmXxx}} 的填充者）使用。</li>
 * </ul>
 * 两套编码由本文件集中定义，避免散落在编译/解析两处各写一遍而漂移。
 */

/** Flowable 扩展命名空间（引擎识别的标准前缀） */
export const FLOWABLE_NS = 'http://flowable.org/bpmn'
export const FLOWABLE_PREFIX = 'flowable'

/**
 * CK-PLM 业务语义扩展命名空间。
 *
 * <p>放在 {@code extensionElements} 内，Flowable 部署时会忽略未知扩展元素
 * —— 这是 BPMN 规范允许的扩展方式，不会导致部署失败。
 */
export const CKPLM_NS = 'http://www.ck.com/plm/workflow/dsl'
export const CKPLM_PREFIX = 'ckplm'

/** BPMN 2.0 标准命名空间 */
export const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL'
export const BPMNDI_NS = 'http://www.omg.org/spec/BPMN/20100524/DI'
export const OMGDC_NS = 'http://www.omg.org/spec/DD/20100524/DC'
export const OMGDI_NS = 'http://www.omg.org/spec/DD/20100524/DI'
export const DEFAULT_TARGET_NAMESPACE = 'http://www.ck.com/plm/workflow'

// ==================== 运行期约定的流程变量名 ====================

/**
 * 运行期解析器必须填充的流程变量（编译层只负责声明，不负责求值）。
 *
 * <p>这是编译层与运行期的**契约**：任务创建前，由运行期支持组件
 * （对应现有 {@code ProcessIdentitySupport} 的角色）把下列变量写入流程作用域，
 * 编译产物中的 {@code ${...}} 才能解析成功。
 */
export const RUNTIME_VARIABLES = {
  /** 发起人（Flowable 内置 initiator 变量） */
  INITIATOR: 'initiator',
  /** 会签/并行的人员集合（List\<String\>） */
  APPROVERS: 'ckplmApprovers',
  /**
   * 「设置审批人」活动指定的按活动人员变量<b>前缀</b>（实际变量为
   * `ckplmSetupAssignees_<活动id>`）。
   *
   * <p>一个活动一个变量：多活动共用集合会互相覆盖，也无法按活动校验人数下限。
   */
  SETUP_ASSIGNEES: 'ckplmSetupAssignees_',
  /** 多实例的单个人员变量名 */
  APPROVER: 'ckplmApprover',
  /**
   * 部门主管 —— <b>仅用于导入旧产物</b>（历史版本曾把策略编译成 `assignee="${ckplmDeptLeader}"`）。
   *
   * <p>现在不再编译出这个变量：`DEPT_LEADER` 由 `ckplm:assignee` 的 strategy + `deptFieldKey`
   * 声明，运行期在任务创建那一步按「业务对象上的部门字段 → 组织 → 部门主管」解析并直接指派
   * （见 `ProcessServiceImpl#resolveDeclaredAssignee`）。沿用声明式而不是回到变量，
   * 是因为变量式只能"发起时快照"。
   */
  DEPT_LEADER: 'ckplmDeptLeader',
  /**
   * 发起人上级 —— <b>仅用于导入旧产物</b>。
   *
   * <p>现在不再编译出这个变量：`INITIATOR_LEADER` 由 `ckplm:assignee` 的 strategy 声明，
   * 运行期在任务创建那一步按「发起人 → 所属组织 → 部门主管」解析并直接指派
   * （见 `ProcessServiceImpl#resolveDeclaredAssignee`）。
   */
  INITIATOR_LEADER: 'ckplmInitiatorLeader',
} as const

/**
 * 会签「本轮已不可能通过」标记（<b>多实例根执行</b>的局部变量，运行期每次投票后写入）。
 *
 * <p>编译层把它拼进 `completionCondition`（`… || ckplmVetoed`）：一旦为 true，多实例立刻结束，
 * 剩余评审人的任务随之取消 —— 一票否决（或剩余票数已凑不够）时，不该让人再白办一遍。
 *
 * <p>写入方：后端 `ProcessServiceImpl#tallyCountersign`，<b>每次投票都写</b>（含 false）。
 * 写成局部变量而非流程变量：会签可能被走多轮（驳回退回后重新提交），挂在根执行上，
 * 本轮活动一结束就随之消失，天然按轮清零。
 */
export const RUNTIME_VETO_VARIABLE = 'ckplmVetoed'

/** 业务对象绑定在运行期的变量名（承载主业务对象的 oid 与类型） */
export const RUNTIME_BUSINESS_KEY = 'businessKey'

/**
 * 调用 PLM 内建服务的委托 Bean 名（SERVICE 节点的统一入口）。
 *
 * <p>早期写法把服务 id 拼进表达式（{@code ${plmService:object.promote}}）是<b>非法 UEL</b>
 * —— 冒号在 UEL 里是函数命名空间语法，Flowable 会直接拒绝部署：
 * {@code syntax error at position 12, encountered ':', expected '}'}。
 * 正确做法是「一个委托 Bean + flowable:field 传参」（见 {@link ./compile} 的 emitService）。
 */
export const SERVICE_DELEGATE = 'plmServiceDelegate'

/** 服务 id 作为委托字段传入时的字段名 */
export const SERVICE_ID_FIELD = 'serviceId'

/** 通知发送的委托表达式（NOTIFY 节点统一走这一个委托） */
export const NOTIFY_DELEGATE = 'plmNotify'

// ==================== ckplm 扩展属性名 ====================

/**
 * 扩展属性名清单（冻结）。
 *
 * <p>命名规则：{@code ckplm:<节点类型小写><语义>}，值为 JSON 字符串（复杂结构）
 * 或字面量（标量）。导入时按同名属性反序列化，保证 DSL 无损往返。
 */
export const CKPLM_ATTR = {
  // 通用
  NODE_TYPE: 'nodeType',
  /**
   * 连线条件的<b>结构化副本</b>（写在 sequenceFlow 上，JSON）。
   *
   * <p>BPMN 只有 {@code <conditionExpression>} 一段文本，字段选择器（objectType/fieldKey/
   * operator/value）编译成 UEL 后就不可逆 —— 导入时只能退化成「表达式模式」，
   * 用户再打开面板看到的是一段他不认识的表达式。多写这份 JSON，往返才能保住结构。
   *
   * <p>只在前端解析（读 XML 文本）时使用，运行期不看它；路由边（{@code ckplm:route}）
   * 不写 —— 那种边的条件由编译层按路由生成，本来就没有用户填的结构。
   */
  CONDITION: 'condition',
  /**
   * 流程变量定义（写在 {@code <process>} 上，JSON 数组）。
   *
   * <p>BPMN 标准里没有"流程变量声明"的位置，变量只在运行期以数据形式存在。
   * 不写这份声明，导入 BPMN 就等于把「任务页要显示哪些变量、默认值是什么」全丢了。
   */
  VARIABLES: 'variables',
  // START
  INITIATOR: 'initiator',
  FORM_REF: 'formRef',
  AUTO_START: 'autoStart',
  BINDING: 'binding',
  // END
  END_CALLBACKS: 'endCallbacks',
  // APPROVAL / TASK
  ASSIGNEE: 'assignee',
  APPROVAL_MODE: 'approvalMode',
  PASS_RULE: 'passRule',
  COMMENT_REQUIRED: 'commentRequired',
  DEADLINE: 'deadline',
  REJECT: 'reject',
  /** 连线承载的路由（通过/驳回）—— 见 dsl-core 的 EdgeRoute */
  ROUTE: 'route',
  /**
   * 「终止同层分支」的连线标记（写在 sequenceFlow 上）。
   *
   * <p>只是给人看与前端往返用的<b>可读副本</b>；运行期判定读的是
   * {@link CKPLM_ATTR.TERMINATE_ON} —— 自定义命名空间属性在 sequenceFlow 上实测可能取不到
   * （见后端 {@code RejectRouter#hasRejectRoute} 的记录），把判据放在节点上才稳。
   */
  TERMINATE_SIBLINGS: 'terminateSiblings',
  /**
   * 「本节点哪些出边带终止语义」的汇总（写在<b>源节点</b>上，逗号分隔的连线 id）。
   *
   * <p>为什么要在节点上再写一份：引擎解析 BPMN 时对 sequenceFlow 上的自定义属性
   * 是否保留与解析器实现有关（{@code ckplm:route} 就遇到过"XML 里明明写着、运行期读不到"）。
   * 节点属性（{@code ckplm:assignee} / {@code ckplm:passRule}）已验证可读，
   * 所以运行期按「任务节点 → terminateOn → 再取这条边的 targetRef」判定，不依赖连线属性。
   */
  TERMINATE_ON: 'terminateOn',
  /** 办理节点是否需要交付件（2026-10-13：原先手填文档 code 的「交付物」换成了这个开关） */
  NEED_DELIVERABLES: 'needDeliverables',
  /** 交付件绑定的业务对象类型（code 列表，可多选） */
  DELIVERABLE_OBJECT_TYPES: 'deliverableObjectTypes',
  // SERVICE
  SERVICE_REF: 'serviceRef',
  SERVICE_PARAMS: 'serviceParams',
  // NOTIFY
  TEMPLATE_CODE: 'templateCode',
  RECIPIENTS: 'recipients',
  ATTACH_PRIMARY_OBJECT: 'attachPrimaryObject',
  // SUB_PROCESS
  VARIABLE_MAP: 'variableMap',
  /**
   * 子流程多实例声明（JSON，{@link SubProcessMultiInstance}）。
   *
   * <p>标准 BPMN 的 `multiInstanceLoopCharacteristics` 只保留 collection 与元素变量名，
   * 丢了"业务 key 用元素上的哪个字段"这类意图 —— 导入后无法还原成可编辑的配置。
   * 这份 JSON 是往返依据，运行期不看。
   */
  SUB_PROCESS_MULTI_INSTANCE: 'multiInstance',
  // TIMER
  TIMER_MODE: 'timerMode',
  // 网关
  ADVANCED: 'advanced',
} as const

/** ckplm 扩展属性在 XML 中的完整限定名（如 {@code ckplm:assignee}） */
export function ckplmAttr(name: string): string {
  return `${CKPLM_PREFIX}:${name}`
}

/** flowable 扩展属性在 XML 中的完整限定名（如 {@code flowable:assignee}） */
export function flowableAttr(name: string): string {
  return `${FLOWABLE_PREFIX}:${name}`
}
