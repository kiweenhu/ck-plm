/**
 * 驾驶舱分组件码 → 中文名。
 *
 * <p>为什么要单独一个文件：这份字典原来在 {@code MethodologyInstancePanel}（列表的"分组件"列）
 * 与 {@code DashboardTemplateConfig}（模板编辑）里各存一份，2026-10-26 整机视图的页签也要用 ——
 * 三处各抄一遍必然漂移（改个名字只改一半）。这里是唯一来源，其余地方引用它。
 *
 * <p>注意：码表本身是**开放集合** —— 后端 {@code ck_dashboard.widgets} 存的是 JSON 数组，
 * 没有定义表可校验。遇到不认识的码，显示原码而不是空白（"未知码"本身就是信息）。
 */

/** 分组件码 → 中文名（与行业包 dashboard.json / 模板管理页保持一致） */
export const WIDGET_LABEL = {
  HEALTH_SCORE_TREND: '健康度趋势',
  COST_ACHIEVE: '成本达成',
  RISK_TOP5: '风险 Top5',
  MODEL_RANKING: '型号排名',
  MODEL_COMPARE_TABLE: '型号对比表',
  STAGE_DISTRIBUTION: '阶段分布',
  COST_TREND: '成本趋势',
  STAGE_READINESS: '阶段就绪度',
  DELIVERABLE_MATRIX: '交付件矩阵',
  REVIEW_STATUS: '评审状态',
  RISK_LIST: '风险清单',
  ACTION_ENTRY: '行动入口'
}

/** 型号作战地图的三块（{@code ModelBattleMap}） */
export const BATTLE_MAP_WIDGETS = ['STAGE_READINESS', 'DELIVERABLE_MATRIX', 'REVIEW_STATUS']

/** 经营战报的四块（{@code ExecutiveBoard}） */
export const EXECUTIVE_WIDGETS = ['HEALTH_SCORE_TREND', 'COST_ACHIEVE', 'RISK_TOP5', 'MODEL_RANKING']

/** 系列战况简报的三块（{@code SeriesBriefing}） */
export const SERIES_BRIEFING_WIDGETS = ['MODEL_COMPARE_TABLE', 'STAGE_DISTRIBUTION', 'COST_TREND']

/**
 * 已有渲染实现的分组件码 —— 三个渲染器合起来（作战地图 / 经营战报 / 系列战况）。
 * 其余码（风险清单 / 行动入口）只显示在"待接入"清单里，不假装有内容。
 */
export const RENDERED_WIDGETS = [...BATTLE_MAP_WIDGETS, ...EXECUTIVE_WIDGETS, ...SERIES_BRIEFING_WIDGETS]

/** 码 → 中文名；不认识的码原样返回 */
export function widgetLabel(code) {
  if (!code) return ''
  return WIDGET_LABEL[code] || code
}

/** 分组件码数组里是否至少有一个能渲染（空数组 = 老数据没配，按三件套渲染） */
export function hasRenderedWidget(codes) {
  if (!codes || !codes.length) return true
  return codes.some((c) => RENDERED_WIDGETS.includes(c))
}

/** 某视角的 widgets（JSON 字符串）→ 码数组；解析失败按空处理 */
export function parseWidgets(raw) {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
