<template>
  <div class="lcd-root">
    <div v-if="loading" class="lcd-display lcd-loading">
      <a-spin size="small" />
      <span class="lcd-loading-text">加载生命周期...</span>
    </div>

    <div v-else-if="!template" class="lcd-display lcd-empty">
      <NodeIndexOutlined class="lcd-icon lcd-icon-empty" />
      <span class="lcd-empty-text">未绑定生命周期模板</span>
    </div>

    <div v-else class="lcd-display">
      <NodeIndexOutlined class="lcd-icon" />
      <span class="lcd-tpl-name">{{ template.name || template.code }}</span>
      <a-divider type="vertical" class="lcd-divider" />
      <span class="lcd-state-label">初始状态</span>
      <a-tag color="blue" class="lcd-state-tag">{{ initialStatusName }}</a-tag>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Spin, Tag, Divider } from 'ant-design-vue'
import { NodeIndexOutlined } from '@ant-design/icons-vue'
import { getTypeLifecycleTemplateLink, getLifecycleTemplate } from '@/api'

const props = defineProps({
  /** 类型定义 oid，用于查询该类型绑定的生命周期模板 */
  typeDefinitionOid: { type: String, default: null },
  /** 是否禁用（只读展示，此属性保持接口兼容） */
  disabled: { type: Boolean, default: false },
})

const loading = ref(false)
const template = ref(null)

// 初始状态显示名：优先 statusDisplayName，回退 statusCode
const initialStatusName = computed(() => {
  if (!template.value) return '—'
  const code = template.value.initialStateCode
  if (!code) return '—'
  const st = template.value.states?.find(s => s.statusCode === code)
  return st ? (st.statusDisplayName || st.statusCode) : code
})

async function loadLifecycle() {
  if (!props.typeDefinitionOid) {
    template.value = null
    return
  }
  loading.value = true
  try {
    // 1. 查类型绑定的生命周期模板 code
    const linkRes = await getTypeLifecycleTemplateLink(props.typeDefinitionOid)
    const link = linkRes?.data || linkRes
    const code = link?.lifecycleTemplateCode
    if (!code) {
      template.value = null
      return
    }
    // 2. 查模板详情（含 name、initialStateCode、states）
    const tplRes = await getLifecycleTemplate(code)
    template.value = tplRes?.data || tplRes || null
  } catch {
    template.value = null
  } finally {
    loading.value = false
  }
}

watch(() => props.typeDefinitionOid, () => {
  template.value = null
  loadLifecycle()
})

onMounted(loadLifecycle)

defineExpose({ loadLifecycle, template, initialStatusName })
</script>

<style scoped>
.lcd-root {
  display: flex;
  align-items: center;
}

.lcd-display {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 32px;
  padding: 4px 11px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #f5f5f5;
  font-size: 14px;
  line-height: 1.5715;
  cursor: default;
  user-select: none;
}

.lcd-icon {
  font-size: 16px;
  color: #1677ff;
  flex-shrink: 0;
}

.lcd-icon-empty {
  color: #bfbfbf;
}

.lcd-tpl-name {
  color: #333;
  font-weight: 500;
  white-space: nowrap;
}

.lcd-divider {
  margin: 0;
}

.lcd-state-label {
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
}

.lcd-state-tag {
  margin: 0;
}

.lcd-empty-text {
  color: #bfbfbf;
  font-size: 12px;
}

.lcd-loading-text {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
