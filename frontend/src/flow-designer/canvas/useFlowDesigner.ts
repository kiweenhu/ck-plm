/**
 * CK-PLM 流程设计器 · 画布状态与动作（Vue 组合式封装）
 *
 * <p>把「DSL 状态 + 校验 + 图元映射 + 快照栈」收拢成一个可复用单元，
 * 让 `FlowCanvas.vue` 只负责渲染与事件转发。
 *
 * <p>所有改动都走同一条路径：<b>算新 DSL → commit</b>。
 * 图元永远由 DSL 派生，不存在「图上改了但 DSL 没变」的状态。
 */

import { computed, ref } from 'vue'
import {
  ALL_NODE_TYPES,
  NodeType,
  SETUP_ASSIGNEE_FORM_CODE,
  addEdge,
  addNode,
  autoLayout,
  createEmptyDsl,
  defaultNodeSize,
  moveNode as moveNodeOp,
  presetById,
  rebindEdge,
  removeEdge,
  removeNode,
  replaceEdge,
  replaceMeta,
  setEdgeWaypoints,
  replaceNode,
  setEdgeAnchor,
  setVariables,
  validateDsl,
  type FlowDsl,
  type FlowEdge,
  type FlowMeta,
  type FlowNode,
  type NodeType as NodeTypeT,
  type ValidateOptions,
  type VariableDef,
} from '@flow-dsl-core'
import { DslHistory, sameDsl } from './undo-stack'
import { badgeStateOf, geometryOf, issuesOf, toGraphData } from './render'
import { anchorsOf } from './node-style'

/**
 * 画布状态与动作。
 *
 * @param initial 初始 DSL（不给则按 meta 建一份空流程）
 * @param meta    空流程的模板属性
 * @param optionsProvider <b>校验的外部输入</b>（如"系统启用了哪些通知渠道"）。
 *        做成<b>取值函数</b>而不是直接传值：系统配置是异步取到的，传值只会在挂载那一刻定住，
 *        之后取到了也不会重新校验。由调用方（app 层）提供，画布层因此不必知道 `@/api`。
 */
export function useFlowDesigner(
  initial?: FlowDsl,
  meta?: FlowMeta,
  optionsProvider?: () => ValidateOptions | undefined,
) {
  const dsl = ref<FlowDsl>(
    initial ?? createEmptyDsl(meta ?? { key: 'new_flow', name: '新建流程' }),
  )
  const history = new DslHistory()
  history.reset(dsl.value)

  // 读 optionsProvider() 里的响应式引用（系统渠道取到时）会让这个 computed 自动重算
  const report = computed(() => validateDsl(dsl.value, optionsProvider?.()))
  const dataset = computed(() => toGraphData(dsl.value, report.value))
  const canSave = computed(() => report.value.ok)
  const version = ref(0)

  const selection = ref<{ nodeIds: string[]; edgeIds: string[] }>({ nodeIds: [], edgeIds: [] })
  const canUndo = ref(false)
  const canRedo = ref(false)
  /** 最近一次被拒的原因（供 UI 提示） */
  const lastReject = ref<string | null>(null)

  function syncHistoryFlags(): void {
    canUndo.value = history.canUndo
    canRedo.value = history.canRedo
  }

  /** 提交一个新 DSL：更新状态并压入快照栈 */
  function commit(next: FlowDsl): void {
    dsl.value = next
    history.push(next)
    version.value += 1
    syncHistoryFlags()
  }

  /**
   * 不压栈的更新（拖动过程用）。
   *
   * <p>无实际变化时不入状态：DSL 是画布的重绘依据，若「内容相同但对象不同」也赋值，
   * 就会白白触发一次重绘，并可能与其他回写路径叠加成渲染回环。
   */
  function patchSilently(next: FlowDsl): void {
    if (sameDsl(next, dsl.value)) {
      return
    }
    dsl.value = next
    version.value += 1
  }

  // ==================== 编辑动作 ====================

  /**
   * 在画布上新增节点。
   *
   * <p>第 1 个参数是<b>节点库条目 id</b>（缺省与类型同名的条目即通用入口），
   * 也接受直接给类型 —— 程序化调用（测试、模板生成）不必先造条目。
   *
   * <p>节点的结构初值仍由 {@link defaultNodeOf} 按类型给出（业务默认值属于类型，
   * 不该散落在节点库条目上）；条目额外带的 {@code prefill} 只在"从节点库拖出来"时生效，
   * 例如「设置状态」预置好服务与节点名 —— 它写的是 DSL 里的普通字段，往返不丢。
   */
  function addNodeAt(presetIdOrType: string, position: { x: number; y: number }): FlowNode {
    const preset = presetById(presetIdOrType)
    const type = (preset?.type ?? presetIdOrType) as NodeTypeT
    // 未知类型直接拒绝：若放行会造出 type 非法的节点（等到保存时才被 zod 拦下就晚了）
    if (!ALL_NODE_TYPES.includes(type)) {
      throw new Error(`未知的节点类型: ${presetIdOrType}`)
    }
    const id = nextNodeId(type)
    const node = defaultNodeOf(type, id, preset?.prefill)
    commit(addNode(dsl.value, node, position))
    return node
  }

  /**
   * 新增连线。
   *
   * <p><b>必须把失败原因返回给调用方</b>：连线合法性由 `canConnect` 判定，
   * 非法时若只是内部吞掉，界面上会出现「拖了一下、线不见了、没有任何提示」的最差体验
   * （画布那边已经把 X6 的临时线删掉了）。由调用方弹出原因。
   */
  function connect(
    source: string,
    target: string,
    ports?: { sourcePort?: string; targetPort?: string },
  ): { ok: boolean; error?: string } {
    try {
      let next = addEdge(dsl.value, source, target)
      // 用户起手点中的锚点 / 落点吸附到的锚点：一并记下来。
      // 不记就等于"画布上别的锚点点了也没用"—— 渲染时会按自动分配把它拉回中点。
      const sourcePort = validAnchor(source, ports?.sourcePort)
      const targetPort = validAnchor(target, ports?.targetPort)
      if (sourcePort || targetPort) {
        const before = new Set(dsl.value.edges.map((edge) => edge.id))
        const created = next.edges.find((edge) => !before.has(edge.id))
        if (created) {
          if (sourcePort) {
            next = setEdgeAnchor(next, created.id, 'source', sourcePort)
          }
          if (targetPort) {
            next = setEdgeAnchor(next, created.id, 'target', targetPort)
          }
        }
      }
      commit(next)
      lastReject.value = null
      return { ok: true }
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error)
      lastReject.value = reason
      return { ok: false, error: reason }
    }
  }

  /**
   * 该锚点在这条连线的那一端是否真实存在。
   *
   * <p><b>存在就认</b> —— 用户按在哪个点上，线就从哪个点出去。这里刻意不再做
   * "是否朝向对方"的取舍：那样会静默丢弃用户的选择（按了右侧的点却被拉到中点），
   * 而"线会不会绕一圈"是画布观感问题，不该由代码替用户决定；想回到自动，
   * 面板里选「自动」即可。只拦一种情况：锚点在这类节点上根本不存在（类型改了、老数据里的失效 id）。
   */
  function validAnchor(nodeId: string, portId?: string): string | undefined {
    if (!portId) {
      return undefined
    }
    const node = dsl.value.nodes.find((n) => n.id === nodeId)
    if (!node) {
      return undefined
    }
    const exists = anchorsOf(node.type, defaultNodeSize(node.type)).some((anchor) => anchor.id === portId)
    return exists ? portId : undefined
  }

  /**
   * 拖动已有连线的端点（重连）：改两端 + 锚点，<b>其余配置原样保留</b>。
   *
   * <p>为什么要有它：此前想改一条线的起止点只能删了重画 —— 分支名、类型、条件、
   * 路由全部要重设一遍。这里只动 {@code source/target/anchor}，是一条"改接"而不是"重建"。
   *
   * <p>锚点只在<b>精确拖到圆点上</b>时才写（`anchor` 由画布层判定后给出）；落在节点附近
   * 一律写 undefined = 回到自动分配 —— 否则端点会被吸到背对的一侧，线绕节点兜一大圈
   * （真实反馈）。只动被拖的那一端，另一端保持原样。
   */
  function reconnectEdge(connection: {
    edgeId: string
    source: string
    target: string
    end: 'source' | 'target'
    anchor?: string
  }): { ok: boolean; error?: string } {
    try {
      // 顺序要紧：rebindEdge 会清掉"换过那一端"的旧锚点（旧节点的锚点对新节点无意义），
      // 再由 setEdgeAnchor 写入这一端的实际落点 —— 传 undefined 即"回到自动分配"，
      // 这正是"端点随手落在节点附近"时该有的结果（否则端点会被吸到背对的一侧、线绕一圈）。
      let next = rebindEdge(dsl.value, connection.edgeId, connection.source, connection.target)
      next = setEdgeAnchor(next, connection.edgeId, connection.end, connection.anchor)
      commit(next)
      lastReject.value = null
      return { ok: true }
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error)
      lastReject.value = reason
      return { ok: false, error: reason }
    }
  }

  /**
   * 拖动更新节点坐标（**幂等**）。
   *
   * <p>位置没变就什么都不做。这不只是省一次计算 —— 画布重绘会再次触发
   * `node:change:position`，若这里无条件替换 DSL 对象，就会形成
   * 「重绘 → 位置事件 → 改 DSL → 重绘」的**无限渲染回环**（表现为画布持续闪烁）。
   */
  function moveNode(id: string, position: { x: number; y: number }): void {
    if (!dsl.value.nodes.some((n) => n.id === id)) {
      return
    }
    const layout = dsl.value.layout.nodes[id]
    if (layout && layout.x === position.x && layout.y === position.y) {
      return
    }
    if (!layout && position.x === 0 && position.y === 0) {
      return
    }
    patchSilently(moveNodeOp(dsl.value, id, position))
  }

  /** 拖动结束：把当前位置作为一步快照 */
  function commitMove(): void {
    history.push(dsl.value)
    syncHistoryFlags()
  }

  /**
   * 用属性面板产出的完整节点替换现有节点。
   *
   * <p>必须用「替换」而非「浅合并」：面板可能删除嵌套字段，
   * 而合并无法表达删除（详见 dsl-core `replaceNode` 的说明）。
   */
  function applyNode(nodeId: string, node: FlowNode): void {
    commit(replaceNode(dsl.value, nodeId, node))
  }

  function applyEdge(edgeId: string, edge: FlowEdge): void {
    commit(replaceEdge(dsl.value, edgeId, edge))
  }

  /**
   * 用户拖动折角：把手动折点写进 DSL。
   *
   * <p>与手动锚点同级，都是**用户意图** —— 写进去之后，自动走线与自动排版都不再覆盖它的走线。
   * 空数组（折点被拖没了）= 回到自动走线。
   */
  function applyEdgeWaypoints(edgeId: string, points: Array<{ x: number; y: number }>): void {
    commit(setEdgeWaypoints(dsl.value, edgeId, points))
  }

  function applyMeta(meta: FlowMeta): void {
    commit(replaceMeta(dsl.value, meta))
  }

  function applyVariables(variables: VariableDef[]): void {
    commit(setVariables(dsl.value, variables))
  }

  function removeSelection(selectionToRemove = selection.value): { removedNodes: number; removedEdges: number } {
    let next = dsl.value
    let removedNodes = 0
    let removedEdges = 0
    for (const edgeId of selectionToRemove.edgeIds) {
      next = removeEdge(next, edgeId)
      removedEdges += 1
    }
    for (const nodeId of selectionToRemove.nodeIds) {
      const exists = next.nodes.some((n) => n.id === nodeId)
      if (!exists) {
        continue
      }
      next = removeNode(next, nodeId)
      removedNodes += 1
    }
    if (removedNodes > 0 || removedEdges > 0) {
      commit(next)
      selection.value = { nodeIds: [], edgeIds: [] }
    }
    return { removedNodes, removedEdges }
  }

  function runAutoLayout(): void {
    commit(autoLayout(dsl.value))
  }

  // ==================== 撤销 / 重做 ====================

  function undo(): void {
    const previous = history.undo()
    if (previous) {
      dsl.value = previous
      version.value += 1
    }
    syncHistoryFlags()
  }

  function redo(): void {
    const next = history.redo()
    if (next) {
      dsl.value = next
      version.value += 1
    }
    syncHistoryFlags()
  }

  // ==================== 只读派生（供面板与角标使用） ====================

  function badgeOf(nodeId: string) {
    return badgeStateOf(report.value, nodeId)
  }

  function issuesOfNode(nodeId: string): string[] {
    return issuesOf(report.value, nodeId)
  }

  function geometryOfNode(nodeId: string) {
    const node = dsl.value.nodes.find((n) => n.id === nodeId)
    return node ? geometryOf(dsl.value, node) : undefined
  }

  function replaceDsl(next: FlowDsl): void {
    dsl.value = next
    history.reset(next)
    version.value += 1
    syncHistoryFlags()
  }

  return {
    // 状态
    dsl,
    report,
    dataset,
    version,
    selection,
    canUndo,
    canRedo,
    canSave,
    lastReject,
    // 动作
    commit,
    addNodeAt,
    connect,
    reconnectEdge,
    moveNode,
    commitMove,
    applyNode,
    applyEdge,
    applyEdgeWaypoints,
    applyMeta,
    applyVariables,
    removeSelection,
    runAutoLayout,
    undo,
    redo,
    replaceDsl,
    // 派生
    badgeOf,
    issuesOfNode,
    geometryOfNode,
  }
}

/** 生成不与现有节点冲突的 id */
function nextNodeId(type: NodeTypeT): string {
  const prefix = type.toLowerCase()
  return `${prefix}_${Math.random().toString(36).slice(2, 8)}`
}

/**
 * 各类型节点的初始结构（保证「新建即可通过结构校验」，业务必填项交给 validate 提示）。
 *
 * <p>{@code prefill} 是节点库条目自带的初始属性（如「设置状态」的 serviceRef 与节点名）：
 * 覆盖在结构初值之上，但 <b>id / type 不允许被它改写</b> —— 那两个是画布的身份，
 * 条目里写错就会造出"id 与画布登记不一致"的节点，撤销/选中全乱。
 */
function defaultNodeOf(type: NodeTypeT, id: string, prefill?: Record<string, unknown>): FlowNode {
  const base = structuralDefaultOf(type, id)
  return prefill ? ({ ...base, ...prefill, id, type } as FlowNode) : base
}

/** 类型的结构初值（不含节点库条目的 prefill） */
function structuralDefaultOf(type: NodeTypeT, id: string): FlowNode {
  switch (type) {
    case NodeType.START:
      return { id, type, name: '开始', initiator: { strategy: 'INITIATOR' } }
    case NodeType.END:
      return { id, type, name: '结束' }
    case NodeType.APPROVAL:
      return {
        id,
        type,
        name: '审批',
        assignee: { strategy: 'INITIATOR' },
        approvalMode: 'SINGLE',
        // 审批节点天然有两条路由：通过（画布上的出边）与驳回（下面的 reject 声明）。
        // 因此默认就带上驳回路由、且要求填写驳回意见 —— 新拖入的节点即符合审批实务。
        reject: { enabled: true, target: 'PREVIOUS', commentRequired: true },
      }
    case NodeType.SET_ASSIGNEE:
      return {
        id,
        type,
        name: '设置审批人',
        // 执行人默认就是发起人本人（流程 owner）—— 这正是这个活动的业务定位
        assignee: { strategy: 'INITIATOR' },
        // 表单是内置固定的（设置流程参与者）：这里就给上，面板里也只读不可改
        formRef: SETUP_ASSIGNEE_FORM_CODE,
        // 无 reject：设置活动不产生审批结论
      }
    case NodeType.COUNTERSIGN_APPROVAL:
      return {
        id,
        type,
        name: '会签审批',
        // 会签至少要两名参与人，故默认给「指定角色」空列表：结构合法，但会立刻被校验
        // 标为「未配置」—— 默认值不该假装一个会签已经配好了人
        assignee: { strategy: 'ROLE', roleCodes: [] },
        // 默认「全员通过 + 弃权不计入」：最保守的会签口径（宁严不宽）
        passRule: { mode: 'PERCENT', percent: 100, abstain: 'IGNORE' },
        reject: { enabled: true, target: 'PREVIOUS', commentRequired: true },
      }
    case NodeType.TASK:
      return { id, type, name: '办理', assignee: { strategy: 'INITIATOR' } }
    case NodeType.SERVICE:
      // 与节点库入口同名（那里现在只有「函数调用」一条）：结构初值就是用户看到的名字，
      // 两处不一致时会出现"拖出来叫函数调用、导入的节点叫自动服务"这种同物两名
      return { id, type, name: '函数调用' }
    case NodeType.NOTIFY:
      return { id, type, name: '通知', recipients: { strategy: 'INITIATOR' } }
    case NodeType.SUB_PROCESS:
      return { id, type, name: '子流程', processKey: '' }
    case NodeType.TIMER:
      return { id, type, name: '等待', mode: 'DURATION', duration: 'PT1H' }
    case NodeType.EXCLUSIVE_GATEWAY:
      return { id, type, name: '条件分支' }
    case NodeType.PARALLEL_GATEWAY:
      return { id, type, name: '并行分支' }
    case NodeType.INCLUSIVE_GATEWAY:
      return { id, type, name: '包容分支' }
    default:
      return { id, type: NodeType.TASK, name: '任务', assignee: { strategy: 'INITIATOR' } }
  }
}
