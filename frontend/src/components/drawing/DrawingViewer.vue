<template>
  <div class="drawing-viewer" ref="viewerRef">
    <!-- 保真度警告：LibreDWG 跳过的高级实体要显式告知，不能假装预览完整 -->
    <div v-if="fidelityWarnings.length" class="drawing-warnings">
      <span v-for="(w, i) in fidelityWarnings" :key="i">{{ w }}</span>
    </div>

    <div class="drawing-canvas" @wheel.prevent="onWheel" @mousedown="onDown"
         @mousemove="onMove" @mouseup="onUp" @mouseleave="onUp">
      <img v-if="svgUrl" :src="svgUrl" class="drawing-svg"
           :style="{ transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})` }"
           alt="二维图纸预览" draggable="false" />
    </div>

    <div class="drawing-toolbar">
      <a-button size="small" @click="zoomIn">放大</a-button>
      <a-button size="small" @click="zoomOut">缩小</a-button>
      <a-button size="small" @click="reset">复位</a-button>
      <span class="drawing-zoom">{{ Math.round(scale * 100) }}%</span>
      <span v-if="stats" class="drawing-stats">
        实体 {{ stats.entityCount ?? '-' }} · 图层 {{ stats.layerCount ?? '-' }} · {{ stats.dwgVersion || '' }}
      </span>
    </div>
  </div>
</template>

<script setup>
/**
 * 二维图纸预览（MVP）。
 *
 * 服务端 CK-DrawingForge 已把 DWG/DXF 转成 SVG，这里直接 <img> 显示，
 * 支持滚轮缩放 + 拖拽平移。第二阶段可替换为 mxdraw 渲染 DXF（图层开关/测量）。
 */
import { ref, reactive, computed, onBeforeUnmount } from 'vue'

const props = defineProps({
  svgUrl: { type: String, default: '' },
  stats: { type: Object, default: null },
  warnings: { type: [Array, String], default: () => [] }
})

const fidelityWarnings = computed(() => {
  if (Array.isArray(props.warnings)) return props.warnings.filter(Boolean)
  if (typeof props.warnings === 'string' && props.warnings.trim()) return [props.warnings]
  return []
})

const scale = ref(1)
const offset = reactive({ x: 0, y: 0 })
const dragging = ref(false)
let last = { x: 0, y: 0 }

const MIN = 0.2
const MAX = 6

function clamp(v) { return Math.min(MAX, Math.max(MIN, v)) }

function zoomIn() { scale.value = clamp(scale.value * 1.2) }
function zoomOut() { scale.value = clamp(scale.value / 1.2) }
function reset() { scale.value = 1; offset.x = 0; offset.y = 0 }

function onWheel(e) {
  const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15
  scale.value = clamp(scale.value * factor)
}

function onDown(e) {
  dragging.value = true
  last = { x: e.clientX, y: e.clientY }
}
function onMove(e) {
  if (!dragging.value) return
  offset.x += e.clientX - last.x
  offset.y += e.clientY - last.y
  last = { x: e.clientX, y: e.clientY }
}
function onUp() { dragging.value = false }

onBeforeUnmount(() => { dragging.value = false })
</script>

<style scoped>
.drawing-viewer { display: flex; flex-direction: column; height: 100%; background: #fafafa; }
.drawing-warnings {
  padding: 6px 12px; background: #fffbe6; border: 1px solid #ffe58f;
  color: #ad6800; font-size: 12px; display: flex; flex-direction: column; gap: 2px;
}
.drawing-canvas {
  flex: 1; min-height: 0; overflow: hidden; position: relative; cursor: grab;
  display: flex; align-items: center; justify-content: center; user-select: none;
}
.drawing-canvas:active { cursor: grabbing; }
.drawing-svg { max-width: 92%; max-height: 92%; object-fit: contain; transform-origin: center; pointer-events: none; }
.drawing-toolbar {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  border-top: 1px solid #f0f0f0; background: #fff;
}
.drawing-zoom { font-size: 12px; color: #666; min-width: 42px; }
.drawing-stats { font-size: 12px; color: #999; margin-left: auto; }
</style>
