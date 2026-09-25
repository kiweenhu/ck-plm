/**
 * CK-PLM 流程设计器 · X6 图封装
 *
 * <p>职责边界（spec §2.2）：<b>只做视图与手势，不持有业务逻辑</b>。
 * 所有手势都通过 {@link FlowGraphHooks} 回调交给上层（由上层调用 `@flow-dsl-core` 的 ops），
 * 再由上层把新 DSL 经 {@link applyDataset} 推回图中。
 *
 * <p>连线合法性不由本层判断，而是回调 {@link FlowGraphHooks.canConnect}
 * —— 判定矩阵的唯一实现在 `@flow-dsl-core` 的 `canConnect`，避免两处规则分叉。
 */

import { Graph, type Edge } from '@antv/x6'
import { Snapline } from '@antv/x6-plugin-snapline'
import { Selection } from '@antv/x6-plugin-selection'
import { Keyboard } from '@antv/x6-plugin-keyboard'
import type { NodeType as NodeTypeT } from '@flow-dsl-core'
import { CANVAS_STYLE, EDGE_STYLE, isPreciseAnchorHit } from './node-style'
import { EDGE_ROUTER, needsReanchor } from './render'
import type { GraphDataset, GraphEdgeMeta, GraphNodeMeta } from './render'

/** 自定义节点 shape 名（注册一次） */
export const FLOW_NODE_SHAPE = 'ckplm-flow-node'

export interface GraphSelection {
  nodeIds: string[]
  edgeIds: string[]
}

export interface FlowGraphHooks {
  /**
   * 连线合法性判定（唯一实现来自 dsl-core 的 canConnect）。
   *
   * <p>`excludeEdgeId` 在**拖动已有连线的端点**时传入：判定前必须把这条线自己从 DSL 里摘掉，
   * 否则 `canConnect` 的「两节点之间已存在连线」会命中它自己 ——
   * 拖到**同一节点的另一个锚点**时 source/target 没变，会被判成非法并弹回。
   * 这正是"锚点只能回属性面板改"的根因：能改接别的节点，却改不了落点。
   */
  canConnect(sourceId: string, targetId: string, excludeEdgeId?: string): { ok: boolean; reason?: string }
  /** 节点被拖动结束（只改 layout，不触碰语义） */
  onNodeMoved?(nodeId: string, position: { x: number; y: number }): void
  /**
   * 拖动结束（松手）—— 用于把「拖动」作为一个可撤销步骤压栈。
   *
   * <p>拖动过程会连续触发 {@link onNodeMoved}，若每次都压栈会把撤销栈冲垮，
   * 且「挪了下坐标」不该与「改了会签比例」等价。故：过程只更新 layout，松手才记一步。
   */
  onNodeMoveEnd?(): void
  /**
   * 新建了连线（由上层写入 DSL）。
   *
   * <p>带上两端实际落到的锚点：那是用户起手时点中的小圆点、以及落点吸附到的位置。
   * 不传就等于放弃用户的选择，新线一律被自动分配拉回中点。
   */
  onConnected?(connection: {
    source: string
    target: string
    sourcePort?: string
    targetPort?: string
  }): void
  /**
   * 拖动了**已有连线**的端点（重连）。
   *
   * <p>与 {@link onConnected} 的区别是"这条线本来就在"：上层只改它的两端（含锚点），
   * 其余配置（分支名 / 类型 / 条件 / 路由）原样保留 —— 这正是「想改起止点却只能删了重画」
   * 要解决的问题。画布不改 DSL，只把结果报上来。
   */
  onEdgeReconnected?(connection: {
    edgeId: string
    source: string
    target: string
    /** 被拖动的那一端（另一端保持原样） */
    end: 'source' | 'target'
    /**
     * 拖到锚点圆点上的话，这是该锚点 id；落在节点附近（没精确命中）时为 undefined
     * —— 上层据此决定"钉住这个锚点"还是"回到自动分配"。
     */
    anchor?: string
  }): void
  /**
   * 用户拖动了连线的折角（折点）。
   *
   * <p>与 {@link onEdgeReconnected} 同一套路：画布不改 DSL，只把结果报上来。
   * 空数组 = 折点被拖没了（回到自动走线）。
   */
  onEdgeWaypointsChanged?(edgeId: string, waypoints: Array<{ x: number; y: number }>): void
  /** 选中项变化 */
  onSelectionChanged?(selection: GraphSelection): void
  /** 请求删除选中项（Delete / Backspace） */
  onDeleteSelected?(selection: GraphSelection): void
  /** 请求撤销 / 重做（Ctrl+Z / Ctrl+Shift+Z） */
  onUndo?(): void
  onRedo?(): void
  /** 请求保存（Ctrl+S） */
  onSave?(): void
  /** 合法性被拒时的提示（供 UI 弹出原因） */
  onReject?(reason: string): void
}

let shapeRegistered = false

/**
 * 正在执行 {@link applyDataset} 的图实例。
 *
 * <p>用途：本层把 DSL 的坐标写进画布时，X6 会发出 `node:change:position`，
 * 而我们的处理器会把它当成「用户挪了节点」回写 DSL —— 形成
 * 「渲染 → 位置事件 → 改 DSL → 渲染」的回环。
 *
 * <p><b>为什么不能用 `setPosition(x, y, { silent: true })` 来挡</b>：silent 会连
 * X6 自己的视图更新一起挡掉 —— 节点在界面上根本不动，而连线却按新坐标重画，
 * 于是"节点没动、线却脱开"，看起来就是渲染坏了（真实事故）。
 * 所以改为：位置正常写入（视图与相连的边都由 X6 正常重算），
 * 只是**忽略本轮由我们自己触发的位置事件**。
 */
const applyingDatasets = new WeakSet<Graph>()

/**
 * 图实例 → DOM 监听清理函数。
 *
 * <p>连线手势需要在 `window` 上挂 mouseup（画布外松手也要收尾），
 * 组件卸载时必须摘掉 —— 否则残留的监听会去操作已销毁的图。
 */
const domCleanups = new WeakMap<Graph, () => void>()

/**
 * 正在拉连线的图实例。
 *
 * <p>做成模块级 WeakSet 而不是 `createFlowGraph` 的闭包变量：`applyDataset` 是模块级
 * 函数（也要读这个状态来决定标签显隐），闭包变量它看不见。
 */
const connectingGraphs = new WeakSet<Graph>()

/**
 * 当前被悬停的节点 id。
 *
 * <p>与 {@link connectingGraphs} 同理做成模块级 WeakMap：`applyDataset` 是模块级函数，
 * 数据集重建后要按这个状态重新显形端口，闭包变量它看不见。
 */
const hoveredNodes = new WeakMap<Graph, string>()

/**
 * 最近一次应用的数据集。
 *
 * <p>用途只有一个：手势被上层（DSL 规则）拒绝时把画面拉回来。X6 的端点拖动会
 * <b>先改视图</b>再发事件，而 DSL 才是事实源 —— 没有这份快照，被拒的重连就会表现为
 * 「线在画布上赖着不走，直到下次重绘才跳回去」（真实发生过的体验问题）。
 */
const lastDatasets = new WeakMap<Graph, GraphDataset>()

/**
 * 连线两端的**端点手柄**（X6 内置工具 `source-arrowhead` / `target-arrowhead`）。
 *
 * <p>为什么要有它：改锚点原先只能回属性面板选下拉 —— 可"这条线该落在哪个点上"是画布上的
 * 空间信息，用文字选既别扭、又看不见结果。挂上这两个手柄后，悬停/选中连线会在两端各出现
 * 一个小圆点，**拖它就是改接**：拖到本节点的另一个锚点=换落点，拖到别的节点=换目标。
 *
 * <p>它走的是 X6 原生的端点拖动（`arrowheadMovable`，默认可用），松手后照旧发
 * `edge:connected` + `isNew:false` —— 与面板改锚点走**同一条上抛链路**（`onEdgeReconnected`
 * → `rebindEdge` + `setEdgeAnchor`），不存在"两种改法、两套规则"。
 */
// d 比 X6 默认形状略大一圈：端点手柄是要用鼠标抓的，太小会变成"知道能拖却抓不住"
const HANDLE_ATTRS = {
  d: 'M 0 -7 L 7 0 L 0 7 L -7 0 Z',
  fill: '#1677ff',
  stroke: '#ffffff',
  strokeWidth: 2,
}

const EDGE_HANDLES: Array<{ name: string; args: Record<string, unknown> }> = [
  { name: 'source-arrowhead', args: { attrs: HANDLE_ATTRS } },
  { name: 'target-arrowhead', args: { attrs: HANDLE_ATTRS } },
]

/**
 * 折角手柄（折点）。**只在选中连线时**出现，且只认已有折点。
 *
 * <p>三个开关按用户反馈定（原话：一拖就多出折角、只想改折角方向）：
 * <ul>
 *   <li>`addable: false` —— 不在线上新增折点，折角数量不变；</li>
 *   <li>`removable: false` —— 双击也不删点，避免误删；</li>
 *   <li>`snapRadius` —— 拖动时吸附到相邻点的轴线：一次只朝一个方向动，折线始终保持正交。</li>
 * </ul>
 * `onChanged` 把结果报给上层写回 DSL（折点与手动锚点同级，属于用户意图）。
 */
function edgeVertexTool(hooks: FlowGraphHooks): { name: string; args: Record<string, unknown> } {
  return {
    name: 'vertices',
    args: {
      addable: false,
      removable: false,
      removeRedundancies: true,
      snapRadius: 24,
      attrs: { r: 5, fill: '#ffffff', stroke: SELECTED_STROKE, strokeWidth: 2, cursor: 'move' },
      onChanged: ({ edge }: { edge: Edge }) => {
        // 兜底：这个回调在 X6 的鼠标抬起处理里同步执行，一旦抛异常，
        // 后面的 `undelegateDocumentEvents()` 就不会执行 —— 拖拽状态残留，
        // 之后指针一动就重复触发，表现就是「画布卡死」。所以这里绝不能让异常外泄。
        try {
          hooks?.onEdgeWaypointsChanged?.(
            edge.id,
            edge.getVertices().map((point) => ({ x: Math.round(point.x), y: Math.round(point.y) })),
          )
        } catch (error) {
          // 忽略：折点没写回去最多是丢一次手动调整，绝不能卡住画布
        }
      },
    },
  }
}

/** 给连线挂上两端手柄 + 折角手柄（已挂则跳过，避免重复叠加） */
function showEdgeHandles(edge: Edge, hooks: FlowGraphHooks): void {
  if (!edge.hasTool?.('source-arrowhead')) {
    edge.addTools(EDGE_HANDLES)
  }
  if (!edge.hasTool?.('vertices')) {
    edge.addTools([edgeVertexTool(hooks) as never])
  }
}

/** 摘掉端点手柄（只读态：线不再悬停/选中） */
function hideEdgeHandles(edge: Edge): void {
  edge.removeTools()
}

/** 注册自定义节点（幂等） */
export function ensureFlowNodeRegistered(): void {
  if (shapeRegistered) {
    return
  }
  Graph.registerNode(
    FLOW_NODE_SHAPE,
    {
      inherit: 'rect',
      width: 150,
      height: 52,
      // 默认 markup 与实例级 markup 结构一致，仅作为兜底
      markup: [
        { tagName: 'rect', selector: 'body' },
        { tagName: 'text', selector: 'label' },
        { tagName: 'text', selector: 'badgeText' },
      ],
      attrs: {
        body: { fill: '#fff', stroke: '#8c8c8c', strokeWidth: 1.5, rx: 8, ry: 8 },
        // 与 render.ts 的实例级属性同一口径（节点缩小后字号同步下调）
        label: { fontSize: 11, fill: '#333' },
        badgeText: { fontSize: 12, fontWeight: 700 },
      },
      // 兜底端口（实例级 ports 由 render.ts 按节点类型给出，这里只是防"完全没有端口"）
      // 四个方位都必须 magnet: true —— 只把出口设为 magnet 会让连线"无处落下"，
      // 这是真实发生过的事故（见 render.ts 的端口契约说明与 canvas.test.ts 的用例）
      ports: {
        groups: {
          top: { position: 'top', attrs: { circle: { r: 3.5, magnet: true } } },
          right: { position: 'right', attrs: { circle: { r: 3.5, magnet: true } } },
          bottom: { position: 'bottom', attrs: { circle: { r: 3.5, magnet: true } } },
          left: { position: 'left', attrs: { circle: { r: 3.5, magnet: true } } },
        },
      },
    },
    true,
  )
  shapeRegistered = true
}

// ==================== 选中 / 悬停的呈现（标签显隐 + 高亮）====================

/** 选中态配色（与主题蓝一致） */
const SELECTED_STROKE = '#1677ff'
const SELECTED_STROKE_WIDTH = 2.5
/** 节点选中时在基础描边宽度上加的量（刻意不改颜色，见 GraphNodeMeta.data 的说明） */
const SELECTED_NODE_EXTRA_WIDTH = 1.5

/**
 * 同步连线的呈现：**标签显隐 + 选中高亮**。
 *
 * <ol>
 *   <li><b>标签按语义区分显隐</b>：
 *       <ul>
 *         <li>分支连线（带条件 / 默认分支）—— <b>常显</b>。分支名是读懂条件分支的
 *             唯一信息，藏起来条件分支就不可读了；</li>
 *         <li>顺序连线（开始→审批→办理）—— <b>不显示</b>。它们的标签是纯噪音，
 *             常显会把画布铺满气泡（用户反馈原话）；</li>
 *         <li>拉连线时 —— <b>全部显示</b>。那一刻最需要知道「已有分支分别叫什么」。</li>
 *       </ul>
 *       （早先一刀切成"只在拉线时显示"，结果连分支名也一起消失了 —— 过度修正。）</li>
 *   <li><b>选中连线要有反馈</b>：X6 的 Selection 只给节点画选框，
 *       <b>选中连线时画布上没有任何变化</b> —— 面板已经切到「连线」了，
 *       用户却看不出自己选的是哪一条。</li>
 * </ol>
 *
 * <p>基础样式取自 `render.ts` 放进 `data` 的快照（`line` / `labelTexts` / `branchEdge`），
 * 本函数只决定「显示什么」，不重算样式 —— 否则基础样式就有了第二处真相。
 */
function syncEdgePresentation(graph: Graph, showAllLabels: boolean): void {
  const selectedEdges = new Set<string>()
  for (const cell of graph.getSelectedCells()) {
    if (cell.isEdge()) {
      selectedEdges.add(cell.id)
    }
  }
  for (const edge of graph.getEdges()) {
    const data = (edge.getData() ?? {}) as {
      labelTexts?: unknown[]
      line?: Record<string, unknown>
      branchEdge?: boolean
    }
    const baseLine = data.line ?? {}
    edge.setAttrs({
      line: selectedEdges.has(edge.id)
        ? { ...baseLine, stroke: SELECTED_STROKE, strokeWidth: SELECTED_STROKE_WIDTH }
        : baseLine,
    } as never)
    const visible = showAllLabels || data.branchEdge === true
    edge.setLabels((visible ? (data.labelTexts ?? []) : []) as never)
  }
}

/**
 * 节点选中：把描边加粗。
 *
 * <p><b>刻意不动描边颜色</b>：颜色承载校验角标（红=错误、琥珀=提醒），
 * 被选中态盖掉的话，选中一个有问题节点反而看不到它有问题。
 */
function syncNodeSelection(graph: Graph): void {
  const selected = new Set<string>()
  for (const cell of graph.getSelectedCells()) {
    if (cell.isNode()) {
      selected.add(cell.id)
    }
  }
  for (const node of graph.getNodes()) {
    const data = (node.getData() ?? {}) as { baseStrokeWidth?: number }
    const base = typeof data.baseStrokeWidth === 'number' ? data.baseStrokeWidth : 1.5
    node.setAttrByPath(
      'body/strokeWidth',
      selected.has(node.id) ? base + SELECTED_NODE_EXTRA_WIDTH : base,
    )
  }
}

/**
 * 同步端口显隐：**默认隐藏，需要时显形**。
 *
 * <p>每个节点有 3–4 个端口（一个方位一个），常显时整个画布都是小圆点
 * （用户反馈原话：太多了影响体验）。显形时机只有三种：
 *
 * <ol>
 *   <li><b>悬停该节点</b> —— 想连线时的指引：这里可以拉出去；</li>
 *   <li><b>选中该节点</b> —— 面板已切到该节点，随时可能连出；</li>
 *   <li><b>正在拉连线</b> —— 全部节点都显形，否则用户不知道该往哪落。</li>
 * </ol>
 *
 * <p><b>只改 opacity 与 fill，绝不改几何</b>：端口半径（r）与位置一动，就会触发
 * 端口重排与相连连线重算 —— 实测表现是「鼠标一移上去，该节点前后的连线就变形」
 * （悬停放大半径就是这么炸的）。opacity / fill 既不改 bbox、也不把端口移出命中区，
 * 所以隐藏状态下依然能吸附，「从节点内部拖出连线」（`allowNode` + `snap` 半径 26）
 * 始终可用。
 *
 * <p>填充色也在这里复位：悬停时端口会被统一染蓝，离开时要回到**它自己那一份**
 * 基准色（已连=淡灰实心 / 未连=白底，见 `GraphNodeMeta.data.portFills`），
 * 而不是统一刷成白色 —— 否则「已连」这个提示悬停一次就没了。
 */
function syncPortVisibility(graph: Graph): void {
  const showAll = connectingGraphs.has(graph)
  const selected = new Set<string>()
  for (const cell of graph.getSelectedCells()) {
    if (cell.isNode()) {
      selected.add(cell.id)
    }
  }
  const hovered = hoveredNodes.get(graph)
  for (const node of graph.getNodes()) {
    const visible = showAll || selected.has(node.id) || hovered === node.id
    const opacity = visible ? CANVAS_STYLE.portActiveOpacity : CANVAS_STYLE.portIdleOpacity
    // 悬停语义是「这些方位都能起手连线」，故此刻统一染蓝，不区分已连/未连
    const hovering = hovered === node.id
    const fills = ((node.getData() ?? {}) as { portFills?: Record<string, string> }).portFills ?? {}
    for (const port of node.getPorts()) {
      // 端口必有 id（就是方位名），但 X6 的类型把它标成可选
      if (!port.id) {
        continue
      }
      const fill = hovering ? CANVAS_STYLE.portHoverFill : (fills[port.id] ?? CANVAS_STYLE.portFill)
      // **只在真的变化时写**：本函数在每次 DSL 改动（含面板里每敲一个字符）后都会跑，
      // 无脑写会白触发一轮 DOM 属性更新（端口数 × 2）
      const circle = (port as { attrs?: { circle?: { opacity?: number; fill?: string } } }).attrs?.circle
      if (circle?.opacity !== opacity) {
        node.setPortProp(port.id, 'attrs/circle/opacity', opacity)
      }
      if (circle?.fill !== fill) {
        node.setPortProp(port.id, 'attrs/circle/fill', fill)
      }
    }
  }
}

/** 创建画布图实例 */
export function createFlowGraph(container: HTMLElement, hooks: FlowGraphHooks): Graph {
  ensureFlowNodeRegistered()

  let graph: Graph

  graph = new Graph({
    container,
    background: { color: CANVAS_STYLE.background },
    grid: { visible: true, size: 12, type: 'dot', args: { color: CANVAS_STYLE.gridColor } },
    panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheel'] },
    mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'], factor: 1.1, maxScale: 2, minScale: 0.3 },
    autoResize: true,
    connecting: {
      // 吸附到端口，避免连线悬空
      snap: { radius: 26 },
      allowBlank: false,
      allowLoop: false,
      allowMulti: false,
      // 允许以「节点」本身作为端点：这样可以从节点内部直接拖出连线，
      // 不必精确命中那个小圆点；配合上面的 snap 半径会自动吸附到最近的端口。
      allowNode: true,
      allowEdge: false,
      highlight: true,
      router: EDGE_ROUTER,
      connector: { name: 'rounded', args: { radius: 8 } },
      // 判定矩阵的唯一实现在 dsl-core，本层只做回调
      validateConnection: (args: {
        sourceCell?: { id: string } | null
        targetCell?: { id: string } | null
        /** 正在被拖动的连线（新建连线时为 null）—— 见 FlowGraphHooks.canConnect 的说明 */
        edge?: { id: string } | null
        edgeView?: { cell?: { id: string } | null } | null
      }) => {
        const { sourceCell, targetCell } = args
        if (!sourceCell || !targetCell) {
          return false
        }
        const movingEdgeId = args.edge?.id ?? args.edgeView?.cell?.id
        const result = hooks.canConnect(sourceCell.id, targetCell.id, movingEdgeId)
        if (!result.ok && result.reason) {
          hooks.onReject?.(result.reason)
        }
        return result.ok
      },
      createEdge: () =>
        graph.createEdge({
          attrs: { line: { ...EDGE_STYLE.normal, targetMarker: { name: 'block', width: 10, height: 7 } } },
          zIndex: 1,
        }),
    },
  })

  graph.use(new Snapline({ enabled: true, sharp: true }))
  graph.use(
    new Selection({
      enabled: true,
      multiple: true,
      rubberband: true,
      movable: true,
      showNodeSelectionBox: true,
    }),
  )
  graph.use(new Keyboard({ enabled: true, global: false }))

  // ==================== 手势 → 回调（不在此处改 DSL）====================

  graph.on('node:change:position', ({ node, current }: { node: { id: string }; current?: { x: number; y: number } }) => {
    if (!current || applyingDatasets.has(graph)) {
      // 本轮坐标是本层按 DSL 写的，不是用户挪的 —— 回写会形成渲染回环
      return
    }
    hooks.onNodeMoved?.(node.id, { x: Math.round(current.x), y: Math.round(current.y) })
  })

  // 松手才记一步（拖动过程只更新 layout）
  graph.on('node:moved', () => {
    if (applyingDatasets.has(graph)) {
      return
    }
    hooks.onNodeMoveEnd?.()
  })

  // 悬停：显形 + 染蓝（"这些方位都能起手连线"），离开即复位
  graph.on('node:mouseenter', ({ node }: { node: { id: string } }) => {
    hoveredNodes.set(graph, node.id)
    syncPortVisibility(graph)
  })
  graph.on('node:mouseleave', ({ node }: { node: { id: string } }) => {
    if (hoveredNodes.get(graph) === node.id) {
      hoveredNodes.delete(graph)
    }
    // 若该节点仍处于选中态，syncPortVisibility 会让端口保持显形（判断在它内部），
    // 并把填充色复位成「它自己那一份」
    syncPortVisibility(graph)
  })

  /**
   * 落点是否**精确落在**该节点某个锚点的圆点上 —— 是才把它当成"用户选了这个锚点"。
   *
   * <p>拖拽有 26px 的吸附半径：落点只要在节点附近，X6 就会报出一个端口，但那只是
   * "碰巧落在附近"。按附近的端口钉住会出真实问题 —— 端点被吸附到背对的一侧时，
   * 线要绕节点兜一大圈（视频反馈里的那一圈）。所以只有落在圆点本体附近才算数。
   */
  function preciseAnchorAt(
    nodeId: string,
    portId: string | undefined,
    point: { x: number; y: number } | null,
  ): string | undefined {
    if (!point || !portId) {
      return undefined
    }
    const cell = graph.getCellById(nodeId)
    if (!cell || !cell.isNode()) {
      return undefined
    }
    const type = (cell.getData() as { dslType?: NodeTypeT } | undefined)?.dslType
    if (!type) {
      return undefined
    }
    const position = cell.getPosition()
    const size = cell.getSize()
    const rect = { ...position, width: size.width, height: size.height }
    return isPreciseAnchorHit(type, rect, point, portId) ? portId : undefined
  }

  /** 鼠标松开的位置（画布局部坐标）—— 落点那一端用它判断是否精确命中锚点 */
  function dropPointOf(event?: MouseEvent): { x: number; y: number } | null {
    if (!event) {
      return null
    }
    const point = graph.clientToLocal(event.clientX, event.clientY)
    return { x: point.x, y: point.y }
  }

  graph.on('edge:connected', ({
    edge,
    isNew,
    e,
    type: terminalType,
  }: {
    edge: {
      id: string
      getSourceCellId(): string | null
      getTargetCellId(): string | null
      getSource(): { cell?: string; port?: string }
      getTarget(): { cell?: string; port?: string }
    }
    isNew: boolean
    e?: MouseEvent
    /** 被改动的端点（拖的是起点还是终点）—— X6 的事件载荷里就叫 `type` */
    type?: 'source' | 'target'
  }) => {
    const source = edge.getSourceCellId()
    const target = edge.getTargetCellId()
    // 端点落在哪个锚点上，在**移除这条临时线之前**取出来 —— 它就是用户起手/落点选的那个点。
    // 早先只上报了节点、把锚点丢了，于是新画的线一律回到"自动"的中点，
    // 画布上那些别的锚点也就"怎么点都没用上"。
    const sourcePort = edge.getSource().port
    const targetPort = edge.getTarget().port
    if (isNew) {
      // 先移除 X6 自动创建的临时连线：DSL 才是事实源，稍后由 applyDataset 重建
      graph.removeEdge(edge as never)
      if (source && target) {
        hooks.onConnected?.({
          source,
          target,
          // 起手那端一定是按在端口圆点上的（按节点内部是"拖节点"而非拉线），端口由 X6 给出 ✓
          sourcePort,
          // 落点那端要判"是不是真的点在圆点上"：X6 的 26px 吸附会把"落在节点附近"也报成端口，
          // 照单全收就会把端点吸到背对的一侧、线绕节点一圈（视频反馈里的那一圈）
          targetPort: preciseAnchorAt(target, targetPort, dropPointOf(e)),
        })
      }
      return
    }
    // ==================== 已有连线被拖动端点（重连）====================
    // 这段原先直接 return，于是"画布上看着改了、DSL 没改"，下一次重绘就复原 ——
    // 表现出来就是"锚点永远只能是原来那两个，想改只能删了重画"。
    const revert = () => {
      // 视图已经按 X6 的结果变了，但 DSL 不接受：用上一次的数据集把画面拉回来，
      // 否则画布与 DSL 会长期不一致（直到下一次重绘才莫名其妙地跳回去）
      const last = lastDatasets.get(graph)
      if (last) {
        applyDataset(graph, last)
      }
    }
    if (!source || !target) {
      revert()
      return
    }
    // 判定时排除这条线自己：否则"两节点之间已存在连线"会命中它自己（详见 hooks.canConnect）
    const check = hooks.canConnect(source, target, edge.id)
    if (!check.ok) {
      hooks.onReject?.(check.reason ?? '这条连线不能改接到该节点')
      revert()
      return
    }
    // 只上报**被拖动的那一端**的锚点：另一端保持着它原来的样子（可能是自动分配的），
    // 若把它一起写回去，就等于把"自动"悄悄变成了"手动钉住"——那条线以后不再跟随自动分配。
    const draggedEnd = terminalType === 'target' ? 'target' : 'source'
    const dropPoint = dropPointOf(e)
    const droppedPort = draggedEnd === 'source' ? sourcePort : targetPort
    const nodeOfEnd = draggedEnd === 'source' ? source : target
    hooks.onEdgeReconnected?.({
      edgeId: edge.id,
      source,
      target,
      end: draggedEnd,
      // 同样只在"精确点在圆点上"时才认锚点，落在节点附近就走自动（否则线会绕一圈）
      anchor: preciseAnchorAt(nodeOfEnd, droppedPort, dropPoint),
    })
  })

  // ==================== 端点手柄：拖它就能改锚点 / 改接 ====================
  // 悬停或选中连线时在两端显示手柄，让"改落点"这件事在画布上直接可做，
  // 不必回属性面板选下拉（见 EDGE_HANDLES 的说明）。
  graph.on('edge:mouseenter', ({ edge }) => showEdgeHandles(edge, hooks))
  graph.on('edge:mouseleave', ({ edge, e }) => {
    // 拖动手柄的过程中指针会离开连线本体（此时左键仍按着）——
    // 这一下不能收手柄，否则拖到一半手柄消失、拖动直接断掉
    const leftButtonDown = (e as unknown as { buttons?: number } | undefined)?.buttons
    if (leftButtonDown) {
      return
    }
    hideEdgeHandles(edge)
  })
  graph.on('edge:selected', ({ edge }) => showEdgeHandles(edge, hooks))
  graph.on('edge:unselected', ({ edge }) => hideEdgeHandles(edge))

  graph.on('selection:changed', ({ selected }: { selected: Array<{ id: string; isEdge(): boolean }> }) => {
    const nodeIds: string[] = []
    const edgeIds: string[] = []
    for (const cell of selected ?? []) {
      if (cell.isEdge()) {
        edgeIds.push(cell.id)
      } else {
        nodeIds.push(cell.id)
      }
    }
    hooks.onSelectionChanged?.({ nodeIds, edgeIds })
    // 选中态要立刻反映到画布上（连线高亮 + 节点加粗 + 端口显形），见各 sync 的说明
    syncEdgePresentation(graph, connectingGraphs.has(graph))
    syncNodeSelection(graph)
    syncPortVisibility(graph)
  })

  // ==================== 连线手势（标签只在这一刻显示）====================
  //
  // 起手信号取两个来源，取其一即可：
  //   ① X6 的端口按下事件（该版本若没有这个名字，注册不会报错、只是不触发）；
  //   ② DOM 兜底：mousedown 落点在端口元素上（端口圆点带 magnet="true" 标记）。
  // 两者都拿不到时的表现是「标签不显示」—— 与用户的主要诉求一致，
  // 不会退化成「又变吵」，因此这个降级方向是安全的。
  const setConnecting = (next: boolean) => {
    if (connectingGraphs.has(graph) === next) {
      return
    }
    if (next) {
      connectingGraphs.add(graph)
    } else {
      connectingGraphs.delete(graph)
    }
    syncEdgePresentation(graph, next)
    // 拉线期间所有端口显形：否则用户看不到可落点（这一刻最需要它们出现）
    syncPortVisibility(graph)
  }
  graph.on('node:magnet:mousedown', () => setConnecting(true))
  const onContainerMouseDown = (event: MouseEvent) => {
    const target = event.target as Element | null
    if (!target || typeof target.closest !== 'function') {
      return
    }
    if (target.closest('[magnet="true"]')) {
      setConnecting(true)
      return
    }
    // 拖端点手柄时同样要显形端口与标签：这一刻用户正要把线落到某个锚点上，
    // 看不见落点就是盲拖 —— 与"拉新线"是同一个诉求（见 syncPortVisibility）
    if (target.closest('.x6-edge-tool-source-arrowhead, .x6-edge-tool-target-arrowhead')) {
      setConnecting(true)
    }
  }
  container.addEventListener('mousedown', onContainerMouseDown)
  // 松手即结束：必须挂 window —— 在画布外松手也要收尾，否则标签会一直挂着
  const onWindowMouseUp = () => setConnecting(false)
  window.addEventListener('mouseup', onWindowMouseUp)
  domCleanups.set(graph, () => {
    container.removeEventListener('mousedown', onContainerMouseDown)
    window.removeEventListener('mouseup', onWindowMouseUp)
  })

  const currentSelection = (): GraphSelection => {
    const cells = graph.getSelectedCells()
    return {
      nodeIds: cells.filter((c) => c.isNode()).map((c) => c.id),
      edgeIds: cells.filter((c) => c.isEdge()).map((c) => c.id),
    }
  }

  graph.bindKey(['ctrl+z', 'meta+z'], () => {
    hooks.onUndo?.()
    return false
  })
  graph.bindKey(['ctrl+shift+z', 'meta+shift+z', 'ctrl+y'], () => {
    hooks.onRedo?.()
    return false
  })
  graph.bindKey(['backspace', 'delete'], () => {
    hooks.onDeleteSelected?.(currentSelection())
    return false
  })
  graph.bindKey(['ctrl+s', 'meta+s'], () => {
    hooks.onSave?.()
    return false
  })

  return graph
}

/**
 * 用 DSL 派生的图元集合更新画布（**就地增量更新，不销毁重建**）。
 *
 * <p><b>为什么不能再用 `fromJSON` 整体重建</b>：清空重建会带来三个连锁问题 ——
 * <ol>
 *   <li>每次改动（含在属性面板里每敲一个字符）都重建全部 DOM，画布**闪烁**；</li>
 *   <li>重建会再次触发 {@code node:change:position}，而该事件会回写 DSL →
 *       形成「重绘 → 位置事件 → 改 DSL → 重绘」的**无限渲染回环**；</li>
 *   <li>选中状态与视口被清空 —— 属性面板会被打回「模板」态。</li>
 * </ol>
 *
 * <p>因此改为：结构性增删走 add/removeCell，其余只更新**真正会变**的东西
 * （节点外观与角标数据、连线外观与分支名、连线的附着端口、以及确实变化了的坐标）。
 * 路由 / 连接器 / zIndex 由节点身份与 {@link toGraphData} 决定，故不重复写入
 * （避免无谓的视图抖动）。
 *
 * <p><b>端口必须每次比对</b>：端口方位由两节点的相对位置推导
 * （`pickPortSides`），拖动任一节点都会改变推导结果 —— 不算在内，线就会挂在旧边上。
 */
export function applyDataset(graph: Graph, dataset: GraphDataset): void {
  // 记一份：拖端点重连被拒时，要按它把画面拉回 DSL 的样子（见 edge:connected）
  lastDatasets.set(graph, dataset)
  // 本轮的位置事件是本层自己写坐标产生的，必须忽略（见 applyingDatasets 的说明），
  // 否则「渲染 → 位置事件 → 回写 DSL → 渲染」会形成回环
  applyingDatasets.add(graph)
  try {
    applyDatasetInternal(graph, dataset)
  } finally {
    applyingDatasets.delete(graph)
  }
}

function applyDatasetInternal(graph: Graph, dataset: GraphDataset): void {
  const incoming = new Map<string, GraphNodeMeta | GraphEdgeMeta>()
  for (const node of dataset.nodes) {
    incoming.set(node.id, node)
  }
  for (const edge of dataset.edges) {
    incoming.set(edge.id, edge)
  }

  // 1) 删除 DSL 中已不存在的图元
  for (const cell of graph.getCells()) {
    if (!incoming.has(cell.id)) {
      graph.removeCell(cell)
    }
  }

  // 2) 节点先就位。顺序是必须的：边的几何从端点推导，所以必须先动节点、再挂边。
  const moved = new Set<string>()
  for (const meta of dataset.nodes) {
    const existing = graph.getCellById(meta.id)
    if (!existing) {
      graph.addNode(meta as never)
      continue
    }
    if (!existing.isNode()) {
      continue
    }
    existing.setAttrs(meta.attrs as never)
    // 角标三态（含描边变红）随校验结果变化
    existing.setData(meta.data as never)
    // 坐标只有真的不同才写。**刻意不加 silent**：silent 会把 X6 的视图更新一起挡掉，
    // 节点在界面上不动、连线却按新坐标重画 → 线与节点错位。
    // 回环由 applyingDatasets 挡住（见其说明）。
    const current = existing.position()
    if (Math.round(current.x) !== meta.x || Math.round(current.y) !== meta.y) {
      existing.setPosition(meta.x, meta.y)
      moved.add(meta.id)
    }
  }

  // 3) 连线：外观、标签与**附着端点**。
  //    端点节点刚被静默挪过的边必须重挂，否则线会留在旧坐标上、与节点脱开
  //    （表现为「点了自动排版之后线断了/只剩一个箭头」）。
  for (const meta of dataset.edges) {
    const existing = graph.getCellById(meta.id)
    if (!existing) {
      graph.addEdge(meta as never)
      continue
    }
    if (!existing.isEdge()) {
      continue
    }
    existing.setAttrs(meta.attrs as never)
    // 标签与基础线样式写进 data（而不是直接 setLabels）：
    // 「何时显示标签」由 syncEdgePresentation 决定，基础样式只保留在 data 里这一份
    existing.setData(meta.data as never)

    const currentSource = existing.getSource() as { cell?: string; port?: string } | undefined
    if (needsReanchor(currentSource, meta.source, moved.has(meta.source.cell))) {
      existing.setSource(meta.source as never)
    }
    const currentTarget = existing.getTarget() as { cell?: string; port?: string } | undefined
    if (needsReanchor(currentTarget, meta.target, moved.has(meta.target.cell))) {
      existing.setTarget(meta.target as never)
    }

    // 固定走线点（回边车道）：走线点是由节点坐标**推导**出来的，节点一动它就失效 ——
    // 不重写的话，自动排版之后回边会按旧车道画，重新穿过节点（用户看到的就是"排完版线穿框"）。
    // 没有车道的边要显式清空：一条回边被拉到目标下方之后就不再是回边，旧走线点必须丢掉。
    const nextVertices = meta.vertices ?? []
    const currentVertices = existing.getVertices()
    const sameVertices =
      currentVertices.length === nextVertices.length &&
      currentVertices.every(
        (point, index) => point.x === nextVertices[index].x && point.y === nextVertices[index].y,
      )
    if (!sameVertices) {
      existing.setVertices(nextVertices as never)
    }
  }

  // 呈现态必须由它们收尾：上面的 setAttrs 会把「选中高亮」覆盖回基础样式，
  // 而标签 / 端口的显隐也不由元数据决定（见三个 sync 的说明）
  syncEdgePresentation(graph, connectingGraphs.has(graph))
  syncNodeSelection(graph)
  syncPortVisibility(graph)
}

/** 销毁画布（组件卸载时调用，避免内存泄漏） */
export function destroyGraph(graph: Graph): void {
  // 先摘掉挂在 window 上的监听：否则组件卸载后残留，指针一动还会去操作已销毁的图
  domCleanups.get(graph)?.()
  domCleanups.delete(graph)
  graph.dispose()
}
