/**
 * 任务表单派发：**没写 formKey 的老实例也要拿到专属模板**。
 *
 * <p>为什么这条要钉住：BPMN 里的 {@code flowable:formKey} 是<b>部署那一刻</b>编译出来的，
 * 此后新登记的内置表单不会凭空出现在已经在跑的实例里。如果运行期只认 formKey，
 * 就会出现"模板登记了、正在办理的审批任务却还停在通用表单上"——典型的"改了却看不到"。
 * 所以这里按节点类型派生一次（与编译期 fixedFormCodeOf 同一规则）。
 */

import { describe, expect, it, vi } from 'vitest'
import {
  APPROVAL_OPINION_FORM_CODE,
  COUNTERSIGN_FORM_CODE,
  SETUP_ASSIGNEE_FORM_CODE,
  NodeType,
  registerTaskFormTemplates,
} from '@flow-dsl-core'

// 视图注册表把全部表单组件（含阶段门申请单 → 交付物弹框 → DocumentViewer）拉进模块图，
// 而 DocumentViewer 顶层就 import 了 @vue-office/docx —— 那个包在模块初始化时访问 window，
// node 环境下会直接抛 "window is not defined"，整个套件一条用例都跑不起来。
// 本测试关心的是"派发到哪个组件"，把这条与派发无关的依赖挡掉即可。
vi.mock('@/views/product/DocumentViewer.vue', () => ({ default: { template: '<div />' } }))

import {
  FALLBACK_FORM_VIEW,
  FORM_COMPONENT_VIEWS,
  effectiveFormCode,
  resolveStartFormView,
  resolveTaskFormView,
} from '@/components/task/taskForms'

/** 造一个"表单上下文"，节点类型可指定（extra 用于给节点加字段，如 approvalMode） */
function formOf(nodeType: string | null, formKey = '', extra: Record<string, unknown> = {}) {
  const nodes = nodeType ? [{ id: 'n1', type: nodeType, name: '节点', ...extra }] : []
  return {
    formKey,
    taskDefinitionKey: 'n1',
    dslJson: nodeType ? JSON.stringify({ nodes }) : null,
  }
}

describe('生效表单 code 的派生', () => {
  it('没有固定表单的节点类型：部署时写了 formKey → 用它', () => {
    expect(effectiveFormCode(formOf(NodeType.TASK, 'MY_FORM'))).toBe('MY_FORM')
  })

  it('审批节点显式指定了表单 → 用它（内置「审批意见」是默认，不盖过显式选择）', () => {
    // 这是"同一节点类型多张模板"的运行期一半：企业建了「技术评审单」并让节点指过去，
    // 办理页就必须是技术评审单 —— 而不是被内置默认表单顶掉
    expect(effectiveFormCode(formOf(NodeType.APPROVAL, 'MY_FORM'))).toBe('MY_FORM')
  })

  it('语义固定的表单：残留的历史 formKey 盖不过它', () => {
    // 老模板里可能躺着一个当年手填的 formRef（当时内置表单还没登记）——
    // 「设置审批人」的表单由代码生成，让它被顶掉就会出现"发起人挑人的那一步没了"
    expect(effectiveFormCode(formOf(NodeType.SET_ASSIGNEE, 'MY_FORM')))
      .toBe(SETUP_ASSIGNEE_FORM_CODE)
  })

  it('没写 formKey 的审批活动 → 派生为「审批意见」', () => {
    expect(effectiveFormCode(formOf(NodeType.APPROVAL))).toBe(APPROVAL_OPINION_FORM_CODE)
  })

  it('审批节点选了「会签」模式 → 同样派发到「会签」表单（会签的两种表达是同一件事）', () => {
    expect(effectiveFormCode(formOf(NodeType.APPROVAL, '', { approvalMode: 'COUNTERSIGN' })))
      .toBe(COUNTERSIGN_FORM_CODE)
  })

  it('没写 formKey 的会签活动 → 派生为「会签」', () => {
    expect(effectiveFormCode(formOf(NodeType.COUNTERSIGN_APPROVAL))).toBe(COUNTERSIGN_FORM_CODE)
  })

  it('没有固定表单的节点类型（办理节点）→ 不派生，交给兜底模板', () => {
    expect(effectiveFormCode(formOf(NodeType.TASK))).toBe('')
  })

  it('取不到 DSL（老实例没有 dslJson）→ 不猜，交给兜底模板', () => {
    expect(effectiveFormCode(formOf(null))).toBe('')
    expect(effectiveFormCode(null)).toBe('')
  })
})

describe('表单视图的派发', () => {
  it('审批活动拿到专属视图，而不是兜底视图', () => {
    expect(resolveTaskFormView(formOf(NodeType.APPROVAL))).not.toBe(FALLBACK_FORM_VIEW)
  })

  it('办理活动仍走兜底视图', () => {
    expect(resolveTaskFormView(formOf(NodeType.TASK))).toBe(FALLBACK_FORM_VIEW)
  })

  it('未知 / 缺失 formKey 也不至于渲染不出来（办理不能打不开）', () => {
    expect(resolveTaskFormView({ formKey: 'GONE', taskDefinitionKey: 'x', dslJson: null }))
      .toBe(FALLBACK_FORM_VIEW)
  })

  it('变更管理两张行业会签表各回各的视图（影响分析 / CCB 决策）', () => {
    // 这两张是 ECR 流程的两个会签节点：影响分析按专业给结论，CCB 按考量项做决策。
    // 绑定关系靠 forms.json 的 component 名，掉一处就会静默退回「审批意见」——
    // 表现为"表单没做"，所以在这里钉住。
    registerTaskFormTemplates([
      {
        code: 'change-request-impact',
        label: '变更请求影响分析会签',
        builtin: false,
        component: 'EcrImpactCountersignForm',
        nodeTypes: [NodeType.COUNTERSIGN_APPROVAL],
      },
      {
        code: 'change-request-ccb',
        label: '变更请求 CCB 决策会签',
        builtin: false,
        component: 'EcrCcbDecisionForm',
        nodeTypes: [NodeType.COUNTERSIGN_APPROVAL],
      },
    ])
    try {
      const impact = formOf(NodeType.COUNTERSIGN_APPROVAL, 'change-request-impact')
      const ccb = formOf(NodeType.COUNTERSIGN_APPROVAL, 'change-request-ccb')
      expect(resolveTaskFormView(impact)).toBe(FORM_COMPONENT_VIEWS.EcrImpactCountersignForm)
      expect(resolveTaskFormView(ccb)).toBe(FORM_COMPONENT_VIEWS.EcrCcbDecisionForm)
      expect(resolveTaskFormView(ccb)).not.toBe(FALLBACK_FORM_VIEW)
    } finally {
      registerTaskFormTemplates([])
    }
  })

  it('变更通告审批 / 会签各回各的视图（通告基本属性与更改事项就靠这张表带出来）', () => {
    // 这两张挂了才看得到「变更通告」摘要（名称/编码/变更类型/原因/建议方案/影响初判
    // + 受影响对象 + 更改事项清单）。节点 formRef 或 forms.json 的 component 掉一处，
    // 运行期就静默退回通用表单 —— 表现为"审批节点什么都没显示"（2026-11-04 实际踩过：
    // 模板 v11 已指向这两张表，但部署的还是 v10，formKey 仍是 CKPLM_APPROVAL_OPINION）。
    registerTaskFormTemplates([
      {
        code: 'change-notice-review',
        label: '变更通告审批',
        builtin: false,
        component: 'EcnReviewForm',
        nodeTypes: [NodeType.APPROVAL],
      },
      {
        code: 'change-notice-countersign',
        label: '变更通告会签',
        builtin: false,
        component: 'EcnCountersignForm',
        nodeTypes: [NodeType.COUNTERSIGN_APPROVAL],
      },
    ])
    try {
      const review = formOf(NodeType.APPROVAL, 'change-notice-review')
      const countersign = formOf(NodeType.COUNTERSIGN_APPROVAL, 'change-notice-countersign')
      expect(resolveTaskFormView(review)).toBe(FORM_COMPONENT_VIEWS.EcnReviewForm)
      expect(resolveTaskFormView(countersign)).toBe(FORM_COMPONENT_VIEWS.EcnCountersignForm)
      // 审批节点最容易掉回兜底模板 —— 那只有意见框，看不到在批什么
      expect(resolveTaskFormView(review)).not.toBe(FALLBACK_FORM_VIEW)
    } finally {
      registerTaskFormTemplates([])
    }
  })
})

/**
 * 发起表单（开始节点配的表单）—— 2026-10-13 接通。
 *
 * <p>钉住的口径：开始节点的 formRef 从前只是"编译进 BPMN 没人读"的声明，现在发起弹框会渲染它。
 * 但只有**自足模板**才能填（不依赖任务上下文），依赖 taskId 的表单在发起页必须老实返回 null
 * —— 渲染一张取不到关联器件的空表比不渲染更糟。
 */
describe('发起表单的派发（开始节点表单）', () => {
  /** 造一个发起表单上下文（与后端 ProcessStartFormVO 同形，前端映射成办理侧形状） */
  function startFormOf(formKey: string) {
    return { formKey, taskDefinitionKey: 'start_1', dslJson: null }
  }

  it('开始节点没配表单 → null（老流程零影响）', () => {
    expect(resolveStartFormView(null)).toBeNull()
    expect(resolveStartFormView(startFormOf(''))).toBeNull()
  })

  it('自足的行业申请单（阶段门决策申请单）→ 拿到组件', () => {
    registerTaskFormTemplates([
      {
        code: 'gate-decision-apply',
        label: '阶段门决策申请单',
        builtin: false,
        component: 'GateDecisionApplyForm',
        nodeTypes: ['START', 'TASK'],
      },
    ])
    try {
      expect(resolveStartFormView(startFormOf('gate-decision-apply')))
        .toBe(FORM_COMPONENT_VIEWS.GateDecisionApplyForm)
    } finally {
      registerTaskFormTemplates([])
    }
  })

  it('只读实例数据的表单（评审表 / 会签表）→ null，不能填就不给填', () => {
    registerTaskFormTemplates([
      {
        code: 'component-qualification-review',
        label: '元器件认证审批',
        builtin: false,
        component: 'ComponentQualificationReviewForm',
        nodeTypes: ['APPROVAL'],
      },
      {
        code: 'component-qualification-countersign',
        label: '元器件认证会签',
        builtin: false,
        component: 'ComponentQualificationCountersignForm',
        nodeTypes: ['COUNTERSIGN_APPROVAL'],
      },
    ])
    try {
      // 这两张要读流程变量与各人办理情况（getTaskContext(taskId)）—— 发起时没有 taskId
      expect(resolveStartFormView(startFormOf('component-qualification-review'))).toBeNull()
      expect(resolveStartFormView(startFormOf('component-qualification-countersign'))).toBeNull()
    } finally {
      registerTaskFormTemplates([])
    }
  })

  it('元器件认证申请是「双来源」表 → 发起页也给视图（器件取自发起时选中的对象）', () => {
    registerTaskFormTemplates([
      {
        code: 'component-qualification-apply',
        label: '元器件认证申请',
        builtin: false,
        component: 'ComponentQualificationForm',
        nodeTypes: ['START', 'TASK'],
      },
    ])
    try {
      // 2026-10-13 用户要求：有任务读流程关联的 PBS，发起时读弹框里选中的对象 —— 两侧都能用
      expect(resolveStartFormView(startFormOf('component-qualification-apply')))
        .toBe(FORM_COMPONENT_VIEWS.ComponentQualificationForm)
    } finally {
      registerTaskFormTemplates([])
    }
  })

  it('技术评审申请单是「双来源」表 → 发起页也给视图（待评对象取自发起时选中的对象）', () => {
    registerTaskFormTemplates([
      {
        code: 'technical-review-apply',
        label: '技术评审申请单',
        builtin: false,
        component: 'TechnicalReviewApplyForm',
        nodeTypes: ['START', 'TASK'],
      },
    ])
    try {
      // 2026-10-14 用户要求：待评对象有任务读流程关联的 PBS，发起时读弹框里选中的对象；
      // 评审模板 / 阶段 / 关联门 / 送审材料全部手填 —— 没有字段是"只有办理时才拿得到"的
      expect(resolveStartFormView(startFormOf('technical-review-apply')))
        .toBe(FORM_COMPONENT_VIEWS.TechnicalReviewApplyForm)
    } finally {
      registerTaskFormTemplates([])
    }
  })

  it('内置办理表单（会签）→ null：它是"给这次办理下结论"，不是发起申请', () => {
    expect(resolveStartFormView(startFormOf(COUNTERSIGN_FORM_CODE))).toBeNull()
    expect(resolveStartFormView(startFormOf(SETUP_ASSIGNEE_FORM_CODE))).toBeNull()
  })

  it('数据里残留的 code 不在模板清单 → null（不猜、也不落兜底表单）', () => {
    expect(resolveStartFormView(startFormOf('GONE'))).toBeNull()
    expect(resolveStartFormView(startFormOf('GONE'))).not.toBe(FALLBACK_FORM_VIEW)
  })
})
