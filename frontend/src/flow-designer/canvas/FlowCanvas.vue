<template>
  <div class="flow-canvas-wrap">
    <div
      ref="containerRef"
      class="flow-canvas"
      @dragover.prevent="onDragOver"
      @drop="onDrop"
    ></div>
    <!-- 空图引导：连线只能从端口起手，把说明直接放在画布上，避免用户找不到入口 -->
    <div v-if="showConnectHint" class="connect-hint">
      还没有连线 —— 把鼠标移到节点上，从显形的小圆点拖到目标节点即可连线（也可直接从节点内部拖出）
    </div>
  </div>
</template>

<script setup>
/**
 * CK-PLM 流程设计器 · 画布组件
 *
 * <p>只负责：承载 X6 容器、把 DSL 渲染成图、把手势转成事件。
 * 业务状态与校验全部来自 props（由 useFlowDesigner 提供），本组件不持有 DSL。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { canConnect } from '@flow-dsl-core'
import { applyDataset, createFlowGraph, destroyGraph } from './graph'
import { toGraphData } from './render'

const props = defineProps({
  /** 流程 DSL（唯一事实源，由上层持有） */
  dsl: { type: Object, required: true },
  /** 校验报告（驱动节点角标） */
  report: { type: Object, default: null },
  /** 从节点库拖拽时使用的 dataTransfer 键名（载荷为节点库条目 id） */
  dragDataType: { type: String, default: 'application/x-ckplm-node-preset' },
})

const emit = defineEmits([
  'node-moved',
  'node-move-end',
  'connected',
  /** 已有连线的端点被拖动（重连）：只改两端与锚点，其余配置保留 */
  'edge-reconnected',
  /** 连线的折角被拖动：把折点（用户意图）交给上层写进 DSL */
  'edge-waypoints-changed',
  'selection-changed',
  'delete-selected',
  'undo',
  'redo',
  'save',
  'reject',
  'drop-node',
])

const containerRef = ref(null)
let graph = null

/** 图上还没有任何连线时，显示连线手势引导 */
const showConnectHint = computed(() => (props.dsl?.edges?.length ?? 0) === 0)

function render() {
  if (!graph) return
  applyDataset(graph, toGraphData(props.dsl, props.report))
}

onMounted(() => {
  if (!containerRef.value) return
  graph = createFlowGraph(containerRef.value, {
    // 判定矩阵的唯一实现在 dsl-core：本组件只把当前 DSL 快照传进去，不重复实现规则。
    // 拖动已有连线的端点时（excludeEdgeId 有值）先把这条线自己摘掉再判 ——
    // 否则「两节点之间已存在连线」会命中它自己：拖到同一节点的另一个锚点会被判非法、线被弹回，
    // 用户就只能回属性面板改锚点了（这正是本次要修的现象）。
    canConnect: (source, target, excludeEdgeId) => {
      const scope = excludeEdgeId
        ? { ...props.dsl, edges: props.dsl.edges.filter((edge) => edge.id !== excludeEdgeId) }
        : props.dsl
      return canConnect(scope, source, target)
    },
    onNodeMoved: (nodeId, position) => emit('node-moved', nodeId, position),
    onNodeMoveEnd: () => emit('node-move-end'),
    onConnected: (connection) => emit('connected', connection),
    onEdgeReconnected: (connection) => emit('edge-reconnected', connection),
    onEdgeWaypointsChanged: (edgeId, points) => emit('edge-waypoints-changed', edgeId, points),
    onSelectionChanged: (selection) => emit('selection-changed', selection),
    onDeleteSelected: (selection) => emit('delete-selected', selection),
    onUndo: () => emit('undo'),
    onRedo: () => emit('redo'),
    onSave: () => emit('save'),
    onReject: (reason) => emit('reject', reason),
  })
  render()
})

// DSL 采用不可变更新（每次操作产出新对象），故浅监听即可精确捕获变更
watch(
  () => props.dsl,
  () => render(),
)
watch(
  () => props.report,
  () => render(),
)

onBeforeUnmount(() => {
  if (graph) {
    destroyGraph(graph)
    graph = null
  }
})

function onDragOver(event) {
  // 允许放置（部分浏览器需要显式设置 dropEffect）
  event.dataTransfer.dropEffect = 'copy'
}

/**
 * 节点库拖入画布：把屏幕坐标换算为画布坐标，事件交给上层创建节点。
 *
 * <p>透传的是<b>条目 id</b>（不是类型）：创建时要靠它取回条目，才知道该预置什么
 * （如「设置状态」的 serviceRef 与节点名）。
 */
function onDrop(event) {
  if (!graph) return
  const presetId = event.dataTransfer?.getData(props.dragDataType)
  if (!presetId) return
  const point = graph.clientToLocal(event.clientX, event.clientY)
  emit('drop-node', { presetId, position: { x: Math.round(point.x), y: Math.round(point.y) } })
}

defineExpose({
  /** 缩放到适应内容（供上层工具栏调用） */
  fit() {
    graph?.zoomToFit({ padding: 32, maxScale: 1 })
  },
  /** 居中内容，避免新模板落在画布外 */
  center() {
    graph?.centerContent()
  },
})
</script>

<style scoped>
.flow-canvas-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}
.flow-canvas {
  width: 100%;
  height: 100%;
  min-height: 320px;
}
.connect-hint {
  position: absolute;
  left: 50%;
  bottom: 14px;
  transform: translateX(-50%);
  padding: 6px 14px;
  font-size: 12px;
  color: #0958d9;
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 16px;
  pointer-events: none;
  white-space: nowrap;
}
/*
 * 关键修复：选中框必须放行指针事件。
 * X6 的选中框渲染在节点上层，且其内层元素负责「拖动移动」，
 * 于是它会盖住节点边缘的端口 —— 结果是「按住端口想连线，实际变成了拖节点」。
 * 关掉内层的指针事件即可：选择视觉仍在，端口恢复可点（节点拖动走 X6 原生节点拖拽）。
 */
.flow-canvas-wrap :deep(.x6-widget-selection-box),
.flow-canvas-wrap :deep(.x6-widget-selection-inner),
.flow-canvas-wrap :deep(.x6-widget-selection-content) {
  pointer-events: none;
}
</style>
