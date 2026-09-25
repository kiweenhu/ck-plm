/**
 * CK-PLM 流程设计器 · 画布节点视觉规格
 *
 * <p>与业务语义严格分离（spec §2.2）：本文件只描述「长什么样」，
 * 不参与「是什么」——节点类型与属性的唯一事实源是 `@flow-dsl-core`。
 */

import { NodeType, type NodeType as NodeTypeT } from '@flow-dsl-core'

export type NodeShapeKind = 'rect' | 'diamond' | 'circle'

export interface NodeStyleSpec {
  /**
   * 几何形状：矩形（任务）/ 菱形（网关）/ 圆形（事件）。
   *
   * <p><b>这里刻意不含尺寸</b>：尺寸同时被画布（画框）与编译器（BPMN DI 坐标）使用，
   * 属于共享的几何契约，唯一事实源是 `@flow-dsl-core` 的 `defaultNodeSize`。
   * 曾把它放在这里，结果与契约层的数字各说一套（190×68 vs 180×64），
   * 且真正生效的是契约层 —— 改本文件毫无效果。
   */
  shape: NodeShapeKind
  fill: string
  stroke: string
  /**
   * 描边粗细（缺省 1.5）。
   *
   * <p>用于让某些业务活动在视觉上「更重」—— 例如会签审批是集体决策活动，
   * 比单人审批「重」，仅靠颜色区分对色觉障碍用户不够。
   */
  strokeWidth?: number
  /** 是否虚线描边（如「等待」类节点的弱化表达） */
  dashed?: boolean
  textColor: string
}

/** 状态角标三态（spec §4-A：未配置 / 警告 / 校验错误） */
export const BadgeState = {
  /** 配置完整且无问题 */
  OK: 'OK',
  /** 有告警（可保存） */
  WARNING: 'WARNING',
  /** 有阻断错误（不可保存） */
  ERROR: 'ERROR',
} as const
export type BadgeState = (typeof BadgeState)[keyof typeof BadgeState]

export const BADGE_MARK: Record<BadgeState, string> = { OK: '', WARNING: '?', ERROR: '!' }

export const BADGE_COLOR: Record<BadgeState, string> = {
  OK: '#52c41a',
  WARNING: '#faad14',
  ERROR: '#ff4d4f',
}

/** 按节点类型的外观规格（配色对应业务语义，而非 BPMN 元素名）；尺寸见 `defaultNodeSize` */
export const NODE_STYLE: Record<NodeTypeT, NodeStyleSpec> = {
  START: { shape: 'circle', fill: '#f6ffed', stroke: '#52c41a', textColor: '#389e0d' },
  END: { shape: 'circle', fill: '#fff1f0', stroke: '#ff4d4f', textColor: '#cf1322' },
  APPROVAL: { shape: 'rect', fill: '#e6f4ff', stroke: '#1677ff', textColor: '#0958d9' },
  // 会签审批：与单人审批同属「审批」，故沿用蓝色语义，但用更深的蓝 + 加粗描边
  // 表达「集体决策」这一更重的业务活动（不靠单一色相区分，兼顾色觉障碍）
  COUNTERSIGN_APPROVAL: {
    shape: 'rect',
    fill: '#dbeafe',
    stroke: '#1d39c4',
    strokeWidth: 2.5,
    textColor: '#10239e',
  },
  // 设置审批人：由发起人执行的「准备」活动，用绿色（与开始节点同族，语义上贴近发起）
  // 但以更深的绿 + 矩形形状与「开始」的浅绿圆点区分
  SET_ASSIGNEE: {
    shape: 'rect',
    fill: '#f6ffed',
    stroke: '#237804',
    strokeWidth: 2,
    textColor: '#135200',
  },
  TASK: { shape: 'rect', fill: '#f9f0ff', stroke: '#722ed1', textColor: '#531dab' },
  SERVICE: { shape: 'rect', fill: '#fff7e6', stroke: '#fa8c16', textColor: '#d46b08' },
  NOTIFY: { shape: 'rect', fill: '#fff0f6', stroke: '#eb2f96', textColor: '#c41d7f' },
  SUB_PROCESS: { shape: 'rect', fill: '#f0f5ff', stroke: '#2f54eb', textColor: '#1d39c4' },
  TIMER: { shape: 'rect', fill: '#f5f5f5', stroke: '#8c8c8c', dashed: true, textColor: '#595959' },
  EXCLUSIVE_GATEWAY: { shape: 'diamond', fill: '#fffbe6', stroke: '#faad14', textColor: '#d48806' },
  PARALLEL_GATEWAY: { shape: 'diamond', fill: '#e6fffb', stroke: '#13c2c2', textColor: '#006d75' },
  INCLUSIVE_GATEWAY: { shape: 'diamond', fill: '#fff7e6', stroke: '#fa8c16', textColor: '#d46b08' },
}

/** 连线配色：默认分支用灰色虚线弱化，条件分支用蓝色强调 */
export const EDGE_STYLE = {
  normal: { stroke: '#8c8c8c', strokeWidth: 1.5, strokeDasharray: undefined as string | undefined },
  condition: { stroke: '#1677ff', strokeWidth: 1.5, strokeDasharray: undefined as string | undefined },
  default: { stroke: '#bfbfbf', strokeWidth: 1.5, strokeDasharray: '5 3' as string | undefined },
} as const

/** 画布底色与网格 */
export const CANVAS_STYLE = {
  background: '#fafafa',
  gridColor: '#e8e8e8',
  portColor: '#8c8c8c',
  /** 端口圆点填充：未连是白底（空心）/ 悬停主题蓝 */
  portFill: '#fff',
  portHoverFill: '#1677ff',
  /**
   * 已连端口的填充：淡灰实心。
   *
   * <p>语义是「这个方位已经用过」—— 比未连的白底更弱，但**依然可见、依然可落**。
   * 之所以不把它藏起来：端口刻意不分出入，同一方位既能出线也能入线，
   * 已经连了一条的方位**仍然可以再接**（比如「通过」「驳回」分连两个目标）。
   * 藏掉它等于告诉用户"这里不能再连"。
   *
   * <p>只改填充，不改半径与位置：端口几何一动就会牵动相连连线的重算。
   */
  portConnectedFill: '#bfbfbf',
  /**
   * 端口常态不透明度：0 = 默认不可见。
   *
   * <p>每个节点有 3–4 个端口，常显时整个画布都是小圆点（用户反馈：太多了影响体验）。
   * 端口只在「悬停该节点 / 选中该节点 / 正在拉连线」时显形（见 `graph.ts` 的
   * `syncPortVisibility`）。
   *
   * <p><b>用 opacity 而不是 visibility/display</b>：后者会把端口移出命中区，
   * 于是"能看到端口却落不下线"—— 这是本项目真实发生过的事故
   * （见 `__tests__/canvas.test.ts` 的端口契约用例）。
   */
  portIdleOpacity: 0,
  /** 端口活跃不透明度：悬停 / 选中 / 拉线时显形 */
  portActiveOpacity: 1,
} as const

/**
 * 端口圆点半径。
 *
 * <p><b>只在建图时写一次，运行期绝不再改</b> —— 这条是踩过坑写下的：
 * 运行期改端口尺寸会引发端口重排与相连连线重算，实测表现为
 * 「鼠标一移到节点上，该节点前后的连线就变形」（悬停放大半径就是这么炸的）。
 * 具体是"位置随尺寸偏移"还是"尺寸变化触发了重新路由"，从源码没确认到；
 * 但结论不变：<b>运行期一律不动端口几何</b>，就没这个风险。
 *
 * <p>命中区不靠放大解决：`allowNode: true` + `snap: { radius: 26 }` 允许从节点
 * 任意位置起手并自动吸附到最近端口（见 `graph.ts` 的 connecting 配置）。
 *
 * <p>同理，端口「默认不可见」是通过 {@link CANVAS_STYLE}.portIdleOpacity 实现的，
 * 半径与位置始终不动：看得见与否是绘制层的事，几何一旦变了就会牵动连线。
 */
export const PORT_RADIUS = 5.5

/** 端口方位（一个方位一个端口，端口 id 就是方位名） */
export type PortSide = 'top' | 'right' | 'bottom' | 'left'

export const ALL_PORT_SIDES: PortSide[] = ['top', 'right', 'bottom', 'left']

/**
 * 同一方位上相邻锚点的间距下限（px）。
 *
 * <p>太密会让"吸附到最近锚点"变成抢点：起手拖线时的吸附半径是 26px，
 * 两个锚点只相距 13px 时，落点会在这两个之间摇摆 —— 用户反而更难落准。
 *
 * <p>34 是随活动框尺寸一起定的：锚点把边长四等分（见 {@link portSlotCount}），
 * 活动框宽 142（见 dsl-core 的 `defaultNodeSize`）→ 间距 35.5px ✓ 够；
 * 高 44 → 间距 11px ✗ 不够。
 * 下限必须 ≤ 35.5，否则活动框上下两边会掉回"只有中点"，
 * 而存量模板里已经有连线挂在 `bottom-2` / `bottom-3` 上。
 */
export const PORT_SLOT_MIN_SPACING = 34

/** 单个方位最多放几个锚点 */
export const PORT_SLOT_MAX = 3

/**
 * 槽位相对位置：0 = 方位中点，其余向两侧铺开。
 *
 * <p>0 号必须是中点，因为它要沿用方位名做端口 id（`bottom`）——
 * 既有连线数据（已存模板、历史快照）都指向这个 id，一改老模板的线就会"找不到端口"。
 */
const PORT_SLOT_FRACTIONS = [0.5, 0.25, 0.75]

/**
 * 锚点数量 —— 同一方位上能挂几条线。
 *
 * <p>为什么要有它：一个方位只有一个锚点时，同一节点的多条出边（「通过」「驳回」、
 * 以及后续的企业自定义路由）只能靠"换方位"避让；方位用尽就只能复用主锚点，
 * 线从同一点出发叠在一起 —— 而"哪条是驳回、哪条是自定义路由"恰恰是流程图里最该看清的。
 *
 * <p>按<b>边长</b>算而不是每边固定几个：活动框是 150×52，左右两侧只有 52px 高，
 * 硬塞 3 个锚点会让它们相距 13px（见 {@link PORT_SLOT_MIN_SPACING} 的说明）；
 * 宽度方向放得下 3 个，高度方向只放得下 1 个。
 */
export function portSlotCount(side: PortSide, size: { width: number; height: number }): number {
  const length = side === 'top' || side === 'bottom' ? size.width : size.height
  // 锚点把边长四等分（25% / 50% / 75%），相邻间距恒为 length/4（与放 2 个还是 3 个无关，
  // 因为位置是按比例定的）。所以判据就一条：四等分后还够宽就放满，不够就只放中点。
  return length / 4 >= PORT_SLOT_MIN_SPACING ? PORT_SLOT_MAX : 1
}

/**
 * 锚点 id：第 0 号沿用方位名（`bottom`），其余为 `bottom-2` / `bottom-3`。
 *
 * <p>id 是画布与 DSL 之间唯一的端口约定，必须稳定且可读 —— 调试时一眼能看出
 * 一条线挂在哪个方位的第几个锚点上。
 */
export function portIdOf(side: PortSide, slot: number): string {
  return slot === 0 ? side : `${side}-${slot + 1}`
}

/** 第 slot 个锚点在该方位上的相对位置（0–1，0.5 = 中点） */
export function portSlotFraction(slot: number): number {
  return PORT_SLOT_FRACTIONS[Math.min(slot, PORT_SLOT_FRACTIONS.length - 1)]
}

/** 方位的中文名（面板里给用户看的说法） */
export const PORT_SIDE_LABEL: Record<PortSide, string> = {
  top: '上方',
  right: '右侧',
  bottom: '下方',
  left: '左侧',
}

/**
 * 槽位在方位上的位置说法：0=中，之后按该方位铺开的方向命名。
 *
 * <p>横边（上/下）是左右铺开，竖边（左/右）是上下铺开 —— 不这么分，
 * 面板里会出现「右侧 左」这种谁也读不懂的选项。
 */
const PORT_SLOT_LABEL: Record<PortSide, string[]> = {
  top: ['中', '左', '右'],
  bottom: ['中', '左', '右'],
  left: ['中', '上', '下'],
  right: ['中', '上', '下'],
}

/** 一个节点上的全部锚点（id + 可读名称），按方位顺序展开 */
export interface AnchorInfo {
  id: string
  label: string
}

/**
 * 从锚点 id 反推方位（`bottom` / `bottom-2` → `bottom`）。
 *
 * <p>判不出来（id 不是本层生成的）返回 undefined，由调用方决定怎么兜。
 */
export function sideOfPortId(portId: string | undefined): PortSide | undefined {
  if (!portId) {
    return undefined
  }
  const side = portId.split('-')[0] as PortSide
  return ALL_PORT_SIDES.includes(side) ? side : undefined
}

/** 第 slot 个锚点在节点内的坐标（与 X6 端口组的百分比定位同源） */
export function anchorPoint(side: PortSide, slot: number, rect: Rect): { x: number; y: number } {
  const fraction = portSlotFraction(slot)
  switch (side) {
    case 'top':
      return { x: rect.x + rect.width * fraction, y: rect.y }
    case 'bottom':
      return { x: rect.x + rect.width * fraction, y: rect.y + rect.height }
    case 'left':
      return { x: rect.x, y: rect.y + rect.height * fraction }
    default:
      return { x: rect.x + rect.width, y: rect.y + rect.height * fraction }
  }
}

/**
 * 从锚点 id 反推槽位号（`bottom` → 0、`bottom-2` → 1）。判不出来返回 undefined。
 */
export function slotOfPortId(portId: string | undefined): number | undefined {
  if (!portId) {
    return undefined
  }
  const parts = portId.split('-')
  if (parts.length === 1) {
    return 0
  }
  const slot = Number(parts[1])
  return Number.isInteger(slot) && slot >= 2 ? slot - 1 : undefined
}

/**
 * 落点是否<b>精确落在</b>某个锚点圆点上（判定"用户是不是真的选了这个锚点"）。
 *
 * <p>为什么要这个判据：连线拖拽有 26px 的吸附半径，落点只要在节点附近就会被吸附到某个锚点上
 * —— 那是"碰巧落在附近"，不是"我选了这个点"。按"附近的锚点"钉住会出现真实问题：
 * 把线拖到某个节点上时被吸附到背对的一侧，线就绕节点兜一大圈（见视频反馈）。
 * 所以只有落在圆点本体附近（默认 8px，圆点半径 5.5px）才算用户的选择。
 *
 * <p>锚点之间相距 37.5px（活动框下边 3 个点），8px 的判定圈既不重叠、也不难瞄。
 */
export const ANCHOR_HIT_RADIUS = 8

export function isPreciseAnchorHit(
  type: NodeTypeT,
  rect: Rect,
  point: { x: number; y: number },
  portId: string | undefined,
  radius = ANCHOR_HIT_RADIUS,
): boolean {
  const side = sideOfPortId(portId)
  const slot = slotOfPortId(portId)
  if (!side || slot === undefined) {
    return false
  }
  if (!portSidesFor(type).includes(side) || slot >= portSlotCount(side, rect)) {
    // 该锚点在这类节点上不存在（老数据/类型改过）：不算命中，交由上层忽略
    return false
  }
  const anchor = anchorPoint(side, slot, rect)
  return Math.hypot(anchor.x - point.x, anchor.y - point.y) <= radius
}

/**
 * 离给定点最近的锚点 —— 「从这里起手」的那一端该挂哪个点。
 *
 * <p>注意：本函数<b>不用于决定"端点钉在哪个锚点"</b> —— 那件事由
 * {@link isPreciseAnchorHit} 判定（必须精确落在圆点上）。按"最近的锚点"钉住会出真实问题：
 * 落点靠近背对的一侧时，线要绕节点兜一大圈（视频反馈里的一圈就是这么来的）。
 */
export function nearestAnchorId(
  type: NodeTypeT,
  rect: Rect,
  point: { x: number; y: number },
): string | undefined {
  let best: { id: string; distance: number } | undefined
  for (const side of portSidesFor(type)) {
    const count = portSlotCount(side, rect)
    for (let slot = 0; slot < count; slot += 1) {
      const anchor = anchorPoint(side, slot, rect)
      const distance = (anchor.x - point.x) ** 2 + (anchor.y - point.y) ** 2
      if (!best || distance < best.distance) {
        best = { id: portIdOf(side, slot), distance }
      }
    }
  }
  return best?.id
}

/**
 * 列出某个节点类型暴露的全部锚点。
 *
 * <p>渲染层与属性面板共用同一份推导：面板里能选到的锚点，一定是画布上真实存在的那个点
 * —— 两处各算一遍必然分叉（用户会选中一个"选了没反应"的锚点）。
 */
export function anchorsOf(type: NodeTypeT, size: { width: number; height: number }): AnchorInfo[] {
  const result: AnchorInfo[] = []
  for (const side of portSidesFor(type)) {
    const count = portSlotCount(side, size)
    for (let slot = 0; slot < count; slot += 1) {
      result.push({
        id: portIdOf(side, slot),
        label: `${PORT_SIDE_LABEL[side]}${PORT_SLOT_LABEL[side][slot] ?? `${slot + 1}`}`,
      })
    }
  }
  return result
}

/** 矩形（左上角 + 尺寸），用于按相对位置推导端口 */
export interface Rect {
  x: number
  y: number
  width: number
  height: number
}

/**
 * 节点开放的端口方位。
 *
 * <p><b>四个方位都兼具「出」与「入」</b>（都 `magnet: true`）：连哪一侧由两个节点的
 * 相对位置推导（见 {@link pickPortSides}），所以端口不该再分 in/out ——
 * 同一个方位，在这条连线是出口、在那条就是入口。
 *
 * <p>起始节点不可能有入边（校验规则 `START_HAS_INCOMING`），故不给 `top`；
 * 结束节点不可能有出边（`END_HAS_OUTGOING`），故不给 `bottom` ——
 * 少画两个永远用不上的点。
 */
export function portSidesFor(type: NodeTypeT): PortSide[] {
  if (type === NodeType.START) {
    return ['right', 'bottom', 'left']
  }
  if (type === NodeType.END) {
    return ['top', 'right', 'left']
  }
  return ALL_PORT_SIDES
}

function centerOf(rect: Rect): { x: number; y: number } {
  return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }
}

/**
 * 方位平局时的基准序：偏向横向。
 *
 * <p>只在「两个方位得分完全相同」时起作用（例如两点中心重合）。横向优先的理由是
 * 左→右的阅读顺序更常见，且水平排版更能容下复杂流程。
 */
export const PORT_SIDE_ORDER: PortSide[] = ['right', 'left', 'bottom', 'top']

/**
 * 按「朝向目标的程度」给该节点开放的方位排序（最朝向目标的在前）。
 *
 * <p><b>为什么按半尺寸归一化</b>：直接比较中心位移的绝对值，宽节点会吃亏 ——
 * 190×68 的任务与正下方邻居纵向差 131px，与正右方邻居横向差 175px，
 * 不归一化就会把「明明在下方」判成横向。归一化后比的是「谁被挤得更明显」，
 * 与节点尺寸无关：纵向 131/57≈2.3 > 横向 175/190≈0.9 → 判纵向 ✓。
 *
 * <p>返回的顺序是渲染层选端口的依据：取第一个（主方位）；若该方位已被同节点的
 * 其他连线占用，就顺次退到下一个 —— 这就是网关多分支的「扇出」。
 * 由于只从 `available` 里挑，起始节点永远不会拿到 `top`、结束节点不会拿到 `bottom`。
 */
export function rankPortSides(from: Rect, to: Rect, available: PortSide[]): PortSide[] {
  const score = sideScores(from, to)
  return [...available].sort(
    (a, b) => score[b] - score[a] || PORT_SIDE_ORDER.indexOf(a) - PORT_SIDE_ORDER.indexOf(b),
  )
}

/** 各方位「朝向目标」的得分（正数=朝向，负数=背对） */
function sideScores(from: Rect, to: Rect): Record<PortSide, number> {
  const fc = centerOf(from)
  const tc = centerOf(to)
  const nx = (tc.x - fc.x) / Math.max(1, (from.width + to.width) / 2)
  const ny = (tc.y - fc.y) / Math.max(1, (from.height + to.height) / 2)
  return { right: nx, left: -nx, bottom: ny, top: -ny }
}

/**
 * 只保留「朝向目标」的方位（得分 ≥ 0），并按朝向程度排序。
 *
 * <p>用于同节点多分支的退让：优先挑一个**还没被占用**且朝向目标的方位；
 * 若朝向目标的方位都被占了，就宁可**复用主方位**（两条线重叠一小段，随后由
 * manhattan 路由自然分开），也不要挂到背对目标的那一侧 —— 后者会让线绕整整一圈。
 *
 * <p>这就是网关多分支的「扇出」：既不会全挤在一个点，也不会朝反方向甩出去。
 */
export function facingPortSides(from: Rect, to: Rect, available: PortSide[]): PortSide[] {
  const score = sideScores(from, to)
  const facing = available.filter((side) => score[side] >= 0)
  return facing.length > 0 ? rankPortSides(from, to, facing) : rankPortSides(from, to, available)
}

/** 一条连线的两个附着方位（各自朝向对方的那一侧） */
export function pickPortSides(source: Rect, target: Rect): { source: PortSide; target: PortSide } {
  return {
    source: rankPortSides(source, target, ALL_PORT_SIDES)[0],
    target: rankPortSides(target, source, ALL_PORT_SIDES)[0],
  }
}
