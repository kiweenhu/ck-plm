/**
 * 任务办理页「页头文案」—— 显示什么、缺字段时降级成什么。
 *
 * <p><b>背景</b>：办理页是从任务中心新窗口打开的，常同时开好几个。原先页头只有
 * 「任务名 + 流程名」，两张卡片长得几乎一样，找实体得翻下面的「通用信息」。
 * 现在页头要答出「业务实体名称 + 编码 + 大版本 ｜ 流程名 · 任务名」。
 *
 * <p>本文件钉住两件事：
 * <ol>
 *   <li>五项信息各自<b>取哪个字段</b>（名称/编码/版本/流程/任务）；</li>
 *   <li>字段缺失或回查失败时的<b>降级顺序</b> —— 宁可退成类型码，也不出现空白标题。</li>
 * </ol>
 *
 * <p>这些是「数据 → 字符串」的纯函数（放在 `utils/taskHeader`），所以能脱离组件直接断言。
 */

import { describe, expect, it } from 'vitest'
import {
  entityCodeText,
  entityHeadline,
  entityVersionText,
  flowTaskText,
} from '@/utils/taskHeader'

/** 截图里那条任务：ELECTRONIC / 20W电容 / 版本 A / 元部件引入 · 设置审批人 */
const entity = { typeCode: 'ELECTRONIC', rootTypeCode: 'PART', entityOid: 'oid-1', entityVersion: 'A' }
const info = { code: 'PART-202609-0020', name: '20W电容' }

describe('页头 · 业务实体名称', () => {
  it('有回查结果时用实体名称', () => {
    expect(entityHeadline(entity, info)).toBe('20W电容')
  })

  it('只有编码时用编码（不显示空白）', () => {
    expect(entityHeadline(entity, { code: 'PART-202609-0020', name: '' })).toBe('PART-202609-0020')
  })

  it('回查失败时退成类型码，而不是空标题', () => {
    expect(entityHeadline(entity, undefined)).toBe('ELECTRONIC')
    expect(entityHeadline({ rootTypeCode: 'PART' }, {})).toBe('PART')
  })

  it('连类型码都没有才给兜底文案', () => {
    expect(entityHeadline({}, {})).toBe('业务实体')
    expect(entityHeadline(null, null)).toBe('业务实体')
  })
})

describe('页头 · 业务实体编码', () => {
  it('回查到就显示编码', () => {
    expect(entityCodeText(info)).toBe('PART-202609-0020')
  })

  it('没有编码就返回空串，让调用方整块不渲染（不留空位）', () => {
    expect(entityCodeText({ name: '20W电容' })).toBe('')
    expect(entityCodeText(undefined)).toBe('')
    expect(entityCodeText({ code: '   ' })).toBe('')
  })
})

describe('页头 · 版本', () => {
  it('用流程关联的大版本，与「通用信息」同一口径', () => {
    expect(entityVersionText(entity)).toBe('版本 A')
  })

  it('大版本是数字也照常显示', () => {
    expect(entityVersionText({ entityVersion: 1 })).toBe('版本 1')
  })

  it('没有版本就不显示（不写"版本 -"）', () => {
    expect(entityVersionText({ typeCode: 'ELECTRONIC' })).toBe('')
    expect(entityVersionText(null)).toBe('')
  })
})

describe('页头 · 流程名 · 任务名', () => {
  it('两者都给出「流程名 · 任务名」', () => {
    expect(flowTaskText('元部件引入', '设置审批人')).toBe('元部件引入 · 设置审批人')
  })

  it('缺一个就只显示另一个，不留孤零零的分隔符', () => {
    expect(flowTaskText('元部件引入', '')).toBe('元部件引入')
    expect(flowTaskText('', '设置审批人')).toBe('设置审批人')
    expect(flowTaskText(null, '设置审批人')).toBe('设置审批人')
  })

  it('都没有才退成兜底文案', () => {
    expect(flowTaskText(undefined, undefined)).toBe('办理任务')
  })
})

describe('页头 · 一条真实任务的完整组装（截图那条）', () => {
  it('五项信息都落位', () => {
    expect({
      title: entityHeadline(entity, info),
      code: entityCodeText(info),
      version: entityVersionText(entity),
      flowTask: flowTaskText('元部件引入', '设置审批人'),
    }).toEqual({
      title: '20W电容',
      code: 'PART-202609-0020',
      version: '版本 A',
      flowTask: '元部件引入 · 设置审批人',
    })
  })
})
