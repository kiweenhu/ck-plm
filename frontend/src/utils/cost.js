/**
 * 成本/金额的展示口径（纯函数，零 DOM）。
 *
 * <p>为什么单独拿出来：金额在页面里出现在好几处（BOM 行的单位成本、成本报告的本层/累计/合计），
 * 各写一份"保留两位 + 千分位"必然分叉 —— 那时同一笔钱在两张表里长得不一样，用户先怀疑数据。
 */

/**
 * 金额格式化：最多两位小数 + 千分位分隔。
 *
 * <p><b>空值返回「-」，0 返回「0.00」</b>：金额为空（没填）与金额为 0（真的是 0）是两件事，
 * 都写成 0 会让"成本漏填"看起来像"这项不要钱"。
 */
export function formatMoney(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const num = Number(value)
  if (Number.isNaN(num)) {
    return '-'
  }
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 成本报告的口径说明 —— 放在报告底部，让数字可被复核（口径不写在界面上的报表没人敢用） */
export const COST_METHOD_TEXT =
  '卷积口径：每一行的金额 = 数量 × 单位成本；子件的「完整单位成本」= 其自身单位成本 + 它下挂各行金额之和；'
  + '父件总成本 = 各直接子行「数量 × 子件完整单位成本」之和（用量逐层连乘，单位成本只在其所在行计一次）。'
  + '金额每层落到分（两位小数），因此明细相加与合计一致。'
