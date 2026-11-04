/**
 * 阶段门（Gate）字典：类型 / 决策选项的「码 → 中文」映射，以及 JSON 数组解析。
 *
 * <p>为什么单独放一份：阶段门模板页（业务配置）与项目内的阶段门面板（系列 / 型号页）
 * 都要把 code 翻成中文；各写一套必然漂移 —— 用户已经两次反馈"界面上显示的是 code"。
 * 这里作为唯一来源，两个页面都从这里取。
 *
 * <p>码来自行业包（smart-hardware-ipd 的 technical-gates.json / decision-gates.json
 * —— 2026-10-29 起门文档按技术门 / 决策门拆成两份，评审内嵌在技术门的 reviews[] 里），
 * 不要在前端臆造新码；
 * 认不出的码一律原样显示 —— 显示生码好过显示空白。
 */

/** 阶段门类型（ck_gate.type / ck_gate_template.type） */
export const GATE_TYPE_LABEL = {
  TECHNICAL_REVIEW: '技术评审',
  DECISION_REVIEW: '决策评审'
}

/** 类型对应的标签颜色（antd tag color） */
export const GATE_TYPE_COLOR = {
  TECHNICAL_REVIEW: 'cyan',
  DECISION_REVIEW: 'purple'
}

/**
 * 评审类型（ck_review.type）。
 *
 * <p>与门类型是两套码：门写 TECHNICAL_REVIEW，评审写 TECHNICAL —— 别混用一张表，
 * 混了就会出现"评审页的类型还是英文"。
 */
export const REVIEW_TYPE_LABEL = {
  TECHNICAL: '技术评审',
  DECISION: '决策评审',
  MANAGEMENT: '管理评审'
}

/** 决策选项 / 实际决策（ck_gate.decision_options 里的元素、ck_gate.decision） */
export const GATE_DECISION_LABEL = {
  GO: 'Go 通过',
  GO_WITH_CONDITION: '有条件通过',
  RECYCLE: '退回重做',
  HOLD: 'Hold 暂缓',
  TERMINATE: 'Terminate 终止'
}

/** 决策结论的标签颜色：放行绿、有条件蓝、退回橙、终止红 */
export const GATE_DECISION_COLOR = {
  GO: 'green',
  GO_WITH_CONDITION: 'blue',
  RECYCLE: 'orange',
  HOLD: 'default',
  TERMINATE: 'red'
}

export function gateTypeLabel(code) {
  return GATE_TYPE_LABEL[code] || code || '-'
}

export function gateTypeColor(code) {
  return GATE_TYPE_COLOR[code] || 'default'
}

export function reviewTypeLabel(code) {
  return REVIEW_TYPE_LABEL[code] || code || '-'
}

/**
 * 这条 gate 行是不是**决策门**（真正意义上的"门"）。
 *
 * <p>2026-10-29 扁平化后技术评审直接挂阶段（一份评审 = 一场会），不再有"技术门"这一层；
 * 但老库里还留着 type=TECHNICAL_REVIEW 的 gate 行 —— 它们没有决策、永远不会 PASSED。
 * 阶段门页签早已把它们排除（见 `ProductLineDashboard#stageGates`），
 * 任何"阶段门 x/y"的统计也必须同样排除：否则分母凭空多出 6 道永远过不了的门，
 * 通过率永远是 0（2026-10-29 用户问"这些统计是不是都有问题"时查出来的）。
 *
 * <p>type 为空（老数据没写）按决策门处理：宁可多算一条，也不静默吞掉真实的门。
 */
export function isDecisionGate(gate) {
  return !!gate && gate.type !== 'TECHNICAL_REVIEW'
}

export function gateDecisionLabel(code) {
  return GATE_DECISION_LABEL[code] || code || '-'
}

export function gateDecisionColor(code) {
  return GATE_DECISION_COLOR[code] || 'default'
}

/**
 * 解析库里那些「JSON 字符串数组」列（如 ["GO","GO_WITH_CONDITION"]）。
 *
 * <p>三件事必须一起做，否则会炸出"整串原文"这种现状：
 * 兼容已是数组 / 已是字符串的两种来源；解析失败返回空数组；非数组值一律当空。
 */
export function parseGateJsonArray(json) {
  if (!json) return []
  try {
    const value = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}
