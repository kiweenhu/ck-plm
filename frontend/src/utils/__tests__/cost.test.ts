/**
 * 金额展示口径。
 *
 * <p>钉住一条容易搞错的区分：**空值不是 0**。金额没填显示「-」，真的是 0 显示「0.00」
 * —— 两者都写成 0，会把"成本漏填"伪装成"这项不要钱"，而成本报告正是据此汇总的。
 */

import { describe, expect, it } from 'vitest'
import { COST_METHOD_TEXT, formatMoney } from '../cost'

describe('金额格式化', () => {
  it('保留两位小数并加千分位', () => {
    expect(formatMoney(1234.5)).toBe('1,234.50')
    expect(formatMoney(0.99)).toBe('0.99')
    expect(formatMoney(1234567.891)).toBe('1,234,567.89')
  })

  it('0 显示为 0.00，空值显示为「-」（空 ≠ 0）', () => {
    expect(formatMoney(0)).toBe('0.00')
    expect(formatMoney(null)).toBe('-')
    expect(formatMoney(undefined)).toBe('-')
    expect(formatMoney('')).toBe('-')
    expect(formatMoney('abc')).toBe('-')
  })

  it('字符串数字照样格式化（后端 JSON 里可能是字符串）', () => {
    expect(formatMoney('12.3')).toBe('12.30')
  })

  it('口径说明覆盖三个要点：逐层连乘、单位成本只计一次、每层落分', () => {
    expect(COST_METHOD_TEXT).toContain('数量 × 单位成本')
    expect(COST_METHOD_TEXT).toContain('完整单位成本')
    expect(COST_METHOD_TEXT).toContain('落到分')
  })
})
