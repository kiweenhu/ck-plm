import { describe, expect, it, beforeEach } from 'vitest'
import {
  TTL_MS,
  clearStartFormDraft,
  draftKeyOf,
  draftSavedAtText,
  hasDraftContent,
  loadStartFormDraft,
  saveStartFormDraft,
} from '@/utils/startFormDraft'

/**
 * 发起流程草稿（2026-10-13）—— 本地存储逻辑的测试。
 *
 * <p>钉住的核心：**键不串**（不同器件 / 不同表单各存各的）、**过期不恢复**、**坏数据当没有**、
 * **提交后能清掉**。存储注入假的 Map 版 storage，避免测试之间互相污染。
 */
function fakeStorage() {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    size: () => map.size,
  }
}

const KEY_PARTS = {
  tenantOid: 't1',
  username: 'sysadmin',
  processKey: 'component-introduction',
  entityOid: 'part-1',
  formKey: 'component-qualification-apply',
}

describe('草稿键', () => {
  it('相同上下文 → 同一把键；任一维度不同 → 不同键（不串草稿）', () => {
    const base = draftKeyOf(KEY_PARTS)
    expect(draftKeyOf({ ...KEY_PARTS })).toBe(base)
    expect(draftKeyOf({ ...KEY_PARTS, entityOid: 'part-2' })).not.toBe(base)
    expect(draftKeyOf({ ...KEY_PARTS, formKey: 'other-form' })).not.toBe(base)
    expect(draftKeyOf({ ...KEY_PARTS, username: 'someone' })).not.toBe(base)
    expect(draftKeyOf({ ...KEY_PARTS, tenantOid: 't2' })).not.toBe(base)
  })

  it('缺字段不报错，且不会与完整键相同', () => {
    expect(() => draftKeyOf({})).not.toThrow()
    expect(draftKeyOf({})).not.toBe(draftKeyOf(KEY_PARTS))
  })
})

describe('保存 / 读取 / 清除', () => {
  let storage
  beforeEach(() => { storage = fakeStorage() })

  it('存进去能原样读回来（带 savedAt）', () => {
    const key = draftKeyOf(KEY_PARTS)
    expect(saveStartFormDraft(key, { usageLocationOid: 'x', certRequirements: ['CE'] }, storage, 1000)).toBe(true)
    const draft = loadStartFormDraft(key, storage, 2000)
    expect(draft.data.usageLocationOid).toBe('x')
    expect(draft.data.certRequirements).toEqual(['CE'])
    expect(draft.savedAt).toBe(1000)
  })

  it('过期（超 7 天）→ 读不到，且顺手清掉', () => {
    const key = draftKeyOf(KEY_PARTS)
    saveStartFormDraft(key, { a: 1 }, storage, 1000)
    expect(loadStartFormDraft(key, storage, 1000 + TTL_MS + 1)).toBeNull()
    // 过期时被 remove，不留残渣
    expect(storage.getItem(key)).toBeNull()
  })

  it('坏数据（非 JSON / 缺 data）→ 当作没有', () => {
    const key = draftKeyOf(KEY_PARTS)
    storage.setItem(key, '{oops')
    expect(loadStartFormDraft(key, storage)).toBeNull()
    storage.setItem(key, JSON.stringify({ savedAt: Date.now() }))
    expect(loadStartFormDraft(key, storage)).toBeNull()
  })

  it('清除后读不到；存储不可用时不抛错', () => {
    const key = draftKeyOf(KEY_PARTS)
    saveStartFormDraft(key, { a: 1 }, storage)
    clearStartFormDraft(key, storage)
    expect(loadStartFormDraft(key, storage)).toBeNull()
    expect(() => saveStartFormDraft(key, { a: 1 }, null)).not.toThrow()
    expect(loadStartFormDraft(key, null)).toBeNull()
  })
})

describe('草稿内容判断与时间文案', () => {
  it('全空对象不算有内容（避免"存了个空壳还提示已恢复"）', () => {
    expect(hasDraftContent({})).toBe(false)
    expect(hasDraftContent({ a: '', b: null, c: [] })).toBe(false)
    expect(hasDraftContent({ a: '', certRequirements: ['CE'] })).toBe(true)
    expect(hasDraftContent({ needAlternate: false })).toBe(true)
  })

  it('保存时间 → 今天 / 具体日期', () => {
    const now = new Date('2026-10-13T15:00:00').getTime()
    const today = new Date('2026-10-13T09:05:00').getTime()
    const older = new Date('2026-10-11T20:30:00').getTime()
    expect(draftSavedAtText(today, now)).toBe('今天 09:05')
    expect(draftSavedAtText(older, now)).toBe('10 月 11 日 20:30')
    expect(draftSavedAtText(0, now)).toBe('')
  })
})
