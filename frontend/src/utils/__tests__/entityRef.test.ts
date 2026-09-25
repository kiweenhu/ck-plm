/**
 * 流程监控「业务标识」的链接口径。
 *
 * <p><b>背景</b>：这一列原来直接显示 {@code businessKey}（一个 oid），看不出办的是哪个对象。
 * 现在显示「名称 + 编码 + 版本 + 生命周期状态」，并链到该<b>大版本的最新版本</b>。
 *
 * <p>本文件钉住链接规则，尤其是"<b>不能</b>带上发起流程那一刻的迭代 oid"
 * —— 带上就把人锁在历史小版本上（流程还在这条大版本上继续产出 A.2 / A.3）。
 */

import { describe, expect, it } from 'vitest'
import { buildEntityLabel, entityDetailPath, entityRefFallbackText } from '@/utils/entityRef'

describe('业务实体引用 → 详情页链接', () => {
  it('零组件（宿主 PART）链到大版本详情页，不带迭代 oid —— 进去就是最新版本', () => {
    const ref = { entityOid: 'part-1', typeCode: 'ELECTRONIC', rootTypeCode: 'PART', entityVersion: 'A' }
    expect(entityDetailPath(ref)).toBe('/part/part-1')
  })

  it('软类型（如 ELECTRONIC）也算零组件：看的是宿主而不是具体类型', () => {
    expect(entityDetailPath({ entityOid: 'p2', typeCode: 'PART' })).toBe('/part/p2')
  })

  it('没有对应详情页的宿主不给链接（链到打不开的地址比没有链接更糟）', () => {
    expect(entityDetailPath({ entityOid: 'd1', typeCode: 'ENG_DOCUMENT', rootTypeCode: 'ENG_DOCUMENT' })).toBe('')
  })

  it('没有 oid 就不给链接', () => {
    expect(entityDetailPath({ rootTypeCode: 'PART' })).toBe('')
    expect(entityDetailPath(undefined)).toBe('')
    expect(entityDetailPath(null)).toBe('')
  })
})

describe('「业务标识」展示模型（列表与详情共用）', () => {
  const ref = { entityOid: 'part-1', typeCode: 'ELECTRONIC', rootTypeCode: 'PART', entityVersion: 'A' }
  const info = {
    oid: 'part-1',
    code: 'PART-202609-0020',
    name: '20W电容',
    statusName: '已发布',
    revision: 'A',
  }

  it('编码 + 名称 + 版本 + 生命周期状态，名称带详情链接', () => {
    expect(buildEntityLabel(ref, info)).toEqual({
      resolved: true,
      path: '/part/part-1',
      name: '20W电容',
      code: 'PART-202609-0020',
      version: 'A',
      status: '已发布',
      fallback: 'ELECTRONIC · part-1',
    })
  })

  it('版本以流程绑定的那个大版本为准，回查到的 revision 只作兜底', () => {
    // 回查里是 B，流程绑的是 A：显示 A —— 否则列表与办理页会出现两个版本号
    expect(buildEntityLabel(ref, { ...info, revision: 'B' })?.version).toBe('A')
    expect(buildEntityLabel({ ...ref, entityVersion: '' }, info)?.version).toBe('A')
  })

  it('没回查到就不编造：不给链接、不给名称，只给可核对的引用', () => {
    const label = buildEntityLabel(ref, null)
    expect(label?.resolved).toBe(false)
    expect(label?.path).toBe('')
    expect(label?.name).toBe('')
    expect(label?.fallback).toBe('ELECTRONIC · part-1')
  })

  it('有名称用名称，没名称回落编码（不显示空白）', () => {
    expect(buildEntityLabel(ref, { ...info, name: '' })?.name).toBe('PART-202609-0020')
  })

  it('没有实体引用时给 null（该行没有关联业务对象）', () => {
    expect(buildEntityLabel(undefined, info)).toBeNull()
    expect(buildEntityLabel({ entityOid: '' }, info)).toBeNull()
  })
})

describe('回查失败时的兜底文案', () => {
  it('给类型 + oid，至少能跟人核对', () => {
    expect(entityRefFallbackText({ entityOid: 'part-1', typeCode: 'ELECTRONIC' })).toBe('ELECTRONIC · part-1')
  })

  it('没有类型就只给 oid；没有 oid 给空串（调用方另作处理）', () => {
    expect(entityRefFallbackText({ entityOid: 'part-1' })).toBe('part-1')
    expect(entityRefFallbackText({ typeCode: 'ELECTRONIC' })).toBe('')
  })
})
