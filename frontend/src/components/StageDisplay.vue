<template>
  <div class="sd-root">
    <div class="sd-display">
      <component :is="stageDef?.icon || ExperimentOutlined" class="sd-icon" :style="{ color: stageDef?.color || '#1677ff' }" />
      <span class="sd-name">{{ displayName }}</span>
      <span v-if="stageDef?.description" class="sd-desc">{{ stageDef.description }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ExperimentOutlined } from '@ant-design/icons-vue'
import { getStageDef, getStageTitle } from '@/utils/stageDefs'

const props = defineProps({
  /** stageOid 值 */
  value: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const displayName = computed(() => getStageTitle(props.value))
const stageDef = computed(() => getStageDef(props.value))
</script>

<style scoped>
.sd-root {
  display: flex;
  align-items: center;
}

.sd-display {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 4px 11px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #f5f5f5;
  font-size: 14px;
  line-height: 1.5715;
  cursor: default;
  user-select: none;
}

.sd-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.sd-name {
  color: #333;
  font-weight: 500;
  white-space: nowrap;
}

.sd-desc {
  color: #bfbfbf;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
