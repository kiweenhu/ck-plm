<template>
  <div class="np-root">
    <div v-if="loading" class="np-display np-loading">
      <a-spin size="small" />
      <span class="np-loading-text">生成预编码...</span>
    </div>

    <!-- 编辑场景：已有实际编码，优先展示实际编码 -->
    <div v-else-if="actualCode" class="np-display">
      <BarcodeOutlined class="np-icon" />
      <code class="np-code">{{ actualCode }}</code>
      <span v-if="rule" class="np-rule-name">{{ rule.name || rule.code }}</span>
    </div>

    <div v-else-if="!rule" class="np-display np-empty">
      <BarcodeOutlined class="np-icon np-icon-empty" />
      <span class="np-empty-text">未绑定编码规则</span>
    </div>

    <div v-else class="np-display">
      <BarcodeOutlined class="np-icon" />
      <code class="np-code">{{ previewCode }}</code>
      <span class="np-rule-name">{{ rule.name || rule.code }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Spin } from 'ant-design-vue'
import { BarcodeOutlined } from '@ant-design/icons-vue'
import { getTypeNumberRuleLink, getNumberRule } from '@/api'

const props = defineProps({
  /** 类型定义 oid，用于查询该类型绑定的编码规则 */
  typeDefinitionOid: { type: String, default: null },
  /** 实际编码值（编辑场景，已保存的编码号） */
  value: { type: String, default: '' },
  /** 是否禁用（只读展示，此属性保持接口兼容） */
  disabled: { type: Boolean, default: false },
})

const loading = ref(false)
const rule = ref(null)

// 实际编码（编辑场景已有值）
const actualCode = computed(() => props.value || '')

// 预编码：遍历段拼装，流水码段用 * 代替，日期/分类用真实值
const previewCode = computed(() => {
  const segs = rule.value?.segments || []
  if (!segs.length) return '—'
  return segs.map(seg => {
    switch (seg.segmentType) {
      case 'CONST':
      case 'SEPARATOR':
        return seg.fixedValue || ''
      case 'YEAR':
        return String(new Date().getFullYear())
      case 'MONTH':
        return String(new Date().getMonth() + 1).padStart(2, '0')
      case 'DAY':
        return String(new Date().getDate()).padStart(2, '0')
      case 'SERIAL':
        return '*'.repeat(seg.serialLength || 4)
      case 'CLASSIFICATION': {
        try {
          const cfg = typeof seg.config === 'string' ? JSON.parse(seg.config) : (seg.config || {})
          return cfg.clsPreview || ''
        } catch { return '' }
      }
      default:
        return ''
    }
  }).join('')
})

async function loadRule() {
  if (!props.typeDefinitionOid) {
    rule.value = null
    return
  }
  loading.value = true
  try {
    // 1. 查类型绑定的编码规则 code
    const linkRes = await getTypeNumberRuleLink(props.typeDefinitionOid)
    const link = linkRes?.data || linkRes
    const code = link?.numberRuleCode
    if (!code) {
      rule.value = null
      return
    }
    // 2. 查规则详情（含 segments）
    const ruleRes = await getNumberRule(code)
    rule.value = ruleRes?.data || ruleRes || null
  } catch {
    rule.value = null
  } finally {
    loading.value = false
  }
}

watch(() => props.typeDefinitionOid, () => {
  rule.value = null
  loadRule()
})

onMounted(loadRule)

defineExpose({ loadRule, rule, previewCode })
</script>

<style scoped>
.np-root {
  display: flex;
  align-items: center;
}

.np-display {
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

.np-icon {
  font-size: 16px;
  color: #1677ff;
  flex-shrink: 0;
}

.np-icon-empty {
  color: #bfbfbf;
}

.np-code {
  font-size: 14px;
  font-weight: 600;
  color: #1677ff;
  letter-spacing: 0.5px;
  background: transparent;
  border: none;
  padding: 0;
}

.np-rule-name {
  color: #8c8c8c;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.np-empty-text {
  color: #bfbfbf;
  font-size: 12px;
}

.np-loading-text {
  color: #8c8c8c;
  font-size: 12px;
}
</style>
