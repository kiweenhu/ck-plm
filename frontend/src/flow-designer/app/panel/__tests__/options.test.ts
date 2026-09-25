import { describe, expect, it } from 'vitest'
import {
  lifecycleStateOption,
  lifecycleStateOptions,
  mergeServiceOptions,
} from '../options'

/**
 * 服务候选 = 前端声明 + 后端已注册。
 *
 * <p>两份清单的分工：前端说"这个服务叫什么、要填哪些参数"（面板靠它渲染控件），
 * 后端说"这个服务真的实现了"。合并规则错一边的后果都很具体：
 * 覆盖掉声明 → 参数控件退化成手输；丢掉后端那份 → 新部署的函数在设计器里选不到。
 */
describe('服务候选合并（前端声明 + 后端已注册）', () => {
  const declared = [
    { value: 'object.checkout', label: '检出对象' },
    { value: 'integration.publishToSystem', label: '系统集成' },
  ]

  it('以后端补漏：声明的顺序与显示名不动，多出来的追加在后面', () => {
    const merged = mergeServiceOptions(declared, [
      { value: 'custom.sendMail', label: '发送邮件' },
      { value: 'object.checkout', label: '检出对象（后端）' },
    ])
    expect(merged.map((o) => o.value)).toEqual([
      'object.checkout',
      'integration.publishToSystem',
      'custom.sendMail',
    ])
    // 显示名以声明为准（后端那份只说明"有这么个 id"）
    expect(merged.find((o) => o.value === 'object.checkout')!.label).toBe('检出对象')
  })

  it('后端清单为空（接口失败降级）时原样返回声明，不阻断设计', () => {
    expect(mergeServiceOptions(declared, [])).toEqual(declared)
  })
})

/**
 * 生命周期状态下拉的取值/显示名映射。
 *
 * <p>样本就是后端真实返回的形态（`ck_lifecycle_template_state` 的状态引用）：
 * `{ oid, iterationOid, statusCode, statusDisplayName, sortOrder }` —— 注意它<b>没有</b>
 * `code` / `name` 这两个通用字段名，这正是当初下拉里整片 UUID 的原因。
 */
const stateRef = (code: string, display: string, oid = 'f15267b8-de28-44b0-8d41-a7dacedbed45') => ({
  oid,
  iterationOid: '4729c352-7f21-47bb-b108-729d9f51a15a',
  statusCode: code,
  statusDisplayName: display,
  sortOrder: 1,
})

describe('生命周期状态选项', () => {
  it('取值 = 状态 code、显示名 = 状态名（不是 oid）', () => {
    expect(lifecycleStateOption(stateRef('RELEASED', '已发布'))).toEqual({
      value: 'RELEASED',
      label: '已发布',
    })
  })

  it('认不出状态 code 时宁可丢弃，也不拿 oid 顶上（设计者看不懂 oid）', () => {
    expect(lifecycleStateOption({ oid: 'abc-123' })).toBeNull()
    expect(lifecycleStateOption(null)).toBeNull()
    expect(lifecycleStateOption('RELEASED')).toBeNull()
  })

  it('缺状态名时退回 code（至少还能认出是哪个状态）', () => {
    expect(lifecycleStateOption({ statusCode: 'DRAFT' })).toEqual({ value: 'DRAFT', label: 'DRAFT' })
  })

  it('兼容通用字段名（code / name）', () => {
    expect(lifecycleStateOption({ code: 'IN_WORK', name: '工作中' })).toEqual({
      value: 'IN_WORK',
      label: '工作中',
    })
  })
})

describe('生命周期模板 → 状态选项', () => {
  const template = (...refs: ReturnType<typeof stateRef>[]) => ({ oid: 'tpl-1', states: refs })

  it('多个模板共用同一批状态时按 code 去重，保持首次出现顺序', () => {
    const options = lifecycleStateOptions([
      template(stateRef('DRAFT', '草稿'), stateRef('IN_WORK', '工作中'), stateRef('RELEASED', '已发布')),
      template(stateRef('DRAFT', '草稿', 'other-oid'), stateRef('RELEASED', '已发布', 'other-oid')),
    ])
    expect(options.map((o) => o.value)).toEqual(['DRAFT', 'IN_WORK', 'RELEASED'])
  })

  it('没有状态 / 结构异常时不抛错，返回已认出的部分', () => {
    expect(lifecycleStateOptions([{ oid: 'tpl-1' }, null, 'x'])).toEqual([])
    expect(lifecycleStateOptions([template(stateRef('DRAFT', '草稿'))])).toHaveLength(1)
  })
})
