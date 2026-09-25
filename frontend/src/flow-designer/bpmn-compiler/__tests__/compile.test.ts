import { describe, expect, it } from 'vitest'
import {
  ApprovalMode,
  AssigneeStrategy,
  ConditionMode,
  ConditionOperator,
  DSL_VERSION,
  EdgeKind,
  NodeType,
  PassRuleMode,
  TimerMode,
  type FlowDsl,
} from '@flow-dsl-core'
import { compileToBpmnXml, parseBpmnXml } from '../index'

/** 极简 DSL 构造器（测试夹具与 dsl-core 的 ops 解耦） */
function dsl(partial: Partial<FlowDsl>): FlowDsl {
  return {
    dslVersion: DSL_VERSION,
    meta: { key: 'test_flow', name: '测试流程' },
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    edges: [{ id: 'flow_1', source: 'start_1', target: 'end_1', kind: EdgeKind.NORMAL }],
    layout: {
      nodes: {
        start_1: { x: 240, y: 40, width: 36, height: 36 },
        end_1: { x: 240, y: 200, width: 36, height: 36 },
      },
    },
    ...partial,
  }
}

describe('基本编译', () => {
  it('生成可部署的 BPMN 骨架与图形信息', () => {
    const { xml, warnings } = compileToBpmnXml(dsl({}))
    expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>')
    expect(xml).toContain('<process id="test_flow" name="测试流程" isExecutable="true">')
    expect(xml).toContain('<startEvent id="start_1"')
    expect(xml).toContain('flowable:initiator="initiator"')
    expect(xml).toContain('<endEvent id="end_1"')
    expect(xml).toContain('<sequenceFlow id="flow_1" sourceRef="start_1" targetRef="end_1"/>')
    expect(xml).toContain('<bpmndi:BPMNDiagram')
    expect(xml).toContain('xmlns:ckplm="http://www.ck.com/plm/workflow/dsl"')
    expect(warnings).toEqual([])
  })

  it('可关闭图形信息以减小部署体积', () => {
    const { xml } = compileToBpmnXml(dsl({}), { includeDiagram: false })
    expect(xml).not.toContain('BPMNDiagram')
  })

  it('节点名中的 XML 特殊字符被转义', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'n1', type: NodeType.TASK, name: 'A & B <C> "D"', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        ],
      }),
    )
    expect(xml).toContain('name="A &amp; B &lt;C&gt; &quot;D&quot;"')
  })
})

describe('审批人策略映射', () => {
  it('角色 → candidateGroups（运行期身份来自 ck_role.code）', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'a1', type: NodeType.APPROVAL, name: '评审', assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG_MANAGER', 'QUALITY'] }, approvalMode: ApprovalMode.SINGLE },
        ],
      }),
    )
    expect(xml).toContain('flowable:candidateGroups="ENG_MANAGER,QUALITY"')
    expect(xml).toContain('ckplm:assignee="{&quot;strategy&quot;:&quot;ROLE&quot;')
  })

  it('指定单用户 → assignee；多用户 → candidateUsers', () => {
    const one = compileToBpmnXml(
      dsl({
        nodes: [{ id: 'a1', type: NodeType.TASK, name: 'x', assignee: { strategy: AssigneeStrategy.USER, userOids: ['u1'] } }],
      }),
    ).xml
    expect(one).toContain('flowable:assignee="u1"')
    const many = compileToBpmnXml(
      dsl({
        nodes: [{ id: 'a1', type: NodeType.TASK, name: 'x', assignee: { strategy: AssigneeStrategy.USER, userOids: ['u1', 'u2'] } }],
      }),
    ).xml
    expect(many).toContain('flowable:candidateUsers="u1,u2"')
  })

  it('部门主管 → 运行期变量约定 + 明确告警', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'a1', type: NodeType.APPROVAL, name: '主管审批', assignee: { strategy: AssigneeStrategy.DEPT_LEADER, deptFieldKey: 'dept' }, approvalMode: ApprovalMode.SINGLE },
        ],
      }),
    )
    expect(xml).toContain('flowable:assignee="${ckplmDeptLeader}"')
  })

  it('角色未选具体角色 → 不生成审批人属性且告警', () => {
    const { xml, warnings } = compileToBpmnXml(
      dsl({
        nodes: [{ id: 'a1', type: NodeType.APPROVAL, name: '评审', assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: [] }, approvalMode: ApprovalMode.SINGLE }],
      }),
    )
    expect(warnings.some((w) => w.includes('未选择角色'))).toBe(true)
    expect(xml).not.toContain('flowable:candidateGroups')
  })
})

describe('会签编译', () => {
  it('比例通过 → multiInstance + completionCondition', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          {
            id: 'a1',
            type: NodeType.APPROVAL,
            name: '会签',
            assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
            approvalMode: ApprovalMode.COUNTERSIGN,
            passRule: { mode: PassRuleMode.PERCENT, percent: 80, abstain: 'IGNORE' },
          },
        ],
      }),
    )
    expect(xml).toContain('<multiInstanceLoopCharacteristics isSequential="false"')
    expect(xml).toContain('flowable:collection="${ckplmApprovers}"')
    expect(xml).toContain('flowable:elementVariable="ckplmApprover"')
    expect(xml).toContain('${nrOfCompletedInstances * 100 &gt;= nrOfInstances * 80}')
  })

  it('票数通过 → completionCondition 按票数', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          {
            id: 'a1',
            type: NodeType.APPROVAL,
            name: '会签',
            assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
            approvalMode: ApprovalMode.COUNTERSIGN,
            passRule: { mode: PassRuleMode.COUNT, count: 3 },
          },
        ],
      }),
    )
    expect(xml).toContain('${nrOfCompletedInstances &gt;= 3}')
  })

  it('一票否决 → 不生成 completionCondition 且明示需运行期支持', () => {
    const { xml, warnings } = compileToBpmnXml(
      dsl({
        nodes: [
          {
            id: 'a1',
            type: NodeType.APPROVAL,
            name: '会签',
            assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
            approvalMode: ApprovalMode.COUNTERSIGN,
            passRule: { mode: PassRuleMode.VETO, veto: true },
          },
        ],
      }),
    )
    expect(warnings.some((w) => w.includes('一票否决'))).toBe(true)
    expect(xml).not.toContain('completionCondition')
    expect(xml).toContain('ckplm:passRule')
  })

  it('串行 → isSequential=true', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'a1', type: NodeType.APPROVAL, name: '串行', assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] }, approvalMode: ApprovalMode.SERIAL },
        ],
      }),
    )
    expect(xml).toContain('isSequential="true"')
  })

  it('单签 → 不生成 multiInstance', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'a1', type: NodeType.APPROVAL, name: '单签', assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] }, approvalMode: ApprovalMode.SINGLE },
        ],
      }),
    )
    expect(xml).not.toContain('multiInstanceLoopCharacteristics')
  })
})

describe('网关与条件编译', () => {
  const gatewayDsl = (): FlowDsl =>
    dsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '评审结果' },
        { id: 't1', type: NodeType.TASK, name: '实施', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f0', source: 'start_1', target: 'gw_1', kind: EdgeKind.NORMAL },
        {
          id: 'f1',
          source: 'gw_1',
          target: 't1',
          kind: EdgeKind.CONDITION,
          name: '通过',
          condition: { mode: ConditionMode.FIELD, fieldKey: 'changeLevel', operator: ConditionOperator.EQ, value: 'A' },
        },
        { id: 'f2', source: 'gw_1', target: 'end_1', kind: EdgeKind.DEFAULT, name: '默认' },
        { id: 'f3', source: 't1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
    })

  it('默认分支编译为网关 default 属性', () => {
    const { xml } = compileToBpmnXml(gatewayDsl())
    expect(xml).toContain('default="f2"')
  })

  it('字段条件编译为 UEL 表达式', () => {
    const { xml } = compileToBpmnXml(gatewayDsl())
    expect(xml).toContain('${changeLevel == &#39;A&#39;}'.replace(/&#39;/g, "'"))
  })

  it('高级表达式模式直接透传并加 UEL 包裹', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
          { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '分支', advanced: true },
          { id: 'end_1', type: NodeType.END, name: '结束' },
        ],
        edges: [
          { id: 'f0', source: 'start_1', target: 'gw_1', kind: EdgeKind.NORMAL },
          { id: 'f1', source: 'gw_1', target: 'end_1', kind: EdgeKind.DEFAULT },
        ],
      }),
    )
    expect(xml).toContain('<exclusiveGateway')
    expect(xml).toContain('ckplm:advanced="true"')
  })

  it('并行网关不生成 default 属性', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
          { id: 'gw_p', type: NodeType.PARALLEL_GATEWAY, name: '并行' },
          { id: 'end_1', type: NodeType.END, name: '结束' },
        ],
        edges: [
          { id: 'f0', source: 'start_1', target: 'gw_p', kind: EdgeKind.NORMAL },
          { id: 'f1', source: 'gw_p', target: 'end_1', kind: EdgeKind.DEFAULT },
        ],
      }),
    )
    expect(xml).toContain('<parallelGateway id="gw_p"')
    expect(xml).not.toContain('default="f1"')
  })
})

describe('服务 / 通知 / 子流程 / 定时', () => {
  it('服务节点 → 委托 Bean + flowable:field 传参（服务 id 不拼进表达式）', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 's1', type: NodeType.SERVICE, name: '升版', serviceRef: 'object.promote', params: { state: 'RELEASED' } },
        ],
      }),
    )
    expect(xml).toContain('flowable:delegateExpression="${plmServiceDelegate}"')
    expect(xml).toContain(
      '<flowable:field name="serviceId"><flowable:string>object.promote</flowable:string></flowable:field>',
    )
    expect(xml).toContain(
      '<flowable:field name="state"><flowable:string>RELEASED</flowable:string></flowable:field>',
    )
    expect(xml).toContain('ckplm:serviceRef="object.promote"')
    expect(xml).toContain('ckplm:serviceParams')
    // 回归守卫：冒号形式是非法 UEL，Flowable 会直接拒绝部署（真实部署验证发现）
    expect(xml).not.toContain('plmService:')
  })

  it('未配置服务 → 告警但不静默', () => {
    const { warnings } = compileToBpmnXml(dsl({ nodes: [{ id: 's1', type: NodeType.SERVICE, name: '空服务' }] }))
    expect(warnings.some((w) => w.includes('既未选择服务'))).toBe(true)
  })

  it('通知节点 → 统一委托 + 接收人声明', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [
          { id: 'n1', type: NodeType.NOTIFY, name: '通知', recipients: { strategy: AssigneeStrategy.ROLE, roleCodes: ['QUALITY'] }, templateCode: 'TPL_1' },
        ],
      }),
    )
    expect(xml).toContain('flowable:delegateExpression="${plmNotify}"')
    expect(xml).toContain('ckplm:templateCode')
  })

  it('子流程 → callActivity calledElement', () => {
    const { xml } = compileToBpmnXml(
      dsl({
        nodes: [{ id: 'sp1', type: NodeType.SUB_PROCESS, name: '子流程', processKey: 'sub_flow', variableMap: { a: 'b' } }],
      }),
    )
    expect(xml).toContain('<callActivity id="sp1"')
    expect(xml).toContain('calledElement="sub_flow"')
    expect(xml).toContain('ckplm:variableMap')
  })

  it('定时节点 → intermediateCatchEvent + timeDuration', () => {
    const { xml } = compileToBpmnXml(
      dsl({ nodes: [{ id: 'tm1', type: NodeType.TIMER, name: '等待', mode: TimerMode.DURATION, duration: 'PT2H' }] }),
    )
    expect(xml).toContain('<intermediateCatchEvent')
    expect(xml).toContain('<timeDuration>PT2H</timeDuration>')
  })
})

describe('截止时间与逾期后果', () => {
  it('逾期重新分配 → 告警需运行期边界事件', () => {
    const { xml, warnings } = compileToBpmnXml(
      dsl({
        nodes: [
          {
            id: 'a1',
            type: NodeType.APPROVAL,
            name: '审批',
            assignee: { strategy: AssigneeStrategy.INITIATOR },
            approvalMode: ApprovalMode.SINGLE,
            deadline: { anchor: 'NODE_START', durationHours: 24, action: 'REASSIGN', reassignTo: { strategy: AssigneeStrategy.ROLE, roleCodes: ['MGR'] } },
          },
        ],
      }),
    )
    expect(warnings.some((w) => w.includes('逾期后果'))).toBe(true)
    expect(xml).toContain('ckplm:deadline')
  })
})

describe('往返无损（DSL → BPMN → DSL）', () => {
  it('节点类型、连线、会签规则、审批人策略均可还原', () => {
    const source = dsl({
      meta: { key: 'round_trip_flow', name: '往返流程' },
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'gw_1', type: NodeType.EXCLUSIVE_GATEWAY, name: '分支' },
        {
          id: 'a1',
          type: NodeType.APPROVAL,
          name: '会签',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG', 'QA'] },
          approvalMode: ApprovalMode.COUNTERSIGN,
          passRule: { mode: PassRuleMode.PERCENT, percent: 80, abstain: 'IGNORE' },
          reject: { enabled: true, target: 'INITIATOR' },
        },
        { id: 't1', type: NodeType.TASK, name: '办理', assignee: { strategy: AssigneeStrategy.USER, userOids: ['u1'] } },
        { id: 's1', type: NodeType.SERVICE, name: '升版', serviceRef: 'object.promote' },
        { id: 'end_1', type: NodeType.END, name: '结束', callbacks: [{ kind: 'OBJECT_STATE', targetState: 'RELEASED' }] },
      ],
      edges: [
        { id: 'f0', source: 'start_1', target: 'gw_1', kind: EdgeKind.NORMAL },
        { id: 'f1', source: 'gw_1', target: 'a1', kind: EdgeKind.CONDITION, name: '高', condition: { mode: ConditionMode.FIELD, fieldKey: 'changeLevel', operator: ConditionOperator.EQ, value: 'A' } },
        { id: 'f2', source: 'gw_1', target: 't1', kind: EdgeKind.DEFAULT },
        { id: 'f3', source: 'a1', target: 's1', kind: EdgeKind.NORMAL },
        { id: 'f4', source: 's1', target: 'end_1', kind: EdgeKind.NORMAL },
        { id: 'f5', source: 't1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
      layout: {
        nodes: {
          start_1: { x: 240, y: 40 },
          gw_1: { x: 240, y: 140 },
          a1: { x: 120, y: 240 },
          t1: { x: 420, y: 240 },
          s1: { x: 120, y: 340 },
          end_1: { x: 240, y: 440 },
        },
      },
    })

    const { xml } = compileToBpmnXml(source)
    const imported = parseBpmnXml(xml)

    expect(imported.unsupported).toEqual([])
    expect(imported.dsl.meta.key).toBe('round_trip_flow')
    expect(imported.dsl.nodes).toHaveLength(source.nodes.length)
    expect(imported.dsl.edges).toHaveLength(source.edges.length)

    const approval = imported.dsl.nodes.find((n) => n.id === 'a1') as { type: string; approvalMode: string; passRule?: { percent?: number; mode: string }; assignee: { strategy: string; roleCodes?: string[] }; reject?: { target: string } }
    expect(approval.type).toBe(NodeType.APPROVAL)
    expect(approval.approvalMode).toBe(ApprovalMode.COUNTERSIGN)
    expect(approval.passRule?.mode).toBe(PassRuleMode.PERCENT)
    expect(approval.passRule?.percent).toBe(80)
    expect(approval.assignee.strategy).toBe(AssigneeStrategy.ROLE)
    expect(approval.assignee.roleCodes).toEqual(['ENG', 'QA'])
    expect(approval.reject?.target).toBe('INITIATOR')

    const task = imported.dsl.nodes.find((n) => n.id === 't1') as { type: string; assignee: { strategy: string; userOids?: string[] } }
    expect(task.type).toBe(NodeType.TASK)
    expect(task.assignee.strategy).toBe(AssigneeStrategy.USER)
    expect(task.assignee.userOids).toEqual(['u1'])

    const service = imported.dsl.nodes.find((n) => n.id === 's1') as { type: string; serviceRef?: string }
    expect(service.type).toBe(NodeType.SERVICE)
    expect(service.serviceRef).toBe('object.promote')

    const end = imported.dsl.nodes.find((n) => n.id === 'end_1') as { callbacks?: Array<{ targetState?: string }> }
    expect(end.callbacks?.[0]?.targetState).toBe('RELEASED')

    const condition = imported.dsl.edges.find((e) => e.id === 'f1')
    expect(condition?.kind).toBe(EdgeKind.CONDITION)
    expect(condition?.condition?.fieldKey).toBe('changeLevel')
    expect(condition?.condition?.value).toBe('A')

    const defaultEdge = imported.dsl.edges.find((e) => e.id === 'f2')
    expect(defaultEdge?.kind).toBe(EdgeKind.DEFAULT)

    // 布局随 BPMNDI 往返
    expect(imported.dsl.layout.nodes.a1.x).toBe(120)
    expect(imported.dsl.layout.nodes.a1.y).toBe(240)
  })
})

describe('不支持元素明示（spec §1 第 5 条）', () => {
  it('scriptTask / boundaryEvent 被上报而非丢弃', () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">
  <process id="legacy_flow" name="存量流程" isExecutable="true">
    <startEvent id="s"/>
    <scriptTask id="script_1" name="脚本" scriptFormat="groovy"/>
    <boundaryEvent id="bnd_1" attachedToRef="s"/>
    <sequenceFlow id="f1" sourceRef="s" targetRef="e"/>
    <endEvent id="e"/>
  </process>
</definitions>`
    const result = parseBpmnXml(xml)
    const tags = result.unsupported.map((u) => u.tag)
    expect(tags).toContain('scriptTask')
    expect(tags).toContain('boundaryEvent')
    // 可映射的部分仍然导入
    expect(result.dsl.nodes.map((n) => n.id)).toContain('s')
    expect(result.dsl.nodes.map((n) => n.id)).toContain('e')
    expect(result.dsl.edges).toHaveLength(1)
  })

  it('无 process 元素时报错而非返回空流程', () => {
    expect(() => parseBpmnXml('<definitions/>')).toThrow('未找到 <process>')
  })

  it('第三方 BPMN（无 ckplm 标记）按标准属性推断类型与审批人', () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">
  <process id="legacy2" name="存量2" isExecutable="true">
    <startEvent id="s"/>
    <userTask id="u1" name="审批" flowable:candidateGroups="manager"/>
    <userTask id="u2" name="会签">
      <multiInstanceLoopCharacteristics isSequential="false"/>
      <completionCondition>\${nrOfCompletedInstances * 100 >= nrOfInstances * 60}</completionCondition>
    </userTask>
    <endEvent id="e"/>
  </process>
</definitions>`
    const result = parseBpmnXml(xml)
    const u1 = result.dsl.nodes.find((n) => n.id === 'u1') as { type: string; assignee: { strategy: string; roleCodes?: string[] } }
    expect(u1.type).toBe(NodeType.TASK)
    expect(u1.assignee.strategy).toBe(AssigneeStrategy.ROLE)
    expect(u1.assignee.roleCodes).toEqual(['manager'])
    const u2 = result.dsl.nodes.find((n) => n.id === 'u2') as { approvalMode: string }
    expect(u2.approvalMode).toBe(ApprovalMode.COUNTERSIGN)
    expect(result.warnings.some((w) => w.includes('推断'))).toBe(true)
  })
})

describe('「设置审批人」契约（内置表单 + 人员由按活动变量决定）', () => {
  /** START → 设置审批人 → 技术评审（单签）→ 质量会签 → END */
  const setupFlow = () =>
    dsl({
      nodes: [
        { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
        { id: 'set_1', type: NodeType.SET_ASSIGNEE, name: '设置审批人', assignee: { strategy: AssigneeStrategy.INITIATOR } },
        {
          id: 'appr_1',
          type: NodeType.APPROVAL,
          name: '技术评审',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
          approvalMode: ApprovalMode.SINGLE,
        },
        {
          id: 'cs_1',
          type: NodeType.COUNTERSIGN_APPROVAL,
          name: '质量会签',
          assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['QA'] },
          passRule: { mode: PassRuleMode.COUNT, count: 2 },
        },
        { id: 'end_1', type: NodeType.END, name: '结束' },
      ],
      edges: [
        { id: 'f1', source: 'start_1', target: 'set_1', kind: EdgeKind.NORMAL },
        { id: 'f2', source: 'set_1', target: 'appr_1', kind: EdgeKind.NORMAL },
        { id: 'f3', source: 'appr_1', target: 'cs_1', kind: EdgeKind.NORMAL },
        { id: 'f4', source: 'cs_1', target: 'end_1', kind: EdgeKind.NORMAL },
      ],
    })

  it('设置活动用内置固定表单：DSL 没写 formRef 也要输出 formKey（运行期按它派发表单）', () => {
    const { xml } = compileToBpmnXml(setupFlow())
    expect(xml).toContain('flowable:formKey="CKPLM_SETUP_ASSIGNEE"')
    expect(xml).toContain('ckplm:formRef="CKPLM_SETUP_ASSIGNEE"')
  })

  it('下游人员取「按活动变量」：单签直接指派，多实例取集合且逐实例指派到人', () => {
    const { xml } = compileToBpmnXml(setupFlow())
    // 单签：人员 oid 单个，直接指派
    expect(xml).toContain('flowable:assignee="${ckplmSetupAssignees_appr_1}"')
    // 多实例：集合按活动取，且每个实例指派到多实例元素变量
    // —— 少了最后这一句，集合再正确也没人接到任务（会签任务会全部"无人办理"）
    expect(xml).toContain('flowable:collection="${ckplmSetupAssignees_cs_1}"')
    expect(xml).toContain('flowable:elementVariable="ckplmApprover"')
    expect(xml).toContain('flowable:assignee="${ckplmApprover}"')
  })
})
