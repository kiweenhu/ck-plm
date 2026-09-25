/**
 * 任务表单派发：**没写 formKey 的老实例也要拿到专属模板**。
 *
 * <p>为什么这条要钉住：BPMN 里的 {@code flowable:formKey} 是<b>部署那一刻</b>编译出来的，
 * 此后新登记的内置表单不会凭空出现在已经在跑的实例里。如果运行期只认 formKey，
 * 就会出现"模板登记了、正在办理的审批任务却还停在通用表单上"——典型的"改了却看不到"。
 * 所以这里按节点类型派生一次（与编译期 fixedFormCodeOf 同一规则）。
 */

import { describe, expect, it } from 'vitest'
import {
  APPROVAL_OPINION_FORM_CODE,
  COUNTERSIGN_FORM_CODE,
  SETUP_ASSIGNEE_FORM_CODE,
  NodeType,
} from '@flow-dsl-core'
import {
  FALLBACK_FORM_VIEW,
  effectiveFormCode,
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
})
