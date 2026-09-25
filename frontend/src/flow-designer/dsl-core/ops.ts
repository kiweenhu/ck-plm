/**
 * CK-PLM 流程 DSL · 模型操作（纯函数）
 *
 * <p>全部为不可变操作：入参 DSL 不被修改，返回新 DSL。
 * 画布手势 → 调用此处 ops → 产出新 DSL → 重渲染；撤销/重做即 DSL 快照栈
 * （spec §2.3「单源同步」）。
 *
 * <p>零 DOM / 零 X6 依赖，可直接单测。
 */

import {
  ASSIGNEE_NODE_TYPES,
  DSL_VERSION,
  EdgeKind,
  GATEWAY_TYPES,
  NodeType,
  SETUP_COVERED_TYPES,
  type NodeType as NodeTypeT,
} from './constants'
import type {
  AssigneeSpec,
  FlowDsl,
  FlowEdge,
  FlowNode,
  FlowMeta,
  Layout,
  NodeLayout,
  VariableDef,
} from './schema'

/**
 * 深拷贝 DSL 为<b>纯数据</b>。
 *
 * <p><b>刻意不用 `structuredClone`</b>：调用方传入的通常是 Vue 的响应式 Proxy
 * （`useFlowDesigner` 用 `ref` 持有 DSL，`ref` 会把对象深度转为 Proxy），
 * 而 `structuredClone` 无法克隆 Proxy —— 会直接抛 `DataCloneError`。
 * 由于本函数被<b>所有</b> ops 调用，该异常会打断组件 setup，表现为整页白屏；
 * 而单测里传的是纯对象，永远测不出来（真实事故见 `canvas/__tests__/reactivity-safety.test.ts`）。
 *
 * <p>JSON 往返同时满足两个要求：① 接受任何 JSON 形态输入（含 Proxy）；
 * ② <b>脱离响应式</b> —— 结果必须是纯数据，不能与实时 DSL 共享底层对象。
 * DSL 本身就是纯 JSON 结构（无 Date / 函数 / undefined），故 JSON 往返无损。
 */
export function cloneDsl<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

/**
 * 节点基准尺寸 —— <b>尺寸的唯一事实源</b>。
 *
 * <p>为什么放在契约层而不是画布的外观规格里：尺寸被两处共用 —— 画布（画框与端口）
 * 与编译器（BPMN DI 的坐标）。放在外观层会分裂成两份，之前正是如此：
 * 外观层写 190×68、这里写 180×64，而<b>真正生效的是这里</b>（画布渲染优先取
 * layout 里持久化的这份值），于是「改外观层的尺寸毫无效果」。
 *
 * <p>调整这里的数字<b>立即作用于存量模板</b>：画布渲染不再读 layout 里的宽高 ——
 * 设计器没有缩放节点的能力，那份值只是「创建时的默认值」，是历史快照而非用户数据。
 */
const DEFAULT_NODE_SIZE: Record<string, { width: number; height: number }> = {
  START: { width: 30, height: 30 },
  END: { width: 30, height: 30 },
  /**
   * 活动框：142×44（原 180×64 → 150×52 → 现在）。
   *
   * <p>为什么还能再小：复杂流程（十几个活动 + 分支）在 150×52 下要频繁缩放/平移才看得全，
   * 而活动框里只放"节点名 + 类型"，字号同步降到 11 之后 44 高仍有一行半的余量。
   *
   * <p><b>宽度不能小于 142</b>：锚点把边长四等分（见 canvas 的 portSlotCount），
   * 142 / 4 = 35.5px ≥ 锚点间距下限 34px → 上下两边仍能挂 3 个锚点；
   * 再窄就会掉到"每边只有中点"，而存量模板里已有连线挂在 `bottom-2` / `bottom-3` 上。
   */
  APPROVAL: { width: 142, height: 44 },
  COUNTERSIGN_APPROVAL: { width: 142, height: 44 },
  SET_ASSIGNEE: { width: 142, height: 44 },
  TASK: { width: 142, height: 44 },
  // 自动服务/通知此前矮 4px（48）—— 与其它活动统一高度：同类活动长得一样，画布才好扫
  SERVICE: { width: 142, height: 44 },
  NOTIFY: { width: 142, height: 44 },
  TIMER: { width: 124, height: 42 },
  SUB_PROCESS: { width: 142, height: 44 },
  EXCLUSIVE_GATEWAY: { width: 38, height: 38 },
  PARALLEL_GATEWAY: { width: 38, height: 38 },
  INCLUSIVE_GATEWAY: { width: 38, height: 38 },
}

export function defaultNodeSize(type: NodeTypeT): { width: number; height: number } {
  return DEFAULT_NODE_SIZE[type] ?? { width: 170, height: 60 }
}

/** 生成模板内唯一 id，形如 {@code approval_3} */
export function nextId(dsl: FlowDsl, prefix: string): string {
  const used = new Set<string>([
    ...dsl.nodes.map((n) => n.id),
    ...dsl.edges.map((e) => e.id),
  ])
  const base = prefix.toLowerCase().replace(/[^a-z0-9]/g, '_')
  let i = 1
  while (used.has(`${base}_${i}`)) {
    i += 1
  }
  return `${base}_${i}`
}

/** 创建空白模板：含唯一的 START / END 与二者之间的占位连线 */
export function createEmptyDsl(meta: FlowMeta): FlowDsl {
  const dsl: FlowDsl = {
    dslVersion: DSL_VERSION,
    meta,
    variables: [],
    nodes: [
      { id: 'start_1', type: NodeType.START, name: '开始', initiator: { strategy: 'INITIATOR' } },
      { id: 'end_1', type: NodeType.END, name: '结束' },
    ],
    edges: [{ id: 'edge_1', source: 'start_1', target: 'end_1', kind: EdgeKind.NORMAL }],
    layout: {
      nodes: {
        start_1: { x: 240, y: 40, ...defaultNodeSize(NodeType.START) },
        end_1: { x: 240, y: 200, ...defaultNodeSize(NodeType.END) },
      },
    },
  }
  return dsl
}

/** 新增节点（同时写入 layout，保证「有节点必有坐标」） */
export function addNode(dsl: FlowDsl, node: FlowNode, position: { x: number; y: number }): FlowDsl {
  const next = cloneDsl(dsl)
  next.nodes.push(node)
  next.layout.nodes[node.id] = {
    x: position.x,
    y: position.y,
    ...defaultNodeSize(node.type),
  }
  return next
}

/** 更新节点：浅合并 patch，{@code id} 与 {@code type} 不可改 */
export function updateNode<T extends FlowNode>(
  dsl: FlowDsl,
  nodeId: string,
  patch: Partial<T>,
): FlowDsl {
  const next = cloneDsl(dsl)
  next.nodes = next.nodes.map((n) =>
    n.id === nodeId ? ({ ...n, ...patch, id: n.id, type: n.type } as FlowNode) : n,
  )
  return next
}

/** 删除节点：级联删除其关联连线、布局、分组引用、驳回目标引用 */
export function removeNode(dsl: FlowDsl, nodeId: string): FlowDsl {
  const next = cloneDsl(dsl)
  next.nodes = next.nodes.filter((n) => n.id !== nodeId)
  next.edges = next.edges.filter((e) => e.source !== nodeId && e.target !== nodeId)
  delete next.layout.nodes[nodeId]
  if (next.layout.groups) {
    next.layout.groups = next.layout.groups
      .map((g) => ({ ...g, nodeIds: g.nodeIds.filter((id) => id !== nodeId) }))
      .filter((g) => g.nodeIds.length > 0)
  }
  // 清理指向已删节点的驳回目标，避免留下悬空引用
  // 会签审批同样带驳回目标：漏掉它就会留下指向已删节点的 targetNodeId
  next.nodes = next.nodes.map((n) => {
    if (
      (n.type === NodeType.APPROVAL || n.type === NodeType.COUNTERSIGN_APPROVAL) &&
      n.reject?.targetNodeId === nodeId
    ) {
      return { ...n, reject: { ...n.reject, target: 'INITIATOR', targetNodeId: undefined } }
    }
    return n
  })
  return next
}

/**
 * 连线合法性矩阵（spec §4-A P0）。
 *
 * <p>硬约束在此拦截，返回原因供画布提示；软约束（如多出边需条件）交给 validate。
 */
export function canConnect(
  dsl: FlowDsl,
  source: string,
  target: string,
): { ok: true } | { ok: false; reason: string } {
  const src = dsl.nodes.find((n) => n.id === source)
  const tgt = dsl.nodes.find((n) => n.id === target)
  if (!src || !tgt) {
    return { ok: false, reason: '连线端点不存在' }
  }
  if (source === target) {
    return { ok: false, reason: '不允许自环' }
  }
  if (src.type === NodeType.END) {
    return { ok: false, reason: '结束节点不能有出边' }
  }
  if (tgt.type === NodeType.START) {
    return { ok: false, reason: '开始节点不能有入边' }
  }
  if (dsl.edges.some((e) => e.source === source && e.target === target)) {
    return { ok: false, reason: '两节点之间已存在连线' }
  }
  if (src.type === NodeType.START && dsl.edges.some((e) => e.source === source)) {
    return { ok: false, reason: '开始节点只能有一条出边' }
  }
  return { ok: true }
}

/** 新增连线（内部先过合法性矩阵） */
export function addEdge(
  dsl: FlowDsl,
  source: string,
  target: string,
  options?: Partial<Pick<FlowEdge, 'id' | 'kind' | 'name' | 'route' | 'condition'>>,
): FlowDsl {
  const check = canConnect(dsl, source, target)
  if (!check.ok) {
    throw new Error(check.reason)
  }
  const next = cloneDsl(dsl)
  const sourceType = next.nodes.find((n) => n.id === source)?.type
  /**
   * 出边是否默认带条件：网关出边要配条件，**但并行分支除外**。
   *
   * <p>并行分支的出边一律"无条件全走"（校验规则 {@link IssueCode.PARALLEL_HAS_CONDITION} 明令禁止），
   * 面板在并行分支上也只提供「普通流转」。若这里仍默认条件流转，用户从并行分支拉一条线的
   * **瞬间**就报错，且面板显示的类型（普通流转）与 DSL 里存的值（条件流转）不是一回事 ——
   * 这正是"类型默认不对"的真实反馈。
   */
  const conditionalSource =
    GATEWAY_TYPES.includes(sourceType as NodeTypeT) && sourceType !== NodeType.PARALLEL_GATEWAY
  next.edges.push({
    id: options?.id ?? nextId(next, 'flow'),
    source,
    target,
    kind: options?.kind ?? (conditionalSource ? EdgeKind.CONDITION : EdgeKind.NORMAL),
    name: options?.name,
    // 路由（通过/驳回）也允许在建线时一次给全：否则程序化建流程（测试、模板生成）
    // 得先建线再改一次，多一步撤销记录
    route: options?.route,
    condition: options?.condition,
  })
  return next
}

export function updateEdge(dsl: FlowDsl, edgeId: string, patch: Partial<FlowEdge>): FlowDsl {
  const next = cloneDsl(dsl)
  next.edges = next.edges.map((e) => {
    if (e.id !== edgeId) {
      return e
    }
    const merged = { ...e, ...patch, id: e.id, source: e.source, target: e.target }
    // 改锚点 = 走线两端变了，旧的手动折点随即失效（还可能把线扯成一个怪形状）→ 一起清掉、
    // 回到自动走线。这也是「还原自动走线」的入口：在面板里把锚点选一次「自动」即可。
    if ('anchor' in patch) {
      return { ...merged, waypoints: undefined }
    }
    return merged
  })
  return next
}

/**
 * 设置连线的手动折点（画布把用户拖过的折角报上来）。
 *
 * <p>语义只有一件事：**用户亲手改过这条线的走线**（与 {@link FlowEdge.anchor} 同级）。
 * 传空数组即清掉手动折点、回到自动走线；坐标取整，避免亚像素抖动被写进 DSL。
 */
export function setEdgeWaypoints(
  dsl: FlowDsl,
  edgeId: string,
  points: Array<{ x: number; y: number }>,
): FlowDsl {
  const next = cloneDsl(dsl)
  next.edges = next.edges.map((edge) => {
    if (edge.id !== edgeId) {
      return edge
    }
    const rounded = points.map((point) => ({ x: Math.round(point.x), y: Math.round(point.y) }))
    return rounded.length > 0 ? { ...edge, waypoints: rounded } : { ...edge, waypoints: undefined }
  })
  return next
}

export function removeEdge(dsl: FlowDsl, edgeId: string): FlowDsl {
  const next = cloneDsl(dsl)
  next.edges = next.edges.filter((e) => e.id !== edgeId)
  return next
}

/**
 * 改接连线端点（画布拖拽重连）。
 *
 * <p><b>除了端点，其余一切照旧</b>：分支名、类型、条件、路由、锚点都跟着这条边留下 ——
 * 这正是「改个起止点却只能删了重画、配置全要重设」要解决的问题。
 *
 * <p>但<b>换了节点的那一端，锚点必须清掉</b>：锚点 id 是"该节点上的第几个落点"，
 * 旧节点的 `bottom-3` 挂到新节点上没有意义（甚至不存在）。旧值留着等于给渲染层
 * 埋一个"指向不存在锚点"的隐患 —— 宁可回到自动分配。
 */
export function rebindEdge(dsl: FlowDsl, edgeId: string, source: string, target: string): FlowDsl {
  const next = cloneDsl(dsl)
  const existing = next.edges.find((e) => e.id === edgeId)
  if (!existing) {
    throw new Error(`连线不存在: ${edgeId}`)
  }
  const others: FlowDsl = { ...next, edges: next.edges.filter((e) => e.id !== edgeId) }
  const check = canConnect(others, source, target)
  if (!check.ok) {
    throw new Error(check.reason)
  }
  next.edges = next.edges.map((e) => {
    if (e.id !== edgeId) {
      return e
    }
    const anchor = { ...(e.anchor ?? {}) }
    if (source !== e.source) {
      delete anchor.source
    }
    if (target !== e.target) {
      delete anchor.target
    }
    const rebound = { ...e, source, target }
    if (Object.keys(anchor).length > 0) {
      rebound.anchor = anchor
    } else {
      delete rebound.anchor
    }
    return rebound
  })
  return next
}

/**
 * 手动指定连线某一端的锚点；传 undefined 即<b>恢复自动分配</b>。
 *
 * <p>锚点只影响画布表现（挂在哪一个落点上），不参与 BPMN 语义 ——
 * 但它必须被保存：用户手动摆好的位置，下一次打开不能变。
 */
export function setEdgeAnchor(
  dsl: FlowDsl,
  edgeId: string,
  end: 'source' | 'target',
  anchor: string | undefined,
): FlowDsl {
  const next = cloneDsl(dsl)
  next.edges = next.edges.map((e) => {
    if (e.id !== edgeId) {
      return e
    }
    const merged = { ...(e.anchor ?? {}), [end]: anchor }
    // 两端都清空时整块删掉，避免留下 `anchor: {}` 这种"看着配了、其实没配"的脏数据
    const empty = !merged.source && !merged.target
    return { ...e, anchor: empty ? undefined : merged }
  })
  return next
}

/** 设置某网关的出边中哪一条是默认分支（同一时刻只允许一条） */
export function setDefaultEdge(dsl: FlowDsl, gatewayId: string, edgeId: string): FlowDsl {
  const next = cloneDsl(dsl)
  next.edges = next.edges.map((e) => {
    if (e.source !== gatewayId) {
      return e
    }
    if (e.id === edgeId) {
      return { ...e, kind: EdgeKind.DEFAULT, condition: undefined }
    }
    // 原默认分支降级为条件分支（保留名字，条件需用户补配 → 由 validate 提示）
    return e.kind === EdgeKind.DEFAULT ? { ...e, kind: EdgeKind.CONDITION } : e
  })
  return next
}

/** 移动节点：只改 layout，不触碰语义 */
export function moveNode(dsl: FlowDsl, nodeId: string, position: { x: number; y: number }): FlowDsl {
  const next = cloneDsl(dsl)
  const current: NodeLayout = next.layout.nodes[nodeId] ?? {
    x: 0,
    y: 0,
    ...defaultNodeSize(next.nodes.find((n) => n.id === nodeId)?.type as NodeTypeT),
  }
  next.layout.nodes[nodeId] = { ...current, x: position.x, y: position.y }
  return next
}

export function setLayout(dsl: FlowDsl, layout: Layout): FlowDsl {
  const next = cloneDsl(dsl)
  next.layout = layout
  return next
}

export function updateMeta(dsl: FlowDsl, patch: Partial<FlowMeta>): FlowDsl {
  const next = cloneDsl(dsl)
  next.meta = { ...next.meta, ...patch }
  return next
}

// ==================== 整对象替换（属性面板写入用）====================

/**
 * 整对象替换节点。
 *
 * <p><b>为什么需要它</b>：属性面板的写入不是「打补丁」，而是「换一份完整配置」——
 * 字段可见性变化会<b>删除</b>嵌套字段（把审批模式从「会签」改成「单签」要移掉
 * `passRule`；把驳回关掉要移掉 `reject`）。而 {@link updateNode} 的浅合并
 * 表达不了删除：patch 里没有的键会保留旧值，于是 DSL 里留下
 * 「看着配了、其实不该存在」的数据，进而绕过「未配置」判定。
 *
 * <p>`id` 与 `type` 以参数为准，节点身份不可被面板改写。
 */
export function replaceNode(dsl: FlowDsl, nodeId: string, node: FlowNode): FlowDsl {
  const next = cloneDsl(dsl)
  next.nodes = next.nodes.map((n) =>
    // 类型断言必要：展开判别联合再回写 type 会丢失字面量收窄
    n.id === nodeId ? ({ ...node, id: nodeId, type: n.type } as FlowNode) : n,
  )
  return next
}

/**
 * 整对象替换连线（端点与 id 不可被面板改写）。
 *
 * <p>同时维护一条<b>模型不变量</b>：一个分支节点最多只有一条默认分支。
 * 把某条边置为默认分支时，同一源节点的其他默认分支自动降级 ——
 * 否则用户在面板里点两次就能造出「两条默认分支」，只能靠校验报错，
 * 再让他自己去猜该取消哪一条。不变量由写入路径保证，比事后报错可靠。
 */
export function replaceEdge(dsl: FlowDsl, edgeId: string, edge: FlowEdge): FlowDsl {
  const next = cloneDsl(dsl)
  const sourceId = next.edges.find((e) => e.id === edgeId)?.source
  const becomesDefault = edge.kind === EdgeKind.DEFAULT && sourceId !== undefined
  next.edges = next.edges.map((e) => {
    if (e.id === edgeId) {
      return { ...edge, id: edgeId, source: e.source, target: e.target }
    }
    // 同源的其他默认分支降级：带条件的回到条件流转，否则回到普通流转
    if (becomesDefault && e.source === sourceId && e.kind === EdgeKind.DEFAULT) {
      return { ...e, kind: e.condition ? EdgeKind.CONDITION : EdgeKind.NORMAL }
    }
    return e
  })
  return next
}

/** 整对象替换模板元信息（清空字段时能真正删除键） */
export function replaceMeta(dsl: FlowDsl, meta: FlowMeta): FlowDsl {
  const next = cloneDsl(dsl)
  next.meta = meta
  return next
}

/** 替换流程变量列表（模板根字段） */
export function setVariables(dsl: FlowDsl, variables: VariableDef[]): FlowDsl {
  const next = cloneDsl(dsl)
  next.variables = variables.length ? variables : undefined
  return next
}

// ==================== 查询工具（画布与面板共用）====================

export function findNode(dsl: FlowDsl, nodeId: string): FlowNode | undefined {
  return dsl.nodes.find((n) => n.id === nodeId)
}

export function outgoingEdges(dsl: FlowDsl, nodeId: string): FlowEdge[] {
  return dsl.edges.filter((e) => e.source === nodeId)
}

export function incomingEdges(dsl: FlowDsl, nodeId: string): FlowEdge[] {
  return dsl.edges.filter((e) => e.target === nodeId)
}

// ==================== 参与人查询（「设置审批人」相关，跨节点规则共用）====================

/**
 * 取节点的参与人声明。
 *
 * <p>通知节点用的是 `recipients`，其余参与人节点用 `assignee`。
 * 「设置审批人」活动自身的执行人<b>不算</b>「需要被指定的人员」，故返回 undefined ——
 * 否则它会把自己也算进任务表单（自指）。
 */
export function participantSpecOf(node: FlowNode): AssigneeSpec | undefined {
  if (!ASSIGNEE_NODE_TYPES.includes(node.type) || node.type === NodeType.SET_ASSIGNEE) {
    return undefined
  }
  if (node.type === NodeType.NOTIFY) {
    return (node as { recipients?: AssigneeSpec }).recipients
  }
  return (node as { assignee?: AssigneeSpec }).assignee
}

/** 流程里的「设置审批人」活动 */
export function setupNodes(dsl: FlowDsl): FlowNode[] {
  return dsl.nodes.filter((node) => node.type === NodeType.SET_ASSIGNEE)
}

/**
 * 被「设置审批人」活动覆盖的人工活动 id 集合。
 *
 * <p>判定规则：**类型属于 {@link SETUP_COVERED_TYPES}（审批 / 会签 / 办理）
 * 且位于任一设置活动的下游**。刻意<b>不要求活动自己声明</b> ——
 * 「设置审批人」的职责就是把后面所有人工活动的人一次指定好，覆盖是自动的。
 *
 * <p>这是「谁的人员会被设置活动改写」的<b>唯一实现</b>：校验、任务表单、编译三处共用。
 * 否则会出现「表单列出了某个活动、编译产物却没给它绑定变量」这种两处真相。
 */
export function setupControlledIds(dsl: FlowDsl): Set<string> {
  const ids = new Set<string>()
  const setters = setupNodes(dsl)
  if (setters.length === 0) {
    return ids
  }
  for (const node of dsl.nodes) {
    if (!SETUP_COVERED_TYPES.includes(node.type)) {
      continue
    }
    if (setters.some((setter) => canReach(dsl, setter.id, node.id))) {
      ids.add(node.id)
    }
  }
  return ids
}

/**
 * 从 fromId 出发能否到达 targetId（含自身）。
 *
 * <p>用于判定「设置审批人活动是否在该活动的上游」—— 不在上游就填不生效。
 */
export function canReach(dsl: FlowDsl, fromId: string, targetId: string): boolean {
  if (fromId === targetId) {
    return true
  }
  const visited = new Set<string>([fromId])
  const queue: string[] = [fromId]
  while (queue.length > 0) {
    const current = queue.shift() as string
    for (const edge of dsl.edges) {
      if (edge.source !== current) {
        continue
      }
      if (edge.target === targetId) {
        return true
      }
      if (!visited.has(edge.target)) {
        visited.add(edge.target)
        queue.push(edge.target)
      }
    }
  }
  return false
}

/** 从 START 出发的广度优先可达节点集合 */
export function reachableNodeIds(dsl: FlowDsl): Set<string> {
  const start = dsl.nodes.find((n) => n.type === NodeType.START)
  const visited = new Set<string>()
  if (!start) {
    return visited
  }
  const queue: string[] = [start.id]
  visited.add(start.id)
  while (queue.length > 0) {
    const current = queue.shift() as string
    for (const edge of dsl.edges) {
      if (edge.source === current && !visited.has(edge.target)) {
        visited.add(edge.target)
        queue.push(edge.target)
      }
    }
  }
  return visited
}

/**
 * 找出会形成环的「回边」—— DFS 三色法：指向仍在当前递归栈上（灰色）的节点，即为回边。
 *
 * <p>自环（source === target）同样算回边。返回的是<b>边 id 集合</b>，只用于让布局把这些边
 * 排除在层级传播之外；边本身与语义都不动。
 */
function findBackEdges(dsl: FlowDsl): Set<string> {
  const outgoing = new Map<string, { id: string; target: string }[]>()
  dsl.nodes.forEach((node) => outgoing.set(node.id, []))
  dsl.edges.forEach((edge) => {
    if (!outgoing.has(edge.source)) {
      outgoing.set(edge.source, [])
    }
    ;(outgoing.get(edge.source) as { id: string; target: string }[]).push({
      id: edge.id,
      target: edge.target,
    })
  })

  const WHITE = 0
  const GRAY = 1
  const BLACK = 2
  const state = new Map<string, number>()
  const backEdges = new Set<string>()

  const visit = (id: string): void => {
    state.set(id, GRAY)
    for (const edge of outgoing.get(id) ?? []) {
      // 端点不存在（脏数据）：跳过，不参与判断
      if (!outgoing.has(edge.target)) {
        continue
      }
      const targetState = state.get(edge.target) ?? WHITE
      if (targetState === GRAY) {
        backEdges.add(edge.id)
      } else if (targetState === WHITE) {
        visit(edge.target)
      }
    }
    state.set(id, BLACK)
  }

  dsl.nodes.forEach((node) => {
    if ((state.get(node.id) ?? WHITE) === WHITE) {
      visit(node.id)
    }
  })
  return backEdges
}

/** 自动排版：按拓扑层级纵向排布（spec §4-A「一键自动排版」） */
export function autoLayout(dsl: FlowDsl, options?: { xGap?: number; yGap?: number }): FlowDsl {
  const xGap = options?.xGap ?? 240
  const yGap = options?.yGap ?? 110
  const next = cloneDsl(dsl)

  /**
   * 先剔除「回边」（会成环的边），把图变成 DAG —— <b>这一步是防卡死的关键</b>。
   *
   * <p>下面的层级计算是「最长路径松弛」：无环图上它必然收敛，但环上的节点会被反复抬高 ——
   * 层级每绕一圈 +1、目标节点重新入队，队列永远排不空。实测表现就是：
   * 点一下「自动排版」，整个画布卡死（主线程被占满，页面不再响应）。
   *
   * <p>驳回 / 退回在流程里本来就画成回边（会签 → 设置对象状态 → 校对 → 回到会签），
   * 属常态，不能靠"别画环"来回避。
   */
  const backEdges = findBackEdges(next)
  const acyclicEdges = next.edges.filter((edge) => !backEdges.has(edge.id))

  const inDegree = new Map<string, number>()
  next.nodes.forEach((n) => inDegree.set(n.id, 0))
  acyclicEdges.forEach((e) => inDegree.set(e.target, (inDegree.get(e.target) ?? 0) + 1))

  const level = new Map<string, number>()
  const startNodes = next.nodes.filter((n) => (inDegree.get(n.id) ?? 0) === 0)
  const queue: string[] = startNodes.map((n) => n.id)
  startNodes.forEach((n) => level.set(n.id, 0))
  // 无环图上会终止；仍留一道硬上限：宁可排版不完美，也绝不把页面卡死
  let guard = next.nodes.length * acyclicEdges.length + 1000
  while (queue.length > 0 && guard-- > 0) {
    const current = queue.shift() as string
    const currentLevel = level.get(current) ?? 0
    for (const edge of acyclicEdges.filter((e) => e.source === current)) {
      const nextLevel = currentLevel + 1
      if ((level.get(edge.target) ?? -1) < nextLevel) {
        level.set(edge.target, nextLevel)
        queue.push(edge.target)
      }
    }
  }

  // 只经回边可达的节点（整块都在环里，没有入度为 0 的入口）：排在已知层之下，
  // 否则它们会落到 `?? 0` 上、与开始节点叠在同一层
  const knownLevels = [...level.values()]
  const fallbackLevel = (knownLevels.length ? Math.max(...knownLevels) : 0) + 1
  next.nodes.forEach((n) => {
    if (!level.has(n.id)) {
      level.set(n.id, fallbackLevel)
    }
  })

  const byLevel = new Map<number, string[]>()
  next.nodes.forEach((n) => {
    const lv = level.get(n.id) ?? 0
    if (!byLevel.has(lv)) {
      byLevel.set(lv, [])
    }
    ;(byLevel.get(lv) as string[]).push(n.id)
  })
  /**
   * 同一层内**保持用户原来的左右顺序**：按排版前的 x 升序（稳定排序，x 相同则保持节点在 DSL 里的先后）。
   *
   * <p><b>为什么这条不能省</b>：分支网关的出/入边锚点常被手动钉在左/右（语义是"这条从左边出"），
   * 而按 DSL 数组顺序排会把同层两个分支**对调** —— 锚点随即"穿帮"：本该从左出的线指向了右侧的节点，
   * 两条线在半路交叉并在网关正下方的同一段走廊里重叠（实测 flow_5 × flow_11 重叠 42px、
   * flow_12 × flow_13 同理）。用户的排布本来就是"左的还在左"。
   */
  byLevel.forEach((ids) => {
    ids.sort((a, b) => (next.layout.nodes[a]?.x ?? 0) - (next.layout.nodes[b]?.x ?? 0))
  })

  const centerX = 400
  byLevel.forEach((ids, lv) => {
    ids.forEach((id, index) => {
      const node = next.nodes.find((n) => n.id === id) as FlowNode
      const size = defaultNodeSize(node.type)
      const offset = (index - (ids.length - 1) / 2) * xGap
      next.layout.nodes[id] = {
        x: centerX + offset - size.width / 2,
        y: 40 + lv * yGap,
        ...size,
      }
    })
  })
  return next
}
