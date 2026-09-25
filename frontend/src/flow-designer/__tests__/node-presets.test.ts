/**
 * 节点库目录 + 「会签审批」作为独立业务类型的跨层测试。
 *
 * <p><b>核心命题</b>：节点类型是<b>业务词汇</b>。会签是「多人的集体决策活动」，
 * 与单人审批是两种活动，因此它是独立的 {@link NodeType}，而不是「审批节点的一个模式」。
 *
 * <p>而技术侧仍是同一条产线：编译产物与后端契约完全不变
 * （userTask + multiInstanceLoopCharacteristics + `ckplm:approvalMode=COUNTERSIGN`）。
 * 本文件同时钉住这两件事 —— 业务身份独立、技术契约不变。
 */

import { describe, expect, it } from 'vitest'
import {
  ALL_NODE_TYPES,
  ApprovalMode,
  CHECKIN_PRESET_ID,
  CHECKOUT_PRESET_ID,
  FUNCTION_CALL_NODE_NAME,
  FUNCTION_CALL_PRESET_ID,
  IssueCode,
  IssueLevel,
  NODE_PRESETS,
  NODE_TYPE_LABEL,
  NodeType,
  PROMOTE_PRESET_ID,
  PUBLISH_NODE_NAME,
  PUBLISH_PRESET_ID,
  REST_CALL_NODE_NAME,
  REST_CALL_PRESET_ID,
  SERVICE_CHECKIN,
  SERVICE_CHECKOUT,
  SERVICE_PROMOTE,
  SERVICE_PUBLISH_TO_SYSTEM,
  SERVICE_REST_CALL,
  SERVICE_SET_ATTRIBUTE,
  SERVICE_SET_STATE,
  SET_ATTRIBUTE_PRESET_ID,
  SET_STATE_NODE_NAME,
  SET_STATE_PRESET_ID,
  createEmptyDsl,
  parseFlowDsl,
  presetById,
  presetByType,
  presetGroups,
  presetId,
  presetLabel,
  serviceById,
  serviceIdsHiddenFromServiceList,
  serviceParamsAllFromList,
  validateDsl,
  type CountersignNode,
  type FlowDsl,
  type ServiceNode,
} from '@flow-dsl-core'
import { compileToBpmnXml, parseBpmnXml } from '@flow-compiler'
import { NODE_STYLE, useFlowDesigner } from '@flow-canvas'

const blank = (): FlowDsl => createEmptyDsl({ key: 'countersign_flow', name: '会签测试' })

/** 会签参与人默认是空角色列表（故意"未配好"），测试里补成有效配置 */
const ROLES = { strategy: 'ROLE', roleCodes: ['ENG'] } as never

function designerWithCountersign() {
  const designer = useFlowDesigner(blank())
  const node = designer.addNodeAt(NodeType.COUNTERSIGN_APPROVAL, { x: 240, y: 160 }) as CountersignNode
  return { designer, node }
}

describe('节点库目录', () => {
  it('每个节点类型都有目录条目与外观规格（将来加类型忘补会被测出来）', () => {
    const covered = new Set(NODE_PRESETS.map((p) => p.type))
    const missingEntry = ALL_NODE_TYPES.filter((t) => !covered.has(t))
    const missingStyle = ALL_NODE_TYPES.filter((t) => !NODE_STYLE[t])
    expect(missingEntry, `缺少节点库条目: ${missingEntry.join(', ')}`).toEqual([])
    expect(missingStyle, `缺少外观规格: ${missingStyle.join(', ')}`).toEqual([])
  })

  it('「人工」分组：设置审批人 → 审批节点 → 会签审批 → 办理节点', () => {
    const human = presetGroups().find((g) => g.group === '人工')
    expect(human).toBeDefined()
    expect(human!.items.map(presetLabel)).toEqual([
      '设置审批人',
      '审批节点',
      '会签审批',
      '办理节点',
    ])
    expect(human!.items.map((p) => p.type)).toEqual([
      NodeType.SET_ASSIGNEE,
      NodeType.APPROVAL,
      NodeType.COUNTERSIGN_APPROVAL,
      NodeType.TASK,
    ])
  })

  it('显示名回落类型的业务名，不重复维护', () => {
    expect(presetLabel(presetByType(NodeType.APPROVAL)!)).toBe(NODE_TYPE_LABEL[NodeType.APPROVAL])
  })
})

/**
 * 「设置状态」是同一个 SERVICE 类型下的<b>业务入口</b>：拖出来就已选好服务。
 *
 * <p>它不是新节点类型 —— 编译产物仍是 serviceTask + serviceId，反解析也读得回，
 * 所以这里同时钉住「入口好用」与「技术契约没变」。
 */
describe('节点库「设置状态」入口', () => {
  const autoGroup = () => presetGroups().find((g) => g.group === '自动化')!

  it('「自动化」分组：函数调用 → 检出/检入/修订/更新属性 → 设置状态 → 系统集成 → REST 接口调用 → 通知 → 子流程', () => {
    expect(autoGroup().items.map(presetLabel)).toEqual([
      '函数调用',
      '检出对象',
      '检入对象',
      '修订对象',
      '更新对象属性',
      '设置状态',
      '系统集成',
      'REST 接口调用',
      '通知',
      '子流程',
    ])
    // 条目 id 区分同类型入口：通用入口（函数调用）的 id 是固定的 service.function，
    // 其余都是 service.<serviceId>，与预置的服务一一对应
    expect(autoGroup().items.map((p) => presetId(p))).toEqual([
      FUNCTION_CALL_PRESET_ID,
      CHECKOUT_PRESET_ID,
      CHECKIN_PRESET_ID,
      PROMOTE_PRESET_ID,
      SET_ATTRIBUTE_PRESET_ID,
      SET_STATE_PRESET_ID,
      PUBLISH_PRESET_ID,
      REST_CALL_PRESET_ID,
      NodeType.NOTIFY,
      NodeType.SUB_PROCESS,
    ])
  })

  it('对象动作四件套：拖出来即选好服务，且不再在「服务」下拉里重复出现', () => {
    const cases = [
      { id: CHECKOUT_PRESET_ID, service: SERVICE_CHECKOUT, label: '检出对象' },
      { id: CHECKIN_PRESET_ID, service: SERVICE_CHECKIN, label: '检入对象' },
      { id: PROMOTE_PRESET_ID, service: SERVICE_PROMOTE, label: '修订对象' },
      { id: SET_ATTRIBUTE_PRESET_ID, service: SERVICE_SET_ATTRIBUTE, label: '更新对象属性' },
    ]
    const hidden = serviceIdsHiddenFromServiceList()

    for (const item of cases) {
      expect(presetLabel(presetById(item.id)!)).toBe(item.label)

      // 拖出来就已选好服务（少两步点击）；节点名与标签一致 —— 这类动作的短名就是它自己
      const node = useFlowDesigner(blank()).addNodeAt(item.id, { x: 0, y: 0 }) as ServiceNode
      expect(node.serviceRef).toBe(item.service)
      expect(node.name).toBe(item.label)

      // 同一个动作不再在下拉里重复出现（否则设计者得先判断"两处是不是不一样"）
      expect(hidden.has(item.service)).toBe(true)
    }

    // 通用入口与"可配置服务"不能被过滤掉：它们本来就靠下拉选服务
    expect(hidden.has(SERVICE_REST_CALL)).toBe(false)
    expect(hidden.has(SERVICE_SET_STATE)).toBe(false)
  })

  it('「REST 接口调用」：系统来自注册表，路径/方法声明成控件，业务参数走参数集', () => {
    const preset = presetById(REST_CALL_PRESET_ID)!
    expect(preset.type).toBe(NodeType.SERVICE)
    expect(presetLabel(preset)).toBe('REST 接口调用')

    const node = useFlowDesigner(blank()).addNodeAt(REST_CALL_PRESET_ID, {
      x: 200,
      y: 240,
    }) as ServiceNode
    expect(node.serviceRef).toBe(SERVICE_REST_CALL)
    expect(node.name).toBe(REST_CALL_NODE_NAME)

    const service = serviceById(SERVICE_REST_CALL)!
    expect(service.params.map((p) => p.name)).toEqual(['systemCode', 'path', 'method'])
    // 目标系统必须是下拉（值 = 注册表里的 code），不能让人手输
    expect(service.params.find((p) => p.name === 'systemCode')?.optionsSource).toBe('TARGET_SYSTEMS')
    // 关键：声明之外还接受业务参数 —— 否则参数集编辑器会被判成"全部有下拉"而隐藏，
    // 业务参数就无处可填（这正是这个节点存在的意义）
    expect(service.acceptsExtraParams).toBe(true)
    expect(serviceParamsAllFromList(SERVICE_REST_CALL)).toBe(false)
  })

  it('「函数调用」：不预置服务（调哪个后端函数由设计者选），参数集走通用键值编辑器', () => {
    const preset = presetById(FUNCTION_CALL_PRESET_ID)!
    expect(preset.type).toBe(NodeType.SERVICE)
    expect(presetLabel(preset)).toBe('函数调用')

    const node = useFlowDesigner(blank()).addNodeAt(FUNCTION_CALL_PRESET_ID, {
      x: 200,
      y: 240,
    }) as ServiceNode

    // 仍是 SERVICE 节点（编译产物 serviceTask + serviceId 不变），只是"选哪个服务"留给设计者。
    // 关键：不预置 serviceRef —— 预置了就等于替他选了要执行哪段代码。
    expect(node.type).toBe(NodeType.SERVICE)
    expect(node.name).toBe(FUNCTION_CALL_NODE_NAME)
    expect(node.serviceRef).toBeFalsy()

    // 参数集走通用键值编辑器：判据是"服务有没有把参数全部声明成下拉/专用控件"——
    // 未选服务、或所选服务只声明了部分参数时，键值编辑器都会出现（取值可写 ${流程变量}），
    // 这是"参数集"唯一的配置入口，判成 true 就等于参数无处可填
    expect(serviceParamsAllFromList(node.serviceRef)).toBe(false)
  })

  it('条目标签与服务描述符同源（改一处不会漂）', () => {
    expect(presetLabel(presetById(SET_STATE_PRESET_ID)!)).toBe(serviceById(SERVICE_SET_STATE)!.label)
  })

  it('「系统集成」：预置服务与节点名，且目标系统/认证/接口都能在面板上配', () => {
    const designer = useFlowDesigner(blank())
    const node = designer.addNodeAt(PUBLISH_PRESET_ID, { x: 200, y: 240 }) as ServiceNode

    expect(node.type).toBe(NodeType.SERVICE)
    expect(node.name).toBe(PUBLISH_NODE_NAME)
    expect(node.serviceRef).toBe(SERVICE_PUBLISH_TO_SYSTEM)
    expect(presetLabel(presetById(PUBLISH_PRESET_ID)!))
      .toBe(serviceById(SERVICE_PUBLISH_TO_SYSTEM)!.label)

    // 参数齐备：目标系统地址 / 接口路径 / HTTP 方法 / 认证方式 / 凭据
    const service = serviceById(SERVICE_PUBLISH_TO_SYSTEM)!
    expect(service.params.map((p) => p.name))
      .toEqual(['baseUrl', 'apiPath', 'httpMethod', 'authType', 'username', 'secret'])
    // 认证方式与 HTTP 方法是清单型（面板渲染下拉），其余是带标签输入框（password 用于凭据）
    expect(service.params.filter((p) => p.optionsSource).map((p) => p.name))
      .toEqual(['httpMethod', 'authType'])
    expect(service.params.find((p) => p.name === 'secret')?.control).toBe('password')
  })

  it('按类型取条目仍拿到 SERVICE 的入口（程序化创建 / 老载荷不受影响）', () => {
    // 通用「自动服务」条目已并入「函数调用」：按类型取到的是它，
    // 预置只有节点名 —— 老载荷（只带类型）落点后照样是一个"待选服务"的 SERVICE 节点
    expect(presetId(presetByType(NodeType.SERVICE)!)).toBe(FUNCTION_CALL_PRESET_ID)
  })

  it('拖入画布：节点名是「设置对象状态」、服务预置为「设置状态」', () => {
    const designer = useFlowDesigner(blank())
    const node = designer.addNodeAt(SET_STATE_PRESET_ID, { x: 200, y: 120 }) as ServiceNode

    expect(node.type).toBe(NodeType.SERVICE)
    // 节点名比条目标签具体：看画布就知道这个自动服务在改什么
    expect(node.name).toBe(SET_STATE_NODE_NAME)
    expect(node.serviceRef).toBe(SERVICE_SET_STATE)
    // 名字长在节点上，条目本身仍是短标签
    expect(presetLabel(presetById(SET_STATE_PRESET_ID)!)).toBe('设置状态')
  })

  it('预置属性不能改写身份：节点 id / type 仍由画布决定', () => {
    const designer = useFlowDesigner(blank())
    const node = designer.addNodeAt(SET_STATE_PRESET_ID, { x: 0, y: 0 })
    const inDsl = designer.dsl.value.nodes.find((n) => n.id === node.id)
    expect(inDsl, '节点应真的进了 DSL').toBeDefined()
    expect(inDsl!.id).toBe(node.id)
    expect(inDsl!.type).toBe(NodeType.SERVICE)
  })

  it('直接给类型仍然可用（程序化创建 / 老载荷）', () => {
    const designer = useFlowDesigner(blank())
    const node = designer.addNodeAt(NodeType.NOTIFY, { x: 0, y: 0 })
    expect(node.type).toBe(NodeType.NOTIFY)
    expect(node.name).toBe('通知')
  })

  it('编译契约不变：仍是 serviceTask + serviceId（反解析读得回 DSL）', () => {
    const designer = useFlowDesigner(blank())
    const node = designer.addNodeAt(SET_STATE_PRESET_ID, { x: 200, y: 120 }) as ServiceNode
    designer.applyNode(node.id, { ...node, params: { state: 'RELEASED' } })

    const xml = compileToBpmnXml(designer.dsl.value).xml
    expect(xml).toContain('serviceTask')
    expect(xml).toContain(SERVICE_SET_STATE)
    expect(xml).toContain('RELEASED')

    const imported = parseBpmnXml(xml) as unknown as { dsl?: FlowDsl }
    const restored = imported.dsl!.nodes.find((n) => n.id === node.id) as ServiceNode
    expect(restored.type).toBe(NodeType.SERVICE)
    expect(restored.serviceRef).toBe(SERVICE_SET_STATE)
    expect(restored.params).toEqual({ state: 'RELEASED' })
  })
})

describe('会签审批：独立业务类型（不是审批节点的模式）', () => {
  it('已注册为 NodeType，且节点上不再挂 approvalMode', () => {
    expect(ALL_NODE_TYPES).toContain(NodeType.COUNTERSIGN_APPROVAL)
    const { node } = designerWithCountersign()
    expect(node.type).toBe(NodeType.COUNTERSIGN_APPROVAL)
    expect('approvalMode' in node).toBe(false)
  })

  it('默认给保守口径：全员通过 + 弃权不计入（宁严不宽）', () => {
    const { node } = designerWithCountersign()
    expect(node.passRule).toEqual({ mode: 'PERCENT', percent: 100, abstain: 'IGNORE' })
  })

  it('默认参与人「不假装已配好」：空角色列表 → 画布提示未配置', () => {
    const { designer, node } = designerWithCountersign()
    expect(node.assignee).toEqual({ strategy: 'ROLE', roleCodes: [] })

    const report = validateDsl(designer.dsl.value)
    expect(report.nodeState[node.id], '默认态应是「未配置」而非 OK').not.toBe('OK')
    expect((report.byNode[node.id] ?? []).length).toBeGreaterThan(0)
  })

  it('passRule 在结构层就是必需的（zod 拦住）', () => {
    const { designer, node } = designerWithCountersign()
    const withoutRule: FlowDsl = {
      ...designer.dsl.value,
      nodes: designer.dsl.value.nodes.map((n) =>
        n.id === node.id
          ? ({ id: n.id, type: n.type, name: n.name, assignee: ROLES } as never)
          : n,
      ),
    }
    // 会签没有聚合规则就不成立 —— 不能等到校验阶段才发现
    expect(parseFlowDsl(withoutRule).ok).toBe(false)
  })

  it('通过比例低于下限被拦下（复用同一套冻结规则）', () => {
    const { designer, node } = designerWithCountersign()
    designer.applyNode(node.id, {
      ...node,
      assignee: ROLES,
      passRule: { mode: 'PERCENT', percent: 30 },
    } as CountersignNode)

    expect(validateDsl(designer.dsl.value).issues.map((i) => i.code)).toContain(
      IssueCode.PASS_PERCENT_TOO_LOW,
    )
  })

  it('单值参与人策略（发起人）被提醒「会签需要多人」', () => {
    const { designer, node } = designerWithCountersign()
    designer.applyNode(node.id, {
      ...node,
      assignee: { strategy: 'INITIATOR' } as never,
    } as CountersignNode)

    const warn = validateDsl(designer.dsl.value).issues.find(
      (i) => i.code === IssueCode.COUNTERSIGN_NO_APPROVERS,
    )
    expect(warn, '应提醒参与人策略解析出来只有一人').toBeDefined()
    expect(warn?.level).toBe(IssueLevel.WARNING)
  })

  it('驳回路由与审批节点一致（同样支持，且默认要求填写驳回意见）', () => {
    const { node } = designerWithCountersign()
    expect(node.reject?.enabled).toBe(true)
    expect(node.reject?.commentRequired).toBe(true)
  })

  it('删除该节点时，指向它的驳回目标会被清理（不留悬空引用）', () => {
    const { designer, node } = designerWithCountersign()
    // 让会签驳回到指定节点，再删掉那个节点
    designer.applyNode(node.id, {
      ...node,
      assignee: ROLES,
      reject: { enabled: true, target: 'NODE', targetNodeId: 'end_1' },
    } as CountersignNode)
    designer.removeSelection({ nodeIds: ['end_1'], edgeIds: [] })

    const after = designer.dsl.value.nodes.find((n) => n.id === node.id) as CountersignNode
    expect(after.reject?.target).toBe('INITIATOR')
    expect(after.reject?.targetNodeId).toBeUndefined()
  })
})

describe('技术侧：与「审批节点 + 会签模式」共用同一条产线', () => {
  it('编译产物带上契约编码，且如实写出业务类型（往返无损）', () => {
    const { designer, node } = designerWithCountersign()
    designer.applyNode(node.id, { ...node, assignee: ROLES } as CountersignNode)
    const xml = compileToBpmnXml(designer.dsl.value).xml

    expect(xml).toContain('multiInstanceLoopCharacteristics')
    // 后端契约不变：仍然是 approvalMode=COUNTERSIGN
    expect(xml).toContain('COUNTERSIGN')
    // 业务类型如实写出，反解析才能读回独立类型
    expect(xml).toContain(NodeType.COUNTERSIGN_APPROVAL)
  })

  it('两种表达（新类型 / 旧模式）产出等价的引擎语义', () => {
    const { designer, node } = designerWithCountersign()
    const countersign = { ...node, assignee: ROLES } as CountersignNode
    designer.applyNode(node.id, countersign)
    const newXml = compileToBpmnXml(designer.dsl.value).xml

    // 同一语义用旧表达（APPROVAL + approvalMode=COUNTERSIGN）编译作对照
    const legacy: FlowDsl = {
      ...designer.dsl.value,
      nodes: designer.dsl.value.nodes.map((n) =>
        n.id === node.id
          ? ({ ...countersign, type: NodeType.APPROVAL, approvalMode: ApprovalMode.COUNTERSIGN } as never)
          : n,
      ),
    }
    const legacyXml = compileToBpmnXml(legacy).xml

    for (const fragment of ['multiInstanceLoopCharacteristics', 'COUNTERSIGN']) {
      expect(newXml.includes(fragment), `新类型产物缺少 ${fragment}`).toBe(true)
      expect(legacyXml.includes(fragment), `旧表达产物缺少 ${fragment}`).toBe(true)
    }
  })

  it('反解析读回为独立类型（BPMN → DSL 往返）', () => {
    const { designer, node } = designerWithCountersign()
    designer.applyNode(node.id, { ...node, assignee: ROLES } as CountersignNode)
    const xml = compileToBpmnXml(designer.dsl.value).xml

    const imported = parseBpmnXml(xml) as unknown as { dsl?: FlowDsl }
    expect(imported.dsl, 'ImportResult 应包含 dsl').toBeDefined()

    const restored = imported.dsl!.nodes.find((n) => n.id === node.id)
    expect(restored?.type).toBe(NodeType.COUNTERSIGN_APPROVAL)
    expect((restored as CountersignNode).passRule).toEqual({
      mode: 'PERCENT',
      percent: 100,
      abstain: 'IGNORE',
    })
  })
})
