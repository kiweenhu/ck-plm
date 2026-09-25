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
  /** 部门主管（DEPT_LEADER 策略解析结果） */
  DEPT_LEADER: 'ckplmDeptLeader',
  /** 发起人上级（INITIATOR_LEADER 策略解析结果） */
  INITIATOR_LEADER: 'ckplmInitiatorLeader',
} as const

/** 会签「一票否决」在运行期使用的变量名（任务监听器读到 Reject 时置为 true） */
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
  DELIVERABLES: 'deliverables',
  // SERVICE
  SERVICE_REF: 'serviceRef',
  SERVICE_PARAMS: 'serviceParams',
  // NOTIFY
  TEMPLATE_CODE: 'templateCode',
  RECIPIENTS: 'recipients',
  ATTACH_PRIMARY_OBJECT: 'attachPrimaryObject',
  // SUB_PROCESS
  VARIABLE_MAP: 'variableMap',
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
