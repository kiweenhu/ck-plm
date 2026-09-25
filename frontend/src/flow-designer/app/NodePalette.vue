<template>
  <div class="node-palette">
    <div v-for="group in groups" :key="group.label" class="palette-group">
      <div class="palette-group__title">{{ group.label }}</div>
      <div
        v-for="item in group.items"
        :key="item.id"
        class="palette-item"
        draggable="true"
        :style="{ borderLeftColor: item.stroke, color: item.textColor }"
        @dragstart="onDragStart($event, item.id)"
      >
        {{ item.label }}
      </div>
    </div>
    <div class="palette-hint">
      <div><b>拖拽</b>节点到画布。</div>
      <div><b>连线</b>：鼠标移到节点上，边缘的圆点会显形，<b>按住某个圆点</b>拖到目标节点即可。</div>
      <div>连线从你按下的那个圆点出发：想接在哪个落点就从哪个圆点起手。按在节点内部是「拖动节点」。</div>
      <div>非法连接会被拒绝并提示原因（如自环、结束节点出边、开始节点入边）。</div>
    </div>
  </div>
</template>

<script setup>
/**
 * 节点库 —— 拖拽源。
 *
 * <p>载荷是<b>条目 id</b>（不是节点类型）：同一类型可能有多个业务入口
 * （「自动服务」与预置好服务的「设置状态」），落点处要靠 id 取回条目才知道该预置什么。
 * 条目 id 缺省等于类型名，所以"一条目一类型"的老条目行为完全不变。
 */
import { presetGroups, presetId, presetLabel } from '@flow-dsl-core'
import { NODE_STYLE } from '@flow-canvas'

const props = defineProps({
  /** 与 FlowCanvas 约定的 dataTransfer 键名（载荷为节点库条目 id） */
  dragDataType: { type: String, default: 'application/x-ckplm-node-preset' },
})

// 条目来自契约层目录：节点库不自行维护清单，分组与顺序只在 dsl-core 声明一次，
// 落点逻辑与节点库不会各写一份
const groups = presetGroups().map(({ group, items }) => ({
  label: group,
  items: items.map((preset) => ({
    id: presetId(preset),
    label: presetLabel(preset),
    stroke: NODE_STYLE[preset.type].stroke,
    textColor: NODE_STYLE[preset.type].textColor,
  })),
}))

function onDragStart(event, presetKey) {
  event.dataTransfer.setData(props.dragDataType, presetKey)
  event.dataTransfer.effectAllowed = 'copy'
}
</script>

<style scoped>
.node-palette {
  width: 176px;
  padding: 10px 10px 14px;
  border-right: 1px solid #f0f0f0;
  overflow-y: auto;
  background: #fff;
}
.palette-group {
  margin-bottom: 12px;
}
.palette-group__title {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 6px;
}
.palette-item {
  padding: 7px 10px;
  margin-bottom: 6px;
  font-size: 13px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-left-width: 3px;
  border-radius: 6px;
  cursor: grab;
  user-select: none;
}
.palette-item:hover {
  background: #f5f5f5;
}
.palette-hint {
  font-size: 11px;
  color: #bfbfbf;
  line-height: 1.6;
  margin-top: 8px;
}
</style>
