/**
 * CK-PLM 流程设计器 · DSL → 图元映射（纯函数，零 X6 运行时依赖）
 *
 * <p>这是画布层<b>唯一可单测的接缝</b>：把 DSL 状态映射为图元描述（自有的 metadata 类型），
 * 由 {@link ./graph} 交给 X6 渲染。这样「映射规则」在 Node 里就能测，
 * 不必依赖 DOM / Canvas。
 *
 * <p>方向严格单向：<b>DSL → 图元</b>。画布手势只产出 ops 调用，
 * 绝不直接改图元（spec §2.3「单源同步」）。
 */

import {
  EDGE_ROUTE_LABEL,
  EdgeKind,
  EdgeRoute,
  NODE_TYPE_LABEL,
  NodeType,
  defaultNodeSize,
  type FlowDsl,
  type FlowEdge,
  type FlowNode,
  type ValidationReport,
} from '@flow-dsl-core'
import {
  BADGE_COLOR,
  BADGE_MARK,
  BadgeState,
  CANVAS_STYLE,
  EDGE_STYLE,
  NODE_STYLE,
  PORT_RADIUS,
  anchorPoint,
  anchorsOf,
  facingPortSides,
  portIdOf,
  portSidesFor,
  portSlotCount,
  portSlotFraction,
  rankPortSides,
  sideOfPortId,
  slotOfPortId,
  type NodeStyleSpec,
  type PortSide,
  type Rect,
} from './node-style'

/**
 * 连线标签的图元形状：**白底圆角矩形 + 文字**。
 *
 * <p>为什么带底色：标签落在连线上时（折角附近很常见）没有底就会被线穿过去、读不清 ——
 * 这正是用户反馈的红框之一。底色给白、描边用极浅灰：视觉上仍是"一句话浮在线上"，
 * 但字始终读得清。
 */
const EDGE_LABEL_MARKUP = [
  { tagName: 'rect', selector: 'labelBody' },
  { tagName: 'text', selector: 'label' },
]

/** 连线标签图元（markup + 属性，与 X6 的 label 元数据结构兼容） */
export interface EdgeLabelMeta {
  markup: Array<{ tagName: string; selector: string }>
  attrs: Record<string, Record<string, unknown>>
}

/** 图元描述（与 X6 的 Node.Metadata / Edge.Metadata 结构兼容，但类型自有） */
export interface GraphNodeMeta {
  id: string
  shape: string
  x: number
  y: number
  width: number
  height: number
  markup: Array<{ tagName: string; selector: string }>
  attrs: Record<string, Record<string, unknown>>
  ports: {
    groups: Record<string, Record<string, unknown>>
    items: Array<{ id: string; group: string }>
  }
  data: {
    dslType: string
    badge: BadgeState
    badgeMark: string
    /**
     * 基础描边宽度（选中态在此基础上加粗）。
     *
     * <p>选中态刻意<b>只加粗、不改颜色</b>：描边颜色承载校验角标（红=错误、琥珀=提醒），
     * 若被选中态覆盖，选中时反而看不见错误。
     */
    baseStrokeWidth: number
    /**
     * 各端口的**基准填充色**（已连=淡灰实心，未连=白底）。
     *
     * <p>为什么要放进 data：端口在悬停时会被染成蓝色，离开时要复位到**它自己那一份**
     * 颜色，而不是统一刷成白色 —— 否则「已连」这个提示会在悬停一次后就消失。
     * 与 `baseStrokeWidth` 同理，呈现快照由 `render.ts` 算好，图工厂只负责读写。
     */
    portFills: Record<string, string>
  }
  zIndex: number
}

export interface GraphEdgeMeta {
  id: string
  source: { cell: string; port?: string }
  target: { cell: string; port?: string }
  attrs: Record<string, Record<string, unknown>>
  labels: EdgeLabelMeta[]
  data: {
    dslKind: string
    /**
     * 呈现快照：标签与基础线样式由本层算好，图工厂只决定「显示什么」。
     *
     * <p>这样「标签何时显示」「选中怎么高亮」不会与声明式映射分叉 ——
     * 基础样式始终只有这一处真相。
     */
    labelTexts: EdgeLabelMeta[]
    line: Record<string, unknown>
    /**
     * 是否分支连线（带条件或是默认分支）—— 决定标签是否**常显**。
     *
     * <p>分支名（「同意」/「拒绝」/「默认」）是读懂条件分支的<b>唯一信息</b>：
     * 藏起来会让条件分支不可读。而顺序连线（开始→审批→办理）的标签是纯噪音，
     * 常显只会把画布铺满气泡 —— 所以按语义区分，而不是一刀切。
     */
    branchEdge: boolean
  }
  /**
   * 固定走线点（回边车道用）。
   *
   * <p>不给就完全交给路由算法；给了就必须走这几个点 —— 回边（驳回/退回画回上游）
   * 靠它绕开中间的节点，见 {@link returnLaneVertices}。
   */
  vertices?: Array<{ x: number; y: number }>
  zIndex: number
  router: { name: string; args?: Record<string, unknown> }
  connector: { name: string; args?: Record<string, unknown> }
}

export interface GraphDataset {
  nodes: GraphNodeMeta[]
  edges: GraphEdgeMeta[]
}

/**
 * 连线是否需要重新挂接端点。
 *
 * <p><b>为什么「端点节点刚移动过」也必须重挂</b>：X6 的边几何是由端点**推导**出来的，
 * 但推导只在端点发生变化时发生。而本项目的节点坐标是**静默写入**的
 * （`setPosition(..., { silent: true })`，为避免「渲染 → 位置事件 → 回写 DSL」的回环），
 * 静默写入不会触发 X6 重算相连的边 —— 于是自动排版、撤销/重做、加载模板这类
 * 「批量挪坐标」之后，线会停在旧坐标上、与节点脱开。
 *
 * <p>手动拖拽不会出这个问题（拖拽走 X6 自己的手势，边会正常跟着走），
 * 所以缺陷只在"程序自己挪节点"的路径上暴露 —— 这正是它容易被漏掉的原因。
 */
export function needsReanchor(
  current: { cell?: string; port?: string } | undefined,
  next: { cell: string; port?: string },
  endpointMoved: boolean,
): boolean {
  return endpointMoved || current?.cell !== next.cell || current?.port !== next.port
}

/**
 * 节点在画布上的几何信息。
 *
 * <p><b>位置</b>取自 DSL 的 layout（用户拖拽的结果，是真实数据）；
 * <b>尺寸</b>只认 {@link defaultNodeSize} —— 设计器没有缩放节点的能力，
 * layout 里的宽高只是创建时写下的历史快照，若以它为准，
 * 调整基准尺寸对存量模板永远不生效（这正是"框偏大"改不掉的原因）。
 */
export function geometryOf(dsl: FlowDsl, node: FlowNode): { x: number; y: number; width: number; height: number } {
  const size = defaultNodeSize(node.type)
  const layout = dsl.layout.nodes[node.id]
  return {
    x: layout?.x ?? 0,
    y: layout?.y ?? 0,
    width: size.width,
    height: size.height,
  }
}

/** 由校验报告推导节点的角标状态（未配置视为 WARNING：允许保存但需提醒） */
export function badgeStateOf(report: ValidationReport | undefined, nodeId: string): BadgeState {
  if (!report) {
    return BadgeState.OK
  }
  const state = report.nodeState[nodeId]
  if (state === 'ERROR') {
    return BadgeState.ERROR
  }
  if (state === 'WARNING') {
    return BadgeState.WARNING
  }
  return BadgeState.OK
}

/** 校验问题（供悬停提示 / 侧栏定位） */
export function issuesOf(report: ValidationReport | undefined, nodeId: string): string[] {
  if (!report) {
    return []
  }
  return (report.byNode[nodeId] ?? []).map((issue) => issue.message)
}

function bodyMarkup(tagName: string): Array<{ tagName: string; selector: string }> {
  return [
    { tagName, selector: 'body' },
    { tagName: 'text', selector: 'label' },
    { tagName: 'text', selector: 'badgeText' },
  ]
}

function bodyTagFor(shape: NodeStyleSpec['shape']): string {
  switch (shape) {
    case 'circle':
      return 'circle'
    case 'diamond':
      return 'polygon'
    default:
      return 'rect'
  }
}

function bodyAttrs(
  style: NodeStyleSpec,
  badge: BadgeState,
  width: number,
  height: number,
): Record<string, unknown> {
  const attrs: Record<string, unknown> = {
    fill: style.fill,
    stroke: style.stroke,
    strokeWidth: badge === BadgeState.OK ? (style.strokeWidth ?? 1.5) : 2,
  }
  if (style.dashed) {
    attrs.strokeDasharray = '4 3'
  }
  if (style.shape === 'rect') {
    attrs.rx = 8
    attrs.ry = 8
  } else if (style.shape === 'diamond') {
    // 菱形用绝对坐标点串：不依赖 X6 的 refPoints（那是 polygon 内置形状的特殊属性，
    // 本画布用的是自定义 shape，不保证生效）
    attrs.points = `0,${height / 2} ${width / 2},0 ${width},${height / 2} ${width / 2},${height}`
  } else if (style.shape === 'circle') {
    attrs.cx = width / 2
    attrs.cy = height / 2
    attrs.r = Math.min(width, height) / 2 - 1
  }
  // 有错误时描边即变红 —— 比小图标更强的视觉信号
  if (badge === BadgeState.ERROR) {
    attrs.stroke = BADGE_COLOR.ERROR
  } else if (badge === BadgeState.WARNING) {
    attrs.stroke = BADGE_COLOR.WARNING
  }
  return attrs
}

/**
 * 节点标签属性。
 *
 * <p><b>菱形（分支节点）单独处理</b>：它只有 46×46，名字居中写必然溢出到图形外，
 * 看起来就像个漂浮的气泡。所以标签挂到**图形下方**（BPMN 工具的通行画法），
 * 字号也小一号。
 */
function labelAttrs(style: NodeStyleSpec): Record<string, unknown> {
  if (style.shape === 'diamond') {
    return {
      refX: 0.5,
      refY: 1,
      // refY2 是绝对偏移：贴在图形下沿之外一点
      refY2: 4,
      textAnchor: 'middle',
      textVerticalAnchor: 'top',
      fontSize: 10.5,
      fill: style.textColor,
    }
  }
  return {
    refX: 0.5,
    refY: 0.5,
    textAnchor: 'middle',
    textVerticalAnchor: 'middle',
    // 字号随活动框同步下调（12.5 → 12 → 11）：142×44 的框里 12 号字依然头重脚轻，
    // 而活动框里只有"节点名 + 类型"两行，11 号仍清晰、又能让长名字少折一行
    fontSize: 11,
    fill: style.textColor,
  }
}

/**
 * 画布上的节点标签文本。
 *
 * <p>节点库与面板用的是类型的业务名（{@link NODE_TYPE_LABEL}）；画布上多一条专属规则：
 * <b>菱形节点不显示类型名</b> —— 「条件分支」这类名字已由形状与配色表达，
 * 46×46 的菱形也放不下；只有用户**改过名**（如「金额判断」）时才显示。
 *
 * <p>矩形节点相反：一个未命名的审批节点必须写出「审批」，否则只剩一个空白方块。
 */
export function canvasLabelOf(node: FlowNode): string {
  const typeLabel = NODE_TYPE_LABEL[node.type] ?? node.type
  const hasOwnName = !!node.name && node.name !== typeLabel
  if (NODE_STYLE[node.type].shape === 'diamond') {
    return hasOwnName ? node.name : ''
  }
  return hasOwnName ? node.name : typeLabel
}

function badgeAttrs(badge: BadgeState): Record<string, unknown> {
  return {
    refX: 1,
    refY: 0,
    refX2: -2,
    refY2: 2,
    textAnchor: 'end',
    textVerticalAnchor: 'top',
    // 角标随节点一起缩小（14 → 12）：38×38 的菱形或 44 高的活动框上，14 号「!」几乎占掉半格
    fontSize: 12,
    fontWeight: 700,
    fill: BADGE_COLOR[badge],
  }
}

/**
 * 端口外观与定位（网关的左/右出口需要显式坐标）。
 *
 * <p><b>输入端口同样必须是 `magnet: true`</b>：X6 中 `magnet` 决定「该元素能否作为连线端点」，
 * 只把出口设为 magnet，会让连线能从出口拖出、却<b>无处落下</b> ——
 * 表现就是「根本连不上线」。这个缺陷真实发生过（见 `__tests__/canvas.test.ts` 的端口契约用例）。
 *
 * <p><b>默认不可见</b>（`opacity: 0`）：每节点 3–4 个端口常显会把画布铺满小圆点。
 * 显隐交给 `graph.ts` 的 `syncPortVisibility`（悬停 / 选中 / 拉线时显形）。
 * 这里只把初值设成"隐藏"，因此导入、撤销、重建后都不会先闪一下再消失。
 *
 * <p>用 opacity 而非 visibility/display：后两者会让端口失去命中区，
 * 于是"看得见却落不下线"；几何（r / position）则始终不动。
 *
 * @param fill 该端口的基准填充色（已连=淡灰实心 / 未连=白底），由调用方按占用情况给出
 */
function portGroupForPosition(position: unknown, fill: string): Record<string, unknown> {
  const circle = {
    r: PORT_RADIUS,
    magnet: true,
    stroke: CANVAS_STYLE.portColor,
    fill,
    strokeWidth: 1.5,
    opacity: CANVAS_STYLE.portIdleOpacity,
  }
  return { position, attrs: { circle } }
}

/**
 * 第 slot 个锚点的位置（X6 端口组 position）。
 *
 * <p>0 号沿用内置方位（`bottom` 等）—— 几何与加入多锚点之前<b>完全一致</b>，
 * 所以既有的单出边连线一根都不会挪位。
 *
 * <p>其余槽位用 {@code absolute} + 百分比坐标：百分比按节点尺寸归一
 * （X6 的 `normalizePercentage`），节点尺寸将来变了也不用改这里。
 */
function portPositionOf(side: PortSide, slot: number): unknown {
  if (slot === 0) {
    return side
  }
  const pct = `${Math.round(portSlotFraction(slot) * 100)}%`
  switch (side) {
    case 'top':
      return { name: 'absolute', args: { x: pct, y: '0%' } }
    case 'bottom':
      return { name: 'absolute', args: { x: pct, y: '100%' } }
    case 'left':
      return { name: 'absolute', args: { x: '0%', y: pct } }
    default:
      return { name: 'absolute', args: { x: '100%', y: pct } }
  }
}

/** 把 DSL（+ 校验结果）映射为完整图元集合 */
export function toGraphData(dsl: FlowDsl, report?: ValidationReport): GraphDataset {
  const rectOf = new Map<string, Rect>()
  for (const node of dsl.nodes) {
    rectOf.set(node.id, geometryOf(dsl, node))
  }
  // 先算连线：节点端口的填充色要知道「哪些锚点已被占用」（见 mapEdges）
  const { edges, connectedPorts } = mapEdges(
    dsl,
    rectOf,
    new Map(dsl.nodes.map((n) => [n.id, n])),
  )

  const nodes: GraphNodeMeta[] = dsl.nodes.map((node, index) => {
    const style = NODE_STYLE[node.type]
    const geo = geometryOf(dsl, node)
    const badge = badgeStateOf(report, node.id)
    const sides = portSidesFor(node.type)
    const connected = connectedPorts.get(node.id) ?? new Set<string>()
    const groups: Record<string, Record<string, unknown>> = {}
    const portFills: Record<string, string> = {}
    const portItems: Array<{ id: string; group: string }> = []
    for (const side of sides) {
      // 每个方位放若干个锚点（数量按边长算，见 portSlotCount）：
      // 多条出边/入边因此有各自的落点，而不是全挤在方位中点上
      const slots = portSlotCount(side, geo)
      for (let slot = 0; slot < slots; slot += 1) {
        const portId = portIdOf(side, slot)
        // 已连的锚点用淡灰实心：既提示"这里用过了"，又保留"仍可再连"的可见性
        const fill = connected.has(portId) ? CANVAS_STYLE.portConnectedFill : CANVAS_STYLE.portFill
        groups[portId] = portGroupForPosition(portPositionOf(side, slot), fill)
        portFills[portId] = fill
        portItems.push({ id: portId, group: portId })
      }
    }
    return {
      id: node.id,
      // 统一用一个自定义 shape 名，靠 markup 决定几何 —— 避免 X6 内置 shape 与自定义 markup 冲突
      shape: 'ckplm-flow-node',
      x: geo.x,
      y: geo.y,
      width: geo.width,
      height: geo.height,
      markup: bodyMarkup(bodyTagFor(style.shape)),
      attrs: {
        body: bodyAttrs(style, badge, geo.width, geo.height),
        label: { ...labelAttrs(style), text: canvasLabelOf(node) },
        badgeText: { ...badgeAttrs(badge), text: BADGE_MARK[badge] },
      },
      ports: { groups, items: portItems },
      data: {
        dslType: node.type,
        badge,
        badgeMark: BADGE_MARK[badge],
        // 与上面 attrs.body 同源同参调用，避免把描边宽度的算法抄成两份
        baseStrokeWidth: Number(bodyAttrs(style, badge, geo.width, geo.height).strokeWidth) || 1.5,
        portFills,
      },
      zIndex: node.type === NodeType.START || node.type === NodeType.END ? 20 : 10 + index,
    }
  })

  return { nodes, edges }
}

/**
 * 回边车道离画布最外侧节点的距离（px）。
 *
 * <p>必须大于端口吸附半径（26）：车道不能落在"一拖就被吸到节点上"的范围里。
 */
export const RETURN_LANE_MARGIN = 48

/**
 * 回边折进目标前那条「层间空带」的高度（px）。
 *
 * <p>层距 110、节点最高 44 → 空带有 66px：取 40 既在上一层节点的下沿之下（留 26px），
 * 又在目标上沿之上（留 40px）。走空带而不是走目标那一层，是因为那一层可能有别的节点挡着。
 */
export const RETURN_LANE_BAND = 40

/**
 * 回边（目标整块在源的上方 —— 驳回 / 退回画回上游）的走线点。
 *
 * <p><b>为什么要自己给走线</b>：这条线要从下游一路竖着回到上游、中间横跨好几层，
 * 直线必然穿过中间的节点；而 X6 的自动路由对这类长边并不可靠 —— 实测它会退回
 * `orth`（完全不避让障碍物），画出一条笔直穿过节点的竖线，这就是用户看到的「线穿框」。
 * 与其赌路由算法，不如确定性地给一条**外车道**：
 * 从源的侧向锚点横出去 → 贴画布最外侧竖着往上 → 在目标上方那条空带里横回来 → 落到目标顶部。
 * 三段都是直线，任何路由算法都会照走。
 */
export function returnLaneVertices(
  sourceRect: Rect,
  targetRect: Rect,
  sourcePort: string | undefined,
  targetPort: string | undefined,
  laneX: number,
): Array<{ x: number; y: number }> {
  const sourceAnchor = anchorPoint(
    sideOfPortId(sourcePort) ?? 'right',
    slotOfPortId(sourcePort) ?? 0,
    sourceRect,
  )
  const targetSide = sideOfPortId(targetPort) ?? 'top'
  const targetAnchor = anchorPoint(targetSide, slotOfPortId(targetPort) ?? 0, targetRect)
  // 从**顶部**进：先在目标上方那条空带里横过去，再落下来 —— 空带里没有节点，压不到谁
  if (targetSide === 'top') {
    const bandY = targetRect.y - RETURN_LANE_BAND
    return [
      { x: laneX, y: sourceAnchor.y },
      { x: laneX, y: bandY },
      { x: targetAnchor.x, y: bandY },
    ]
  }
  // 从**侧向**进（目标锚点被手动钉在 left/right，车道恰在同一侧）：竖段直接走到那个锚点的
  // 高度，再横着进去 —— 横线只需穿过"目标 ↔ 画布外缘"这段空档。
  return [
    { x: laneX, y: sourceAnchor.y },
    { x: laneX, y: targetAnchor.y },
  ]
}

/** 分支线出线/入线的「贴边段」长度（px）：拐角离节点边至少这么远，不再贴着框拐弯 */
export const BRANCH_STUB = 20

/** 同层两条分支的水平段错开量（px）：不错开的话它们在同一高度上看着像一条线 */
export const BRANCH_BAND_STAGGER = 12

/** 轴对齐线段是否穿过某个矩形（留 `pad` 的安全距离）—— 自检用，防自己画出"穿框"的线 */
function crossesRect(
  from: { x: number; y: number },
  to: { x: number; y: number },
  rect: Rect,
  pad = 2,
): boolean {
  const x1 = Math.min(from.x, to.x)
  const x2 = Math.max(from.x, to.x)
  const y1 = Math.min(from.y, to.y)
  const y2 = Math.max(from.y, to.y)
  return (
    x1 < rect.x + rect.width - pad &&
    x2 > rect.x + pad &&
    y1 < rect.y + rect.height - pad &&
    y2 > rect.y + pad
  )
}

/**
 * 这一侧是否**朝向**对方。
 *
 * <p>为什么不复用 `facingPortSides`：它在"没有任何朝向侧"时会退化成返回**全部侧**
 * （那是给"多条出边退让"用的兜底），拿它判"背对"会永远为真 —— 背对绕行分支会永远进不去。
 */
function sideFaces(side: PortSide, from: Rect, to: Rect): boolean {
  const fromCx = from.x + from.width / 2
  const fromCy = from.y + from.height / 2
  const toCx = to.x + to.width / 2
  const toCy = to.y + to.height / 2
  switch (side) {
    case 'left':
      return toCx <= fromCx
    case 'right':
      return toCx >= fromCx
    case 'top':
      return toCy <= fromCy
    default:
      return toCy >= fromCy
  }
}

/** 折线是否压到任何节点（走线自检） */
function crossesNode(chain: Array<{ x: number; y: number }>, obstacles: Rect[]): boolean {
  for (let i = 0; i < chain.length - 1; i += 1) {
    if (obstacles.some((rect) => crossesRect(chain[i], chain[i + 1], rect))) {
      return true
    }
  }
  return false
}

/**
 * 「锚点背对源」时的入场走线：绕到目标那一侧**之外**，再从外面正对着进锚点。
 *
 * <p>不这么走的话，线会从节点另一侧横穿过去 —— 而边在画布上画在**节点下层**，
 * 于是箭头被压在节点底下、根本看不见（用户反馈的原话：驳回连线的带箭头锚点被压在设置状态下面）。
 *
 * <p>两个候选（目标的左右外侧 × 上下外侧）里取第一个不压到任何节点的：
 * 先沿源自己的那一侧横出去 → 绕到目标外侧 → 回到锚点正前方 → 正对着进。
 */
function wrapApproachVertices(
  sourceRect: Rect,
  targetRect: Rect,
  sourceSide: PortSide,
  sourceAnchor: { x: number; y: number },
  targetSide: PortSide,
  targetAnchor: { x: number; y: number },
  obstacles: Rect[],
): Array<{ x: number; y: number }> | undefined {
  const outPoint =
    sourceSide === 'left'
      ? { x: sourceAnchor.x - BRANCH_STUB, y: sourceAnchor.y }
      : sourceSide === 'right'
        ? { x: sourceAnchor.x + BRANCH_STUB, y: sourceAnchor.y }
        : sourceSide === 'top'
          ? { x: sourceAnchor.x, y: sourceAnchor.y - BRANCH_STUB }
          : { x: sourceAnchor.x, y: sourceAnchor.y + BRANCH_STUB }
  const leftBand = targetRect.x - BRANCH_STUB
  const rightBand = targetRect.x + targetRect.width + BRANCH_STUB
  const topBand = targetRect.y - BRANCH_STUB
  const bottomBand = targetRect.y + targetRect.height + BRANCH_STUB

  const candidates: Array<Array<{ x: number; y: number }>> = []
  if (targetSide === 'bottom' || targetSide === 'top') {
    // 从上下进：先在目标左右之一的外侧竖着过去，再横到锚点正前方，最后竖着进
    const bandY = targetSide === 'bottom' ? bottomBand : topBand
    for (const bandX of [leftBand, rightBand]) {
      candidates.push([
        outPoint,
        { x: bandX, y: outPoint.y },
        { x: bandX, y: bandY },
        { x: targetAnchor.x, y: bandY },
      ])
    }
  } else {
    // 从左右进：先在目标上下之一的外侧横过去，再竖到锚点那一层，最后横着进
    const bandX = targetSide === 'left' ? leftBand : rightBand
    for (const bandY of [topBand, bottomBand]) {
      candidates.push([
        outPoint,
        { x: outPoint.x, y: bandY },
        { x: bandX, y: bandY },
        { x: bandX, y: targetAnchor.y },
      ])
    }
  }
  for (const points of candidates) {
    if (!crossesNode([sourceAnchor, ...points, targetAnchor], obstacles)) {
      return points
    }
  }
  return undefined
}

/**
 * 分支线（网关扇出 / 扇入这类"侧向出 → 纵向进"的折线）的走线点。
 *
 * <p><b>为什么要自己给拐角</b>（用户反馈的四条里三条都落在这里）：
 * <ol>
 *   <li>交给路由算法时拐角**贴着节点边**（离框几像素），看着局促；</li>
 *   <li>同一层两条分支的水平段被画在**同一高度**上，一眼看去像一条线穿过去，分不清谁连谁；</li>
 *   <li>入线偶尔落在偏心锚点上，像是"没对准"。</li>
 * </ol>
 *
 * <p>这里把拐角统一放进**层间空带**（层距 110、节点高 44 → 空带 66px），按分支左右
 * **错开 {@link BRANCH_BAND_STAGGER}**；出线先在节点侧边横走 {@link BRANCH_STUB} 再拐。
 * 末尾还会逐个矩形自检：一旦自己的走线要穿过别的节点，就<b>整个放弃</b>、交回路由算法
 * —— 宁可不动，也不给出更怪的形状。
 */
export function branchLaneVertices(
  sourceRect: Rect,
  targetRect: Rect,
  sourcePort: string | undefined,
  targetPort: string | undefined,
  obstacles: Rect[],
): Array<{ x: number; y: number }> | undefined {
  const sourceSide = sideOfPortId(sourcePort)
  const targetSide = sideOfPortId(targetPort)
  if (!sourceSide || !targetSide) {
    return undefined
  }
  // 源这一端必须朝外：从背对目标的一侧出线要绕整圈，那种情况交给路由算法更好
  if (!sideFaces(sourceSide, sourceRect, targetRect)) {
    return undefined
  }
  // 目标这一端**背对源**（例：目标在下方，锚点却钉在"下边沿"）：绕到那一侧之外、从外面正对着进锚点。
  // 否则线会从节点另一侧横穿过去 —— 边画在节点下层，箭头就被压在节点底下（用户反馈的那张图）
  if (!sideFaces(targetSide, targetRect, sourceRect)) {
    return wrapApproachVertices(
      sourceRect,
      targetRect,
      sourceSide,
      anchorPoint(sourceSide, slotOfPortId(sourcePort) ?? 0, sourceRect),
      targetSide,
      anchorPoint(targetSide, slotOfPortId(targetPort) ?? 0, targetRect),
      obstacles,
    )
  }
  const horizontalOut = sourceSide === 'left' || sourceSide === 'right'
  const verticalIn = targetSide === 'top' || targetSide === 'bottom'
  const verticalOut = sourceSide === 'top' || sourceSide === 'bottom'
  const horizontalIn = targetSide === 'left' || targetSide === 'right'
  // 三种可用形状：
  //   side-to-band  源从侧向出、目标从上下进（钉了左右锚点的网关扇出）→ 出线段 + 空带 + 横移
  //   band-only     源从上下出、目标也从上下进（网关默认从底边走、分支在下一层）→ 空带 + 横移
  //   drop-then-side 源从上下出、目标从侧向进（分支汇回网关）→ 竖到目标锚点那一层再横着进
  const shape =
    horizontalOut && verticalIn
      ? 'side-to-band'
      : verticalOut && verticalIn
        ? 'band-only'
        : verticalOut && horizontalIn
          ? 'drop-then-side'
          : undefined
  if (!shape) {
    return undefined
  }
  // 两行之间的空带（纵向）；太窄就放弃
  const gapTop = Math.min(sourceRect.y + sourceRect.height, targetRect.y + targetRect.height)
  const gapBottom = Math.max(sourceRect.y, targetRect.y)
  if (gapBottom - gapTop < 24) {
    return undefined
  }
  const sourceAnchor = anchorPoint(sourceSide, slotOfPortId(sourcePort) ?? 0, sourceRect)
  const targetAnchor = anchorPoint(targetSide, slotOfPortId(targetPort) ?? 0, targetRect)
  // 错开方向按"这条分支朝哪边"定：朝左的分支把水平段抬高一档、朝右的压低一档 ——
  // 同一层两条分支于是永远不在同一高度，不会被看成一条线穿过去
  const stagger = targetAnchor.x < sourceAnchor.x ? -BRANCH_BAND_STAGGER : BRANCH_BAND_STAGGER
  const bandY = Math.min(gapBottom - 10, Math.max(gapTop + 10, (gapTop + gapBottom) / 2 + stagger))

  let points: Array<{ x: number; y: number }>
  if (shape === 'side-to-band') {
    // 侧向先横走一小段（拐角离框远）→ 到空带 → 横移到目标正上/下方 → 竖着进锚点
    const stubX = sourceAnchor.x + (sourceSide === 'left' ? -BRANCH_STUB : BRANCH_STUB)
    points = [
      { x: stubX, y: sourceAnchor.y },
      { x: stubX, y: bandY },
      { x: targetAnchor.x, y: bandY },
    ]
  } else if (shape === 'band-only') {
    // 竖着下到空带 → 横移到目标正上方（拐角全在空带里，两侧节点都够远）
    points = [
      { x: sourceAnchor.x, y: bandY },
      { x: targetAnchor.x, y: bandY },
    ]
  } else {
    // 竖着走到目标锚点那一层 → 横着进侧向锚点
    points = [{ x: sourceAnchor.x, y: targetAnchor.y }]
  }

  // 去掉与端点重合/相邻重复的折点，再要求"确实拐了个弯" ——
  // 去重后共线（等于一条直线）就交回路由算法：不该为了形式上的统一反而塞进多余的走线点
  const chain = [sourceAnchor, ...points, targetAnchor].filter(
    (point, index, all) =>
      index === 0 || point.x !== all[index - 1].x || point.y !== all[index - 1].y,
  )
  let turns = 0
  for (let i = 1; i < chain.length - 1; i += 1) {
    const dx1 = chain[i].x - chain[i - 1].x
    const dy1 = chain[i].y - chain[i - 1].y
    const dx2 = chain[i + 1].x - chain[i].x
    const dy2 = chain[i + 1].y - chain[i].y
    if (dx1 * dy2 - dy1 * dx2 !== 0) {
      turns += 1
    }
  }
  if (turns === 0) {
    return undefined
  }
  // 自检：任何一段压到别的节点上就放弃（含源/目标自己的框，贴边不算压）
  for (let i = 0; i < chain.length - 1; i += 1) {
    if (obstacles.some((rect) => crossesRect(chain[i], chain[i + 1], rect))) {
      return undefined
    }
  }
  return chain.slice(1, -1)
}

/**
 * 连线映射：**同时产出端口占用情况**。
 *
 * <p>与节点映射分开、且<b>先于</b>节点执行 —— 节点端口的填充色（已连=淡灰实心）
 * 依赖「哪些方位已被占用」。这两件事本就是同一件事的因果，故放在一处：
 * 先挑定附着方位，顺手标记该方位已被占用。
 */
function mapEdges(
  dsl: FlowDsl,
  rectOf: Map<string, Rect>,
  nodeById: Map<string, FlowNode>,
): { edges: GraphEdgeMeta[]; connectedPorts: Map<string, Set<string>> } {
  /** 出边已用掉的锚点 id：节点 → 锚点 id 集合 */
  const usedBySource = new Map<string, Set<string>>()
  /** 入边已用掉的锚点 id（同一侧汇入多条时也分开落，否则箭头全叠在一点） */
  const usedByTarget = new Map<string, Set<string>>()
  /** 每个节点上「已有连线附着」的锚点 id（出入皆算）—— 供端口取填充色 */
  const connectedPorts = new Map<string, Set<string>>()
  /** 该节点上被用户手动指定的锚点：自动分配必须避开（否则两条线仍会挤在同一个点） */
  const manualPorts = new Map<string, Set<string>>()
  const markConnected = (nodeId: string, portId: string | undefined) => {
    if (!portId) {
      return
    }
    const used = connectedPorts.get(nodeId) ?? new Set<string>()
    used.add(portId)
    connectedPorts.set(nodeId, used)
  }
  /** 该节点上真实存在的锚点 id（渲染与面板共用同一份推导） */
  const anchorIdsOf = (nodeId: string): Set<string> => {
    const node = nodeById.get(nodeId)
    const rect = rectOf.get(nodeId)
    return new Set(node && rect ? anchorsOf(node.type, rect).map((a) => a.id) : [])
  }
  /** 手动锚点：只认该节点上真实存在的，失效的（如节点类型改小）一律忽略并回退自动 */
  for (const edge of dsl.edges) {
    for (const [end, nodeId] of [['source', edge.source], ['target', edge.target]] as const) {
      const pinned = edge.anchor?.[end]
      if (pinned && anchorIdsOf(nodeId).has(pinned)) {
        const set = manualPorts.get(nodeId) ?? new Set<string>()
        set.add(pinned)
        manualPorts.set(nodeId, set)
      }
    }
  }
  const hasFreeSlot = (store: Map<string, Set<string>>, nodeId: string, side: PortSide,
                       size: Rect): boolean => {
    const used = store.get(nodeId)
    const manual = manualPorts.get(nodeId)
    for (let slot = 0; slot < portSlotCount(side, size); slot += 1) {
      const id = portIdOf(side, slot)
      if (!used?.has(id) && !manual?.has(id)) {
        return true
      }
    }
    return false
  }
  /**
   * 取该方位下一个空闲锚点。
   *
   * <p>用尽（短边只有 1 个）就复用主锚点：两条线重叠一小段，随后由 manhattan 路由自然分开 ——
   * 也比挂到背对目标的一侧、让线绕整整一圈好（与原实现的取舍一致）。
   */
  const takePort = (store: Map<string, Set<string>>, nodeId: string, side: PortSide,
                    size: Rect): string => {
    const used = store.get(nodeId) ?? new Set<string>()
    const manual = manualPorts.get(nodeId)
    for (let slot = 0; slot < portSlotCount(side, size); slot += 1) {
      const id = portIdOf(side, slot)
      if (!used.has(id) && !manual?.has(id)) {
        used.add(id)
        store.set(nodeId, used)
        return id
      }
    }
    return portIdOf(side, 0)
  }
  // 画布左右边界：回边的外车道贴在它们之外 —— 整条竖线在所有节点之外，不可能穿框
  const rects = [...rectOf.values()]
  const leftMost = rects.length ? Math.min(...rects.map((r) => r.x)) : 0
  const rightMost = rects.length ? Math.max(...rects.map((r) => r.x + r.width)) : 0
  const graphMidX = (leftMost + rightMost) / 2
  /** 回边 → 固定走线点（见 returnLaneVertices 的说明） */
  const returnVertices = new Map<string, Array<{ x: number; y: number }>>()

  // 附着方位按两个节点的相对位置推导：横向相邻走左右、纵向相邻走上下 ——
  // 横向排版（复杂流程更清晰）不再只能绕到上下两侧
  const chosen = new Map<string, { sourcePort?: string; targetPort?: string }>()
  // <b>锚点分配按路由固定顺序</b>，而不是按 DSL 里连线的先后：
  // 「通过」永远拿主锚点、「驳回」拿下一个，将来加自定义路由时它们排在后面 ——
  // 这样调换两条连线在数组里的顺序（或插入新路由）不会让已有连线在画布上"挪锚点"
  const byRouteThenId = [...dsl.edges].sort((a, b) =>
    a.source.localeCompare(b.source) || routeRank(a) - routeRank(b) || a.id.localeCompare(b.id))
  /** 该边两端的几何；缺任何一环（脏数据）就不参与自动分配 */
  const geometryOfEdge = (edge: FlowEdge) => {
    const sourceNode = nodeById.get(edge.source)
    const targetNode = nodeById.get(edge.target)
    const sourceRect = rectOf.get(edge.source)
    const targetRect = rectOf.get(edge.target)
    return sourceNode && targetNode && sourceRect && targetRect
      ? { sourceNode, targetNode, sourceRect, targetRect }
      : undefined
  }
  /** 回边车道：先由源侧定下，目标侧不相容时再撤掉（见第二遍） */
  const returnLanes = new Map<string, PortSide>()

  // ── 第一遍：只分**出边**的锚点 ───────────────────────────────────────────────
  // 为什么必须分两遍：网关这类小节点的每个方位只有 1 个锚点（38 / 4 < 锚点间距下限 34），
  // 若一遍之内既分出边又分入边，"汇聚"节点上的两条入边会同时抢榜首那个方位 ——
  // 抢不到的那条只能复用主锚点，两条线从此完全叠在一起（即用户反馈的「出边入边重叠」）。
  // 先让出边占住它朝向的那一侧，入边再从剩下的空闲方位里挑，重叠自然消失。
  for (const edge of byRouteThenId) {
    const geo = geometryOfEdge(edge)
    if (!geo) {
      chosen.set(edge.id, {})
      continue
    }
    const { sourceNode, sourceRect, targetRect } = geo
    // 用户手动指定过的那一端就用他指定的锚点（拖拽重连 / 面板选择都会写进 edge.anchor），
    // 另一端照旧自动分配 —— 手动与自动可以只各管一端
    const pinnedSource = edge.anchor?.source
    let sourcePort = pinnedSource && anchorIdsOf(edge.source).has(pinnedSource) ? pinnedSource : undefined
    // 回边（目标整块在源的上方）：竖线会横跨好几层、必定穿过中间的节点，改走画布外侧的「车道」。
    // 车道在哪一侧由**源这一端**定：自动分配时取源所在那一半的外侧（第一段横线出去不会撞上
    // 同层的邻居）；源被手动钉过就听他那一侧。钉在上下就说明走不了车道，交回路由算法。
    let laneSide: PortSide | undefined
    if (targetRect.y + targetRect.height <= sourceRect.y) {
      const pinnedSide = sideOfPortId(pinnedSource)
      laneSide =
        pinnedSide === 'left' || pinnedSide === 'right'
          ? pinnedSide
          : sourceRect.x + sourceRect.width / 2 <= graphMidX
            ? 'left'
            : 'right'
    }
    if (!sourcePort) {
      if (laneSide) {
        sourcePort = takePort(usedBySource, edge.source, laneSide, sourceRect)
      } else {
        // 只在「朝向目标」的方位里挑，且优先挑<b>还有空锚点</b>的方位 ——
        // 同一方位有多个锚点时，两条出边不必挤在两个方位上（原实现一个方位只有一个点）
        const facing = facingPortSides(sourceRect, targetRect, portSidesFor(sourceNode.type))
        const side =
          facing.find((candidate) => hasFreeSlot(usedBySource, edge.source, candidate, sourceRect))
          ?? facing[0]
        sourcePort = takePort(usedBySource, edge.source, side, sourceRect)
      }
    } else if (laneSide && sideOfPortId(sourcePort) !== laneSide) {
      // 源被钉在别的方位：车道让位给用户的选择；钉的是上下就说明走不了车道
      const sourceSide = sideOfPortId(sourcePort)
      laneSide = sourceSide === 'left' || sourceSide === 'right' ? sourceSide : undefined
    }
    if (laneSide) {
      returnLanes.set(edge.id, laneSide)
    }
    markConnected(edge.source, sourcePort)
    chosen.set(edge.id, { sourcePort })
  }

  // ── 第二遍：只分**入边**的锚点 ───────────────────────────────────────────────
  // 与出边同一处规则：**优先挑还有空锚点的方位**，而不是死盯榜首那个 ——
  // 榜首被占就退到次一档的方位，「多条入边（或与出边）汇到同一个点」从此不会发生。
  for (const edge of byRouteThenId) {
    const geo = geometryOfEdge(edge)
    if (!geo) {
      continue
    }
    const { targetNode, sourceRect, targetRect } = geo
    const pinnedTarget = edge.anchor?.target
    let targetPort =
      pinnedTarget && anchorIdsOf(edge.target).has(pinnedTarget) ? pinnedTarget : undefined
    let laneSide = returnLanes.get(edge.id)
    if (!targetPort) {
      if (laneSide) {
        // 从目标**上方**进：那条层间空带里没有节点，横回来的线压不到谁
        targetPort = takePort(usedByTarget, edge.target, 'top', targetRect)
      } else {
        // 入边侧：方位按朝向排序，但同一方位上的多条入边各占一个锚点 ——
        // 多个箭头汇在同一个点会让"从哪来"看不出来（驳回与通过常常同时指回同一个节点）
        const ranked = rankPortSides(targetRect, sourceRect, portSidesFor(targetNode.type))
        const side =
          ranked.find((candidate) => hasFreeSlot(usedByTarget, edge.target, candidate, targetRect))
          ?? ranked[0]
        targetPort = takePort(usedByTarget, edge.target, side, targetRect)
      }
    } else if (laneSide) {
      // 目标锚点也被手动钉过：只有"朝外那一侧"或"顶部"与车道相容（车道本就是贴着外缘走的），
      // 钉在背对的一侧（如左车道却要从右边进）就撤掉车道，按用户的选择走。
      const targetSide = sideOfPortId(targetPort)
      if (targetSide !== 'top' && targetSide !== laneSide) {
        laneSide = undefined
      }
    }
    // 出边与入边都算「该锚点已使用」：端口不分出入，两种占用都占住同一个点
    markConnected(edge.target, targetPort)
    const sourcePort = chosen.get(edge.id)?.sourcePort
    chosen.set(edge.id, { sourcePort, targetPort })
    if (laneSide) {
      const laneX = laneSide === 'left' ? leftMost - RETURN_LANE_MARGIN : rightMost + RETURN_LANE_MARGIN
      returnVertices.set(
        edge.id,
        returnLaneVertices(sourceRect, targetRect, sourcePort, targetPort, laneX),
      )
    } else if (sourcePort && targetPort) {
      // 分支线（网关扇出/扇入这类"侧向出 → 纵向进"的折线）：拐角进层间空带、左右错开，
      // 且自检不穿别的节点 —— 见 branchLaneVertices 的说明
      // 障碍物要**含源/目标自身**：走线不能压任何节点框，包括自己两端的框 ——
      // 漏掉它们时，"竖线从节点内部穿进去"这种错反而检不出来
      const branch = branchLaneVertices(sourceRect, targetRect, sourcePort, targetPort, rects)
      if (branch) {
        returnVertices.set(edge.id, branch)
      }
    }
  }

  // 输出顺序仍按 DSL（图元顺序稳定，撤销/重建不会抖动）
  const edges: GraphEdgeMeta[] = dsl.edges.map((edge) => {
    const { sourcePort, targetPort } = chosen.get(edge.id) ?? {}
    // 手动折点优先：用户拖过的走线属于**用户意图**（与手动锚点同级），自动走线不再覆盖它
    const vertices = edge.waypoints?.length ? edge.waypoints : returnVertices.get(edge.id)
    return {
      id: edge.id,
      source: { cell: edge.source, ...(sourcePort ? { port: sourcePort } : {}) },
      target: { cell: edge.target, ...(targetPort ? { port: targetPort } : {}) },
      ...(vertices ? { vertices } : {}),
      attrs: { line: { ...edgeStyleOf(edge) } },
      labels: edgeLabelOf(edge),
      data: {
        dslKind: edge.kind,
        labelTexts: edgeLabelOf(edge),
        line: { ...edgeStyleOf(edge) },
        // 带路由的连线也是分支（通过/驳回）：标签必须常显，
        // 否则"哪条是驳回"在图上读不出来 —— 它和条件分支一样是读懂流程的关键信息
        branchEdge: edge.kind !== EdgeKind.NORMAL || !!edge.route,
      },
      zIndex: 1,
      // 有折点的边**不再交给路由算法**：折点就是最终走线。否则 manhattan 会自己加折角、
      // 甚至绕出小圈（用户反馈的"一拖就多出折角"就是这么来的）。没有折点的边才走自动路由。
      router: vertices?.length ? { name: 'normal' } : EDGE_ROUTER,
      connector: { name: 'rounded', args: { radius: 8 } },
    }
  })

  return { edges, connectedPorts }
}

/**
 * 路由的锚点优先级：通过 → 驳回 → 其余（条件连线等）。
 *
 * <p>只影响"谁先挑锚点"，不影响语义。
 */
function routeRank(edge: FlowEdge): number {
  if (edge.route === EdgeRoute.PASS) {
    return 0
  }
  if (edge.route === EdgeRoute.REJECT) {
    return 1
  }
  return 2
}

/**
 * 连线路由配置：图默认与每条边共用这一份。
 *
 * <p><b>为什么要显式写 `excludeTerminals`</b>：X6 的 manhattan 路由默认
 * `excludeTerminals: []` —— 也就是把<b>连线两端的节点自己也当成障碍物</b>。
 * 而端点恰好落在节点边界上（位于"被 padding 撑大的障碍矩形"内部），于是 A\* 的
 * 起/终点全被判成不可达，一次都没跑就退回 `orth`（纯正交、完全不看障碍物）。
 * 实测控制台连刷 5 条「Unable to execute manhattan algorithm, use orth instead」，
 * 表现为跨多层的**回边（驳回 → 上游）直线穿过中间节点**。
 *
 * <p>`maxLoopCount` 放宽同理：跨多层的回边（13 个节点时最长跨 7 层）要探索的网格点
 * 远超默认的 2000，超限一样退回 orth。默认 step = 10px，2 万次足够覆盖设计器里的
 * 流程规模，单条边的开销仍在毫秒级。
 *
 * <p>这里只影响画布表现，不写进 DSL —— 改了<b>立即作用于存量模板</b>。
 */
export const EDGE_ROUTER = {
  name: 'manhattan',
  args: { excludeTerminals: ['source', 'target'], maxLoopCount: 50000 },
} as const

/** 连线外观：默认分支虚线弱化、条件分支蓝色强调 */
export function edgeStyleOf(edge: FlowEdge): Record<string, unknown> {
  const style =
    edge.kind === EdgeKind.DEFAULT
      ? EDGE_STYLE.default
      : edge.kind === EdgeKind.CONDITION
        ? EDGE_STYLE.condition
        : EDGE_STYLE.normal
  const attrs: Record<string, unknown> = { stroke: style.stroke, strokeWidth: style.strokeWidth, targetMarker: { name: 'block', width: 10, height: 7 } }
  if (style.strokeDasharray) {
    attrs.strokeDasharray = style.strokeDasharray
  }
  return attrs
}

/**
 * 连线标签：显示分支名（如「同意」/「拒绝」/「默认」）。
 *
 * <p>没填分支名时按语义兜底：<b>路由优先</b>（「通过」/「驳回」）——
 * 驳回边/通过边是读懂流程的关键信息，标签空着等于让图上少了一句话
 * （真实反馈：画了驳回边，线上什么都不显示）。
 */
export function edgeLabelOf(edge: FlowEdge): EdgeLabelMeta[] {
  let text = edge.name ?? ''
  if (!text && edge.route) {
    text = EDGE_ROUTE_LABEL[edge.route]
  }
  if (!text && edge.kind === EdgeKind.DEFAULT) {
    text = '默认'
  }
  if (!text && edge.condition?.fieldKey) {
    text = `${edge.condition.fieldKey}`
  }
  if (!text) {
    return []
  }
  return [
    {
      markup: EDGE_LABEL_MARKUP,
      attrs: {
        label: { text, fontSize: 10.5, fill: '#595959' },
        // 白底贴合文字：refWidth/refHeight 给一点内边距，x/y 让底色居中
        labelBody: {
          ref: 'label',
          refWidth: 8,
          refHeight: 4,
          x: -4,
          y: -2,
          rx: 3,
          ry: 3,
          fill: '#ffffff',
          stroke: '#f0f0f0',
          strokeWidth: 1,
        },
      },
    },
  ]
}
