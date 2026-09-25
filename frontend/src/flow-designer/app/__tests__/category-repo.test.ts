import { describe, expect, it, vi } from 'vitest'
import {
  CATEGORY_NAME_MAX,
  countTemplatesByCategoryOid,
  createCategoryRepository,
  normalizeCategoryName,
  type CategoryHttp,
  type CategorySummary,
} from '../category-repo'
import type { ApiEnvelope } from '../template-repo'

const envelope = <T>(data: T, code = 200, message?: string): ApiEnvelope<T> => ({
  code,
  message,
  data,
})

/** 记录调用并返回可编排响应的假端口 */
function fakeHttp(overrides: Partial<CategoryHttp> = {}) {
  const calls: Record<string, unknown[]> = {}
  const record = (name: string, args: unknown[]) => {
    calls[name] = args
  }
  const summary: CategorySummary = { oid: 'c1', name: '研发流程', sortOrder: 10 }
  const http: CategoryHttp = {
    list: vi.fn(async () => envelope([summary])),
    create: vi.fn(async (payload) => {
      record('create', [payload])
      return envelope(summary)
    }),
    update: vi.fn(async (oid, payload) => {
      record('update', [oid, payload])
      return envelope(summary)
    }),
    remove: vi.fn(async (oid) => {
      record('remove', [oid])
      return envelope(undefined as void)
    }),
    moveTemplate: vi.fn(async (oid, category) => {
      record('moveTemplate', [oid, category])
      return envelope(undefined as void)
    }),
    ...overrides,
  }
  return { http, calls }
}

describe('分组名规范化', () => {
  it('去首尾空格后返回', () => {
    const result = normalizeCategoryName('  研发流程  ')
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.data).toBe('研发流程')
    }
  })

  it('空 / 纯空格 / 非字符串被拦下', () => {
    for (const value of ['', '   ', null, undefined, 42]) {
      const result = normalizeCategoryName(value)
      expect(result.ok, `${String(value)} 应被拦下`).toBe(false)
      if (!result.ok) {
        expect(result.error).toContain('分组名不能为空')
      }
    }
  })

  it('超长被拦下（与后端 VARCHAR(64) 对齐）', () => {
    const result = normalizeCategoryName('x'.repeat(CATEGORY_NAME_MAX + 1))
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain(String(CATEGORY_NAME_MAX))
    }
  })
})

describe('分组仓储', () => {
  it('新建：名称 trim 后提交，空说明不下发', async () => {
    const { http, calls } = fakeHttp()
    const repo = createCategoryRepository(http)
    const result = await repo.create('  研发流程  ', 10, '   ')
    expect(result.ok).toBe(true)
    const [payload] = calls.create as [Record<string, unknown>]
    expect(payload.name).toBe('研发流程')
    expect(payload.sortOrder).toBe(10)
    expect(payload.description).toBeUndefined()
  })

  it('新建：名称不合法时根本不调接口', async () => {
    const { http, calls } = fakeHttp()
    const repo = createCategoryRepository(http)
    const result = await repo.create('   ')
    expect(result.ok).toBe(false)
    expect(calls.create).toBeUndefined()
  })

  it('改名走 update，并把名称规范化后提交', async () => {
    const { http, calls } = fakeHttp()
    const repo = createCategoryRepository(http)
    await repo.rename('c1', ' 变更流程 ', 20, '变更类')
    const [oid, payload] = calls.update as [string, Record<string, unknown>]
    expect(oid).toBe('c1')
    expect(payload).toEqual({ name: '变更流程', sortOrder: 20, description: '变更类' })
  })

  it('删除的失败原样透出后端 message（不重复弹提示）', async () => {
    const { http } = fakeHttp({
      remove: vi.fn(async () => envelope(undefined as void, 409, '分组下还有 3 个流程')),
    })
    const repo = createCategoryRepository(http)
    const result = await repo.remove('c1')
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toBe('分组下还有 3 个流程')
    }
  })

  it('移动模板：透传模板 oid 与目标分组 oid', async () => {
    const { http, calls } = fakeHttp()
    const repo = createCategoryRepository(http)
    const result = await repo.moveTemplate('t1', ' cat-2 ')
    expect(result.ok).toBe(true)
    expect(calls.moveTemplate).toEqual(['t1', 'cat-2'])
  })

  it('移动模板：目标分组为空时拦下（否则模板会掉出分组）', async () => {
    const { http, calls } = fakeHttp()
    const repo = createCategoryRepository(http)
    const result = await repo.moveTemplate('t1', '')
    expect(result.ok).toBe(false)
    expect(calls.moveTemplate).toBeUndefined()
  })
})

describe('分组模板计数', () => {
  it('按分组 oid 计数，未分组记到空串', () => {
    const counts = countTemplatesByCategoryOid([
      { categoryOid: 'cat-rd' },
      { categoryOid: 'cat-rd' },
      { categoryOid: 'cat-ecr' },
      { categoryOid: null },
      {},
    ])
    expect(counts.get('cat-rd')).toBe(2)
    expect(counts.get('cat-ecr')).toBe(1)
    expect(counts.get('')).toBe(2)
  })

  it('只数不裁：字典里没有模板的分组由调用方原样保留（此处只是查不到计数）', () => {
    const counts = countTemplatesByCategoryOid([{ categoryOid: 'cat-rd' }])
    expect(counts.get('cat-empty')).toBeUndefined()
  })
})
