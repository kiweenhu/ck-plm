import { describe, expect, it } from 'vitest'
import { ValueType, type VariableDef } from '@flow-dsl-core'
import {
  applyVariableDraft,
  variableNameError,
  variableSummary,
} from '../variable-list'

const variableOf = (patch: Partial<VariableDef> = {}): VariableDef => ({
  name: 'ecrNo',
  type: ValueType.STRING,
  ...patch,
})

describe('变量行摘要', () => {
  it('只声明了名字 → 明确说没设别的（而不是留一片空白）', () => {
    expect(variableSummary(variableOf())).toBe('未设置显示名 / 默认值')
  })

  it('按 显示名 → 默认值 → 标志位 的顺序列出已设项', () => {
    expect(variableSummary(variableOf({
      label: '变更单号',
      defaultValue: 'ECR-001',
      visible: true,
      readonly: true,
      writable: true,
    }))).toBe('显示名 变更单号 · 默认值 ECR-001 · 任务页可见 · 只读 · 允许任务中重写')
  })

  it('默认值取 0 是有效取值，不能当成"没设"', () => {
    expect(variableSummary(variableOf({ defaultValue: 0 }))).toContain('默认值 0')
  })

  it('默认值为空串 / 未设 → 落到"没设别的"那句；设了值才显示', () => {
    expect(variableSummary(variableOf({ defaultValue: '' }))).toBe('未设置显示名 / 默认值')
    expect(variableSummary(variableOf())).toBe('未设置显示名 / 默认值')
    // 对照：设了值就只显示"默认值 xxx"，不再出现"未设置"那句
    expect(variableSummary(variableOf({ defaultValue: 'ECR-001' }))).toBe('默认值 ECR-001')
  })
})

describe('变量名校验', () => {
  const list = [variableOf({ name: 'ecrNo' }), variableOf({ name: 'approver' })]

  it('空 / 只有空格 → 必填提示', () => {
    expect(variableNameError('', list)).toBe('请填写变量名')
    expect(variableNameError('   ', list)).toBe('请填写变量名')
  })

  it('与其它变量重名 → 拒绝', () => {
    expect(variableNameError('approver', list)).toBe('变量名「approver」已存在')
    // 校验前先去空格：' approver ' 与 'approver' 是同一个名字
    expect(variableNameError(' approver ', list)).toBe('变量名「approver」已存在')
  })

  it('编辑自己不算重名（改名不改动名字时不该被拦）', () => {
    expect(variableNameError('ecrNo', list, 0)).toBeNull()
    expect(variableNameError('ecrNo', list, 1)).toBe('变量名「ecrNo」已存在')
  })

  it('新名字 → 通过', () => {
    expect(variableNameError('lifecycle', list)).toBeNull()
  })

  it('与内置变量同名不拦（那是"配了不生效"，由 DSL 校验给警告）', () => {
    expect(variableNameError('approved', list)).toBeNull()
  })
})

describe('应用草稿', () => {
  it('新增：追加到末尾，名字去空格，不改动原数组', () => {
    const list = [variableOf()]
    const next = applyVariableDraft(list, variableOf({ name: '  ecrNo2  ' }), -1)
    expect(next.map((v) => v.name)).toEqual(['ecrNo', 'ecrNo2'])
    expect(list).toHaveLength(1)
  })

  it('编辑：替换指定下标', () => {
    const list = [variableOf(), variableOf({ name: 'approver' })]
    const next = applyVariableDraft(list, variableOf({ name: 'approver', label: '审批人' }), 1)
    expect(next[1]).toEqual({ name: 'approver', type: ValueType.STRING, label: '审批人' })
    expect(next[0].name).toBe('ecrNo')
  })
})
