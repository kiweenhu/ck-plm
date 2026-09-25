/**
 * 「通知方式」（流程级，单选）—— 结构往返 + 历史形态归一 + 校验口径。
 *
 * <p>它解决的问题：流程里「通知」节点用哪种方式发。渠道<b>与凭据</b>
 * （SMTP 地址、OA 接口、飞书·钉钉·企业微信应用凭据）都属于企业集成，
 * 已统一收归服务端配置（application.yml 的 {@code plm.notification}）；
 * 流程模板里只剩下"本流程用哪一种通知方式"，且是<b>单选</b>。
 *
 * <p>本文件钉住四件事：
 * <ol>
 *   <li>通知方式存盘再读回不丢（zod 不声明就会静默丢弃）；</li>
 *   <li>历史形态（根上的 notifications、多选 channels）在解析入口被归一，老模板不丢配置；</li>
 *   <li>选了<b>系统未启用</b>的渠道 → 告警（运行期一定发不出去，早说）；</li>
 *   <li>拿不到系统配置时<b>不猜</b>：那条规则直接跳过，不误报。</li>
 * </ol>
 */

import { describe, expect, it } from 'vitest'
import {
  AssigneeStrategy,
  EdgeKind,
  IssueCode,
  IssueLevel,
  NodeType,
  NotifyChannel,
  OaAuthType,
  createEmptyDsl,
  flowDslSchema,
  parseFlowDsl,
  validateDsl,
  type FlowDsl,
} from '../index'

/** 基础流程：开始 → 审批 → 结束（可选挂一个通知节点） */
function flow(options: { notifyNode?: boolean } = {}): FlowDsl {
  const base = createEmptyDsl({ key: 'notify_flow', name: '通知方式测试' })
  const nodes: FlowDsl['nodes'] = [
    { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: AssigneeStrategy.INITIATOR } },
    {
      id: 'appr_1',
      type: NodeType.APPROVAL,
      name: '审批',
      assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      approvalMode: 'SINGLE',
    },
  ]
  const edges: FlowDsl['edges'] = [
    { id: 'e1', kind: EdgeKind.NORMAL, source: 'start_1', target: 'appr_1' },
  ]
  if (options.notifyNode) {
    nodes.push({
      id: 'notify_1',
      type: NodeType.NOTIFY,
      name: '通知',
      recipients: { strategy: AssigneeStrategy.INITIATOR },
    })
    edges.push({ id: 'e2', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'notify_1' })
    nodes.push({ id: 'end_1', type: NodeType.END, name: '结束' })
    edges.push({ id: 'e3', kind: EdgeKind.NORMAL, source: 'notify_1', target: 'end_1' })
  } else {
    nodes.push({ id: 'end_1', type: NodeType.END, name: '结束' })
    edges.push({ id: 'e2', kind: EdgeKind.NORMAL, source: 'appr_1', target: 'end_1' })
  }
  return { ...base, nodes, edges }
}

/** 带通知方式的流程（直接写进 meta，等价于面板编辑后的形态） */
function withChannel(channel: unknown): unknown {
  const dsl = flow() as unknown as { meta: Record<string, unknown> }
  return { ...(dsl as unknown as Record<string, unknown>), meta: { ...dsl.meta, notifications: { channel } } }
}

function codesOf(dsl: FlowDsl): string[] {
  return validateDsl(dsl).issues.map((issue) => issue.code)
}

describe('通知方式 · 结构往返', () => {
  it('通知方式存盘再读回不丢', () => {
    const parsed = flowDslSchema.parse(withChannel(NotifyChannel.CK_PLM)) as FlowDsl
    expect(parsed.meta.notifications?.channel).toBe('CK_PLM')
  })

  it('没配通知方式时该字段不出现（老模板不受影响）', () => {
    expect(flowDslSchema.parse(flow()).meta.notifications).toBeUndefined()
  })

  it('取值受约束（拼错/多余的渠道直接不合法，不让脏值进 DSL）', () => {
    expect(flowDslSchema.safeParse(withChannel('TELEGRAM')).success).toBe(false)
  })
})

describe('通知方式 · 历史形态归一（在解析入口做一次）', () => {
  it('历史多选 channels 取第一个作为单选 channel，并把数组丢掉', () => {
    const legacy = withChannel(undefined) as { meta: { notifications: Record<string, unknown> } }
    legacy.meta.notifications = { channels: [NotifyChannel.EMAIL, NotifyChannel.OA] }
    const result = parseFlowDsl(legacy)
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.dsl.meta.notifications?.channel).toBe('EMAIL')
      // 留着没人读的数组，下次就会有人以为"真的会发三个渠道"
      expect('channels' in (result.dsl.meta.notifications ?? {})).toBe(false)
    }
  })

  it('挂在 DSL 根上的 notifications 被搬进 meta（老模板的通知方式不丢）', () => {
    const dsl = flow() as unknown as Record<string, unknown>
    const legacy = { ...dsl, notifications: { channels: [NotifyChannel.WECOM] } }
    const result = parseFlowDsl(legacy)
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.dsl.meta.notifications?.channel).toBe('WECOM')
      expect('notifications' in (result.dsl as unknown as Record<string, unknown>)).toBe(false)
    }
  })

  it('两者都有时以 meta 为准（不来回覆盖）', () => {
    const dsl = flow() as unknown as { meta: Record<string, unknown> }
    const legacy = {
      ...(dsl as unknown as Record<string, unknown>),
      meta: { ...dsl.meta, notifications: { channel: NotifyChannel.CK_PLM } },
      notifications: { channels: [NotifyChannel.EMAIL] },
    }
    const result = parseFlowDsl(legacy)
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.dsl.meta.notifications?.channel).toBe('CK_PLM')
    }
  })

  it('历史模板里的凭据字段仍能读入（兼容保留，只为不让老模板打不开）', () => {
    const dsl = flow() as unknown as { meta: Record<string, unknown> }
    const legacy = {
      ...(dsl as unknown as Record<string, unknown>),
      meta: {
        ...dsl.meta,
        notifications: {
          channel: NotifyChannel.EMAIL,
          email: { smtpHost: 'smtp.example.com', smtpPort: 465, ssl: true },
          oa: { apiUrl: 'https://oa.example.com/api/notify', authType: OaAuthType.TOKEN },
        },
      },
    }
    const result = parseFlowDsl(legacy)
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.dsl.meta.notifications?.email?.smtpHost).toBe('smtp.example.com')
      expect(result.dsl.meta.notifications?.oa?.authType).toBe('TOKEN')
    }
  })
})

describe('通知方式 · 校验（凭据已不在流程职责内）', () => {
  it('系统已启用该渠道时，模板里没有任何凭据也不告警', () => {
    // 凭据在服务端配置（plm.notification），流程只选通道。
    // 这条用例钉住"职责边界"：谁也不能把凭据校验偷偷加回流程侧
    const report = validateDsl(withChannel(NotifyChannel.EMAIL) as FlowDsl, {
      availableNotifyChannels: [NotifyChannel.EMAIL],
    })
    expect(report.issues.map((i) => i.code)).not.toContain(IssueCode.NOTIFY_CHANNEL_NOT_ENABLED)
  })
})

describe('通知方式 · 校验（与系统配置对照）', () => {
  it('选了系统未启用的渠道 → 告警，且不阻断保存', () => {
    const report = validateDsl(withChannel(NotifyChannel.EMAIL) as FlowDsl, {
      availableNotifyChannels: [NotifyChannel.CK_PLM],
    })
    const issue = report.issues.find((i) => i.code === IssueCode.NOTIFY_CHANNEL_NOT_ENABLED)
    expect(issue?.level).toBe(IssueLevel.WARNING)
    expect(issue?.message).toContain('邮件系统')
    expect(issue?.message).toContain('CK-PLM（站内）')   // 报清楚系统当前能用什么
    expect(report.ok).toBe(true)
  })

  it('选的渠道在系统启用范围内 → 不告警', () => {
    const report = validateDsl(withChannel(NotifyChannel.CK_PLM) as FlowDsl, {
      availableNotifyChannels: [NotifyChannel.CK_PLM, NotifyChannel.EMAIL],
    })
    expect(report.issues.map((i) => i.code)).not.toContain(IssueCode.NOTIFY_CHANNEL_NOT_ENABLED)
  })

  it('拿不到系统配置（未传 availableNotifyChannels）→ 跳过该规则，不误报', () => {
    expect(codesOf(withChannel(NotifyChannel.EMAIL) as FlowDsl))
      .not.toContain(IssueCode.NOTIFY_CHANNEL_NOT_ENABLED)
  })

  it('系统一个渠道都没启用 → 告警里说明"当前可用：（无）"', () => {
    const issue = validateDsl(withChannel(NotifyChannel.EMAIL) as FlowDsl, { availableNotifyChannels: [] })
      .issues.find((i) => i.code === IssueCode.NOTIFY_CHANNEL_NOT_ENABLED)
    expect(issue?.message).toContain('（无）')
  })

  it('没选通知方式时不报"渠道未启用"（没得可校验）', () => {
    expect(codesOf(flow())).not.toContain(IssueCode.NOTIFY_CHANNEL_NOT_ENABLED)
  })
})

describe('通知方式 · 校验（节点侧）', () => {
  it('有通知节点却没选通知方式 → 告警（语义上跟随系统，但多半是漏配）', () => {
    const issue = validateDsl(flow({ notifyNode: true })).issues
      .find((i) => i.code === IssueCode.NOTIFY_NODE_WITHOUT_CHANNEL)
    expect(issue?.level).toBe(IssueLevel.WARNING)
  })

  it('没有通知节点时，不因"没选通知方式"打扰（用不上就别提）', () => {
    expect(codesOf(flow())).not.toContain(IssueCode.NOTIFY_NODE_WITHOUT_CHANNEL)
  })
})
