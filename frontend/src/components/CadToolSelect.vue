<template>
  <a-select
    :value="value || undefined"
    :disabled="disabled"
    :placeholder="placeholder || defaultPlaceholder"
    :options="options"
    :filter-option="filterOption"
    :allow-clear="allowClear"
    show-search
    style="width: 100%"
    @update:value="onChange"
  />
</template>

<script setup>
import { computed } from 'vue'
import { CAD_TOOL_OPTIONS, CAD_TOOL_PLACEHOLDER } from '@/widgets/cadTools'

/**
 * CAD 工具下拉（PageDesigner 业务组件：mcad-tool-select / ecad-tool-select）
 *
 * 用 kind 区分两套清单（见 @/widgets/cadTools）：
 *   kind = 'MCAD' → 市面上主流机械 CAD 工具（Creo / NX / CATIA / SOLIDWORKS / AutoCAD …）
 *   kind = 'ECAD' → 市面上主流电子 CAD 工具（Altium / Allegro / PADS / KiCad / 立创EDA …）
 *
 * 落库值为工具短码（如 'CREO' / 'ALTIUM'），展示名为「厂商 + 产品」。
 */
const props = defineProps({
  modelValue: { type: String, default: null },
  /** 'MCAD' | 'ECAD' */
  kind: { type: String, default: 'MCAD' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
  allowClear: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue'])

const options = computed(() => CAD_TOOL_OPTIONS[props.kind] || [])
const defaultPlaceholder = computed(() => CAD_TOOL_PLACEHOLDER[props.kind] || '请选择 CAD 工具')

function filterOption(input, option) {
  return String(option?.label || '').toLowerCase().includes(String(input || '').toLowerCase())
}

function onChange(val) {
  emit('update:modelValue', val || null)
}
</script>
