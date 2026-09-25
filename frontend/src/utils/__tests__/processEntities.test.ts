/**
 * 「发起流程」多业务对象的整理规则。
 *
 * <p>钉住三件事：载荷按清单顺序给（后端取第一条填 businessObjectSet.primary，
 * 但前端不给谁贴"主/从"标签）、重复对象只留一份、候选只给同类型且未选中的。
 */

import { describe, expect, it } from 'vitest'
import {
  entityCandidateRejects,
  entityCandidates,
  entityLine,
  entitySubject,
  runningBlockReason,
  sameProcessingBucket,
  sameStatus,
  startedNotice,
  startEntityPayload,
  statusKeyOf,
} from '@/utils/processEntities'

/**
 * 正在流程中的对象不可选。
 *
 * <p>判定结果由后端给（关联表 + 引擎运行时，大版本粒度），这里只钉住"前端怎么用它"：
 * 命中就给理由，两处调用（候选置灰、添加复检）拿的是同一句话 —— 各写一遍必然漂移，
 * 结果就是"灰着却能加进来"。
 */
describe('正在流程中的对象不可选', () => {
  it('命中在流程中的 oid → 给出置灰理由', () => {
    expect(runningBlockReason(primary, new Set(['part-1']))).toContain('已在流程中')
    expect(runningBlockReason(primary, new Set(['part-2']))).toBeNull()
  })

  it('没有结果 / 没传 / 对象没有 oid 时都不拦（宁可不拦，也别误拦）', () => {
    expect(runningBlockReason(primary, new Set())).toBeNull()
    expect(runningBlockReason(primary, null)).toBeNull()
    expect(runningBlockReason({}, new Set(['part-1']))).toBeNull()
  })

  it('也接受数组形态（接口返回的就是字符串数组）', () => {
    expect(runningBlockReason(primary, ['part-1'])).toContain('已在流程中')
    expect(runningBlockReason(primary, ['part-2'])).toBeNull()
  })
})

/** 弹框里的主对象（来自列表行） */
const primary = {
  oid: 'part-1',
  code: 'PART-0001',
  name: '20W电容',
  typeDefinitionCode: 'ELECTRONIC',
  statusCode: 'DRAFT',
}

/** 后加进来的关联对象 */
const extra = {
  oid: 'part-2',
  code: 'PART-0002',
  name: '10W电容',
  typeDefinitionCode: 'ELECTRONIC',
}

describe('发起载荷：照清单顺序 + 去重 + 字段改名', () => {
  it('按清单顺序给（第一条即后端 primary），一条不多一条不少', () => {
    expect(startEntityPayload([primary, extra])).toEqual([
      { entityOid: 'part-1', typeCode: 'ELECTRONIC', entityCode: 'PART-0001' },
      { entityOid: 'part-2', typeCode: 'ELECTRONIC', entityCode: 'PART-0002' },
    ])
  })

  it('只有一个对象时是单元素数组（与老的单对象发起等价）', () => {
    expect(startEntityPayload([primary])).toHaveLength(1)
    expect(startEntityPayload([primary])[0].entityOid).toBe('part-1')
  })

  it('空清单 → 空数组（不关联任何对象）', () => {
    expect(startEntityPayload([])).toEqual([])
    expect(startEntityPayload()).toEqual([])
  })

  it('重复 oid 只留第一次（手滑加第二遍不该让后端记第二行关联）', () => {
    const payload = startEntityPayload([primary, { ...extra }, primary, { ...extra, name: '再来一次' }])
    expect(payload.map((e: { entityOid: string }) => e.entityOid)).toEqual(['part-1', 'part-2'])
  })

  it('没有 oid 的项丢掉；缺类型 / 编码时不传该字段（不猜）', () => {
    expect(startEntityPayload([null, { oid: 'part-9' }, { code: 'X' }])).toEqual([
      { entityOid: 'part-9', typeCode: undefined, entityCode: undefined },
    ])
  })

  it('认后端形态的字段名（entityOid / typeCode / entityCode）', () => {
    expect(startEntityPayload([{ entityOid: 'p-1', typeCode: 'ELECTRONIC', entityCode: 'C-1' }]))
      .toEqual([{ entityOid: 'p-1', typeCode: 'ELECTRONIC', entityCode: 'C-1' }])
  })
})

describe('候选对象：同类型 + 未选中', () => {
  const results = [
    { oid: 'part-2', code: 'PART-0002', name: '10W电容', typeDefinitionCode: 'ELECTRONIC' },
    { oid: 'part-3', code: 'PART-0003', name: '支架', typeDefinitionCode: 'STRUCTURAL' },
    { oid: 'part-1', code: 'PART-0001', name: '20W电容', typeDefinitionCode: 'ELECTRONIC' },
    { oid: '', code: 'BAD', name: '无 oid', typeDefinitionCode: 'ELECTRONIC' },
  ]

  it('只给同类型的，且排除已选中的', () => {
    const candidates = entityCandidates(results, {
      typeCode: 'ELECTRONIC',
      excludeOids: ['part-1'],
    })
    expect(candidates.map((c: { oid: string }) => c.oid)).toEqual(['part-2'])
  })

  it('没有类型信息时不过滤类型（老数据 / 无类型对象也能选）', () => {
    const candidates = entityCandidates(results, { typeCode: undefined, excludeOids: ['part-1'] })
    expect(candidates.map((c: { oid: string }) => c.oid)).toEqual(['part-2', 'part-3'])
  })

  it('空结果 / 缺字段不抛错', () => {
    expect(entityCandidates(undefined, { typeCode: 'ELECTRONIC' })).toEqual([])
    expect(entityCandidates([null], { typeCode: undefined })).toEqual([])
  })
})

describe('行内文案', () => {
  it('编码 + 名称；只有其一时只给那个', () => {
    expect(entityLine({ code: 'PART-0001', name: '20W电容' })).toBe('PART-0001 20W电容')
    expect(entityLine({ code: 'PART-0001' })).toBe('PART-0001')
    expect(entityLine({ name: '20W电容' })).toBe('20W电容')
  })

  it('都没有时给占位（空白行会让人以为没加载出来）', () => {
    expect(entityLine({})).toBe('（未知对象）')
    expect(entityLine(null)).toBe('（未知对象）')
  })
})

describe('发起成功的提示语', () => {
  it('单个对象：名称（编码）+ 流程名，并指明下一步', () => {
    expect(startedNotice({ entities: [primary], processName: '元部件引入' }))
      .toBe('20W电容（PART-0001）的「元部件引入」已启动，请到任务中心跟进。')
  })

  it('多个对象：报第一条 + 总数（整批对象在办理页看得全，不必都塞进提示）', () => {
    expect(startedNotice({ entities: [primary, extra], processName: '元部件引入' }))
      .toBe('20W电容（PART-0001）等 2 个对象的「元部件引入」已启动，请到任务中心跟进。')
  })

  it('主语是"名称（编码）"，与清单行的"编码 名称"顺序相反', () => {
    expect(entitySubject(primary)).toBe('20W电容（PART-0001）')
    expect(entityLine(primary)).toBe('PART-0001 20W电容')
  })

  it('缺名称或编码时只给有的那个；都没有给"业务对象"而不是空白', () => {
    expect(entitySubject({ code: 'PART-0001' })).toBe('PART-0001')
    expect(entitySubject({ name: '20W电容' })).toBe('20W电容')
    expect(entitySubject({})).toBe('业务对象')
    expect(entitySubject(null)).toBe('业务对象')
  })

  it('清单为空时退回发起上下文那个对象；流程名缺失时给"流程"占位', () => {
    expect(startedNotice({ entities: [], business: primary, processName: '元部件引入' }))
      .toBe('20W电容（PART-0001）的「元部件引入」已启动，请到任务中心跟进。')
    expect(startedNotice({ entities: [primary] }))
      .toBe('20W电容（PART-0001）的「流程」已启动，请到任务中心跟进。')
    expect(startedNotice()).toBe('业务对象的「流程」已启动，请到任务中心跟进。')
  })

  it('提示里不出现实例号：那是面向管理员的信息，不占用户要读的正文', () => {
    expect(startedNotice({ entities: [primary], processName: '元部件引入' }))
      .not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}/)
  })
})

/**
 * 谁能进同一批：**同类型 + 同生命周期状态**。
 *
 * <p>一批对象是一起走同一道审批的：类型不同，流程变量与审批意见对不上；
 * 状态不同（草稿 vs 已发布一起进），"这次审批在推进谁的状态"本身就是笔糊涂账。
 */
describe('同一批对象的准入规则', () => {
  const draftElec = {
    oid: 'e1', code: 'E1', name: '电容A',
    typeDefinitionCode: 'ELECTRONIC', typeDefinitionName: '电子元器件',
    statusCode: 'DRAFT', statusName: '草稿',
  }
  // 状态 code 大小写与口径不完全一致（不同接口给的可能是 draft / DRAFT）
  const draftElec2 = {
    oid: 'e2', code: 'E2', name: '电容B',
    typeDefinitionCode: 'ELECTRONIC', statusCode: 'draft',
  }
  const issuedElec = {
    oid: 'e3', code: 'E3', name: '电容C',
    typeDefinitionCode: 'ELECTRONIC', statusCode: 'ISSUED',
  }
  const draftStruct = {
    oid: 's1', code: 'S1', name: '结构件',
    typeDefinitionCode: 'STRUCTURAL', statusCode: 'DRAFT',
  }

  it('候选只留同类型 + 同状态（状态忽略大小写），并带出类型/状态供清单显示', () => {
    const list = entityCandidates([draftElec2, issuedElec, draftStruct], {
      typeCode: 'ELECTRONIC',
      statusCode: 'DRAFT',
    })
    expect(list.map((c) => c.oid)).toEqual(['e2'])
    // 字段要原样带出来：清单里的「类型 / 状态」标签就靠它们，缺了会是一片空白
    expect(list[0].typeDefinitionCode).toBe('ELECTRONIC')
    expect(list[0].statusCode).toBe('draft')
  })

  it('已选中的不再出现在候选里', () => {
    const list = entityCandidates([draftElec, draftElec2], {
      typeCode: 'ELECTRONIC',
      statusCode: 'DRAFT',
      excludeOids: ['e1'],
    })
    expect(list.map((c) => c.oid)).toEqual(['e2'])
  })

  it('只按类型过滤时（老口径）不额外卡状态', () => {
    const list = entityCandidates([draftElec, issuedElec, draftStruct], { typeCode: 'ELECTRONIC' })
    expect(list.map((c) => c.oid)).toEqual(['e1', 'e3'])
  })

  it('被状态挡下的同类型对象单独返回：界面要能解释"搜到了却没列出来"', () => {
    const rejected = entityCandidateRejects([draftElec2, issuedElec, draftStruct], {
      typeCode: 'ELECTRONIC',
      statusCode: 'DRAFT',
    })
    expect(rejected.map((r) => r.oid)).toEqual(['e3'])
  })

  it('搜索源没有状态字段时只按类型筛：同类型一定要看得见（否则下拉会空掉）', () => {
    // 全局搜索的真实返回形状：{ type, oid, code, name, link, typeDefinitionCode } —— 没有状态
    const searchRows = [
      { type: 'PART', oid: 'p1', code: 'PART-1', name: '甲', typeDefinitionCode: 'ELECTRONIC' },
      { type: 'PART', oid: 'p2', code: 'PART-2', name: '乙', typeDefinitionCode: 'ELECTRONIC' },
      { type: 'DOCUMENT', oid: 'd1', code: 'DOC-1', name: '丙', typeDefinitionCode: 'DOC' },
    ]
    expect(entityCandidates(searchRows, { typeCode: 'ELECTRONIC' }).map((c) => c.oid))
      .toEqual(['p1', 'p2'])
    // 反过来：明知源里没有状态还传 statusCode，就会把全部候选滤掉 ——
    // 这正是"输入 PART- 却什么都不显示"的成因，调用方不要这么传（状态在添加时单独核）
    expect(entityCandidates(searchRows, { typeCode: 'ELECTRONIC', statusCode: 'DRAFT' })).toEqual([])
  })

  it('只有状态显示名（列表行常见）也要能判"同一状态"：工作中 vs 草稿 必须拦下', () => {
    // 企业资源/元器件库的列表行就是这样：有 statusName，没有 statusCode
    const draft = { oid: 'p1', code: 'PART-1', typeDefinitionCode: 'ELECTRONIC', statusName: '草稿' }
    const working = { oid: 'p2', code: 'PART-2', typeDefinitionCode: 'ELECTRONIC', statusName: '工作中' }
    // 回归：只认 statusCode 时这里会返回 ok（校验被整段跳过），「工作中」因此能混进「草稿」批次
    const bad = sameProcessingBucket([draft, working])
    expect(bad.ok).toBe(false)
    expect(bad.reason).toContain('PART-2')
    expect(sameProcessingBucket([draft, { ...working, statusName: '草稿' }]).ok).toBe(true)
  })

  it('状态比较口径成对：code 对 code、名对名；一侧 code 一侧名不误判', () => {
    // 同一种状态用不同字段表达 → 视为相同
    expect(sameStatus({ statusCode: 'DRAFT' }, { statusCode: 'draft ' })).toEqual({ same: true, known: true })
    expect(sameStatus({ statusName: '草稿' }, { status: { displayName: '草稿' } }))
      .toEqual({ same: true, known: true })
    expect(sameStatus({ statusName: '工作中' }, { statusName: '草稿' }))
      .toEqual({ same: false, known: true })
    // 一侧只有 code、另一侧只有显示名：拿不到同口径信息 → 不拦（known=false）
    expect(sameStatus({ statusCode: 'DRAFT' }, { statusName: '工作中' })).toEqual({ same: true, known: false })
    // statusKeyOf：code 优先，缺了退回显示名
    expect(statusKeyOf({ statusCode: 'DRAFT', statusName: '草稿' })).toBe('DRAFT')
    expect(statusKeyOf({ statusName: '工作中' })).toBe('工作中')
    expect(statusKeyOf({ status: 'IN_WORK' })).toBe('IN_WORK')
  })

  it('提交前复检：类型不同 / 状态不同都拦下并给出可读原因', () => {
    expect(sameProcessingBucket([draftElec, draftElec2]).ok).toBe(true)
    // 单条永远放行（没有可比的对象）
    expect(sameProcessingBucket([draftElec]).ok).toBe(true)
    expect(sameProcessingBucket([]).ok).toBe(true)

    const mixedType = sameProcessingBucket([draftElec, draftStruct])
    expect(mixedType.ok).toBe(false)
    expect(mixedType.reason).toContain('同类型')

    const mixedStatus = sameProcessingBucket([draftElec, issuedElec])
    expect(mixedStatus.ok).toBe(false)
    expect(mixedStatus.reason).toContain('生命周期状态')
  })
})
