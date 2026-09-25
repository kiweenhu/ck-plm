/**
 * 「会签」表单模型 —— 规则文案与参与人明细。
 *
 * <p>锁住两件容易做错的事：
 * <ol>
 *   <li><b>规则怎么说</b>：设计器里配的是 {@code passRule}（模式 + 参数），
 *       经办人要看的是"1 个通过就通过"这种能读懂的一句话；说错了会让人对"我这票还重不重要"
 *       形成错误预期。</li>
 *   <li><b>他人的情况怎么合</b>：会签同一活动下每人一条任务，表单要把它们合成一张表，
 *       并把自己那一行标出来；存量数据没有逐人结论时显示"已办理"，不猜结论。</li>
 * </ol>
 */

import { describe, expect, it } from 'vitest'
import { NodeType, PassRuleMode, type FlowDsl } from '@flow-dsl-core'
import { buildCountersignModel, passRuleText, validateCountersign } from '../countersign'

const NODE_ID = 'countersign_1'

function dslWith(node: Record<string, unknown>): FlowDsl {
  return {
    dslVersion: '1.0',
    meta: { key: 'cs_flow', name: '会签测试' },
    nodes: [{ id: NODE_ID, type: NodeType.COUNTERSIGN_APPROVAL, name: '会签审批', ...node }],
    edges: [],
    layout: { nodes: {} },
  } as unknown as FlowDsl
}

/** 流程进度里会签活动的三条记录：已同意 / 已驳回 / 未办理 */
function threeActivities() {
  return [
    {
      activityId: NODE_ID,
      status: 'completed',
      taskId: 't1',
      assignees: ['u1'],
      assigneeNames: ['张三'],
      endTime: '2026-09-22T21:48:54',
      comment: '同意，参数没问题',
      decision: 'APPROVE',
    },
    {
      activityId: NODE_ID,
      status: 'completed',
      taskId: 't2',
      assignees: ['u2'],
      assigneeNames: ['李四'],
      endTime: '2026-09-22T22:10:00',
      comment: '驳回：封装要改',
      decision: 'REJECT',
    },
    {
      activityId: NODE_ID,
      status: 'running',
      taskId: 't3',
      assignees: ['u3'],
      assigneeNames: ['王五'],
      startTime: '2026-09-22T21:48:54',
    },
  ]
}

describe('会签通过规则文案', () => {
  it('1 个通过就通过（COUNT=1）', () => {
    expect(passRuleText({ mode: PassRuleMode.COUNT, count: 1 }).text).toBe('1 个通过即通过')
  })

  it('必须 N 票通过（COUNT>1）', () => {
    expect(passRuleText({ mode: PassRuleMode.COUNT, count: 3 }).text).toBe('须 3 个通过')
  })

  it('全票通过（PERCENT=100）与按比例通过', () => {
    expect(passRuleText({ mode: PassRuleMode.PERCENT, percent: 100 }).text).toBe('须全部通过（100%）')
    expect(passRuleText({ mode: PassRuleMode.PERCENT, percent: 80 }).text).toBe('通过比例达到 80% 即通过')
  })

  it('一票否决说清后果', () => {
    expect(passRuleText({ mode: PassRuleMode.VETO }).text).toBe('一票否决：任一驳回即不通过')
  })

  it('没配规则不是"没规则"：引擎按全部通过推进，如实说出来', () => {
    const result = passRuleText(undefined)
    expect(result.text).toBe('须全部通过')
    expect(result.detail).toContain('未配置通过规则')
  })

  it('弃权处理作为注解带上（没配就不提）', () => {
    expect(passRuleText({ mode: PassRuleMode.PERCENT, percent: 100, abstain: 'IGNORE' }).detail)
      .toContain('弃权票不计入分母')
    expect(passRuleText({ mode: PassRuleMode.PERCENT, percent: 100 }).detail).not.toContain('弃权')
  })
})

describe('会签表单模型', () => {
  it('把同一活动的多条任务合成参与人明细，并标出自己那一行', () => {
    const model = buildCountersignModel({
      dsl: dslWith({ passRule: { mode: PassRuleMode.PERCENT, percent: 100, abstain: 'IGNORE' } }),
      nodeId: NODE_ID,
      activities: threeActivities(),
      currentTaskId: 't3',
    })

    expect(model.total).toBe(3)
    expect(model.participants.map((p) => p.name)).toEqual(['张三', '李四', '王五'])
    expect(model.participants.map((p) => p.isMe)).toEqual([false, false, true])
    expect(model.approvedCount).toBe(1)
    expect(model.rejectedCount).toBe(1)
    expect(model.doneCount).toBe(2)
    expect(model.progressText).toBe('已办 2/3')
    // 未办理的人不该被算成任何一种结论
    expect(model.participants[2].decision).toBeUndefined()
    expect(model.participants[0].comment).toBe('同意，参数没问题')
    expect(model.ruleText).toBe('须全部通过（100%）')
  })

  it('只挑当前会签活动的记录（别的节点不混进来）', () => {
    const model = buildCountersignModel({
      dsl: dslWith({ passRule: { mode: PassRuleMode.COUNT, count: 1 } }),
      nodeId: NODE_ID,
      activities: [
        { activityId: 'approval_other', status: 'completed', assigneeNames: ['别人'], decision: 'APPROVE' },
        ...threeActivities(),
      ],
      currentTaskId: 't3',
    })

    expect(model.total).toBe(3)
    expect(model.participants.some((p) => p.name === '别人')).toBe(false)
    expect(model.ruleText).toBe('1 个通过即通过')
  })

  it('存量数据没有逐人结论：显示"已办理"，不编一个结论出来', () => {
    const model = buildCountersignModel({
      dsl: dslWith({ passRule: { mode: PassRuleMode.COUNT, count: 1 } }),
      nodeId: NODE_ID,
      activities: [{ activityId: NODE_ID, status: 'completed', taskId: 't9', assigneeNames: ['老数据'] }],
      currentTaskId: 't9',
    })

    expect(model.participants[0].done).toBe(true)
    expect(model.participants[0].decision).toBeUndefined()
    expect(model.approvedCount).toBe(0)
    expect(model.rejectedCount).toBe(0)
  })

  it('取不到 DSL / 进度时不抛错：规则走缺省、参与人为空', () => {
    const model = buildCountersignModel({ dsl: null, nodeId: NODE_ID, activities: null })
    expect(model.ruleText).toBe('须全部通过')
    expect(model.participants).toEqual([])
    expect(model.progressText).toBe('已办 0/0')
  })

  it('本人这一票的校验沿用审批口径（允许驳回 / 驳回必填意见）', () => {
    const model = buildCountersignModel({
      dsl: dslWith({
        passRule: { mode: PassRuleMode.COUNT, count: 1 },
        reject: { enabled: true, target: 'PREVIOUS', commentRequired: true },
      }),
      nodeId: NODE_ID,
      activities: [],
    })

    expect(validateCountersign(model, 'approve', '')).toBe('')
    expect(validateCountersign(model, 'reject', '')).toBe('驳回必须填写审批意见')
    expect(validateCountersign(model, 'reject', '封装要改')).toBe('')
  })

  it('节点没配「允许驳回」时驳回被拦下（与会签规则无关，是节点的设计决定）', () => {
    const model = buildCountersignModel({
      dsl: dslWith({ passRule: { mode: PassRuleMode.COUNT, count: 1 } }),
      nodeId: NODE_ID,
      activities: [],
    })

    expect(model.opinion.allowReject).toBe(false)
    expect(validateCountersign(model, 'reject', '理由')).toBe('该活动未开启驳回')
  })
})
