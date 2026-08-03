/**
 * 研发阶段定义（共享模块）
 *
 * 统一管理 stageOid → 中文名称的映射关系。
 * 支持硬编码默认阶段 + 运行时从后端 API 加载的动态阶段。
 */

export const STAGE_DEFS = [
  { key: 'MARKET_VALIDATION', title: '市场验证', color: '#eb2f96', description: '验证产品市场可行性与用户需求匹配度', items: ['市场调研分析', '目标用户验证', '竞品对标', '市场可行性评估'] },
  { key: 'REQUIREMENTS',      title: '需求论证', color: '#1677ff', description: '论证产品需求合理性与技术实现路径', items: ['需求分析', '需求评审', '技术可行性论证'] },
  { key: 'SOLUTION',          title: '方案设计', color: '#722ed1', description: '确定系统方案与关键技术选型', items: ['系统架构设计', '方案评审', '关键技术选型验证'] },
  { key: 'DETAILED',          title: '详细设计', color: '#13c2c2', description: '完成各专业详细设计及DFMEA分析', items: ['软件详细设计', '硬件原理图', '结构设计', 'DFMEA分析'] },
  { key: 'PROCESS',           title: '工艺规划', color: '#fa8c16', description: '完成生产工艺规划与试产准备', items: ['生产工艺设计', '工装夹具设计', 'BOM编制', '试产计划'] },
  { key: 'TRIAL',             title: '试产',     color: '#52c41a', description: '小批量试产验证并完成转量产决策', items: ['小批量试产验证', '问题追踪', '试产评审', '转量产决策'] },
]

/** key → 完整定义映射 */
export const stageDefByKey = Object.fromEntries(STAGE_DEFS.map(s => [s.key, s]))

/** key → 中文名称映射 */
export const stageTitleByKey = Object.fromEntries(STAGE_DEFS.map(s => [s.key, s.title]))

/** oid → 完整定义 运行时动态注册表 */
const stageDefByOid = new Map()

/** oid → title 运行时动态注册表 */
const stageTitleByOid = new Map()

/**
 * 注册从后端 API 加载的动态阶段定义，使 getStageTitle / getStageDef 可用 oid 查询。
 * 典型调用场景：ProductLineDashboard 加载 stages 后调用 registerDynamicStages(stages)。
 */
export function registerDynamicStages(stages) {
  if (!stages || !stages.length) return
  for (const s of stages) {
    const oid = s.oid
    const title = s.title || s.name || s.key
    const color = s.color || '#1677ff'
    if (oid) {
      stageTitleByOid.set(oid, title)
      stageDefByOid.set(oid, { key: oid, title, color, description: s.description || '', items: [] })
    }
  }
}

/**
 * 根据 stageKey 或 stageOid 获取中文名称。
 * 查找顺序：动态 oid 表 → 硬编码 key 表 → 原值
 */
export function getStageTitle(keyOrOid) {
  if (!keyOrOid) return ''
  if (stageTitleByOid.has(keyOrOid)) return stageTitleByOid.get(keyOrOid)
  return stageTitleByKey[keyOrOid] || keyOrOid
}

/**
 * 根据 stageKey 或 stageOid 获取完整阶段定义（含 color）。
 * 查找顺序：动态 oid 表 → 硬编码 key 表 → null
 */
export function getStageDef(keyOrOid) {
  if (!keyOrOid) return null
  if (stageDefByOid.has(keyOrOid)) return stageDefByOid.get(keyOrOid)
  return stageDefByKey[keyOrOid] || null
}

