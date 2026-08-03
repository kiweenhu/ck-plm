<template>
  <div class="widget-toolbox">
    <a-input-search
      v-model:value="searchText"
      size="small"
      placeholder="搜索控件..."
      style="margin-bottom:8px"
    />
    <div class="widget-categories">
      <div
        v-for="cat in visibleCategories"
        :key="cat.key"
        class="widget-cat"
      >
        <div class="widget-cat-title" @click="cat.collapsed = !cat.collapsed">
          <CaretRightOutlined
            :style="{
              transform: cat.collapsed ? '' : 'rotate(90deg)',
              fontSize: '10px',
              transition: '0.2s'
            }"
          />
          {{ cat.label }}
        </div>
        <div class="widget-list" v-show="!cat.collapsed">
          <div
            v-for="w in cat.widgets"
            :key="w.type"
            class="widget-item"
            draggable="true"
            @dragstart="$emit('dragstart', $event, w)"
            @dragend="$emit('dragend', $event)"
          >
            <component :is="w.icon" style="font-size:14px;color:#666" />
            <span>{{ w.label }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { CaretRightOutlined } from '@ant-design/icons-vue'
import { getCategorizedWidgets } from '../catalog'

defineEmits(['dragstart', 'dragend'])

const searchText = ref('')

// 每次渲染时基于 catalog.js 重新计算带 collapse 状态的分类列表
const categoriesState = ref(getCategorizedWidgets())

const visibleCategories = computed(() => {
  const kw = searchText.value.toLowerCase()
  if (!kw) return categoriesState.value
  return categoriesState.value
    .map(cat => ({
      ...cat,
      widgets: cat.widgets.filter(
        w => w.label.toLowerCase().includes(kw) || w.type.toLowerCase().includes(kw)
      )
    }))
    .filter(cat => cat.widgets.length)
})

// 暴露重置方法（切换操作时可用）
defineExpose({ resetSearch: () => { searchText.value = '' } })
</script>

<style scoped>
/* 控件分类 */
.widget-categories { display: flex; flex-direction: column; gap: 2px; }
.widget-cat-title {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 8px; font-size: 11px; font-weight: 600; color: #555;
  cursor: pointer; border-radius: 4px; user-select: none;
}
.widget-cat-title:hover { background: #e8ecf1; }
.widget-list { display: flex; flex-direction: column; gap: 2px; padding-left: 16px; }
.widget-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 8px; font-size: 12px; color: #333;
  border: 1px solid transparent; border-radius: 4px;
  cursor: grab; transition: all 0.15s;
}
.widget-item:hover { background: #e6f0fa; border-color: #b3d4f0; }
.widget-item:active { cursor: grabbing; background: #d0e4f7; }
</style>
