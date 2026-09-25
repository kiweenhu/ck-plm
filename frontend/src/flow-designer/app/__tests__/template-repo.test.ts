import { describe, expect, it, vi } from 'vitest'
import {
  ApprovalMode,
  AssigneeStrategy,
  NodeType,
  addNode,
  createEmptyDsl,
  validateDsl,
  type FlowDsl,
} from '@flow-dsl-core'
import {
  createTemplateRepository,
  dslFromJson,
  dslSignature,
  dslToJson,
  ensureSavable,
  extractProcessId,
  isVersionDeletable,
  splitDeletableVersions,
  type ApiEnvelope,
  type TemplateHttp,
  type TemplateSummary,
} from '../template-repo'

/** 空白模板（START → END 已连线）；meta.category 只是可读的名称快照（归属以 oid 为准） */
function blank(key = 'demo_flow'): FlowDsl {
  return createEmptyDsl({ key, name: '示例流程', category: '研发流程' })
}

/** 构造一个必然校验失败的 DSL：会签未配通过规则 */
function broken(key = 'demo_flow'): FlowDsl {
  const withApproval = addNode(
    blank(key),
    {
      id: 'a1',
      type: NodeType.APPROVAL,
      name: '会签',
      assignee: { strategy: AssigneeStrategy.ROLE, roleCodes: ['ENG'] },
      approvalMode: ApprovalMode.COUNTERSIGN,
    } as never,
    { x: 0, y: 0 },
  )
  return withApproval
}

const envelope = <T>(data: T, code = 200, message?: string): ApiEnvelope<T> => ({ code, message, data })

/** 记录调用并返回可编排响应的假端口 */
function fakeHttp(overrides: Partial<TemplateHttp> = {}) {
  const calls: Record<string, unknown[]> = {}
  const record = (name: string, args: unknown[]) => {
    calls[name] = args
  }
  const summary: TemplateSummary = { oid: 't1', key: 'demo_flow', name: '示例流程', latestVersion: 2, enabled: true }
  const http: TemplateHttp = {
    list: vi.fn(async () => envelope([summary])),
    get: vi.fn(async () => envelope({ ...summary, dslJson: dslToJson(blank()) })),
    create: vi.fn(async (payload) => {
      record('create', [payload])
      return envelope(summary)
    }),
    save: vi.fn(async (oid, payload) => {
      record('save', [oid, payload])
      return envelope(summary)
    }),
    removeVersions: vi.fn(async (oid, versions) => {
      record('removeVersions', [oid, versions])
      return envelope({ templateOid: oid, deleted: versions, templateRemoved: false, latestVersion: 1 })
    }),
    copy: vi.fn(async () => envelope(summary)),
    setEnabled: vi.fn(async () => envelope(summary)),
    versions: vi.fn(async () => envelope([{ oid: 'v1', version: 1, deployed: true }])),
    version: vi.fn(async () => envelope({ oid: 'v1', version: 1, dslJson: dslToJson(blank()) })),
    deploy: vi.fn(async (oid, payload) => {
      record('deploy', [oid, payload])
      return envelope({ deploymentId: 'dep-1', processDefinitionId: 'demo_flow:1:999', version: 2 })
    }),
    ...overrides,
  }
  return { http, calls }
}

describe('DSL 序列化往返', () => {
  it('空白模板本身即可保存（基线不变式）', () => {
    expect(validateDsl(blank()).ok).toBe(true)
  })

  it('dslToJson → dslFromJson 无损', () => {
    const dsl = blank()
    const parsed = dslFromJson(dslToJson(dsl))
    expect(parsed.ok).toBe(true)
    if (parsed.ok) {
      expect(parsed.data.meta.key).toBe('demo_flow')
      expect(parsed.data.nodes).toHaveLength(dsl.nodes.length)
    }
  })

  it('空 / 非法 JSON / 结构非法都能给出可读原因', () => {
    expect(dslFromJson(null).ok).toBe(false)
    expect(dslFromJson('').ok).toBe(false)
    const bad = dslFromJson('{ 不是 json')
    expect(bad.ok).toBe(false)
    if (!bad.ok) {
      expect(bad.error).toContain('不是合法 JSON')
    }
    const wrongShape = dslFromJson(JSON.stringify({ meta: { key: 'x' } }))
    expect(wrongShape.ok).toBe(false)
  })

  it('dslSignature 能识别改动', () => {
    const a = blank()
    const b = blank()
    expect(dslSignature(a)).toBe(dslSignature(b))
    expect(dslSignature(addNode(a, { id: 'x', type: NodeType.END, name: '另一个结束' } as never, { x: 0, y: 0 }))).not.toBe(
      dslSignature(a),
    )
  })
})

describe('保存拦截（spec §4-I P0）', () => {
  it('校验有 ERROR 时拒绝保存，且不发起请求', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.save('t1', broken(), '改点什么', 'demo_flow')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain('校验未通过')
    }
    // 关键：拦截发生在客户端，压根不该打到后端
    expect(calls.save).toBeUndefined()
  })

  it('校验有 ERROR 时拒绝部署，且不发起请求', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.deploy('t1', broken(), 2)
    expect(result.ok).toBe(false)
    expect(calls.deploy).toBeUndefined()
  })

  it('新建同样受拦截', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.create(broken(), 'cat-1')
    expect(result.ok).toBe(false)
    expect(calls.create).toBeUndefined()
  })

  it('ensureSavable 报出第一条错误并带上节点位置', () => {
    const result = ensureSavable(broken())
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toMatch(/节点 a1/)
    }
  })
})

describe('流程 key 不可变', () => {
  it('DSL 的 meta.key 与模板 key 不一致时拒绝保存，并指引「复制/另存」', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.save('t1', blank('另一个key'), undefined, 'demo_flow')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain('复制/另存')
    }
    expect(calls.save).toBeUndefined()
  })

  it('未传 currentKey 时不做该项校验（向后兼容）', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.save('t1', blank('任意key'))
    expect(result.ok).toBe(true)
    expect(calls.save).toBeDefined()
  })
})

describe('保存与新建的正向路径', () => {
  it('保存透传 dslJson 与变更说明', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.save('t1', blank(), '调整审批人', 'demo_flow')
    expect(result.ok).toBe(true)
    const [oid, payload] = calls.save as [string, { dslJson: string; changeNote?: string }]
    expect(oid).toBe('t1')
    expect(payload.changeNote).toBe('调整审批人')
    expect(dslFromJson(payload.dslJson).ok).toBe(true)
  })

  it('新建时 key/name 取自 DSL、分组取自入参 oid（保证模板列与 DSL.meta 一致）', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.create(blank('part_release'), 'cat-1')
    expect(result.ok).toBe(true)
    const [payload] = calls.create as [Record<string, unknown>]
    expect(payload.key).toBe('part_release')
    expect(payload.name).toBe('示例流程')
    // 分组是数据库侧的引用（oid），不写进 DSL —— DSL 里的 meta.category 只是可读快照
    expect(payload.categoryOid).toBe('cat-1')
    expect(typeof payload.dslJson).toBe('string')
  })

  it('新建时没有分组被拦下（清单页的用法是「先建分组 → 选分组 → 组内设计流程」）', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.create(createEmptyDsl({ key: 'no_category', name: '无分组流程' }), '')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain('请先选择分组')
    }
    expect(calls.create).toBeUndefined()
  })
})

describe('按版本删除（流程删除的唯一方式）', () => {
  it('版本可否删除：只有未部署的可删', () => {
    expect(isVersionDeletable({ deployed: false })).toBe(true)
    expect(isVersionDeletable({})).toBe(true)
    expect(isVersionDeletable(null)).toBe(true)
    expect(isVersionDeletable({ deployed: true })).toBe(false)
  })

  it('拆分可删 / 已锁定，弹窗据此拿到「可选数、锁定数、是否整体消失」', () => {
    const rows = [
      { version: 3, deployed: false },
      { version: 2, deployed: true },
      { version: 1, deployed: false },
    ]
    const { deletable, locked } = splitDeletableVersions(rows)
    expect(deletable.map((v) => v.version)).toEqual([3, 1])
    expect(locked.map((v) => v.version)).toEqual([2])
    // 存在已锁定版本 → 全选可删版本不会把流程删掉
    expect(deletable.length === rows.length).toBe(false)
    // 全部未部署 → 全选即"流程一并消失"，弹窗要给出警告
    expect(splitDeletableVersions([{ version: 1, deployed: false }]).deletable.length).toBe(1)
  })

  it('删除版本透传 oid 与版本号', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.removeVersions('t1', [3, 2])
    expect(result.ok).toBe(true)
    expect(calls.removeVersions).toEqual(['t1', [3, 2]])
  })

  it('空选不发请求（后端同样会拒）', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.removeVersions('t1', [])
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain('请选择要删除的版本')
    }
    expect(calls.removeVersions).toBeUndefined()
  })
})

describe('部署', () => {
  it('提交 BPMN XML 并带上已保存版本号', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.deploy('t1', blank(), 2)
    expect(result.ok).toBe(true)
    const [oid, payload] = calls.deploy as [string, { version?: number; bpmnXml: string }]
    expect(oid).toBe('t1')
    expect(payload.version).toBe(2)
    expect(extractProcessId(payload.bpmnXml)).toBe('demo_flow')
    if (result.ok) {
      expect(result.data.outcome.deploymentId).toBe('dep-1')
      expect(Array.isArray(result.data.warnings)).toBe(true)
    }
  })

  it('未提供版本号时不带 version（由后端取最新版）', async () => {
    const { http, calls } = fakeHttp()
    const repo = createTemplateRepository(http)
    await repo.deploy('t1', blank(), null)
    const [, payload] = calls.deploy as [string, { version?: number }]
    expect(payload.version).toBeUndefined()
  })

  it('部署失败时把后端原因原样回传（不吞错）', async () => {
    const { http } = fakeHttp({
      deploy: async () => envelope(undefined as never, 409, '该流程已「停止部署」，不能发布新版本'),
    })
    const repo = createTemplateRepository(http)
    const result = await repo.deploy('t1', blank(), 1)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toBe('该流程已「停止部署」，不能发布新版本')
    }
  })
})

describe('extractProcessId', () => {
  it('能取出无前缀与带命名空间前缀两种写法', () => {
    expect(extractProcessId('<bpmn:process id="abc" isExecutable="true">')).toBe('abc')
    expect(extractProcessId('<process id="xyz">')).toBe('xyz')
    expect(extractProcessId('<process name="nope">')).toBeNull()
  })
})

describe('加载与响应归一', () => {
  it('载荷解析失败时给出可读错误而非抛异常', async () => {
    const { http } = fakeHttp({ get: async () => envelope({ oid: 't1', key: 'k', name: 'n', dslJson: '{坏' }) })
    const repo = createTemplateRepository(http)
    const result = await repo.load('t1')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain('不是合法 JSON')
    }
  })

  it('加载成功时同时给出 DSL 与校验报告', async () => {
    const { http } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.load('t1')
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data.template.key).toBe('demo_flow')
      expect(result.data.report.ok).toBe(true)
    }
  })

  it('业务错误（code≠200）归一为 ok:false 且不抛异常', async () => {
    const { http } = fakeHttp({ list: async () => envelope([] as TemplateSummary[], 403, '无权访问') })
    const repo = createTemplateRepository(http)
    const result = await repo.list()
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toBe('无权访问')
    }
  })

  it('后端无响应时不崩', async () => {
    const { http } = fakeHttp({ list: async () => undefined as never })
    const repo = createTemplateRepository(http)
    const result = await repo.list()
    expect(result.ok).toBe(false)
  })

  it('版本快照可解析回 DSL', async () => {
    const { http } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = await repo.loadVersion('t1', 1)
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data.version.version).toBe(1)
      expect(result.data.dsl.meta.key).toBe('demo_flow')
    }
  })

  it('编译预览与部署走同一编译入口', () => {
    const { http } = fakeHttp()
    const repo = createTemplateRepository(http)
    const result = repo.compile(blank())
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data.processId).toBe('demo_flow')
      expect(result.data.xml).toContain('<process')
    }
  })
})
